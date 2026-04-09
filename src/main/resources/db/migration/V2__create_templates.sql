CREATE TABLE templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    system_prompt TEXT NOT NULL,
    fields_schema TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    requires_pro BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_templates_category ON templates(category);
CREATE INDEX idx_templates_active ON templates(is_active);

-- Templates iniciais
INSERT INTO templates (name, description, category, system_prompt, requires_pro) VALUES
(
    'Contrato de Prestação de Serviços',
    'Para freelancers e prestadores de serviço em geral',
    'SERVICOS',
    'Gere um contrato de prestação de serviços completo para o mercado brasileiro. Inclua: identificação das partes, objeto do contrato, obrigações do prestador e contratante, valor e forma de pagamento, prazo de entrega, propriedade intelectual, confidencialidade, rescisão, multas e penalidades, e foro de eleição.',
    FALSE
),
(
    'Contrato de Desenvolvimento de Software',
    'Específico para desenvolvedores e agências de tecnologia',
    'TECNOLOGIA',
    'Gere um contrato de desenvolvimento de software completo. Inclua: especificação técnica do projeto, metodologia de desenvolvimento, entregáveis e cronograma, direitos de propriedade intelectual (especialmente cessão de código-fonte), manutenção e suporte pós-entrega, SLA, confidencialidade de dados (LGPD), e cláusulas específicas para software.',
    FALSE
),
(
    'Contrato de Trabalho CLT',
    'Contrato formal de emprego regido pela CLT',
    'TRABALHO',
    'Gere um contrato de trabalho regido pela CLT (Consolidação das Leis do Trabalho) brasileiro. Inclua: identificação das partes, cargo e função, remuneração e benefícios, jornada de trabalho, local de trabalho, período de experiência, obrigações do empregado e empregador, e conformidade com a legislação trabalhista vigente.',
    FALSE
),
(
    'NDA - Acordo de Confidencialidade',
    'Proteja informações confidenciais em negociações',
    'NDA',
    'Gere um Acordo de Não Divulgação (NDA) completo. Inclua: definição de informações confidenciais, obrigações das partes, exceções à confidencialidade, prazo de vigência, penalidades pelo descumprimento, e conformidade com a LGPD.',
    FALSE
),
(
    'Contrato de Parceria Comercial',
    'Para parcerias entre empresas ou profissionais',
    'PARCERIA',
    'Gere um contrato de parceria comercial completo. Inclua: objeto da parceria, responsabilidades de cada parte, divisão de receitas e despesas, exclusividade (se aplicável), gestão conjunta, prazo e renovação, resolução de conflitos, e cláusulas de saída.',
    TRUE
);
