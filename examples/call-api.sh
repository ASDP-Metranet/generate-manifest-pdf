#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
: "${MANIFEST_API_USERNAME:?set MANIFEST_API_USERNAME}"
: "${MANIFEST_API_PASSWORD:?set MANIFEST_API_PASSWORD}"
curl --fail-with-body -sS -X POST "$BASE/api/v1/manifests/generate-pdf" \
  -u "$MANIFEST_API_USERNAME:$MANIFEST_API_PASSWORD" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' \
  --data-binary @examples/request.json | tee manifest-link.json
printf '\nBuka URL pada field url di browser dan masukkan password DDMMYYYY (hari ini WIB).\n'
