#!/usr/bin/env bash
#
# Single entrypoint for the full test suite. Runs the backend tests with the
# JaCoCo 100% coverage gate and the frontend tests with the v8 100% coverage
# gate. Exits non-zero if either suite or either gate fails.
#
#   ./test.sh             run backend and frontend
#   ./test.sh --backend   run backend only
#   ./test.sh --frontend  run frontend only
#
set -euo pipefail

cd "$(dirname "$0")"

run_backend=true
run_frontend=true
case "${1:-}" in
  --backend) run_frontend=false ;;
  --frontend) run_backend=false ;;
  "") ;;
  *) echo "usage: ./test.sh [--backend|--frontend]" >&2; exit 2 ;;
esac

# The build targets Java 25. Point Gradle at a JDK 25 if JAVA_HOME is not already
# one (CI provides it; locally fall back to the Homebrew keg).
if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/homebrew/opt/openjdk@25/bin/java ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
fi

if $run_backend; then
  echo "==> Backend: tests + coverage gate"
  ./gradlew --console=plain test jacocoTestCoverageVerification
fi

if $run_frontend; then
  echo "==> Frontend: tests + coverage gate"
  if [ ! -d node_modules ]; then
    npm ci 2>/dev/null || npm install
  fi
  npm test
fi

echo "==> All requested suites passed with coverage gates green."
