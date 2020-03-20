#!/usr/bin/env bash

export BABL_CONFIG_FILE="${BABL_CONFIG_FILE:=/babl/config/default-config.properties}"
BABL_SERVER_DIR="$(grep 'babl.server.directory' $BABL_CONFIG_FILE | cut -d'=' -f2)"
export BABL_SERVER_DIR
BABL_CLASSPATH="$(JARS=(/babl/lib/*.jar); IFS=:; echo "${JARS[*]}")"
export BABL_CLASSPATH
EXT_LIB_CLASSPATH="$(JARS=(/babl/ext-lib/*.jar); IFS=:; echo "${JARS[*]}")"
export EXT_LIB_CLASSPATH
export COMMON_JVM_PARAMS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED"
