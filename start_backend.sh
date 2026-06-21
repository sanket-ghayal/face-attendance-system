#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$SCRIPT_DIR/python_backend/venv"
if [ ! -d "$VENV" ]; then
    echo "❌  Virtual environment not found. Run: bash install.sh"
    exit 1
fi
source "$VENV/bin/activate"
echo "Python: $(python --version)"
cd "$SCRIPT_DIR/python_backend"
python app.py
