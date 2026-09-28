#!/usr/bin/env bash
# Copies the in-repo docs to the GitHub wiki, which mirrors them for readers who
# browse the wiki instead of the repo. docs/ and README.md stay the source of truth,
# and nothing on the wiki is edited by hand: the Sync wiki workflow
# (.github/workflows/wiki.yml) runs this on every merge that changes them (#272).
#
#   scripts/sync-wiki.sh            # sync and push
#   scripts/sync-wiki.sh --dry-run  # show what would change, push nothing
#
# In CI it pushes with WIKI_TOKEN; run by hand, with your own git credentials.
set -euo pipefail

REPO_URL="https://github.com/srjohnson1986/soundboard"
BLOB_URL="$REPO_URL/blob/master"

# repo file -> wiki page. The README is the wiki's Home page (#273).
PAGES=(
  "README.md:Home.md"
  "docs/USER_GUIDE.md:User-Guide.md"
  "docs/ARCHITECTURE.md:Architecture.md"
  "docs/RELEASING.md:Releasing.md"
  "docs/IOS.md:iOS.md"
)

dry_run=false
[[ "${1:-}" == "--dry-run" ]] && dry_run=true

root="$(git rev-parse --show-toplevel)"
cd "$root"
# The last commit that touched a mirrored file, so re-running with unchanged docs changes nothing.
commit="$(git log -1 --format=%h -- docs/ README.md)"
python="$(command -v python3 || command -v python)"

wiki_url="$REPO_URL.wiki.git"
if [[ -n "${WIKI_TOKEN:-}" ]]; then
  wiki_url="https://x-access-token:${WIKI_TOKEN}@${REPO_URL#https://}.wiki.git"
fi

wiki="$(mktemp -d)"
trap 'rm -rf "$wiki"' EXIT
git clone --quiet "$wiki_url" "$wiki"

for pair in "${PAGES[@]}"; do
  src="${pair%%:*}"
  dest="$wiki/${pair##*:}"
  {
    echo "> Mirrored from [\`$src\`]($BLOB_URL/$src) at \`$commit\`. Edit it there, not here."
    echo
    # Relative links point nowhere on the wiki: a link to a mirrored file goes to its
    # wiki page, and any other to the file on GitHub. Web links and #anchors stay.
    "$python" - "$src" "$BLOB_URL" "${PAGES[@]}" <<'PY'
import posixpath, re, sys

src, blob, *pairs = sys.argv[1:]
pages = {repo: page[:-len(".md")] for repo, page in (pair.split(":", 1) for pair in pairs)}
base = posixpath.dirname(src)

def rewrite(match):
    text, target = match.group(1), match.group(2)
    if re.match(r"[a-zA-Z][a-zA-Z+.-]*:|#", target):
        return match.group(0)
    path, hash_, anchor = target.partition("#")
    repo_path = posixpath.normpath(posixpath.join(base, path))
    suffix = hash_ + anchor
    if repo_path in pages:
        return f"[{text}]({pages[repo_path]}{suffix})"
    return f"[{text}]({blob}/{repo_path}{suffix})"

with open(src, encoding="utf-8") as f:
    text = re.sub(r"\[([^\]]*)\]\(([^)\s]+)\)", rewrite, f.read())
# UTF-8 whatever the console's encoding (Windows' isn't), and no \r\n on Windows.
sys.stdout.buffer.write(text.encode("utf-8"))
PY
  } > "$dest"
done

cd "$wiki"
git add -A
if git diff --cached --quiet; then
  echo "Wiki already matches the docs at $commit."
  exit 0
fi

git --no-pager diff --cached --stat
if $dry_run; then
  echo "Dry run: not pushing."
  exit 0
fi

git commit --quiet -m "Sync with the docs at $commit"
git push --quiet
echo "Wiki synced with the docs at $commit."
