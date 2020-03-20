#!/usr/bin/env bash

PROJECT_DIR="$(dirname $0)/.."
cd "$PROJECT_DIR"
VERSION=$(cat "$PROJECT_DIR/version.txt")
docker container run --network=host "babl-example:$VERSION"
