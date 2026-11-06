"""reference-transaction 과 pre-push 훅이 함께 쓰는 리뷰 판정 로직."""
import os
import re
import subprocess

DOC_SUFFIXES = {".md", ".txt", ".rst", ".adoc"}
# apps/ 안은 문서 확장자여도 앱에 포함될 수 있어 코드로 본다. 앱 최상위 안내 문서만 예외.
APP_DOC = re.compile(r"^apps/[^/]+/(README\.md|AGENTS\.md)$")
LOCKFILES = {"pnpm-lock.yaml"}
RECORD_COMMAND = 'git rev-parse <브랜치> >> "$(git rev-parse --git-common-dir)/code-review-passed"'
REVIEW_ADVICE = "기본은 `/code-review low <브랜치>` 입니다. 중요도·변경량·버그 위험상 강도를 높여야 하면 사용자에게 먼저 제안하세요."


def git(*args):
    result = subprocess.run(["git", *args], capture_output=True, text=True)
    return result.stdout.strip() if result.returncode == 0 else None


def resolve(ref):
    return git("rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}")


def is_ancestor(commit, of):
    return git("merge-base", "--is-ancestor", commit, of) is not None


def parents(commit):
    return (git("rev-list", "--parents", "-n", "1", commit) or "").split()[1:]


def reviewed_shas():
    try:
        with open(os.path.join(git("rev-parse", "--git-common-dir"), "code-review-passed"), encoding="utf-8") as f:
            return set(f.read().split())
    except (OSError, TypeError):
        return set()


def changes(base, new):
    """(줄 수, 경로) 목록. 알 수 없으면 None."""
    if base is None:
        return None
    output = git("diff", "--no-renames", "--numstat", "-z", base, new)
    if output is None:
        return None
    result = []
    for entry in filter(None, output.split("\0")):
        added, deleted, path = entry.split("\t", 2)
        result.append((0 if added == "-" else int(added) + int(deleted), path))
    return result


def is_doc(path):
    if path.startswith("apps/") and not APP_DOC.match(path):
        return False
    return os.path.splitext(path)[1].lower() in DOC_SUFFIXES


def docs_only(changed):
    return changed is not None and all(is_doc(path) for _, path in changed)


def describe_size(changed):
    if changed is None:
        return "알 수 없음"
    total = sum(lines for lines, path in changed if not is_doc(path) and os.path.basename(path) not in LOCKFILES)
    return f"코드 {total}줄(문서·lockfile 제외)"
