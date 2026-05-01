-- Remove coluna redundante: a contagem mensal é calculada via
-- DocumentRepository.countDocumentsSince() a cada request.
ALTER TABLE users DROP COLUMN IF EXISTS documents_this_month;
