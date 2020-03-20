#!/usr/bin/env bash

source "$(dirname $0)/common_config.sh"

AERON_DRIVER_DIR="$(grep 'babl.proxy.driver.dir' $BABL_CONFIG_FILE | cut -d'=' -f2)"

java $COMMON_JVM_PARAMS -cp "$EXT_LIB_CLASSPATH" -Daeron.dir=${AERON_DRIVER_DIR} io.aeron.samples.AeronStat watch=false