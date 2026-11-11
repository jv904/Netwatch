# NetWatch Web — Angular servido pelo nginx, que também encaminha /api para a NetWatch API.
# Build:  docker build -t netwatch-web ./netwatch-web

# ---------- 1. Compilação ----------
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npx ng build --configuration production

# ---------- 2. Execução ----------
FROM nginx:1.27-alpine

# O nginx aplica as variáveis de ambiente no modelo ao iniciar (ex.: NETWATCH_API_UPSTREAM).
ENV NETWATCH_API_UPSTREAM=api:8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/netwatch-web/browser /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD wget -qO- http://localhost/ > /dev/null || exit 1
