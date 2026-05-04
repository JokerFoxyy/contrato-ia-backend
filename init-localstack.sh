#!/bin/bash
# Cria recursos AWS no LocalStack para desenvolvimento local

echo "=== Criando bucket S3 ==="
awslocal s3 mb s3://contrato-ia-docs
awslocal s3api put-bucket-versioning \
  --bucket contrato-ia-docs \
  --versioning-configuration Status=Enabled
echo "Bucket contrato-ia-docs criado com versionamento ativado."

echo "=== Criando filas SQS ==="
# DLQ (Dead Letter Queue) — mensagens que falharam 3x
awslocal sqs create-queue \
  --queue-name contrato-ia-generation-dlq.fifo \
  --attributes '{"FifoQueue":"true","ContentBasedDeduplication":"true","MessageRetentionPeriod":"1209600"}'

# Fila principal com redrive policy apontando para DLQ
DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/contrato-ia-generation-dlq.fifo \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

awslocal sqs create-queue \
  --queue-name contrato-ia-generation.fifo \
  --attributes "{\"FifoQueue\":\"true\",\"ContentBasedDeduplication\":\"true\",\"VisibilityTimeout\":\"120\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

echo "Filas SQS criadas: contrato-ia-generation.fifo + DLQ"
echo "=== LocalStack pronto ==="
