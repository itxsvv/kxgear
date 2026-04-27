#!/usr/bin/env bash
set -euo pipefail

# Resolve repo root by walking up to the .specify directory so the script works
# regardless of the caller's current working directory.
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

if [ $# -lt 3 ]; then
  echo "Usage: $0 <spec-id> <cr-number> <short-name>"
  echo "Example: $0 001-my-feature 001 move-filter-to-meilisearch"
  exit 1
fi

SPEC_ID="$1"
CR_NUMBER="$2"
SHORT_NAME="$3"

SPEC_DIR="${REPO_ROOT}/specs/${SPEC_ID}"
CHANGES_DIR="${SPEC_DIR}/changes"
CR_FILE="${CHANGES_DIR}/CR-${CR_NUMBER}-${SHORT_NAME}.md"

if [ ! -d "$SPEC_DIR" ]; then
  echo "Spec directory not found: $SPEC_DIR"
  exit 1
fi

mkdir -p "$CHANGES_DIR"

if [ -f "$CR_FILE" ]; then
  echo "CR already exists: $CR_FILE"
  exit 1
fi

cat > "$CR_FILE" <<EOF
# CR-${CR_NUMBER}: ${SHORT_NAME}

## Status
Proposed

## Reason
Why are we changing this?

## Current Behavior
What happens now?

## Proposed Change
What should happen instead?

## Spec Changes
- Add:
  - ...
- Change:
  - ...
- Remove:
  - ...

## Acceptance Criteria
- [ ] ...
- [ ] ...

## Impact
- API:
- DB:
- Performance:
- Risks:

## Follow-up
- Update spec.md
- Regenerate plan.md
- Regenerate tasks.md
EOF

echo "Created: $CR_FILE"
