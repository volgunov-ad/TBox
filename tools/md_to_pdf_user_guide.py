#!/usr/bin/env python3
"""Собрать docs/USER_GUIDE_RU.pdf из docs/USER_GUIDE_RU.md (Chrome headless)."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    import markdown
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Нужен пакет markdown: pip install markdown") from exc

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SRC = ROOT / "docs" / "USER_GUIDE_RU.md"
DEFAULT_PDF = ROOT / "docs" / "USER_GUIDE_RU.pdf"

CSS = """
  @page { size: A4; margin: 18mm 16mm 20mm 16mm; }
  html, body {
    font-family: "Noto Sans", "DejaVu Sans", sans-serif;
    font-size: 10.5pt;
    line-height: 1.45;
    color: #1a1a1a;
  }
  h1 {
    font-size: 20pt;
    margin: 0 0 0.6em;
    line-height: 1.2;
    border-bottom: 2px solid #333;
    padding-bottom: 0.35em;
  }
  h2 {
    font-size: 13.5pt;
    margin: 1.4em 0 0.55em;
    page-break-after: avoid;
    border-bottom: 1px solid #ccc;
    padding-bottom: 0.2em;
  }
  h3 {
    font-size: 11.5pt;
    margin: 1.1em 0 0.4em;
    page-break-after: avoid;
  }
  p, ul, ol, table { margin: 0.45em 0 0.7em; }
  ul, ol { padding-left: 1.35em; }
  li { margin: 0.2em 0; }
  strong { font-weight: 700; }
  hr { border: none; border-top: 1px solid #ddd; margin: 1.2em 0; }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 9.5pt;
    page-break-inside: avoid;
  }
  th, td {
    border: 1px solid #bbb;
    padding: 0.35em 0.5em;
    vertical-align: top;
    text-align: left;
  }
  th { background: #f0f0f0; font-weight: 700; }
  code {
    font-family: "Noto Sans Mono", "DejaVu Sans Mono", monospace;
    font-size: 0.92em;
    background: #f5f5f5;
    padding: 0.05em 0.25em;
    border-radius: 2px;
  }
  a { color: #1a1a1a; text-decoration: none; }
  /* Оглавление — список сразу после первого h2 «Содержание» */
  h2 + ol { margin-top: 0.3em; }
"""


def strip_external_links(md_text: str) -> str:
    """Убрать markdown-ссылки на файлы/URL; оставить только текст подписи."""

    def repl(match: re.Match[str]) -> str:
        label, target = match.group(1), match.group(2).strip()
        if target.startswith("#"):
            return match.group(0)
        return label

    return re.sub(r"\[([^\]]+)\]\(([^)]+)\)", repl, md_text)


def build_html(md_text: str) -> str:
    body = markdown.markdown(
        strip_external_links(md_text),
        extensions=["tables", "fenced_code", "sane_lists", "smarty"],
    )
    return (
        "<!DOCTYPE html>\n<html lang=\"ru\"><head><meta charset=\"utf-8\"/>"
        "<title>Руководство пользователя TBox Monitor</title>"
        f"<style>{CSS}</style></head><body>\n{body}\n</body></html>\n"
    )


def find_chrome() -> str:
    for name in ("google-chrome", "google-chrome-stable", "chromium", "chromium-browser"):
        path = Path("/usr/bin") / name
        if path.exists():
            return str(path)
        which = subprocess.run(["which", name], capture_output=True, text=True)
        if which.returncode == 0 and which.stdout.strip():
            return which.stdout.strip()
    raise SystemExit("Не найден Chrome/Chromium для печати PDF")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--src", type=Path, default=DEFAULT_SRC)
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF)
    args = parser.parse_args()

    if not args.src.is_file():
        print(f"Нет файла: {args.src}", file=sys.stderr)
        return 1

    html = build_html(args.src.read_text(encoding="utf-8"))
    chrome = find_chrome()
    args.pdf.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="tbox-ug-pdf-") as tmp:
        html_path = Path(tmp) / "guide.html"
        html_path.write_text(html, encoding="utf-8")
        userdata = Path(tmp) / "chrome-data"
        userdata.mkdir()
        cmd = [
            chrome,
            "--headless",
            "--disable-gpu",
            f"--user-data-dir={userdata}",
            "--no-pdf-header-footer",
            f"--print-to-pdf={args.pdf.resolve()}",
            html_path.resolve().as_uri(),
        ]
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        if proc.returncode != 0 and not args.pdf.is_file():
            print(proc.stdout, proc.stderr, file=sys.stderr)
            return proc.returncode or 1

    if not args.pdf.is_file() or args.pdf.stat().st_size < 1000:
        print("PDF не создан или слишком маленький", file=sys.stderr)
        return 1

    print(f"OK: {args.pdf} ({args.pdf.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
