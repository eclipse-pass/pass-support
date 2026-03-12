FROM eclipse-temurin:17.0.18_8-jre-noble

WORKDIR /app

COPY target/pass-notification-service-*-exec.jar pass-notification-service-exec.jar
COPY entrypoint.sh .

RUN apt update \
    && apt --no-install-recommends install -y curl \
    && chmod +x entrypoint.sh \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1432 passnsgroup \
    && useradd -m -u 1532 -g passnsgroup passnsuser \
    && chown -R passnsuser:passnsgroup /app

USER passnsuser

ENTRYPOINT ["./entrypoint.sh"]
