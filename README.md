# FCorHUOPM-UOB

Reproducibility package for **Utility-Occupancy Bond for Exact Correlated High-Utility Occupancy Pattern Mining**, submitted to the *International Journal of Data Science and Analytics* (JDSA).

Public archive: https://github.com/Porcupine1805/FCorHUOPM-UOB

This folder contains only what is needed to build the miner, check correctness, and reproduce the selected nine-dataset JDSA study. It does not include manuscript LaTeX, Word drafts, unused datasets, duplicate result snapshots, or legacy HUOPM sources.

## Layout

```
src/                 Java 17 miner
scripts/             build, validation, experiment runner, aggregation, plots
config/              selected-9 grid and a one-run smoke grid
datasets/toy/        10-transaction running example
datasets/real/       nine cleaned benchmark files used in the paper
results/raw/         900 measured rows (9 datasets x 10 modes x 10 repetitions)
results/summary/     medians, IQRs, CIs, paired tests
results/validation/  exhaustive and randomized exact checks
figures/             manuscript figures regenerated from the selected-9 summary
```

## Requirements

- JDK 17 or newer (JDK 21 LTS recommended)
- Python 3.10+
- `matplotlib` (`python -m pip install -r requirements.txt`)
- 8 GB RAM minimum; 16 GB recommended for the full selected-9 run

No external Java mining library is required.

## Quick correctness check

Windows:

```bat
scripts\build.bat
python scripts\validate_exact.py
python scripts\validate_randomized.py
scripts\run_smoke.bat
```

Linux/macOS:

```bash
./scripts/build.sh
python3 scripts/validate_exact.py
python3 scripts/validate_randomized.py
./scripts/run_smoke.sh
```

The exact check matches all UO-Bond variants against exhaustive enumeration on the 10-transaction running database. The randomized check runs 20 fixed seeds (4,940 one-item anti-monotonicity tests).

## Reproduce the selected-9 study

This is the long journal protocol (10 modes, 10 fresh-JVM repetitions, 4 GB heap):

```bat
scripts\run_selected9.bat
```

```bash
./scripts/run_selected9.sh
```

Recorded environment for the committed numbers: Windows 11, Java 24.0.1, Python 3.13.3, 10 logical processors, ~16 GB RAM (`results/system_info.json`).

## Implemented modes

| Mode | Role |
|---|---|
| `HUOPM` | Support + utility occupancy, same vertical engine |
| `CORHUOPM_AC` | HUOP + AllConfidence (adapted control) |
| `CORHUOPM_BOND` | HUOP + ordinary Bond (adapted control) |
| `COUPM_KULC_ADAPTED` | HUOP + Kulczynski (adapted control) |
| `UOB_POSTFILTER` | HUOP mining then exact UO-Bond filtering |
| `UOB_FULL` | Proposed FCorHUOPM-UOB |
| `UOB_NO_EDA`, `UOB_NO_UO_BOUND`, `UOB_EAGER_DUO`, `UOB_RECOMPUTE_DUO` | Ablations |

The runner asserts that every `UOB_*` mode on the same dataset returns the same pattern count and SHA-256.

## Datasets

The paper uses BMSPOS2, Chess, Foodmart, Kosarak, Mushroom, Retail, Pumsb, T10I4N4KD100K, and T10I4N4KD500K. Thresholds are in `config/selected9_grid.csv` and copied into every raw result row.

## License

MIT License. See `LICENSE`.

## Citation

See `CITATION.cff`.
