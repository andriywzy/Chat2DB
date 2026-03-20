FROM node:16-bullseye AS frontend-builder
WORKDIR /workspace/chat2db-client
ARG APP_VERSION=local
ARG APP_PORT=10824
COPY chat2db-client/package.json chat2db-client/yarn.lock ./
RUN yarn install --frozen-lockfile
COPY chat2db-client ./
RUN yarn run build:web:prod --app_version=${APP_VERSION} --app_port=${APP_PORT}

FROM maven:3.9-eclipse-temurin-17 AS backend-builder
WORKDIR /workspace
ARG APP_VERSION=local
COPY chat2db-server ./chat2db-server
COPY --from=frontend-builder /workspace/chat2db-client/dist /tmp/chat2db-web-dist
RUN rm -rf chat2db-server/chat2db-server-web-start/src/main/resources/static/front/* \
    && mkdir -p chat2db-server/chat2db-server-web-start/src/main/resources/static/front \
    && cp -R /tmp/chat2db-web-dist/. chat2db-server/chat2db-server-web-start/src/main/resources/static/front/ \
    && cp /tmp/chat2db-web-dist/index.html chat2db-server/chat2db-server-web-start/src/main/resources/thymeleaf/index.html \
    && mvn -pl chat2db-server-web-start -am -DskipTests package -f chat2db-server/pom.xml

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=backend-builder /workspace/chat2db-server/chat2db-server-web-start/target/chat2db-server-web-start.jar /app/chat2db-server-web-start.jar
EXPOSE 10824
ENTRYPOINT ["java","-Dspring.profiles.active=release","-jar","chat2db-server-web-start.jar"]
