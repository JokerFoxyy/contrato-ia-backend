package br.com.contratoai.service;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class PdfGenerationService {

    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public PdfGenerationService() {
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
    }

    /**
     * Gera um PDF a partir do conteúdo textual/Markdown do contrato.
     *
     * @param content    texto gerado pelo Claude (Markdown ou texto puro)
     * @param title      titulo do documento
     * @param documentId ID do documento para identificacao no rodape
     * @return bytes do PDF gerado
     */
    public byte[] generate(String content, String title, UUID documentId) {
        log.info("Gerando PDF para documento {}", documentId);

        String html = buildHtmlDocument(content, title, documentId);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);

            byte[] pdf = outputStream.toByteArray();
            log.info("PDF gerado com sucesso para documento {}. Tamanho: {} bytes", documentId, pdf.length);
            return pdf;

        } catch (Exception e) {
            log.error("Falha ao gerar PDF para documento {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Converte o conteudo Markdown para HTML e envolve em um template XHTML
     * profissional com estilos para contrato juridico.
     */
    String buildHtmlDocument(String content, String title, UUID documentId) {
        // Converte Markdown -> HTML
        Node document = markdownParser.parse(content);
        String bodyHtml = htmlRenderer.render(document);

        String today = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("pt", "BR")));

        // Template XHTML valido (requisito do Flying Saucer)
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                  <title>%s</title>
                  <style type="text/css">
                    @page {
                      size: A4;
                      margin: 2.5cm 2cm 3cm 2cm;
                      @bottom-center {
                        content: "Pagina " counter(page) " de " counter(pages);
                        font-size: 9pt;
                        color: #666666;
                      }
                      @bottom-right {
                        content: "Doc: %s";
                        font-size: 7pt;
                        color: #999999;
                      }
                    }

                    body {
                      font-family: "Helvetica", "Arial", sans-serif;
                      font-size: 11pt;
                      line-height: 1.6;
                      color: #1a1a1a;
                      text-align: justify;
                    }

                    .header {
                      text-align: center;
                      border-bottom: 2px solid #333333;
                      padding-bottom: 15px;
                      margin-bottom: 25px;
                    }

                    .header h1 {
                      font-size: 16pt;
                      font-weight: bold;
                      text-transform: uppercase;
                      letter-spacing: 1px;
                      margin: 0;
                      color: #1a1a1a;
                    }

                    .header .date {
                      font-size: 9pt;
                      color: #666666;
                      margin-top: 8px;
                    }

                    .content h1 {
                      font-size: 14pt;
                      font-weight: bold;
                      text-transform: uppercase;
                      margin-top: 20px;
                      margin-bottom: 10px;
                    }

                    .content h2 {
                      font-size: 12pt;
                      font-weight: bold;
                      margin-top: 18px;
                      margin-bottom: 8px;
                    }

                    .content h3 {
                      font-size: 11pt;
                      font-weight: bold;
                      margin-top: 15px;
                      margin-bottom: 6px;
                    }

                    .content p {
                      margin-bottom: 10px;
                      text-indent: 1.5cm;
                    }

                    .content ul, .content ol {
                      margin-left: 1cm;
                      margin-bottom: 10px;
                    }

                    .content li {
                      margin-bottom: 4px;
                    }

                    .content strong {
                      font-weight: bold;
                    }

                    .content em {
                      font-style: italic;
                    }

                    .footer-signature {
                      margin-top: 50px;
                      page-break-inside: avoid;
                    }

                    .watermark {
                      text-align: center;
                      font-size: 8pt;
                      color: #999999;
                      margin-top: 30px;
                      border-top: 1px solid #cccccc;
                      padding-top: 10px;
                    }
                  </style>
                </head>
                <body>
                  <div class="header">
                    <h1>%s</h1>
                    <div class="date">Gerado em %s</div>
                  </div>

                  <div class="content">
                    %s
                  </div>

                  <div class="watermark">
                    Documento gerado por ContratoIA - plataforma de contratos juridicos com inteligencia artificial
                  </div>
                </body>
                </html>
                """.formatted(
                escapeXml(title),
                documentId.toString().substring(0, 8),
                escapeXml(title),
                today,
                bodyHtml
        );
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
