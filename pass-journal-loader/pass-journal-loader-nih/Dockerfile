FROM eclipse-temurin:17.0.13_11-jre-jammy

WORKDIR /app

COPY target/ /app/
COPY entrypoint.sh .

RUN mv /app/pass-journal-loader-nih-*-exec.jar /app/pass-journal-loader-nih-exec.jar \
    && apt update \
    && apt install -y curl \
    && chmod +x /app/entrypoint.sh \
    && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["./entrypoint.sh"]