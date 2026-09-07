#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/run_check.sh
./scripts/run_smoke.sh
echo
echo "Correctness and smoke checks finished."
echo "The full selected-9 journal run is long; use ./scripts/run_selected9.sh"
