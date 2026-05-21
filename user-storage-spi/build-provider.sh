#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "${SCRIPT_DIR}"
mvn -q -DskipTests package

echo "Built provider jar:"
echo "  ${SCRIPT_DIR}/target/nexacore-keycloak-user-storage-spi-1.0.0.jar"
