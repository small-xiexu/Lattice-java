#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local-dev}"
export SERVER_PORT="${SERVER_PORT:-18082}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_local_dev}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-postgres}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-postgres}"
export SPRING_FLYWAY_ENABLED="${SPRING_FLYWAY_ENABLED:-true}"
export SPRING_FLYWAY_SCHEMAS="${SPRING_FLYWAY_SCHEMAS:-lattice_local_dev}"
export SPRING_FLYWAY_DEFAULT_SCHEMA="${SPRING_FLYWAY_DEFAULT_SCHEMA:-lattice_local_dev}"
export LATTICE_REDIS_HOST="${LATTICE_REDIS_HOST:-127.0.0.1}"
export LATTICE_REDIS_PORT="${LATTICE_REDIS_PORT:-6379}"

cd "$ROOT_DIR"
exec mvn -q -s .codex/maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=local-dev
