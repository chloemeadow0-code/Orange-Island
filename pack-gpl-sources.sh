#!/usr/bin/env bash
set -euo pipefail
# Collect pinned recipes AND all source inputs; Alpine is no longer optional.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${PYTHON:-python3}" "$SCRIPT_DIR/scripts/pack_sandbox_sources.py" "$@"
