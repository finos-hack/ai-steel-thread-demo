# Multi-stage image: build fluxnova-plugins from a git branch, then the demo app.
# Frontend is built separately (see Dockerfile.frontend).

ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-jammy

# ---------------------------------------------------------------------------
# Stage 1 — install agentic plugins from the unmerged branch into a local .m2
# ---------------------------------------------------------------------------
FROM ${MAVEN_IMAGE} AS plugins
ARG PLUGINS_REPO=https://github.com/finos/fluxnova-plugins.git
ARG PLUGINS_REF=llm-token-usage
WORKDIR /build
RUN apt-get update \
  && apt-get install -y --no-install-recommends git \
  && rm -rf /var/lib/apt/lists/*
# Works for branch names and commit SHAs (not only --branch).
RUN git init fluxnova-plugins \
  && cd fluxnova-plugins \
  && git remote add origin "${PLUGINS_REPO}" \
  && git fetch --depth 1 origin "${PLUGINS_REF}" \
  && git checkout FETCH_HEAD
# Parent POM is not on Maven Central; install it into the local .m2 first so
# downstream consumers can resolve the agentic SNAPSHOT POM chain.
WORKDIR /build/fluxnova-plugins
RUN mvn -B -N -DskipTests install
WORKDIR /build/fluxnova-plugins/agentic-subprocess
RUN mvn -B -DskipTests install

# ---------------------------------------------------------------------------
# Stage 2 — package the Spring Boot demo against that local Maven repo
# ---------------------------------------------------------------------------
FROM ${MAVEN_IMAGE} AS app-build
WORKDIR /workspace
COPY --from=plugins /root/.m2 /root/.m2
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package \
  && cp target/ai-steel-thread-demo-*.jar /workspace/app.jar

# ---------------------------------------------------------------------------
# Stage 3 — runtime
# ---------------------------------------------------------------------------
FROM ${JRE_IMAGE} AS runtime
WORKDIR /app
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && useradd --system --uid 10001 --create-home appuser
COPY --from=app-build /workspace/app.jar /app/app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
