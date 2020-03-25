#!/usr/bin/env bash

PROJECT_DIR="$(dirname $0)/.."
cd "$PROJECT_DIR" || exit
VERSION=$(cat "$PROJECT_DIR/version.txt")
AERON_VERSION=$(cat "$PROJECT_DIR/aeron-version.txt")
./gradlew shadowJar
docker build --build-arg AERON_VERSION="${AERON_VERSION}" -t "aitusoftware/babl:$VERSION" -f "$PROJECT_DIR/docker/base_container/Dockerfile" .
docker build --build-arg AERON_VERSION="${AERON_VERSION}" -t "aitusoftware/babl:latest" -f "$PROJECT_DIR/docker/base_container/Dockerfile" .

docker build -t "aitusoftware/babl-example:latest" -f "$PROJECT_DIR/docker/application_container/Dockerfile" .