FROM eclipse-temurin:17.0.8_7-jre-jammy

WORKDIR /app

COPY target/deposit-core-*-exec.jar pass-deposit-service-exec.jar
COPY entrypoint.sh .

RUN apt update \
    && apt --no-install-recommends install -y curl \
    && chmod +x entrypoint.sh \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1432 passdsgroup \
    && useradd -m -u 1532 -g passdsgroup passdsuser \
    && chown -R passdsuser:passdsgroup /app

USER passdsuser

ENTRYPOINT ["./entrypoint.sh"]