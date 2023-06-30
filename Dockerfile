FROM eclipse-temurin:17-jre-focal

RUN apt-get update && apt-get install -y tzdata ffmpeg webp && apt-get clean --dry-run && rm -rf  /var/lib/apt/lists/*
ENV TZ="Asia/Shanghai"
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

COPY --chown=185 build/libs/lib/* /deployments/lib/
COPY --chown=185 build/libs/*.jar /deployments/
COPY --chown=185 entrypoint.sh /deployments/

EXPOSE 8080
USER 185

WORKDIR /deployments

ENTRYPOINT ["bash", "/deployments/entrypoint.sh"]