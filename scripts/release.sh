#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(dirname $0)/.."
cd "$PROJECT_DIR" || exit

sed -i -e 's/-SNAPSHOT//' version.txt
VERSION=$(cat "$PROJECT_DIR/version.txt")

./gradlew clean assemble shadowJar

./gradlew uploadArchives uploadShadow

git add version.txt
git commit -m "Release v$VERSION"
git tag "v$VERSION"
git push --tags

"$PROJECT_DIR/scripts/docker-build.sh"

docker push "aitusoftware/babl:$VERSION"