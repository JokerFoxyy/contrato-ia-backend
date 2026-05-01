package br.com.contratoai.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocxGenerationServiceTest {

    private DocxGenerationService docxGenerationService;

    @BeforeEach
    void setUp() {
        docxGenerationService = new DocxGenerationService();
    }

    @Test
    @DisplayName("generate - should produce valid DOCX bytes from Markdown content")
    void generate_validMarkdown() throws Exception {
        String content = """
                # CONTRATO DE PRESTACAO DE SERVICOS

                ## CLAUSULA PRIMEIRA - DAS PARTES

                **CONTRATANTE:** Fulano de Tal, inscrito no CPF sob o n. 123.456.789-00.

                ## CLAUSULA SEGUNDA - DO OBJETO

                O presente contrato tem por objeto a prestacao de servicos.
                """;

        byte[] docx = docxGenerationService.generate(content, "Contrato de Servicos", UUID.randomUUID());

        assertThat(docx).isNotNull();
        assertThat(docx.length).isGreaterThan(0);

        // Verifica que e um ZIP valido (DOCX = ZIP)
        assertThat(docx[0]).isEqualTo((byte) 'P');
        assertThat(docx[1]).isEqualTo((byte) 'K');

        // Abre e valida o conteudo
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            assertThat(paragraphs).isNotEmpty();

            // Primeiro paragrafo deve ser o titulo em maiusculas
            String firstText = paragraphs.get(0).getText();
            assertThat(firstText).isEqualTo("CONTRATO DE SERVICOS");
        }
    }

    @Test
    @DisplayName("generate - should handle plain text content")
    void generate_plainText() throws Exception {
        String content = "Este e um contrato simples sem formatacao Markdown.";

        byte[] docx = docxGenerationService.generate(content, "Contrato Simples", UUID.randomUUID());

        assertThat(docx).isNotNull();

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            assertThat(paragraphs).isNotEmpty();

            // Deve conter o texto em algum paragrafo
            boolean foundContent = paragraphs.stream()
                    .anyMatch(p -> p.getText().contains("contrato simples"));
            assertThat(foundContent).isTrue();
        }
    }

    @Test
    @DisplayName("generate - should include footer with ContratoIA branding")
    void generate_includesFooter() throws Exception {
        UUID docId = UUID.randomUUID();
        String content = "## Clausula Unica\n\nConteudo do contrato.";

        byte[] docx = docxGenerationService.generate(content, "Contrato Teste", docId);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            boolean foundBranding = paragraphs.stream()
                    .anyMatch(p -> p.getText().contains("ContratoIA"));
            assertThat(foundBranding).isTrue();

            boolean foundId = paragraphs.stream()
                    .anyMatch(p -> p.getText().contains(docId.toString().substring(0, 8)));
            assertThat(foundId).isTrue();
        }
    }

    @Test
    @DisplayName("generate - should handle lists correctly")
    void generate_withLists() throws Exception {
        String content = """
                ## Obrigacoes

                - Entregar o servico no prazo
                - Manter sigilo das informacoes
                - Fornecer relatorios mensais

                1. Primeiro item numerado
                2. Segundo item numerado
                """;

        byte[] docx = docxGenerationService.generate(content, "Contrato com Listas", UUID.randomUUID());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            // Deve encontrar bullet items
            boolean foundBullet = paragraphs.stream()
                    .anyMatch(p -> p.getText().contains("Entregar o servico"));
            assertThat(foundBullet).isTrue();

            // Deve encontrar numbered items
            boolean foundNumbered = paragraphs.stream()
                    .anyMatch(p -> p.getText().contains("Primeiro item numerado"));
            assertThat(foundNumbered).isTrue();
        }
    }

    @Test
    @DisplayName("generate - should handle long content without error")
    void generate_longContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("## CLAUSULA ").append(i).append("\n\n");
            sb.append("Paragrafo da clausula ").append(i).append(" com texto detalhado.\n\n");
        }

        byte[] docx = docxGenerationService.generate(sb.toString(), "Contrato Extenso", UUID.randomUUID());

        assertThat(docx).isNotNull();
        assertThat(docx.length).isGreaterThan(1000);
    }
}
