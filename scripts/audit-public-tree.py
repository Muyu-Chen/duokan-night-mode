#!/usr/bin/env python3
"""Fail closed on files outside the intentionally public project contents."""
from pathlib import Path
import re, subprocess

ROOT = Path(__file__).resolve().parents[1]
TOP = {".gitignore", ".gitattributes", "LICENSE", "README.md"}
def allowed(name):
    path = Path(name)
    if name in TOP: return True
    return ((name.startswith("app/src/io/github/muyuchen/duokannight/") and path.suffix == ".java")
         or name == "app/AndroidManifest.xml"
         or (name.startswith("app/res/") and path.suffix == ".xml")
         or (name.startswith("scripts/") and path.suffix in {".py", ".ps1"})
         or (name.startswith("docs/") and path.suffix == ".md")
         or (name.startswith("docs/images/") and path.suffix == ".png")
         or name == ".github/workflows/build.yml")
def main():
    result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True)
    names = result.stdout.decode("utf-8").split("\0")
    problems = []
    private = re.compile(r"(?:[A-Za-z]:\\(?:Users|term[0-9]+)\\|\b[0-9]{5}/[0-9]{8}\b)")
    secret = re.compile(r"(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)")
    for name in filter(None, names):
        if not allowed(name): problems.append("Unapproved public path: " + name); continue
        file = ROOT / name
        if file.suffix == ".png":
            if not file.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
                problems.append("Invalid PNG: " + name)
            continue
        text = file.read_text(encoding="utf-8")
        if secret.search(text) or private.search(text):
            problems.append("Private material found: " + name)
    if problems: raise SystemExit("\n".join(problems))
    print("Public file allowlist and credential/path checks passed.")
if __name__ == "__main__": main()
