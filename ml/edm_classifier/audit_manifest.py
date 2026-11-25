#!/usr/bin/env python3
"""Fail-closed audit for the MAXMIZE MUSIC EDM training manifest.

This tool does not decide copyright ownership. It checks that a human has
recorded track-level provenance and explicit permission for commercial ML use.
"""
import csv
import sys
from pathlib import Path

REQUIRED = {
    "track_id", "sha256", "local_path", "source_provider", "source_url",
    "license_id", "license_url", "ml_training_permission",
    "commercial_use_permission", "rights_evidence_url", "subgenre", "split",
}
APPROVED_LICENSES = {"CC0-1.0", "CC-BY-4.0", "CC-BY-3.0", "PUBLIC-DOMAIN"}
VALID_SPLITS = {"train", "validation", "test", "holdout"}

def truth(value: str) -> bool:
    return value.strip().lower() in {"yes", "true", "1", "verified"}

def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python audit_manifest.py path/to/manifest.csv")
        return 2
    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"Manifest not found: {path}")
        return 2

    errors = []
    seen_ids = set()
    seen_hashes = set()
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        missing = REQUIRED - set(reader.fieldnames or [])
        if missing:
            print("Missing columns: " + ", ".join(sorted(missing)))
            return 2
        for line, row in enumerate(reader, start=2):
            track_id = row["track_id"].strip()
            digest = row["sha256"].strip().lower()
            if not track_id:
                errors.append(f"line {line}: missing track_id")
            if track_id in seen_ids:
                errors.append(f"line {line}: duplicate track_id {track_id}")
            seen_ids.add(track_id)
            if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
                errors.append(f"line {line}: invalid SHA-256")
            elif digest in seen_hashes:
                errors.append(f"line {line}: duplicate audio hash")
            seen_hashes.add(digest)

            license_id = row["license_id"].strip().upper()
            if license_id not in APPROVED_LICENSES:
                errors.append(f"line {line}: license not on allowlist: {license_id or 'missing'}")
            for field in ("source_url", "license_url", "rights_evidence_url", "source_provider", "local_path"):
                if not row[field].strip():
                    errors.append(f"line {line}: missing {field}")
            if not truth(row["ml_training_permission"]):
                errors.append(f"line {line}: ML training permission not verified")
            if not truth(row["commercial_use_permission"]):
                errors.append(f"line {line}: commercial use permission not verified")
            if row["split"].strip().lower() not in VALID_SPLITS:
                errors.append(f"line {line}: invalid split")
            if not row["subgenre"].strip():
                errors.append(f"line {line}: missing reviewed subgenre")

    if errors:
        print("DATASET AUDIT FAILED")
        for error in errors:
            print(" - " + error)
        return 1
    print("DATASET AUDIT PASSED. Human verification of every evidence URL is still required.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
