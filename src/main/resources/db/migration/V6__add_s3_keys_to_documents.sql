-- Adiciona colunas para armazenar as chaves S3 dos arquivos gerados.
-- A chave S3 é permanente; a presigned URL é gerada sob demanda e expira.
ALTER TABLE documents ADD COLUMN pdf_s3_key VARCHAR(500);
ALTER TABLE documents ADD COLUMN docx_s3_key VARCHAR(500);
