FROM eclipse-temurin:17.0.13_11-jre-jammy

WORKDIR /app

COPY target/pass-grant-loader-*-exec.jar jhu-grant-loader-exec.jar
COPY entrypoint.sh .

RUN apt update \
    && apt --no-install-recommends install -y curl \
    && chmod +x entrypoint.sh \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1432 passglgroup \
    && useradd -m -u 1532 -g passglgroup passgluser \
    && chown -R passgluser:passglgroup /app

USER passgluser

ENTRYPOINT ["./entrypoint.sh"]
CMD ["--help"]