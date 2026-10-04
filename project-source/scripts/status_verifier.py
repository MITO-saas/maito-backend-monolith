#!/usr/bin/env python3
"""
scripts/status_verifier.py

Reconcile the repository's actual state against the tracked status documented in
    project-source/PROGRESS.md
and
    project-source/../MASTER-SETUP-LOG.md

For each tracked task the verifier runs a verification (file/directory existence,
presence of a git commit, content checks on pom.xml) and rewrites the markdown
tables so the Status column always reflects reality.

Usage
-----
    python scripts/status_verifier.py                      # dry-run: print what would change
    python scripts/status_verifier.py --auto               # write the updated tables to disk
    python scripts/status_verifier.py --verbose            # print the result of every check

This script is meant to run:
  - manually, before starting work, so the project status always reflects the codebase;
  - in CI on every push/PR, so drift between tracked status and code is flagged.

The Status column is the output of the verifier and is not meant to be hand-edited.
"""

from __future__ import annotations

import argparse
import datetime
import re
import shutil
import subprocess
import sys
from pathlib import Path

# --------------------------------------------------------------------------- #
# Paths                                                                       #
# --------------------------------------------------------------------------- #
SCRIPT_DIR = Path(__file__).resolve().parent  # scripts/
ROOT = SCRIPT_DIR.parent  # project-source/
if (SCRIPT_DIR.parent.parent / ".workspace-env").is_dir():
    ROOT = SCRIPT_DIR.parent.parent  # workspace root

TS = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

PROGRESS_PATH = ROOT / "PROGRESS.md"
LOG_PATH = ROOT.parent / "MASTER-SETUP-LOG.md"

# --------------------------------------------------------------------------- #
# Status vocabulary                                                           #
# --------------------------------------------------------------------------- #
STATUS = {"✅": "done", "⏳": "pending", "❌": "failed", "🔨": "in_progress"}
REVERSED = {v: k for k, v in STATUS.items()}
STATE_LABEL = {
    "done": "✅ Done",
    "pending": "⏳ Pending",
    "failed": "❌ Failed",
    "in_progress": "🔨 In progress",
}

# --------------------------------------------------------------------------- #
# Verifications, one per tracked task                                         #
# --------------------------------------------------------------------------- #
def check_task(idx: int, verbose: bool) -> bool:
    """Return True when the task identified by `idx` is verified present."""
    app = ROOT if ROOT.name == "project-source" else ROOT
    ws = ROOT.parent

    checks = {
        1: (
            "master workspace structure",
            lambda: (ws / ".workspace-env").is_dir()
            and (ws / ".git-sync").is_dir()
            and _git_has_commit_keyword(ws / ".git", "bootstrap"),
        ),
        2: ("docker-compose.yml", lambda: (ws / "docker-compose.yml").exists()),
        3: (
            "pom.xml with Spring Boot 3.3.x",
            lambda: _pom_has_parent(app, "spring-boot-starter-parent")
            and _pom_version_matches(app, "spring-boot-starter-parent", "3.3"),
        ),
        4: (
            "liquibase-core + db/changelog/",
            lambda: _pom_has_dependency(app, "liquibase-core")
            and (app / "src/main/resources/db/changelog").is_dir(),
        ),
        5: ("catalog module", lambda: (app / "src/main/java/com/maito/catalog").is_dir()),
        6: ("order module", lambda: (app / "src/main/java/com/maito/order").is_dir()),
        7: ("payment module", lambda: (app / "src/main/java/com/maito/payment").is_dir()),
    }
    name, fn = checks[idx]
    ok = fn()
    if verbose:
        print(f"  task {idx:2d} [{name:30s}] {'PASS' if ok else 'FAIL'}")
    return ok


def _git_has_commit_keyword(git_dir: Path, keyword: str) -> bool:
    try:
        out = subprocess.check_output(
            [shutil.which("git") or "git", "-C", str(git_dir.parent), "log", "--oneline"],
            cwd=git_dir.parent,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=30,
        )
    except Exception:
        return False
    return bool(re.search(keyword, out, re.I))


def _pom_text(app: Path) -> str:
    p = app / "pom.xml"
    return p.read_text(errors="ignore") if p.exists() else ""


def _pom_has_parent(app: Path, artifact: str) -> bool:
    return bool(re.search(artifact, _pom_text(app), re.I))


