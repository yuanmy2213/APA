#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
jar_path=finance-access-review-1.2.0.jar
if [[ ! -f "$jar_path" ]]; then jar_path=target/finance-access-review-1.2.0.jar; fi
if [[ ! -f "$jar_path" ]]; then echo '请先运行 mvn package，或使用包含 JAR 的一期交付包。'; exit 1; fi
exec java -Xms128m -Xmx1g -jar "$jar_path" --spring.profiles.active=demo "$@"
