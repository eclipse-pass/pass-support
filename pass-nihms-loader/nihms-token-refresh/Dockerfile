FROM --platform=linux/amd64 node:20

RUN apt-get update; apt-get clean
RUN apt-get install -y xvfb
RUN curl https://dl-ssl.google.com/linux/linux_signing_key.pub -so - | apt-key add - \
    && echo "deb http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google.list
RUN apt-get update && apt-get -y install google-chrome-stable

RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -so "awscliv2.zip" && \
    unzip awscliv2.zip && \
	./aws/install

WORKDIR /app

COPY package.json .
COPY refreshtoken.js .
COPY run_refresh.sh .
COPY set_aws_param_store.sh .
COPY entrypoint.sh .

RUN chmod +x entrypoint.sh \
    && chmod +x run_refresh.sh \
    && chmod +x set_aws_param_store.sh \
    && npm install \
    && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["./entrypoint.sh"]
