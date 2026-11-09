#!/usr/bin/env bash
# Runs the same checks as CI: compile, unit and integration tests, packaging.
set -euo pipefail

cd "$(dirname "$0")/.."
exec ./mvnw -B clean verify
