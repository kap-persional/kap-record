#!/usr/bin/env python3
"""Bootstrap a wiki/ memory directory into a repository.

Copies the templates from ../assets into <repo>/wiki, creates raw/ and pages/,
and stamps the init entry into log.md with the real clock time.

Usage:
    python3 init_wiki.py --repo /path/to/repo
    python3 init_wiki.py --repo /path/to/repo --force
"""

import argparse
import shutil
import sys
from datetime import datetime
from pathlib import Path

TEMPLATES = ["SCHEMA.md", "index.md", "log.md", "README.md"]
SUBDIRS = ["raw", "pages"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=".", help="repository root (default: cwd)")
    parser.add_argument(
        "--force",
        action="store_true",
        help=(
            "re-copy the templates over an existing wiki/. This REPLACES "
            "SCHEMA.md, index.md, log.md and README.md — the whole log history "
            "and the page catalog are lost. Files under raw/ and pages/ survive "
            "on disk but stop being listed anywhere."
        ),
    )
    args = parser.parse_args()

    assets = Path(__file__).resolve().parent.parent / "assets"
    if not assets.is_dir():
        print(f"error: templates not found at {assets}", file=sys.stderr)
        return 1

    wiki = Path(args.repo).resolve() / "wiki"
    if wiki.exists() and not args.force:
        print(
            f"error: {wiki} already exists.\n"
            "Refusing to overwrite an existing memory. Pass --force only if you\n"
            "have confirmed with the user that discarding it is intended.",
            file=sys.stderr,
        )
        return 1

    if wiki.exists() and args.force:
        # Spell out the damage before doing it: the log is the recovery record,
        # and losing it silently is exactly the failure this tool exists to stop.
        doomed = [n for n in TEMPLATES if (wiki / n).is_file()]
        kept = sum(1 for sub in SUBDIRS for _ in (wiki / sub).glob("*.md"))
        print(f"--force: replacing {', '.join(doomed)} in {wiki}")
        print(f"         log history and page catalog will be lost")
        print(f"         {kept} file(s) under raw/ and pages/ are left untouched")

    for sub in SUBDIRS:
        (wiki / sub).mkdir(parents=True, exist_ok=True)
        # keep the empty dirs in git
        (wiki / sub / ".gitkeep").touch()

    for name in TEMPLATES:
        src = assets / name
        if not src.is_file():
            print(f"error: missing template {src}", file=sys.stderr)
            return 1
        shutil.copy2(src, wiki / name)

    stamp = datetime.now().strftime("%Y-%m-%d %H:%M")
    with (wiki / "log.md").open("a", encoding="utf-8") as fh:
        fh.write(
            f"\n## [{stamp}] init | Khởi tạo bộ nhớ wiki theo mô hình "
            f"LLM Wiki (Karpathy) — DONE\n"
        )

    print(f"Initialized {wiki}")
    print("Next steps:")
    print("  1. Append assets/CLAUDE-snippet.md to the project's CLAUDE.md")
    print("  2. Review wiki/SCHEMA.md with the user and adapt the page types")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
