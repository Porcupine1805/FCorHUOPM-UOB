@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
call scripts\build.bat
if errorlevel 1 exit /b 1
python scripts\system_info.py > results\system_info.json
python scripts\run_experiments.py --config config\selected9_grid.csv --out results\raw\selected9_raw.csv --modes HUOPM,CORHUOPM_AC,CORHUOPM_BOND,COUPM_KULC_ADAPTED,UOB_POSTFILTER,UOB_FULL,UOB_NO_EDA,UOB_NO_UO_BOUND,UOB_EAGER_DUO,UOB_RECOMPUTE_DUO --heap 4g
if errorlevel 1 exit /b 1
python scripts\aggregate_results.py results\raw\selected9_raw.csv results\summary\selected9_summary.csv
if errorlevel 1 exit /b 1
python scripts\paired_tests.py results\raw\selected9_raw.csv --a UOB_FULL --b UOB_POSTFILTER --out results\summary\selected9_uob_full_vs_postfilter_tests.csv
python scripts\paired_tests.py results\raw\selected9_raw.csv --a UOB_FULL --b UOB_NO_EDA --out results\summary\selected9_full_vs_no_eda_tests.csv
python scripts\paired_tests.py results\raw\selected9_raw.csv --a UOB_FULL --b UOB_NO_UO_BOUND --out results\summary\selected9_full_vs_no_uo_bound_tests.csv
python scripts\paired_tests.py results\raw\selected9_raw.csv --a UOB_FULL --b UOB_EAGER_DUO --out results\summary\selected9_full_vs_eager_duo_tests.csv
python scripts\paired_tests.py results\raw\selected9_raw.csv --a UOB_FULL --b UOB_RECOMPUTE_DUO --out results\summary\selected9_full_vs_recompute_duo_tests.csv
python scripts\journal_artifacts.py --summary results\summary\selected9_summary.csv --manifest datasets\real\dataset_cleaning_manifest.csv --config config\selected9_grid.csv --figures figures --tables results\tables --datasets BMSPOS2,Chess,Foodmart,Kosarak,Mushroom,Retail,Pumsb,T10I4N4KD100K,T10I4N4KD500K
