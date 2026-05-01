package br.com.contratoai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfGenerationServiceTest {

    private PdfGenerationService pdfGenerationService;

    @BeforeEach
    void setUp() {
        pdfGenerationService = new PdfGenerationService();
    }

    @Test
    @DisplayName("generate - should produce valid PDF bytes from Markdown content")
    void generate_validMarkdown() {
        String content = """
                # CONTRATO DE PRESTACAO DE SERVICOS

                ## CLAUSULA PRIMEIRA - DAS PARTES

                **CONTRATANTE:** [NOME DO CONTRATANTE], inscrito no CPF sob o n. [CPF].

                **CONTRATADA:** [NOME DA CONTRATADA], inscrita no CNPJ sob o n. [CNPJ].

                ## CLAUSULA SEGUNDA - DO OBJETO

                O presente contrato tem por objeto a prestacao de servicos de desenvolvimento de software.

                1. Item um
                2. Item dois
                3. Item tres
                """;

        byte[] pdf = pdfGenerationService.generate(content, "Contrato de Prestacao de Servicos", UUID.randomUUID());

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
        // PDF header magic bytes: %PDF
        assertThat(pdf[0]).isEqualTo((byte) '%');
        assertThat(pdf[1]).isEqualTo((byte) 'P');
        assertThat(pdf[2]).isEqualTo((byte) 'D');
        assertThat(pdf[3]).isEqualTo((byte) 'F');
    }

    @Test
    @DisplayName("generate - should handle plain text without Markdown formatting")
    void generate_plainText() {
        String content = "Este e um contrato simples sem formatacao Markdown.";

        byte[] pdf = pdfGenerationService.generate(content, "Contrato Simples", UUID.randomUUID());

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("generate - should handle long content with multiple sections")
    void generate_longContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("## CLAUSULA ").append(i).append("\n\n");
            sb.append("Paragrafo da clausula ").append(i).append(" com texto detalhado ");
            sb.append("sobre as obrigacoes e direitos das partes envolvidas.\n\n");
        }

        byte[] pdf = pdfGenerationService.generate(sb.toString(), "Contrato Extenso", UUID.randomUUID());

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(1000); // PDF longo deve ter tamanho razoavel
    }

    @Test
    @DisplayName("generate - should handle content with special characters")
    void generate_specialCharacters() {
        String content = """
                # Contrato com Caracteres Especiais

                Valor: R$ 1.500,00 (mil e quinhentos reais)

                - Percentual: 15%
                - Referencia: Art. 421 do Codigo Civil
                """;

        byte[] pdf = pdfGenerationService.generate(content, "Contrato Especial", UUID.randomUUID());

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("buildHtmlDocument - should produce valid XHTML with title and content")
    void buildHtmlDocument_structure() {
        UUID docId = UUID.randomUUID();
        String html = pdfGenerationService.buildHtmlDocument(
                "**Texto em negrito**", "Titulo do Contrato", docId);

        assertThat(html).contains("Titulo do Contrato");
        assertThat(html).contains("<strong>Texto em negrito</strong>");
        assertThat(html).contains("ContratoIA");
        assertThat(html).contains(docId.toString().substring(0, 8));
        assertThat(html).contains("xmlns=\"http://www.w3.org/1999/xhtml\"");
    }
}
