#!/usr/bin/env python3
"""Append an entry to wiki/log.md with a real timestamp and the right format.

Using this instead of hand-writing entries keeps timestamps honest — a guessed
clock time destroys the log's value as a recovery record.

Usage:
    python3 log_entry.py phase "Refactored the parser" --status DONE
    python3 log_entry.py phase "Refactoring parser" \\
        --status "IN PROGRESS" --next "extract tokenize() then re-run tests"
    python3 log_entry.py ingest "Booking API retry logic"
    python3 log_entry.py lint "Checked for orphan pages"
"""

from __future__ import annotations  # keeps `Path | None` valid on Python 3.8/3.9

import argparse
import sys
from datetime import datetime
from pathlib import Path

KINDS = ["phase", "ingest", "query", "lint", "init"]


def find_log(start: Path) -> Path | None:
    """Find wiki/log.md at or above `start`, without escaping the repository.

    Walking upward is what lets this run from any subdirectory. But an unbounded
    walk is dangerous when several projects are nested or sit side by side under
    a common parent: a repo that has no wiki of its own would silently write
    into a *different* project's log. So the walk stops at the first directory
    holding a .git — that is this repository's ceiling.
    """
    for directory in [start, *start.parents]:
        candidate = directory / "wiki" / "log.md"
        if candidate.is_file():
            return candidate
        if (directory / ".git").exists():
            break  # repo root reached; anything above belongs to someone else
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=KINDS, help="entry type")
    parser.add_argument("description", help="what happened, one line")
    parser.add_argument(
        "--status",
        help="DONE | IN PROGRESS | BLOCKED: <reason> (phase entries)",
    )
    parser.add_argument(
        "--next",
        dest="next_step",
        help="the next step; required when status is not DONE",
    )
    parser.add_argument("--repo", default=".", help="repository root (default: cwd)")
    args = parser.parse_args()

    start = Path(args.repo).resolve()
    log = find_log(start)
    if log is None:
        print(
            f"error: no wiki/log.md at or above {start} (search stopped at the\n"
            "repository root). Run init_wiki.py first, or pass --repo pointing\n"
            "at the project whose wiki you meant to write to.",
            file=sys.stderr,
        )
        return 1

    unfinished = args.status and args.status.strip().upper() != "DONE"
    if unfinished and not args.next_step:
        print(
            "error: --next is required when the work is not DONE.\n"
            "An unfinished entry without a next step cannot be resumed from,\n"
            "which is the whole point of the checkpoint.",
            file=sys.stderr,
        )
        return 1

    stamp = datetime.now().strftime("%Y-%m-%d %H:%M")
    line = f"## [{stamp}] {args.kind} | {args.description}"
    if args.status:
        line += f" — {args.status}"

    with log.open("a", encoding="utf-8") as fh:
        fh.write(f"\n{line}\n")
        if args.next_step:
            fh.write(f"- next: {args.next_step}\n")

    # Name the file written to, so a wrong target is obvious immediately rather
    # than discovered later when the log turns out to be missing entries.
    print(f"{line}\n  → {log}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
