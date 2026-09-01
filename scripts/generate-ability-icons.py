#!/usr/bin/env python3
"""Back-compat entrypoint — delegates to generate-mmo-icons.py."""
from pathlib import Path
import runpy

runpy.run_path(str(Path(__file__).resolve().parent / "generate-mmo-icons.py"), run_name="__main__")
