ARG RUNTIME_BASE_IMAGE=chat2db-runtime-base:local

FROM ${RUNTIME_BASE_IMAGE}

WORKDIR /app
ARG APP_PORT=10824

RUN mkdir -p /opt/tessdata
COPY tessdata/eng.traineddata /opt/tessdata/eng.traineddata
COPY tessdata/chi_sim.traineddata /opt/tessdata/chi_sim.traineddata
ENV CHAT2DB_OCR_TESSDATA_DIR=/opt/tessdata

COPY chat2db-server-web-start.jar /app/chat2db-server-web-start.jar

EXPOSE ${APP_PORT}
ENTRYPOINT ["java", "-Dspring.profiles.active=release", "-jar", "chat2db-server-web-start.jar"]
