#!/usr/bin/env bash

source "$(dirname $0)/common_config.sh"

java $COMMON_JVM_PARAMS -cp "$BABL_CLASSPATH" com.aitusoftware.babl.monitoring.ErrorPrinter "$BABL_SERVER_DIR"