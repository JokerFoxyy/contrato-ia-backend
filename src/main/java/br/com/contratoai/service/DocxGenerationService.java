package br.com.contratoai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class DocxGenerationService {

    private static final String FONT_BODY = "Calibri";
    private static final int FONT_SIZE_BODY = 11;
    private static final int FONT_SIZE_H1 = 16;
    private static final int FONT_SIZE_H2 = 13;
    private static final int FONT_SIZE_H3 = 11;
    private static final int FONT_SIZE_FOOTER = 9;

    private final Parser markdownParser;

    public DocxGenerationService() {
        this.markdownParser = Parser.builder().build();
    }

    /**
     * Gera um DOCX editavel a partir do conteudo textual/Markdown do contrato.
     */
    public byte[] generate(String content, String title, UUID documentId) {
        log.info("Gerando DOCX para documento {}", documentId);

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            configurePageLayout(document);
            addHeader(document, title);
            addBody(document, content);
            addFooter(document, documentId);

            document.write(outputStream);

            byte[] docx = outputStream.toByteArray();
            log.info("DOCX gerado com sucesso para documento {}. Tamanho: {} bytes", documentId, docx.length);
            return docx;

        } catch (Exception e) {
            log.error("Falha ao gerar DOCX para documento {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar DOCX: " + e.getMessage(), e);
        }
    }

    private void configurePageLayout(XWPFDocument document) {
        CTSectPr sectPr = document.getDocument().getBody().addNewSectPr();

        // A4: 210mm x 297mm em twips (1mm = 56.7 twips)
        CTPageSz pageSize = sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906)); // 210mm
        pageSize.setH(BigInteger.valueOf(16838)); // 297mm

        // Margens: 2.5cm top/bottom, 2cm left/right
        CTPageMar margins = sectPr.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1418));    // 2.5cm
        margins.setBottom(BigInteger.valueOf(1701));  // 3cm
        margins.setLeft(BigInteger.valueOf(1134));    // 2cm
        margins.setRight(BigInteger.valueOf(1134));   // 2cm
    }

    private void addHeader(XWPFDocument document, String title) {
        // Titulo centralizado
        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setAlignment(ParagraphAlignment.CENTER);
        titleParagraph.setSpacingAfter(200);

        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setText(title.toUpperCase());
        titleRun.setBold(true);
        titleRun.setFontFamily(FONT_BODY);
        titleRun.setFontSize(FONT_SIZE_H1);
        titleRun.setCharacterSpacing(30);

        // Data
        String today = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.of("pt", "BR")));

        XWPFParagraph dateParagraph = document.createParagraph();
        dateParagraph.setAlignment(ParagraphAlignment.CENTER);
        dateParagraph.setSpacingAfter(100);

        XWPFRun dateRun = dateParagraph.createRun();
        dateRun.setText("Gerado em " + today);
        dateRun.setFontFamily(FONT_BODY);
        dateRun.setFontSize(FONT_SIZE_FOOTER);
        dateRun.setColor("666666");

        // Linha separadora
        XWPFParagraph separator = document.createParagraph();
        separator.setBorderBottom(Borders.SINGLE);
        separator.setSpacingAfter(400);
    }

    private void addBody(XWPFDocument document, String content) {
        Node parsed = markdownParser.parse(content);
        Node node = parsed.getFirstChild();

        while (node != null) {
            if (node instanceof Heading heading) {
                addHeading(document, heading);
            } else if (node instanceof Paragraph paragraph) {
                addParagraph(document, paragraph);
            } else if (node instanceof BulletList bulletList) {
                addList(document, bulletList, false);
            } else if (node instanceof OrderedList orderedList) {
                addList(document, orderedList, true);
            } else if (node instanceof ThematicBreak) {
                XWPFParagraph hr = document.createParagraph();
                hr.setBorderBottom(Borders.SINGLE);
                hr.setSpacingAfter(200);
            } else {
                // Fallback: extrair texto e adicionar como paragrafo
                String text = extractText(node);
                if (!text.isBlank()) {
                    addSimpleParagraph(document, text);
                }
            }
            node = node.getNext();
        }
    }

    private void addHeading(XWPFDocument document, Heading heading) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(300);
        paragraph.setSpacingAfter(150);

        int fontSize = switch (heading.getLevel()) {
            case 1 -> {
                paragraph.setAlignment(ParagraphAlignment.CENTER);
                yield FONT_SIZE_H1;
            }
            case 2 -> FONT_SIZE_H2;
            default -> FONT_SIZE_H3;
        };

        XWPFRun run = paragraph.createRun();
        String headingText = extractText(heading);
        run.setText(heading.getLevel() == 1 ? headingText.toUpperCase() : headingText);
        run.setBold(true);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(fontSize);
    }

    private void addParagraph(XWPFDocument document, Paragraph mdParagraph) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setSpacingAfter(120);
        paragraph.setIndentationFirstLine(850); // ~1.5cm indentacao

        // Percorre os filhos inline do paragrafo
        Node inline = mdParagraph.getFirstChild();
        while (inline != null) {
            if (inline instanceof Text text) {
                XWPFRun run = paragraph.createRun();
                run.setText(text.getLiteral());
                run.setFontFamily(FONT_BODY);
                run.setFontSize(FONT_SIZE_BODY);
            } else if (inline instanceof StrongEmphasis) {
                XWPFRun run = paragraph.createRun();
                run.setText(extractText(inline));
                run.setBold(true);
                run.setFontFamily(FONT_BODY);
                run.setFontSize(FONT_SIZE_BODY);
            } else if (inline instanceof Emphasis) {
                XWPFRun run = paragraph.createRun();
                run.setText(extractText(inline));
                run.setItalic(true);
                run.setFontFamily(FONT_BODY);
                run.setFontSize(FONT_SIZE_BODY);
            } else if (inline instanceof SoftLineBreak || inline instanceof HardLineBreak) {
                XWPFRun run = paragraph.createRun();
                run.addBreak();
            } else {
                // Fallback para outros inline nodes
                String text = extractText(inline);
                if (!text.isEmpty()) {
                    XWPFRun run = paragraph.createRun();
                    run.setText(text);
                    run.setFontFamily(FONT_BODY);
                    run.setFontSize(FONT_SIZE_BODY);
                }
            }
            inline = inline.getNext();
        }
    }

    private void addList(XWPFDocument document, Node list, boolean ordered) {
        Node item = list.getFirstChild();
        int counter = 1;

        while (item != null) {
            if (item instanceof ListItem) {
                String text = extractText(item).trim();
                String prefix = ordered ? counter + ". " : "• "; // bullet

                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setAlignment(ParagraphAlignment.BOTH);
                paragraph.setSpacingAfter(60);
                paragraph.setIndentationLeft(720); // ~1.27cm

                XWPFRun run = paragraph.createRun();
                run.setText(prefix + text);
                run.setFontFamily(FONT_BODY);
                run.setFontSize(FONT_SIZE_BODY);

                counter++;
            }
            item = item.getNext();
        }
    }

    private void addSimpleParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setSpacingAfter(120);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(FONT_SIZE_BODY);
    }

    private void addFooter(XWPFDocument document, UUID documentId) {
        XWPFParagraph separator = document.createParagraph();
        separator.setBorderTop(Borders.SINGLE);
        separator.setSpacingBefore(600);

        XWPFParagraph footer = document.createParagraph();
        footer.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun footerRun = footer.createRun();
        footerRun.setText("Documento gerado por ContratoIA - plataforma de contratos juridicos com inteligencia artificial");
        footerRun.setFontFamily(FONT_BODY);
        footerRun.setFontSize(8);
        footerRun.setColor("999999");

        XWPFParagraph idParagraph = document.createParagraph();
        idParagraph.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun idRun = idParagraph.createRun();
        idRun.setText("ID: " + documentId.toString().substring(0, 8));
        idRun.setFontFamily(FONT_BODY);
        idRun.setFontSize(7);
        idRun.setColor("999999");
    }

    /**
     * Extrai todo o texto de um Node Markdown recursivamente.
     */
    private String extractText(Node node) {
        StringBuilder sb = new StringBuilder();
        extractTextRecursive(node, sb);
        return sb.toString();
    }

    private void extractTextRecursive(Node node, StringBuilder sb) {
        if (node instanceof Text text) {
            sb.append(text.getLiteral());
        } else if (node instanceof SoftLineBreak) {
            sb.append(" ");
        } else if (node instanceof HardLineBreak) {
            sb.append("\n");
        }

        Node child = node.getFirstChild();
        while (child != null) {
            extractTextRecursive(child, sb);
            child = child.getNext();
        }
    }
}
