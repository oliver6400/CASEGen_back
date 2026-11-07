# Multi-stage Dockerfile for CASEGen Web Frontend
# Stage 1: Build
FROM node:20-alpine AS builder
WORKDIR /workspace

# Install dependencies
COPY package*.json ./
RUN npm ci

# Copy sources and build production bundle
COPY . .
RUN npm run build

# Stage 2: Runtime with Nginx
FROM nginx:alpine
COPY --from=builder /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
