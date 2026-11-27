FROM node:22-slim
# ffmpeg decodes whatever audio format a track was uploaded in
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY engine ./engine
COPY src ./src
# The tune index (about 33 MB) is downloaded on first start and kept on this volume
ENV INDEX_DIR=/data PORT=8090 NODE_ENV=production
RUN mkdir -p /data && chown -R node:node /data /app
VOLUME /data
USER node
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD node -e "fetch('http://localhost:8090/health').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"
CMD ["node", "--no-experimental-strip-types", "--import", "tsx", "src/server.ts"]
