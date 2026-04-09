CREATE TABLE signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    signer_email VARCHAR(255) NOT NULL,
    signer_name VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    external_envelope_id VARCHAR(255),
    signature_url VARCHAR(1000),
    signed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_signatures_document_id ON signatures(document_id);
CREATE INDEX idx_signatures_signer_email ON signatures(signer_email);
CREATE INDEX idx_signatures_status ON signatures(status);
