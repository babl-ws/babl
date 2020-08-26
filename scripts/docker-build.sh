#!/usr/bin/env bash

PROJECT_DIR="$(dirname $0)/.."
cd "$PROJECT_DIR" || exit
VERSION=$(cat "$PROJECT_DIR/version.txt")
AERON_VERSION=$(grep aeron-driver build.gradle | cut -d":" -f3 | cut -d "'" -f 1)
./gradlew shadowJar
docker build --build-arg AERON_VERSION="${AERON_VERSION}" -t "aitusoftware/babl:$VERSION" -f "$PROJECT_DIR/docker/base_container/Dockerfile" .
docker build --build-arg AERON_VERSION="${AERON_VERSION}" -t "aitusoftware/babl:latest" -f "$PROJECT_DIR/docker/base_container/Dockerfile" .

docker build -t "aitusoftware/babl-example:latest" -f "$PROJECT_DIR/docker/application_container/Dockerfile" .
docker build -t "aitusoftware/babl-monitoring:latest" -f "$PROJECT_DIR/docker/monitoring_container/Dockerfile" .
docker build -t "aitusoftware/babl-healthcheck:latest" -f "$PROJECT_DIR/docker/healthcheck_container/Dockerfile" .
docker build -t "aitusoftware/babl-error-printer:latest" -f "$PROJECT_DIR/docker/error_printing_container/Dockerfile" .