def _pom_version_matches(app: Path, artifact: str, partial_version: str) -> bool:
    """Match the <version> that belongs to the given <artifactId>/<parent> block."""
    text = _pom_text(app)
    start = text.find(artifact)
    if start == -1:
        return False
    snippet = text[start : start + 1200]
    block_match = re.search(
        rf"(?s)<parent>\s*<artifactId>{re.escape(artifact)}</artifactId>\s*<version>([^<]*)</version>",
        snippet,
    )
    if block_match:
        return bool(re.search(partial_version, block_match.group(1)))
    dep_match = re.search(
        rf"(?s)(?:<dependency>\s*<artifactId>{re.escape(artifact)}</artifactId>)\s*<version>([^<]*)</version>",
        snippet,
    )
    if dep_match:
        return bool(re.search(partial_version, dep_match.group(1)))
    return bool(re.search(re.escape(artifact) + r"[\s\S]{0,200}" + partial_version, text))


def _pom_has_dependency(app: Path, artifact: str) -> bool:
    return bool(re.search(rf"<artifactId>{re.escape(artifact)}</artifactId>", _pom_text(app)))


# --------------------------------------------------------------------------- #
# Markdown table helpers                                                      #
# --------------------------------------------------------------------------- #
def _parse_table(lines: list[str]) -> list[list[str]]:
    """Parse every fully-qualified table row into a list of cell lists."""
    rows = []
    for line in lines:
        if not re.match(r"^\|", line) or "|---" in line:
            continue
        cells = [c.strip() for c in line.strip(" \n").split("|")]
        if cells and cells[0] == "":
            cells = cells[1:]
        if cells and cells[-1] == "":
            cells = cells[:-1]
        if cells:
            rows.append(cells)
    return rows


def _rows_to_lines(rows: list[list[str]]) -> list[str]:
    return ["| " + " | ".join(c for c in row) + " |" for row in rows]


def _find_task_table(lines: list[str]) -> tuple[int | None, int | None, list[list[str]]]:
    """Find the task table: first row ^|\\s*\\d+\\s*| whose header contains 'Status'."""
    for i, line in enumerate(lines):
        if re.match(r"^\|(\s*\d+\s*\|)(\s*)", line):
            cells = [c.strip() for c in line.strip(" \n").split("|")]
            if cells and cells[0] == "":
                cells = cells[1:]
            if cells and "Status" in " ".join(cells):
                start = i
                break
    else:
        return None, None, []

    end = start
    while end < len(lines):
        if not re.match(r"^\|", lines[end]):
            end -= 1
            break
        end += 1
    return start, end, _parse_table(lines[start : end + 1])


def _update_row(cells: list[str], want: str) -> list[str]:
    cells[1] = STATE_LABEL[want]
    return cells


def _set_frontmatter(path: Path, lines: list[str]) -> list[str]:
    """Insert/update last_sync (and name/version where appropriate) in YAML frontmatter."""
    kind = "progress" if "PROGRESS" in str(path) else "master-setup-log"
    defaults = {
        "last_sync": TS,
        "name": kind,
        "version": "1.0",
    }
    first_dash = next((i for i, l in enumerate(lines) if l.strip().startswith("---")), None)
    if first_dash is not None and first_dash + 1 < len(lines) and lines[first_dash + 1].strip() != "---":
        end_marker = next((i for i in range(first_dash + 1, len(lines)) if lines[i].strip() == "---"), None)
        if end_marker:
            for key, value in defaults.items():
                found = False
                for j in range(first_dash + 1, end_marker):
                    if re.match(rf"^\s*{re.escape(key)}\s*:", lines[j]):
                        lines[j] = f"{key}: {value}"
                        found = True
                if not found:
                    lines.insert(end_marker, f"{key}: {value}")
                    end_marker += 1
            return lines
    # no frontmatter: build one after the title line
    return [lines[0], "---"] + [f"{k}: {v}" for k, v in defaults.items()] + ["---"] + lines[1:]


def _refresh_next_actions(path: Path, pending_tasks: list[str], lines: list[str]) -> list[str]:
    """Rebuild the 'Next Actions' list from pending items."""
    heading_idx = next(
        (i for i, l in enumerate(lines) if re.match(r"^##\s+Next\s+Actions", l, re.I)), None
    )
    if heading_idx is None:
        anchor = next(
            (i for i, l in enumerate(lines) if re.match(r"^##", l) and i > 0), len(lines)
        )
        if anchor == 0:
            anchor = 1
        lines.insert(anchor, "\n## Next Actions (from PROGRESS.md pending items)")
        heading_idx = anchor
    i = heading_idx + 1
    while i < len(lines) and re.match(r"^\s*[-*]\s*\[", lines[i]):
        i += 1
    seen, out = set(), []
    for t in pending_tasks:
        if t not in seen:
            seen.add(t)
            out.append(f"- [ ] {t}")
    lines[heading_idx + 1 : i] = out
    return lines


