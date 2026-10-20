#!/usr/bin/env bash
set -euo pipefail

REVIEW_BIN_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REVIEW_APP_DIR="$(cd -- "$REVIEW_BIN_DIR/.." && pwd)"
REVIEW_CONTEXT="${CONTEXT_PATH:-/apa}"
if [[ "$REVIEW_CONTEXT" == / ]]; then REVIEW_CONTEXT=''; fi
if [[ -n "$REVIEW_CONTEXT" && ( "$REVIEW_CONTEXT" != /* || "$REVIEW_CONTEXT" == */ ) ]]; then
  echo 'CONTEXT_PATH须以/开头、不以/结尾；使用/表示根路径。' >&2
  exit 1
fi
REVIEW_JAVA=java
if [[ -n "${JAVA_HOME:-}" ]]; then REVIEW_JAVA="$JAVA_HOME/bin/java"; fi
read -r -a REVIEW_JAVA_OPTIONS <<< "${JAVA_OPTS:--Xms128m -Xmx1g}"
cd -- "$REVIEW_APP_DIR"
exec "$REVIEW_JAVA" "${REVIEW_JAVA_OPTIONS[@]}" \
  "-Dserver.servlet.context-path=$REVIEW_CONTEXT" \
  -jar "$REVIEW_APP_DIR/lib/review.jar" \
  "--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}" "$@"
