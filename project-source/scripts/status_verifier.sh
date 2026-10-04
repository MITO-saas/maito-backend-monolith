#!/usr/bin/env bash
# scripts/status_verifier.sh
#
# Reconcile the repository state against PROGRESS.md / MASTER-SETUP-LOG.md tables.
# Updates the "Status" column to match reality: ✅ Done / ⏳ Pending / ❌ Failed.
#
# Usage:
#   ./scripts/status_verifier.sh           # dry-run (default): show what would change
#   ./scripts/status_verifier.sh --auto    # write the updates to disk
#   ./scripts/status_verifier.sh --verbose # print each check result

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"             # project-source/
WS_ROOT="$(cd "$ROOT/.." && pwd)"                # workspace root

PROGRESS="$ROOT/PROGRESS.md"
LOG="$WS_ROOT/MASTER-SETUP-LOG.md"
TS=$(TZ=UTC date -u '+%Y-%m-%d %H:%M:%S UTC')

AUTO=0
VERBOSE=0
while [[ $# -gt 0 ]]; do
  case $1 in
    --auto) AUTO=1 ;;
    --verbose|-v) VERBOSE=1 ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
  shift
done

# --------------------------------------------------------------------------- #
# Check functions for each task (exit 0 = verified)
# --------------------------------------------------------------------------- #
check_1() {   # workspace: .workspace-env + .git-sync + bootstrap commit
  [[ -d "$WS_ROOT/.workspace-env" ]] && \
  [[ -d "$WS_ROOT/.git-sync" ]] && \
  git -C "$WS_ROOT" log --oneline 2>/dev/null | grep -iq "bootstrap"
}

check_2() { [[ -f "$WS_ROOT/docker-compose.yml" ]]; }

check_3() {   # pom.xml has Spring Boot 3.3.x
  [[ -f "$ROOT/pom.xml" ]] && \
  grep -q "spring-boot-starter-parent" "$ROOT/pom.xml" && \
  grep -q "3\.3\.[0-9]" "$ROOT/pom.xml"
}

check_4() {   # liquibase-core + db/changelog dir
  [[ -f "$ROOT/pom.xml" ]] && \
  grep -q "liquibase-core" "$ROOT/pom.xml" && \
  [[ -d "$ROOT/src/main/resources/db/changelog" ]]
}

check_5() { [[ -d "$ROOT/src/main/java/com/maito/catalog" ]]; }
check_6() { [[ -d "$ROOT/src/main/java/com/maito/order" ]]; }
check_7() { [[ -d "$ROOT/src/main/java/com/maito/payment" ]]; }

# --------------------------------------------------------------------------- #
# --check N : run check_$N, exit 0 = done, exit 1 = pending  (used by awk)
# --------------------------------------------------------------------------- #
if [[ "${1:-}" == "--check" ]]; then
  shift
  if check_$1 > /dev/null 2>&1; then echo "done"; exit 0; else echo "pending"; exit 1; fi
fi

# --------------------------------------------------------------------------- #
# Verbose run
# --------------------------------------------------------------------------- #
if [[ $VERBOSE -eq 1 ]]; then
  descs=(
    [1]="workspace structure"
    [2]="docker-compose.yml"
    [3]="pom.xml spring-boot 3.3.x"
    [4]="liquibase-core + db/changelog"
    [5]="catalog module"
    [6]="order module"
    [7]="payment module"
  )
  for i in 1 2 3 4 5 6 7; do
    if check_$i > /dev/null 2>&1; then
      echo "task $i [${descs[$i]}] PASS"
    else
      echo "task $i [${descs[$i]}] FAIL"
    fi
  done
fi

# --------------------------------------------------------------------------- #
# Replace the Status cell (3rd column) of a task table row, preserving the rest
# --------------------------------------------------------------------------- #
replace_status() {
  local line=$1 new_status=$2
  # Split on '|', trim, rebuild: "| <num> | <status> | <rest...> |"
  echo "$line" | awk -F'|' -v s="$new_status" '
  {
    for (i=1; i<=NF; i++) { gsub(/^[ \t]+|[ \t]+$/, "", $i) }
    # drop a trailing empty field produced by the leading/trailing "|"
    while (NF > 0 && $NF == "") NF--
    printf "| %s | %s |", $2, s
    for (i=4; i<=NF; i++) printf " %s |", $i
    printf "\n"
  }'
}

# --------------------------------------------------------------------------- #
# Update a single file's task table
# --------------------------------------------------------------------------- #
update_table() {
  local file=$1
  local tmp
  tmp=$(mktemp)

  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" =~ ^\|[[:space:]]*([0-9]+)[[:space:]]*\|[[:space:]]*(✅[[:space:]]*Done|⏳[[:space:]]*Pending|❌[[:space:]]*Failed|🔨[[:space:]]*In[[:space:]]*progress)[[:space:]]*\| ]]; then
      num="${BASH_REMATCH[1]}"
      if check_$num > /dev/null 2>&1; then
        new="✅ Done"
      else
        new="⏳ Pending"
      fi
      line=$(replace_status "$line" "$new")
    fi
    echo "$line"
  done < "$file" > "$tmp"

  if [[ $AUTO -eq 1 ]]; then
    cp "$tmp" "$file"
    echo "[write] $file updated"
  else
    echo "[dry-run] $file would be updated"
  fi
  rm -f "$tmp"
}

# --------------------------------------------------------------------------- #
# Update YAML frontmatter: add/refresh last_sync (and name/version)
# --------------------------------------------------------------------------- #
update_frontmatter() {
  local file=$1 kind tmp
  kind=$(basename "$file" .md)
  [[ "$kind" == "PROGRESS" ]] && kind="progress" || kind="master-setup-log"
  tmp=$(mktemp)

  awk -v ts="$TS" -v kind="$kind" '
  BEGIN { in_fm=0; fm_done=0; found_last=0 }
  /^---/ {
    if (in_fm==0) { in_fm=1; print; next }
    else { in_fm=0; if (!fm_done) { print "last_sync: " ts } print "---"; in_fm=2; next }
  }
  in_fm==1 {
    if (/last_sync:/) { print "last_sync: " ts; found_last=1 }
    else print
    next
  }
  in_fm==0 && /^---/ { print }  # safety catch
  in_fm!=1 { print }
  ' "$file" > "$tmp"

  # If no frontmatter found at all, prepend one after the title line
  if ! grep -q "^---" "$tmp"; then
    local with_fm
    with_fm=$(mktemp)
    {
      head -n 1 "$tmp"
      echo "---"
      echo "last_sync: $TS"
      echo "name: $kind"
      echo "version: 1.0"
      echo "---"
      tail -n +2 "$tmp"
    } > "$with_fm"
    mv "$with_fm" "$tmp"
  fi

  if [[ $AUTO -eq 1 ]]; then
    cp "$tmp" "$file"
    echo "[write] frontmatter updated in $file"
  fi
  rm -f "$tmp"
}

# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #
echo "Status last synced: $TS"
update_table "$PROGRESS"
update_table "$LOG"
update_frontmatter "$PROGRESS"
update_frontmatter "$LOG"
