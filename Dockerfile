FROM openjdk:17-alpine AS tester

WORKDIR /home/cuser

ADD ./gradle /home/cuser/gradle
ADD ./gradlew ./gradle.properties ./build.gradle.kts ./settings.gradle.kts /home/cuser/
RUN ./gradlew --no-daemon build
ADD ./src /home/cuser/src
RUN ./gradlew --no-daemon check

FROM tester AS builder
RUN ./gradlew --no-daemon shadowJar

FROM openjdk:17-alpine AS distribution
COPY --from=builder /home/cuser/build/libs/gaia.jar /home/cuser/gaia.jar

WORKDIR /home/cuser

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+UseStringDeduplication", "-jar", "gaia.jar"]
