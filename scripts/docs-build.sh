#!/usr/bin/env bash
set -x
PROJECT_DIR="$(dirname $0)/.."
cd "$PROJECT_DIR" || exit
./gradlew javadoc
VERSION=$(cat "$PROJECT_DIR/version.txt")
OUTPUT_DIR="$(mktemp -d)"

BIN_DIR="$OUTPUT_DIR/bin"
mkdir "$BIN_DIR"
ZOLA_VERSION="0.10.0"
ZOLA_DIR="/tmp/zola-$ZOLA_VERSION"
if [[ ! -d "$ZOLA_DIR" ]]; then
  ZOLA_DOWNLOAD_URL="https://github.com/getzola/zola/releases/download/v$ZOLA_VERSION/zola-v$ZOLA_VERSION-x86_64-unknown-linux-gnu.tar.gz"
  ZOLA_DOWNLOAD_TARGET="$BIN_DIR/zola.tar.gz"
  curl -L "$ZOLA_DOWNLOAD_URL" > "$ZOLA_DOWNLOAD_TARGET"
  mkdir -p "$ZOLA_DIR"
  tar -C "$ZOLA_DIR" -xzf "$ZOLA_DOWNLOAD_TARGET"
fi

ZOLA_BIN="/tmp/zola-$ZOLA_VERSION/zola"

cp -R "$PROJECT_DIR/doc/docs" "$OUTPUT_DIR/"
mkdir "$OUTPUT_DIR/docs/static"
cp -R "$PROJECT_DIR/build/docs/javadoc" "$OUTPUT_DIR/docs/static/"
cd "$OUTPUT_DIR/docs" && mkdir themes &&  cd themes && git clone https://github.com/babl-ws/book.git
sed -ie 's/max-width: 800px/max-width: 1000px/g' book/sass/_content.scss
find "$OUTPUT_DIR/docs/content" -name '*.md' | xargs sed -ie "s/BABL_VERSION/${VERSION}/g"
cd "$OUTPUT_DIR/docs/" && $ZOLA_BIN build --drafts
echo "$OUTPUT_DIR"
if [[ "x$1" == "xserve" ]]; then
  $ZOLA_BIN serve --drafts # || rm -r "$OUTPUT_DIR"
elif [[ "x$1" == "xpublish" ]]; then
  if [[ "x$2" == "x" ]]; then
    echo "Supply the publish directory"
    exit 1
  fi
  PUBLISH_DIR="$2"
  echo "Publishing to $PUBLISH_DIR"
  rsync -a "$OUTPUT_DIR/docs/public/" "$PUBLISH_DIR/"
fi
