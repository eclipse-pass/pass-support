FROM eclipse-temurin:17.0.7_7-jre

WORKDIR /app

COPY target/nihms-data-harvest-cli-exec.jar .

RUN apt update \
    && apt install -y curl \
    && rm -rf /var/lib/apt/lists/*

# COMMAND is passed in when running docker image