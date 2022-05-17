FROM eclipse-temurin:17.0.1_12-jdk-alpine
MAINTAINER kurenai233@yahoo.com
RUN apk add --no-cache ffmpeg libwebp libwebp-tools
WORKDIR /workspace
COPY ./build/libs/im-sync-bot-kt*.jar ./bot.jar
# 修改为上海时区,不需要则删除
RUN cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
ENTRYPOINT ["java", "-jar", "-Dspring.config.location=./config/config.yaml", "./bot.jar"]