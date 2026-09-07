#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/build.sh
python3 scripts/validate_exact.py
python3 scripts/run_experiments.py --config config/smoke_grid.csv --out results/raw/smoke_raw.csv --modes HUOPM,UOB_POSTFILTER,UOB_FULL --heap 2g
python3 scripts/aggregate_results.py results/raw/smoke_raw.csv results/summary/smoke_summary.csv
