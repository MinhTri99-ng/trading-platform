# Trading Platform

## Real BTCUSDT 1h historical data workflow

This project does not fabricate candles and does not allow synthetic demo series to masquerade as historical research data.

### 1) Put a real Binance export in the raw-data folder

Copy the real BTCUSDT 1h CSV export from Binance into this path:

python/data/raw/BTCUSDT_1h.csv

Recommended raw export columns:

timestamp,open,high,low,close,volume

Example export format from Binance 1h candles:

2024-01-01 00:00:00,100.0,101.0,99.5,100.5,1234.0
2024-01-01 01:00:00,100.5,101.5,99.0,101.0,1300.0

### 2) Run the canonical ingestion command

From the project root:

PYTHONPATH=. python -m python.scripts.ingest_btcusdt_data python/data/raw/BTCUSDT_1h.csv --output-dir python/data/processed --output-name BTCUSDT_1h_clean.csv

This command will:
- load the raw CSV
- normalize timestamps to UTC
- remove duplicate candles deterministically
- keep the original missing-candle evidence
- save the cleaned file to:
  python/data/processed/BTCUSDT_1h_clean.csv
- write a dataset-quality JSON report next to it

### 3) Validate the generated file

PYTHONPATH=. python python/scripts/validate_data.py python/data/processed/BTCUSDT_1h_clean.csv --timeframe 1h

### 4) Run walk-forward only when the dataset is large enough

The CLI now includes a minimum-row gate. It will refuse to run backtests if the dataset is too small.

PYTHONPATH=. python python/walk_forward.py --dataset python/data/processed/BTCUSDT_1h_clean.csv --min-rows 200

If the file is still too small, the script exits cleanly and reports the exact limitation instead of running a fake walk-forward backtest.

### 5) If the existing local file is still only a sample

The repo currently contains a tiny sample and should not be treated as valid research history. Replace it with a real Binance export and rerun the same commands above.

### Minimum-row rule

For this project, a usable historical dataset must be large enough to support multiple walk-forward windows. The default minimum is 200 rows, and the CLI also requires enough candles for the train/test window itself.

This prevents accidental backtests on a short demo or placeholder file.

