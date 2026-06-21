#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/java_frontend"
mvn -q compile exec:java -Dexec.mainClass="com.attendance.Main"
