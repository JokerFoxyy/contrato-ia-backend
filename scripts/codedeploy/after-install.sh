#!/bin/bash
set -euo pipefail

REGION="sa-east-1"
PROJECT="contrato-ia"
ENVIRONMENT="production"

echo ">>> [AfterInstall] Pulling new Docker image..."

# Read the image tag from deployment artifact
IMAGE_TAG_FILE="/opt/contrato-ia/deploy/IMAGE_TAG"
if [ ! -f "$IMAGE_TAG_FILE" ]; then
  echo "ERROR: IMAGE_TAG file not found!"
  exit 1
fi
IMAGE=$(cat "$IMAGE_TAG_FILE")
echo ">>> Image: $IMAGE"

# Get secrets from Secrets Manager
echo ">>> Fetching secrets from Secrets Manager..."
SECRETS=$(aws secretsmanager get-secret-value \
  --region "$REGION" \
  --secret-id "${PROJECT}/${ENVIRONMENT}/app-secrets" \
  --query SecretString --output text)

# Export secrets as env vars
export DB_USERNAME=$(echo "$SECRETS" | jq -r '.DB_USERNAME')
export DB_PASSWORD=$(echo "$SECRETS" | jq -r '.DB_PASSWORD')
export CLAUDE_API_KEY=$(echo "$SECRETS" | jq -r '.CLAUDE_API_KEY')
export KEYCLOAK_ISSUER_URI=$(echo "$SECRETS" | jq -r '.KEYCLOAK_ISSUER_URI')

# Get GHCR credentials from Secrets Manager (stored separately)
GHCR_SECRETS=$(aws secretsmanager get-secret-value \
  --region "$REGION" \
  --secret-id "${PROJECT}/${ENVIRONMENT}/ghcr-credentials" \
  --query SecretString --output text 2>/dev/null || echo '{}')

GHCR_USER=$(echo "$GHCR_SECRETS" | jq -r '.GHCR_USER // empty')
GHCR_TOKEN=$(echo "$GHCR_SECRETS" | jq -r '.GHCR_TOKEN // empty')

# Login to GHCR if credentials exist
if [ -n "$GHCR_USER" ] && [ -n "$GHCR_TOKEN" ]; then
  echo ">>> Logging in to GHCR..."
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
fi

# Pull the new image
echo ">>> Pulling image: $IMAGE"
docker pull "$IMAGE"

echo ">>> [AfterInstall] Image pulled successfully."
