#!/usr/bin/env bash

source "$(dirname $0)/common_config.sh"

JVM_PARAMS="${JVM_PARAMS:=}"
JVM_RUNTIME_PARAMETERS="${JVM_RUNTIME_PARAMETERS:=}"

if [[ "true" == "$JVM_PERFORMANCE_TUNING_ENABLED" ]]; then
  JVM_PARAMS="-Djava.lang.Integer.IntegerCache.high=65536 -Dagrona.disable.bounds.checks=true"
fi

USER_CLASSPATH="$BABL_CLASSPATH$JVM_CLASSPATH_APPEND"

java $COMMON_JVM_PARAMS "$JVM_PARAMS" "$JVM_RUNTIME_PARAMETERS" -cp "$USER_CLASSPATH" com.aitusoftware.babl.websocket.BablServer "$BABL_CONFIG_FILE"