FROM aitusoftware/babl-example:latest

COPY docker/healthcheck_container/scripts/*.sh /babl/bin/

ENTRYPOINT [ "/babl/bin/babl_healthcheck.sh" ]