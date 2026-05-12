#!/bin/bash
set -euo pipefail

echo ">>> [ValidateService] Checking backend health..."

MAX_RETRIES=30
RETRY_INTERVAL=5
HEALTH_URL="http://localhost:8080/api/actuator/health"

for i in $(seq 1 $MAX_RETRIES); do
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")

  if [ "$HTTP_STATUS" = "200" ]; then
    echo ">>> [ValidateService] Backend is healthy! (attempt $i)"
    exit 0
  fi

  echo ">>> Waiting for backend... (attempt $i/$MAX_RETRIES, status=$HTTP_STATUS)"
  sleep $RETRY_INTERVAL
done

echo ">>> [ValidateService] ERROR: Backend did not become healthy in time!"
echo ">>> Docker logs:"
docker logs contrato-ia-backend --tail 50 2>&1 || true
exit 1
