#!/bin/bash
set -euo pipefail

REGION="sa-east-1"
PROJECT="contrato-ia"
ENVIRONMENT="production"

echo ">>> [ApplicationStart] Starting services..."

# Read the image tag
IMAGE=$(cat /opt/contrato-ia/deploy/IMAGE_TAG)

# Get secrets from Secrets Manager
SECRETS=$(aws secretsmanager get-secret-value \
  --region "$REGION" \
  --secret-id "${PROJECT}/${ENVIRONMENT}/app-secrets" \
  --query SecretString --output text)

# Export all env vars needed by docker-compose
export DB_USERNAME=$(echo "$SECRETS" | jq -r '.DB_USERNAME')
export DB_PASSWORD=$(echo "$SECRETS" | jq -r '.DB_PASSWORD')
export CLAUDE_API_KEY=$(echo "$SECRETS" | jq -r '.CLAUDE_API_KEY')
export KEYCLOAK_ISSUER_URI=$(echo "$SECRETS" | jq -r '.KEYCLOAK_ISSUER_URI')
export BACKEND_IMAGE="$IMAGE"
export SQS_QUEUE_URL="${SQS_QUEUE_URL:-placeholder}"
export S3_BUCKET="${S3_BUCKET:-contrato-ia-production-documents}"

cd /opt/contrato-ia

# Start/update all services (PostgreSQL + Backend)
docker compose up -d

# Cleanup old images
docker image prune -f

echo ">>> [ApplicationStart] Services started."
