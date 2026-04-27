#!/usr/bin/env bash
set -euo pipefail

# Resolve repo root so the script can validate paths from any cwd.
SCRIPT_DIR="$(CDPATH="" cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEARCH_DIR="$SCRIPT_DIR"
while [ "$SEARCH_DIR" != "/" ] && [ ! -d "$SEARCH_DIR/.specify" ]; do
  SEARCH_DIR="$(dirname "$SEARCH_DIR")"
done

if [ ! -d "$SEARCH_DIR/.specify" ]; then
  echo "Unable to locate repo root from: $SCRIPT_DIR"
  exit 1
fi

REPO_ROOT="$SEARCH_DIR"

if [ $# -lt 2 ]; then
  echo "Usage: $0 <spec-id> <cr-file>"
  exit 1
fi

SPEC_ID="$1"
CR_FILE_INPUT="$2"
SPEC_FILE="${REPO_ROOT}/specs/${SPEC_ID}/spec.md"

if [[ "$CR_FILE_INPUT" = /* ]]; then
  CR_FILE="$CR_FILE_INPUT"
else
  CR_FILE="${REPO_ROOT}/${CR_FILE_INPUT}"
fi

if [ ! -f "$SPEC_FILE" ]; then
  echo "Spec file not found: $SPEC_FILE"
  exit 1
fi

if [ ! -f "$CR_FILE" ]; then
  echo "CR file not found: $CR_FILE"
  exit 1
fi

cat <<EOF
Read ${CR_FILE} and ${SPEC_FILE}.

Analyze:
1. Which sections of spec.md are impacted.
2. Exact requirement changes (add/change/remove).
3. Whether plan.md must change.
4. Whether tasks.md must change.

Do not modify anything.
Return a precise change plan only.
EOF
