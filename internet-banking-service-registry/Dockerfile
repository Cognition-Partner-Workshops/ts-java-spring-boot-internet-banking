FROM eclipse-temurin:21.0.2_13-jre-alpine
LABEL maintainer="chinthaka@javatodev.com"
VOLUME /main-app
ADD build/libs/internet-banking-service-registry-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
# Add bash for wait-for-it.sh
RUN apk add --no-cache bash
RUN addgroup -g 10001 app && adduser -D -h /home/app -s /sbin/nologin -u 10001 -G app app \
    && chmod 644 /app.jar && chmod 755 /wait-for-it.sh
USER 10001
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
