#!/usr/bin/env bash

source "$(dirname $0)/common_config.sh"

JVM_PARAMS="${JVM_PARAMS:=}"
JVM_RUNTIME_PARAMETERS="${JVM_RUNTIME_PARAMETERS:=}"

java $COMMON_JVM_PARAMS -cp "$BABL_CLASSPATH" com.aitusoftware.babl.ext.StatisticsMonitorMain "$BABL_CONFIG_FILE"