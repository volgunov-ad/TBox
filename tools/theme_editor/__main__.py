"""python -m theme_editor  (run from tools/ or with PYTHONPATH=tools)."""

from __future__ import annotations

import sys
from pathlib import Path


def main() -> int:
    # Allow `python -m theme_editor` from repo root or tools/.
    here = Path(__file__).resolve().parent
    parent = str(here.parent)
    if parent not in sys.path:
        sys.path.insert(0, parent)
    if str(here) not in sys.path:
        sys.path.insert(0, str(here))
    from app import main as app_main

    return app_main()


if __name__ == "__main__":
    raise SystemExit(main())
