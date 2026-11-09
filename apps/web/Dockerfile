# 운영 이미지. next.config.ts 의 output: "standalone" 결과만 실행한다.
# BuildKit 전용 문법(--mount, --chmod)은 쓰지 않는다. NAS의 빌더가 구형일 수 있다.

FROM node:24-alpine AS deps
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN corepack enable
WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

FROM deps AS build
COPY . .
# rewrites 대상은 next build 시점에 routes manifest로 고정되므로 빌드 인자로 받는다.
ARG API_INTERNAL_URL
ENV API_INTERNAL_URL=${API_INTERNAL_URL} \
	NEXT_TELEMETRY_DISABLED=1
RUN pnpm build

FROM node:24-alpine AS runner
ENV NODE_ENV=production \
	NEXT_TELEMETRY_DISABLED=1 \
	PORT=3000 \
	HOSTNAME=0.0.0.0
WORKDIR /app
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 3000
CMD ["node", "server.js"]
