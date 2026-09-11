#!/usr/bin/env bash
set -euo pipefail

STAMP="$(date -u +%Y%m%d-%H%M%S)"
BRANCH="$(git branch --show-current)"
SAFE_BRANCH="$(printf '%s' "$BRANCH" | tr -c 'A-Za-z0-9._-' '_')"
OUTPUT="/tmp/OSRS-Flipping-Friend-${SAFE_BRANCH}-${STAMP}.zip"
MANIFEST="/tmp/OSRS-Flipping-Friend-${SAFE_BRANCH}-${STAMP}-MANIFEST.txt"

{
  echo "Created UTC: $(date -u --iso-8601=seconds)"
  echo "Branch: $BRANCH"
  echo "Commit: $(git rev-parse HEAD)"
  echo
  echo "Git status:"
  git status --short
  echo
  echo "Recent commits:"
  git log -10 --oneline
  echo
  echo "Tracked files:"
  git ls-files
} > "$MANIFEST"

git archive --format=zip --output="$OUTPUT" HEAD

echo
echo "Snapshot created:"
echo "$OUTPUT"
echo
echo "Manifest created:"
echo "$MANIFEST"
echo
echo "In the file explorer, open /tmp and download both files."
