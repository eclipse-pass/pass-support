FROM --platform=linux/amd64 node:20

RUN apt-get update \
    && apt-get --no-install-recommends install -y xauth \
    && apt-get --no-install-recommends install -y xvfb \
    && curl https://dl-ssl.google.com/linux/linux_signing_key.pub -so - | apt-key add - \
    && echo "deb https://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google.list \
    && apt-get update \
    && apt-get --no-install-recommends -y install google-chrome-stable \
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
