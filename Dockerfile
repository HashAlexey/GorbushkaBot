FROM gradle:8.13-jdk21 AS builder
WORKDIR /build
COPY . .
RUN gradle build

FROM bellsoft/liberica-openjdk-alpine:21
COPY --from=builder /build/build/libs/*.jar application.jar
ENTRYPOINT ["java", "-jar", "/application.jar"]