FROM adoptopenjdk/openjdk8

WORKDIR /usr/src/project
RUN apt-get update && apt-get install -y vim

COPY . .


RUN curl -O "https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein" && \
    mkdir -p /opt/lein && \
    mv lein /opt/lein && \
    chmod a+x /opt/lein/lein &&\
    /opt/lein/lein

ENV LEIN_HOME="/opt/lein"
ENV PATH="$LEIN_HOME:$PATH"

