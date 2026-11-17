FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --ignore-scripts --cache /root/.npm
COPY frontend/ ./
COPY scripts/Normalize-Assets.mjs /build/scripts/Normalize-Assets.mjs
RUN npm run build && node /build/scripts/Normalize-Assets.mjs

FROM maven:3.10.0-eclipse-temurin-21-noble AS backend
WORKDIR /build
COPY backend/.mvn/ .mvn/
COPY backend/pom.xml ./
COPY backend/services/ services/
COPY backend/src/ src/
COPY --from=frontend /build/frontend/dist/ src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2/repository mvn -B clean package && \
    mkdir /output && \
    for service in identity notification maintenance lending registry config gateway; do \
      cp "services/$service/target/equiflow-$service-2.0.0.jar" /output/ || exit 1; \
    done

FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 10001 equiflow && \
    useradd --system --uid 10001 --gid equiflow --no-create-home equiflow
COPY --from=backend /output/ /app/services/
COPY backend/cluster-config/ /app/cluster-config/
USER 10001:10001
ENTRYPOINT ["java"]
