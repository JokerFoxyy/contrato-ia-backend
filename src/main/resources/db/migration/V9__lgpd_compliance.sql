-- V9: LGPD Compliance — campos de consentimento, soft delete e retenção de dados

-- Adiciona campos de consentimento e controle de exclusão no users
ALTER TABLE users ADD COLUMN privacy_consent_at TIMESTAMP;
ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMP;
ALTER TABLE users ADD COLUMN data_deleted_at TIMESTAMP;
ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMP;

COMMENT ON COLUMN users.privacy_consent_at IS 'Data/hora em que o usuário aceitou a política de privacidade (LGPD Art. 7)';
COMMENT ON COLUMN users.terms_accepted_at IS 'Data/hora em que o usuário aceitou os termos de uso';
COMMENT ON COLUMN users.data_deleted_at IS 'Data/hora em que os dados pessoais foram anonimizados (direito ao apagamento)';
COMMENT ON COLUMN users.deletion_requested_at IS 'Data/hora em que o usuário solicitou exclusão de dados';

-- Índice para buscar contas marcadas para exclusão
CREATE INDEX idx_users_deletion_requested ON users(deletion_requested_at) WHERE deletion_requested_at IS NOT NULL;
