# syntax=docker/dockerfile:1
# Builds one backend module into a runnable image:
#   docker build --build-arg MODULE=ingest -t agent-trace-ingest backend
# No Maven/JDK is needed on the host: the wrapper (mvnw) runs inside the build stage.

# Pinned by digest (the "21-jdk"/"21-jre" tags these resolved to). See docs/environment.md.
FROM eclipse-temurin@sha256:92a2a4d7a928d057e7bd999c418d66c26a34eb9a0442f3ab67721c3f88110b2d AS build
ARG MODULE
WORKDIR /src
COPY . .
# -am builds the modules this one depends on (core). The cache mount keeps ~/.m2
# between builds so dependencies are downloaded only once.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -ntp -pl ${MODULE} -am package

FROM eclipse-temurin@sha256:49e21e16e3c86eb7816a44a67549910ed090fbeb40c29c525d58bf5e02e91b0f
ARG MODULE
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
