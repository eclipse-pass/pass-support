FROM --platform=linux/amd64 node:20

ENV CHROME_VERSION=141.0.7390.122-1

RUN apt-get update \
    && apt-get --no-install-recommends install -y xauth \
    && apt-get --no-install-recommends install -y xvfb \
    && curl -O https://dl.google.com/linux/chrome/deb/pool/main/g/google-chrome-stable/google-chrome-stable_${CHROME_VERSION}_amd64.deb \
    && apt-get update \
    && apt-get install -y ./google-chrome-stable_${CHROME_VERSION}_amd64.deb \
    && curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -so "awscliv2.zip" \
    && unzip awscliv2.zip \
	&& ./aws/install \
    && apt-get clean

WORKDIR /app

COPY package.json .
COPY refreshtoken.js .
COPY run_refresh.sh .
COPY set_aws_param_store.sh .
COPY entrypoint.sh .

RUN chmod +x entrypoint.sh \
    && chmod +x run_refresh.sh \
    && chmod +x set_aws_param_store.sh \
    && npm install --ignore-scripts \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1432 passntrgroup \
    && useradd -m -u 1532 -g passntrgroup passntruser \
    && chown -R passntruser:passntrgroup /app

USER passntruser

ENTRYPOINT ["./entrypoint.sh"]
