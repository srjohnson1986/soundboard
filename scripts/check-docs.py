#!/usr/bin/env python3
"""Checks the Markdown docs for drift (#274), so a renamed file or heading breaks CI
rather than quietly leaving a doc pointing at nothing. In every Markdown file git tracks:

- a relative link must point at a file or directory that exists, and a #anchor at a
  heading there (GitHub's heading IDs);
- a repo path in backticks, like `shared/src/.../ui/Platform.kt`, must exist. `...`
  stands for any directories and `{a,b}` for either; build outputs and anything git
  ignores (generated files, such as the iOS Xcode project) are skipped;
- a Gradle task path, like `:ios:embedAndSignAppleFrameworkForXcode`, must name a
  project in settings.gradle.kts.

Code blocks are skipped. Run it with `python3 scripts/check-docs.py`; CI runs it on
every pull request, docs-only ones included. It prints each problem as file:line.
"""
import glob
import os
import re
import subprocess
import sys

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()
os.chdir(ROOT)

# Top-level directories whose paths the docs name; a backticked token must start with one.
TOP_LEVEL = {"app", "shared", "web", "ios", "docs", "scripts", "presets", ".github", "gradle"}
LINK = re.compile(r"\[([^\]]*)\]\(([^)\s]+)\)")
INLINE_CODE = re.compile(r"`([^`\n]+)`")
PATH_TOKEN = re.compile(r"^[\w.\-/{},]+$")
GRADLE_TASK = re.compile(r"^:([A-Za-z][\w-]*):[A-Za-z]")


def tracked_markdown():
    out = subprocess.run(["git", "ls-files", "*.md"], capture_output=True, text=True, check=True).stdout
    return [f for f in out.splitlines() if f]


def prose_lines(path):
    """(line number, text) for each line outside fenced code blocks."""
    fenced = False
    with open(path, encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            if line.lstrip().startswith("```"):
                fenced = not fenced
                continue
            if not fenced:
                yield number, line


def slug(heading):
    """GitHub's ID for a heading: its text, lowercased, punctuation dropped, spaces to hyphens."""
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", heading)  # a link counts as its text
    text = text.replace("`", "").strip().lower()
    text = re.sub(r"[^\w\- ]", "", text)
    return text.replace(" ", "-")


_anchors = {}


def anchors_of(path):
    if path not in _anchors:
        seen, ids = {}, set()
        for _, line in prose_lines(path):
            match = re.match(r"^(#{1,6})\s+(.*?)\s*#*\s*$", line)
            if not match:
                continue
            base = slug(match.group(2))
            count = seen.get(base, 0)
            seen[base] = count + 1
            ids.add(base if count == 0 else f"{base}-{count}")
        _anchors[path] = ids
    return _anchors[path]


def ignored(path):
    """Whether git ignores it, as a file or (for a directory-only rule) as a directory."""
    return any(
        subprocess.run(["git", "check-ignore", "-q", candidate]).returncode == 0
        for candidate in (path, path.rstrip("/") + "/")
    )


def expand_braces(token):
    match = re.search(r"\{([^{}]*)\}", token)
    if not match:
        return [token]
    return [
        expanded
        for option in match.group(1).split(",")
        for expanded in expand_braces(token[:match.start()] + option + token[match.end():])
    ]


def path_exists(token):
    for candidate in expand_braces(token.rstrip("/")):
        pattern = candidate.replace("...", "**")
        found = glob.glob(pattern, recursive=True) if "*" in pattern else ([pattern] if os.path.exists(pattern) else [])
        if not found:
            return False
    return True


def gradle_projects():
    with open("settings.gradle.kts", encoding="utf-8") as f:
        return set(re.findall(r'include\(":([\w-]+)"\)', f.read()))


def check():
    problems = []
    projects = gradle_projects()
    for doc in tracked_markdown():
        base = os.path.dirname(doc)
        for number, line in prose_lines(doc):
            where = f"{doc}:{number}"

            for _, target in LINK.findall(line):
                if re.match(r"[a-zA-Z][a-zA-Z+.-]*:", target):
                    continue  # a web or mail link
                path, _, anchor = target.partition("#")
                resolved = os.path.normpath(os.path.join(base, path)) if path else doc
                if path and not os.path.exists(resolved):
                    problems.append(f"{where}: link to {target}: no such file")
                elif anchor and resolved.endswith(".md") and anchor not in anchors_of(resolved):
                    problems.append(f"{where}: link to {target}: no heading with that anchor")

            for code in INLINE_CODE.findall(line):
                code = code.strip()
                task = GRADLE_TASK.match(code)
                if task and task.group(1) not in projects:
                    problems.append(f"{where}: `{code}`: no Gradle project :{task.group(1)} in settings.gradle.kts")
                    continue
                if "/" not in code or not PATH_TOKEN.match(code.replace("...", "")):
                    continue
                if code.split("/")[0] not in TOP_LEVEL:
                    continue
                if "/build/" in f"/{code}/" or ignored(code.replace("...", "x")):
                    continue
                if not path_exists(code):
                    problems.append(f"{where}: `{code}`: no such path")
    return problems


if __name__ == "__main__":
    problems = check()
    for problem in problems:
        print(problem)
    if problems:
        print(f"\n{len(problems)} problem(s) in the docs. Fix the doc, or the path it names.")
        sys.exit(1)
    print("Docs check: every link, anchor, path and Gradle project the docs name exists.")
