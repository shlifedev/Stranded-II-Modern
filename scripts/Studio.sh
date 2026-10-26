#!/bin/sh
set -eu
studio_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd -- "$studio_dir"

if [ -n "${S2_STUDIO_PYTHON:-}" ]; then
    studio_python=$S2_STUDIO_PYTHON
elif [ -f .venv/bin/python ]; then
    studio_python=.venv/bin/python
elif [ -f .venv/Scripts/python.exe ]; then
    studio_python=.venv/Scripts/python.exe
elif command -v python3 >/dev/null 2>&1; then
    studio_python=python3
elif command -v python >/dev/null 2>&1; then
    studio_python=python
else
    echo "Python 3.12 or newer is required. Install Python and try again." >&2
    exit 1
fi

exec "$studio_python" -X utf8 src/devtools/studio_launch.py "$@"
