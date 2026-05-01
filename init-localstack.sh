#!/bin/bash
# Cria o bucket S3 no LocalStack para desenvolvimento local
echo "Criando bucket S3 no LocalStack..."
awslocal s3 mb s3://contrato-ia-docs
awslocal s3api put-bucket-versioning \
  --bucket contrato-ia-docs \
  --versioning-configuration Status=Enabled
echo "Bucket contrato-ia-docs criado com versionamento ativado."
