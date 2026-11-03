#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
curl --fail-with-body -sS -X POST "$BASE/api/v1/manifests/generate-pdf" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' \
  --data-binary @examples/request.json | tee manifest-link.json
printf '\nBuka URL pada field url di browser dan masukkan password DDMMYYYY (hari ini WIB).\n'
