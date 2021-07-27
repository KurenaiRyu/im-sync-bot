FROM gradle:6.8-jdk11-hotspot as builder
WORKDIR /usr/src/java-code
COPY build.gradle.kts settings.gradle.kts gradle.properties  ./
RUN gradle clean build -i --stacktrace -x bootJar
COPY src src
RUN gradle clean bootJar -i --stacktrace

# actual container
FROM adoptopenjdk:11-jre-hotspot
#EXPOSE 8080
WORKDIR /im-sync-bot
COPY --from=builder /usr/src/java-code/build/libs/*.jar ./
#COPY ./build/libs/*.jar ./app.jar
ENTRYPOINT ["java", "-jar", "-Dspring.config.location=config.yaml", "app.jar"]
