FROM eclipse-temurin:17.0.7_7-jre

WORKDIR /app

COPY target/pass-journal-loader-nih-exec.jar .
COPY entrypoint.sh .

RUN apt update \
    && apt install -y curl \
    && chmod +x entrypoint.sh \
    && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["./entrypoint.sh"]