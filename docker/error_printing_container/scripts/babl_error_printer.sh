#!/usr/bin/env bash

source "$(dirname $0)/common_config.sh"

JVM_PARAMS="${JVM_PARAMS:=}"
JVM_RUNTIME_PARAMETERS="${JVM_RUNTIME_PARAMETERS:=}"

sleep 3
java $COMMON_JVM_PARAMS -cp "$BABL_CLASSPATH" com.aitusoftware.babl.ext.ErrorPrinterMain "$BABL_CONFIG_FILE"