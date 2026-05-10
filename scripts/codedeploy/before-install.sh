#!/bin/bash
set -euo pipefail

echo ">>> [BeforeInstall] Cleaning up previous deployment artifacts..."

# Remove old deploy directory (CodeDeploy recreates it)
rm -rf /opt/contrato-ia/deploy

echo ">>> [BeforeInstall] Done."