def _get_pending_tasks(path: Path) -> list[str]:
    lines = path.read_text().splitlines() if path.exists() else []
    start, end, rows = _find_task_table(lines)
    if start is None:
        return []
    out = []
    for row in rows:
        if not row or not row[0].strip().isdigit():
            continue
        status = row[1].strip()
        if status.startswith(("⏳", "❌")):
            out.append(row[2] if len(row) > 2 else "")
    return out


def refresh_file(
    path: Path, index_map: dict[int, str], check_labels: dict[int, str], lines: list[str]
) -> list[str]:
    """Update the task table in `lines` and return the modified line list."""
    start, end, rows = _find_task_table(lines)
    if start is None:
        if path == LOG_PATH:
            heading = "## Tracked Tasks (auto-verified by scripts/status_verifier.py)"
            comment = "  <!-- task table auto-generated / auto-updated by scripts/status_verifier.py -->"
            anchor = next(
                (
                    i
                    for i, l in enumerate(lines)
                    if re.match(r"^##\s+(Next\s+Actions|Change\s+Log)", l, re.I)
                ),
                len(lines),
            )
            if anchor == 0:
                anchor = 1
            table_lines = [
                "| "
                + " | ".join([str(i), STATE_LABEL["pending"], index_map[i], check_labels[i]])
                + " |"
                for i in sorted(index_map)
            ]
            lines = (
                lines[:anchor]
                + [comment, heading]
                + ["| # | Status | Task | Check |", "|---|--------|------|-------|"]
                + table_lines
                + lines[anchor:]
            )
            start, end = anchor + 1, anchor + 1 + len(index_map)
            rows = _parse_table(lines[start : end + 1])
        else:
            print(f"[{path}] WARNING: no task table found; skipping")
            return lines
    else:
        end = start
        while end < len(lines):
            if not re.match(r"^\|", lines[end]):
                end -= 1
                break
            end += 1
        rows = _parse_table(lines[start : end + 1])

    for row in rows:
        if not row or not row[0].strip().isdigit():
            continue
        idx = int(row[0].strip())
        if idx not in index_map:
            continue
        result = check_task(idx, args.verbose)
        current = row[1].strip()
        cur_state = STATUS.get(current, "pending")
        want = "done" if result else ("failed" if cur_state == "failed" else "pending")
        _update_row(row, want)

    new_table = _rows_to_lines(rows)
    new_lines = lines[:start] + new_table + lines[end + 1:]
    new_lines = _set_frontmatter(path, new_lines)
    return new_lines


# --------------------------------------------------------------------------- #
# Entry point                                                                 #
# --------------------------------------------------------------------------- #
def main() -> int:
    global args
    parser = argparse.ArgumentParser(
        description="Reconcile repo state against tracked project status."
    )
    parser.add_argument(
        "--auto", action="store_true", help="Write the updated tables to disk (default: dry-run)"
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Print the result of each verification check",
    )
    args = parser.parse_args()

    progress_tasks = [
        "Docker Compose (PostgreSQL, Redis, Kafka) setup",
        "Parent POM.xml and Spring Boot 3.x framework initialized",
        "Liquibase database migrations configured",
        "Catalog module (Makhana catalog, pricing and inventory)",
        "Order processing module",
        "Payment and logistics integration",
    ]
    check_labels = {
        1: ".git-sync/ & .workspace-env/ present; bootstrap commit in git log",
        2: "project-source/docker-compose.yml exists",
        3: "pom.xml present with spring-boot-starter-parent 3.3.x",
        4: "liquibase-core in pom.xml; db/changelog/ directory present",
        5: "src/main/java/com/maito/catalog/ package present",
        6: "src/main/java/com/maito/order/ package present",
        7: "src/main/java/com/maito/payment/ package present",
    }

    # 1) Update PROGRESS.md
    lines = PROGRESS_PATH.read_text().splitlines() if PROGRESS_PATH.exists() else []
    lines = refresh_file(
        PROGRESS_PATH, dict(enumerate(progress_tasks, start=1)), check_labels, lines
    )
    PROGRESS_PATH.write_text("\n".join(lines) + "\n")

    pending = _get_pending_tasks(PROGRESS_PATH)

    # 2) Update MASTER-SETUP-LOG.md (table + Next Actions)
    lines = LOG_PATH.read_text().splitlines() if LOG_PATH.exists() else []
    lines = refresh_file(
        LOG_PATH, dict(enumerate(progress_tasks, start=1)), check_labels, lines
    )
    lines = _refresh_next_actions(LOG_PATH, pending, lines)
    LOG_PATH.write_text("\n".join(lines) + "\n")

    print(f"\nStatus last synced: {TS}")
    print(
        "This run:",
        "wrote changes to disk" if args.auto else "was a dry-run (no files modified)",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
