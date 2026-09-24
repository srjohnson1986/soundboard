#!/usr/bin/env bash
# Copies the in-repo docs to the GitHub wiki, which mirrors them for readers who
# browse the wiki instead of the repo. docs/ stays the source of truth: edit
# there, then run this (it's a step in docs/RELEASING.md's release checklist).
#
#   scripts/sync-wiki.sh            # sync and push
#   scripts/sync-wiki.sh --dry-run  # show what would change, push nothing
#
# Only the mirrored pages below are touched; the wiki's Home page is edited by hand.
set -euo pipefail

REPO_URL="https://github.com/srjohnson1986/soundboard"
BLOB_URL="$REPO_URL/blob/master"

# docs file -> wiki page
PAGES=(
  "docs/USER_GUIDE.md:User-Guide.md"
  "docs/ARCHITECTURE.md:Architecture.md"
  "docs/RELEASING.md:Releasing.md"
)

dry_run=false
[[ "${1:-}" == "--dry-run" ]] && dry_run=true

root="$(git rev-parse --show-toplevel)"
cd "$root"
# The last commit that touched docs/, so re-running with unchanged docs changes nothing.
commit="$(git log -1 --format=%h -- docs/)"

wiki="$(mktemp -d)"
trap 'rm -rf "$wiki"' EXIT
git clone --quiet "$REPO_URL.wiki.git" "$wiki"

for pair in "${PAGES[@]}"; do
  src="${pair%%:*}"
  dest="$wiki/${pair##*:}"
  {
    echo "> Mirrored from [\`$src\`]($BLOB_URL/$src) at \`$commit\`. Edit it there, not here."
    echo
    # Links like ](../presets/README.md) are relative to docs/ in the repo;
    # on the wiki they'd point nowhere, so send them to the file on GitHub.
    sed -E "s#\]\(\.\./([^)]+)\)#](${BLOB_URL}/\1)#g" "$src"
  } > "$dest"
done

cd "$wiki"
git add -A
if git diff --cached --quiet; then
  echo "Wiki already matches docs/ at $commit."
  exit 0
fi

git --no-pager diff --cached --stat
if $dry_run; then
  echo "Dry run: not pushing."
  exit 0
fi

git commit --quiet -m "Sync with docs/ at $commit"
git push --quiet
echo "Wiki synced with docs/ at $commit."
