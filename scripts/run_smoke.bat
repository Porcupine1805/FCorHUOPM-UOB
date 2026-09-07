@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
call scripts\build.bat
if errorlevel 1 exit /b 1
python scripts\validate_exact.py
if errorlevel 1 exit /b 1
python scripts\run_experiments.py --config config\smoke_grid.csv --out results\raw\smoke_raw.csv --modes HUOPM,UOB_POSTFILTER,UOB_FULL --heap 2g
if errorlevel 1 exit /b 1
python scripts\aggregate_results.py results\raw\smoke_raw.csv results\summary\smoke_summary.csv
