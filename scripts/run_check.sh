#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/build.sh
python3 scripts/validate_exact.py
python3 scripts/validate_randomized.py
