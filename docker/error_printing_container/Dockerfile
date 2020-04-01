FROM aitusoftware/babl-example:latest

COPY docker/error_printing_container/scripts/*.sh /babl/bin/

ENTRYPOINT [ "/babl/bin/babl_error_printer.sh" ]