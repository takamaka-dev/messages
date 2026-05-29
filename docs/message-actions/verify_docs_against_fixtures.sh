#!/usr/bin/env bash
#
# verify_docs_against_fixtures.sh — drift guard for the message-actions docs.
#
# Every "canonical wire format" JSON block in this folder's per-action pages is
# anchored to a golden cross-platform fixture. The anchor is an HTML comment
# placed IMMEDIATELY before the fenced block:
#
#     <!-- fixture: V03_reply_text_only/expected_inner_plaintext.json -->
#     ```json
#     { ... verbatim fixture content ... }
#     ```
#
# This script extracts every (marker, json-block) pair from *.md here and asserts
# the block parses to the SAME JSON value as the referenced fixture file under
#     Messages/src/test/resources/cross-platform-vectors/fixtures/
# It fails (non-zero) on any mismatch, missing fixture, or marker without a block.
# Run it in CI after the Messages test phase; the fixtures themselves are the
# source of truth (CrossPlatformVectorTest validates them against the code).
#
# Usage: bash Messages/docs/message-actions/verify_docs_against_fixtures.sh
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# docs/message-actions -> Messages/ -> src/test/.../fixtures
FIXTURES_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)/src/test/resources/cross-platform-vectors/fixtures"

[[ -d "$FIXTURES_DIR" ]] || { echo "ERROR: fixtures dir not found: $FIXTURES_DIR" >&2; exit 2; }
command -v python3 >/dev/null || { echo "ERROR: python3 required" >&2; exit 2; }

python3 - "$SCRIPT_DIR" "$FIXTURES_DIR" <<'PY'
import json, os, re, sys, glob

docs_dir, fixtures_dir = sys.argv[1], sys.argv[2]
# A real anchor is a marker ALONE on its line (the per-action pages place it on its
# own line just before the fenced block). Mid-prose mentions of the marker syntax
# (e.g. in README, inside backticks) are not full-line matches and are ignored.
marker_re = re.compile(r'^<!--\s*fixture:\s*(\S+?)\s*-->$')

checked = 0
failures = []

for md in sorted(glob.glob(os.path.join(docs_dir, "*.md"))):
    lines = open(md, encoding="utf-8").read().splitlines()
    i = 0
    name = os.path.basename(md)
    while i < len(lines):
        m = marker_re.match(lines[i].strip())
        if not m:
            i += 1
            continue
        rel = m.group(1)
        # find the next ```json fence (allow blank lines between marker and fence)
        j = i + 1
        while j < len(lines) and lines[j].strip() == "":
            j += 1
        if j >= len(lines) or not lines[j].strip().startswith("```json"):
            failures.append(f"{name}:{i+1}: marker for '{rel}' is not immediately followed by a ```json block")
            i = j
            continue
        # collect until closing fence
        k = j + 1
        buf = []
        while k < len(lines) and not lines[k].strip().startswith("```"):
            buf.append(lines[k])
            k += 1
        block = "\n".join(buf)
        fpath = os.path.join(fixtures_dir, rel)
        checked += 1
        if not os.path.isfile(fpath):
            failures.append(f"{name}:{i+1}: fixture not found: {rel}")
        else:
            try:
                doc_json = json.loads(block)
            except Exception as e:
                failures.append(f"{name}:{j+1}: doc JSON block does not parse ({e})")
                i = k + 1
                continue
            try:
                fix_json = json.load(open(fpath, encoding="utf-8"))
            except Exception as e:
                failures.append(f"{name}:{i+1}: fixture JSON does not parse: {rel} ({e})")
                i = k + 1
                continue
            if doc_json != fix_json:
                failures.append(f"{name}:{j+1}: doc block != fixture '{rel}'")
        i = k + 1

print(f"checked {checked} fixture-anchored block(s) across {len(glob.glob(os.path.join(docs_dir,'*.md')))} page(s)")
if failures:
    print("\nDRIFT DETECTED:")
    for f in failures:
        print("  - " + f)
    sys.exit(1)
print("OK: all fixture-anchored blocks match their fixtures.")
PY
