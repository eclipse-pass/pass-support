FROM eclipse-temurin:17.0.13_11-jre-jammy

WORKDIR /app

COPY target/pass-journal-loader-nih-*-exec.jar pass-journal-loader-nih-exec.jar
COPY entrypoint.sh .

RUN apt update \
    && apt --no-install-recommends install -y curl \
    && chmod +x entrypoint.sh \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1432 passjlgroup \
    && useradd -m -u 1532 -g passjlgroup passjluser \
    && chown -R passjluser:passjlgroup /app

USER passjluser

ENTRYPOINT ["./entrypoint.sh"]