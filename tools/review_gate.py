"""Bind two fresh-agent review attestations to the exact Git candidate contents."""

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

import check_privacy

ROOT = Path(__file__).resolve().parents[1]
RECORD = "reviews/approvals.json"


def git(root, *args):
    result = subprocess.run(["git", *args], cwd=root, capture_output=True)
    if result.returncode:
        raise ValueError(result.stderr.decode("utf-8", errors="replace").strip())
    return result.stdout


def candidate(root, revision=None):
    entries = []
    raw = (
        git(root, "ls-tree", "-rz", "--full-tree", revision)
        if revision
        else git(root, "ls-files", "--stage", "-z")
    )
    for entry in raw.split(b"\0"):
        if not entry:
            continue
        meta, path = entry.split(b"\t", 1)
        privacy_errors = check_privacy.tracked_errors([path.decode("utf-8")])
        if privacy_errors:
            raise ValueError("\n".join(privacy_errors))
        if path == RECORD.encode():
            continue  # The sole exclusion avoids circular attestation hashes.
        fields = meta.split()
        mode, oid = (fields[0], fields[2]) if revision else (fields[0], fields[1])
        if not revision and fields[2] != b"0":
            raise ValueError("Resolve index conflicts before review")
        entries.append(mode + b" " + oid + b"\t" + path)
    return hashlib.sha256(b"dexdroid-review-v1\0" + b"\0".join(sorted(entries))).hexdigest()


def validate(record, digest):
    if record.get("schema_version") != 1 or record.get("candidate_sha256") != digest:
        raise ValueError("Missing/stale candidate approvals; obtain two fresh reviews")
    reviews = record.get("reviews", [])
    if len(reviews) != 2:
        raise ValueError("Exactly two fresh independent approvals are required")
    identities = set()
    for review in reviews:
        identity = review.get("agent_id", "")
        if not isinstance(identity, str) or not identity.strip() or identity in identities:
            raise ValueError("Two distinct agent identities are required")
        identities.add(identity)
        if review.get("verdict") != "approve" or review.get("fresh_agent") is not True:
            raise ValueError("Each fresh agent must explicitly approve")
        if not review.get("scope", "").strip() or not review.get("summary", "").strip():
            raise ValueError("Review scope and conclusion are required")
        try:
            stamp = datetime.fromisoformat(review["reviewed_at"].replace("Z", "+00:00"))
            if stamp.tzinfo is None or stamp > datetime.now(timezone.utc):
                raise ValueError("Review timestamp must be timezone-aware and not in the future")
        except (KeyError, TypeError, ValueError) as error:
            raise ValueError("Invalid review timestamp") from error


def check(root, revision=None):
    if revision:
        record = json.loads(git(root, "show", f"{revision}:{RECORD}").decode("utf-8"))
    else:
        record = json.loads(git(root, "show", f":{RECORD}").decode("utf-8"))
    validate(record, candidate(root, revision))


def pre_push(root, lines):
    checked = set()
    for line in lines:
        fields = line.split()
        if len(fields) != 4:
            raise ValueError("Invalid pre-push ref update")
        oid = fields[1]
        if re.fullmatch(r"0+", oid):
            continue  # Ref deletion has no release candidate.
        if not re.fullmatch(r"[a-f0-9]{40,64}", oid):
            raise ValueError("Invalid pushed object ID")
        commit = git(root, "rev-parse", "--verify", f"{oid}^{{commit}}").decode().strip()
        if commit not in checked:
            check(root, commit)
            checked.add(commit)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["snapshot", "check", "pre-push"])
    parser.add_argument("--revision", help="Committed revision; otherwise inspect staged index")
    args = parser.parse_args()
    try:
        if args.action == "snapshot":
            print(candidate(ROOT, args.revision))
        elif args.action == "pre-push":
            pre_push(ROOT, sys.stdin)
            print("PASS pushed candidates have two review attestations")
        else:
            check(ROOT, args.revision)
            print("PASS two fresh-agent approvals match candidate")
    except (ValueError, OSError, TypeError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
