-- V8: Adiciona hash SHA-256 do conteúdo gerado para verificação de integridade
-- Garante que o contrato não foi adulterado após a geração pela IA

ALTER TABLE documents ADD COLUMN content_hash VARCHAR(64);

COMMENT ON COLUMN documents.content_hash IS 'SHA-256 hash do conteúdo gerado pela IA, para verificação de integridade';
