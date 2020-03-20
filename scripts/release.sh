#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(dirname $0)/.."
cd "$PROJECT_DIR" || exit

sed -i -e 's/-SNAPSHOT//' version.txt
VERSION=$(cat "$PROJECT_DIR/version.txt")

./gradlew clean assemble shadowJar

./gradlew uploadShadow

git tag "v$VERSION"
git push --tags
