#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MAVEN_SETTINGS="$ROOT_DIR/.codex/maven-settings.xml"
MAVEN_REPO="/Users/sxie/maven/repository"

echo "[smoke] compile"
mvn -q \
  -s "$MAVEN_SETTINGS" \
  -Dmaven.repo.local="$MAVEN_REPO" \
  -DskipTests \
  compile

echo "[smoke] phase e/f/g/h targeted tests"
mvn -q \
  -s "$MAVEN_SETTINGS" \
  -Dmaven.repo.local="$MAVEN_REPO" \
  -Dtest=AdminSourceCredentialControllerTests,AdminSourceControllerTests,AdminUploadControllerTests,VaultExportServiceTests,VaultSyncServiceTests,VaultSnapshotServiceTests,LatticeCliBootstrapTests,LatticeMcpToolsTest \
  test

echo "[smoke] done"
