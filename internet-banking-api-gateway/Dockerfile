FROM docker.io/library/eclipse-temurin:21-jre-alpine@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372
LABEL maintainer="chinthaka@javatodev.com"
VOLUME /main-app
ADD build/libs/internet-banking-api-gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
# Add bash for wait-for-it.sh
RUN apk add --no-cache bash
USER 10001
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
