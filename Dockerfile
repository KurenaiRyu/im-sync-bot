FROM maven:3.6.3-openjdk-11 as build
MAINTAINER Kurenai
WORKDIR /im-sync-bot

# Create a first layer to cache the "Maven World" in the local repository.
# Incremental docker builds will always resume after that, unless you update
# the pom
ADD pom.xml .
RUN mvn verify -DskipTests --fail-never

# Do the Maven build!
# Incremental docker builds will resume here when you change sources
ADD src src
RUN mvn package -DskipTests

# 2nd stage, build the runtime image
FROM openjdk:11-jre-slim

WORKDIR /im-sync-bot
# Copy the binary built in the 1st stage
COPY --from=build /im-sync-bot/target/*.jar ./app.jar

CMD ["java", "-jar", "-Dspring.config.location=config.yaml", "./app.jar"]
