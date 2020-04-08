FROM debian:latest
SHELL [ "/bin/bash", "-o", "pipefail", "-c" ]

COPY build/libs/* /babl/lib/
COPY docker/base_container/scripts/*.sh /babl/bin/
COPY docker/base_container/config/default-config.properties /babl/config/

RUN apt-get update --quiet && apt-get install --no-install-recommends --assume-yes --quiet bash openjdk-11-jdk curl less
ARG AERON_VERSION
RUN curl -L "https://search.maven.org/remotecontent?filepath=io/aeron/aeron-all/${AERON_VERSION}/aeron-all-${AERON_VERSION}.jar" --create-dirs --output /babl/ext-lib/aeron-all-${AERON_VERSION}.jar
RUN useradd --system --uid 950 --gid users --home-dir /babl babl
RUN chown -R babl:users /babl

ENV BABL_CONFIG_FILE=""
ENV JVM_PERFORMANCE_TUNING_ENABLED="true"
ENV JVM_RUNTIME_PARAMETERS=""
ENV JVM_CLASSPATH_APPEND=""

USER babl
WORKDIR /babl
ENTRYPOINT [ "/babl/bin/babl_start.sh" ]
