FROM eclipse-temurin:17.0.13_11-jre-jammy

WORKDIR /app

COPY ./target/nihms-data-harvest-*-exec.jar nihms-data-harvest-cli-exec.jar
COPY ./target/nihms-data-transform-load-*-exec.jar nihms-data-transform-load-exec.jar
COPY entrypoint.sh .

RUN apt update \
    && apt --no-install-recommends install -y curl \
    && chmod +x entrypoint.sh \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1432 passnlgroup \
    && useradd -m -u 1532 -g passnlgroup passnluser \
    && chown -R passnluser:passnlgroup /app

USER passnluser

ENTRYPOINT ["./entrypoint.sh"]