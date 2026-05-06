-- Tabela de auditoria para rastreabilidade e compliance (LGPD)
-- Registros são imutáveis — nunca devem ser atualizados ou deletados

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action VARCHAR(50) NOT NULL,
    user_id UUID,
    resource_type VARCHAR(30),
    resource_id UUID,
    details JSONB,
    client_ip VARCHAR(45),
    request_id VARCHAR(8),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Índices para queries frequentes
CREATE INDEX idx_audit_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_action ON audit_logs (action);
CREATE INDEX idx_audit_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at);

COMMENT ON TABLE audit_logs IS 'Registro imutável de auditoria para compliance e rastreabilidade';
COMMENT ON COLUMN audit_logs.action IS 'Ação realizada (ex: DOCUMENT_GENERATION_REQUESTED)';
COMMENT ON COLUMN audit_logs.details IS 'Detalhes adicionais em JSON (flexível por tipo de ação)';
COMMENT ON COLUMN audit_logs.request_id IS 'Correlação com logs HTTP via MDC';
