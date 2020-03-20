#!/usr/bin/env bash

PROJECT_DIR="$(dirname $0)/.."
cd "$PROJECT_DIR" || exit
./gradlew javadoc
VERSION=$(cat "$PROJECT_DIR/version.txt")
OUTPUT_DIR="$(mktemp -d)"

BIN_DIR="$OUTPUT_DIR/bin"
mkdir "$BIN_DIR"
ZOLA_VERSION="0.10.0"
ZOLA_DOWNLOAD_URL="https://github.com/getzola/zola/releases/download/v$ZOLA_VERSION/zola-v$ZOLA_VERSION-x86_64-unknown-linux-gnu.tar.gz"
ZOLA_DOWNLOAD_TARGET="$BIN_DIR/zola.tar.gz"
curl -L "$ZOLA_DOWNLOAD_URL" > "$ZOLA_DOWNLOAD_TARGET"
tar -C "$BIN_DIR" -xzf "$ZOLA_DOWNLOAD_TARGET"
cp -R "$PROJECT_DIR/doc/docs" "$OUTPUT_DIR/"
mkdir "$OUTPUT_DIR/docs/static"
cp -R "$PROJECT_DIR/build/docs/javadoc" "$OUTPUT_DIR/docs/static/"
cd "$OUTPUT_DIR/docs" && mkdir themes &&  cd themes && git clone https://github.com/getzola/book.git
sed -ie 's/max-width: 800px/max-width: 1000px' book/sass/_content.scss
find "$OUTPUT_DIR/docs/content" -name '*.md' | xargs sed -ie "s/BABL_VERSION/${VERSION}/g"
cd "$OUTPUT_DIR/docs/" && ../bin/zola build --drafts
echo "$OUTPUT_DIR"
if [[ "x$1" != "x" ]]; then
  ../bin/zola serve --drafts # || rm -r "$OUTPUT_DIR"
fi