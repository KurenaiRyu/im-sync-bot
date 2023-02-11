FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache ffmpeg libwebp libwebp-tools bash

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 build/libs/lib/* /deployments/lib/
COPY --chown=185 build/libs/*.jar /deployments/
COPY --chown=185 entrypoint.sh /deployments/

EXPOSE 8080
USER 185

WORKDIR /deployments

ENTRYPOINT ["bash", "/deployments/entrypoint.sh"]