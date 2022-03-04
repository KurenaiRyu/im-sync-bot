FROM eclipse-temurin:17-jdk-alpine as actual-image
MAINTAINER kurenai233@yahoo.com
RUN apk add ffmpeg libwebp libwebp-tools

FROM gradle:7.3.2-jdk17-alpine as builder
WORKDIR /usr/src/java-code
COPY build.gradle.kts settings.gradle.kts gradle.properties  ./
RUN gradle clean build -i --stacktrace -x bootJar
COPY src src
RUN gradle bootJar -i --stacktrace && rm /usr/src/java-code/build/libs/im-sync-bot-kt*-plain.jar

FROM actual-image
WORKDIR /workspace
COPY --from=builder /usr/src/java-code/build/libs/im-sync-bot-kt*.jar ./bot.jar
# 修改为上海时区,不需要则删除
RUN cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
ENTRYPOINT ["java", "-jar", "-Dspring.config.location=./config/config.yaml", "./bot.jar"]
