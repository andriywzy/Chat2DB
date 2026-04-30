FROM node:16-bullseye AS frontend-builder
WORKDIR /workspace/chat2db-client
ARG APP_VERSION=local
ARG APP_PORT=10824
COPY chat2db-client/package.json chat2db-client/yarn.lock ./
RUN --mount=type=cache,id=chat2db-yarn-cache,target=/usr/local/share/.cache/yarn \
    yarn install --frozen-lockfile
COPY chat2db-client ./
RUN yarn run build:web:prod --app_version=${APP_VERSION} --app_port=${APP_PORT}

FROM maven:3.9-eclipse-temurin-17 AS backend-builder
WORKDIR /workspace
ARG APP_VERSION=local
COPY chat2db-server ./chat2db-server
COPY --from=frontend-builder /workspace/chat2db-client/dist /tmp/chat2db-web-dist
RUN --mount=type=cache,id=chat2db-m2-cache,target=/root/.m2 \
    rm -rf chat2db-server/chat2db-server-web-start/src/main/resources/static/front/* \
    && mkdir -p chat2db-server/chat2db-server-web-start/src/main/resources/static/front \
    && cp -R /tmp/chat2db-web-dist/. chat2db-server/chat2db-server-web-start/src/main/resources/static/front/ \
    && cp /tmp/chat2db-web-dist/index.html chat2db-server/chat2db-server-web-start/src/main/resources/thymeleaf/index.html \
    && mvn -pl chat2db-server-web-start -am -DskipTests package -f chat2db-server/pom.xml

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
RUN if command -v apt-get >/dev/null 2>&1; then \
        apt-get update && apt-get install -y --no-install-recommends tesseract-ocr && rm -rf /var/lib/apt/lists/*; \
    elif command -v microdnf >/dev/null 2>&1; then \
        microdnf install -y tesseract && microdnf clean all; \
    elif command -v yum >/dev/null 2>&1; then \
        yum install -y tesseract && yum clean all; \
    elif command -v apk >/dev/null 2>&1; then \
        apk add --no-cache tesseract-ocr; \
    else \
        echo "No supported package manager found for OCR installation" >&2; exit 1; \
    fi
RUN mkdir -p /opt/tessdata
ADD https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata /opt/tessdata/eng.traineddata
ADD https://github.com/tesseract-ocr/tessdata_fast/raw/main/chi_sim.traineddata /opt/tessdata/chi_sim.traineddata
ENV CHAT2DB_OCR_TESSDATA_DIR=/opt/tessdata
COPY --from=backend-builder /workspace/chat2db-server/chat2db-server-web-start/target/chat2db-server-web-start.jar /app/chat2db-server-web-start.jar
EXPOSE 10824
ENTRYPOINT ["java","-Dspring.profiles.active=release","-jar","chat2db-server-web-start.jar"]
