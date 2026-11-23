# 第一阶段：根据锁文件安装依赖，再构建 Vue 静态资源。
# Vite 8 要求 Node.js >= 22.12；Node 22 镜像会持续接收该系列补丁。
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# 只复制构建所需文件，避免将本地环境变量、日志、依赖目录带入镜像。
COPY index.html vite.config.js ./
COPY src ./src
# 生产环境统一通过同源 Nginx 代理访问后端，不需要写服务器 IP。
ENV VITE_API_BASE_URL=/api
RUN npm run build

# 第二阶段：仅保留 Nginx 和构建后的静态资源。
FROM nginx:stable-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
CMD ["nginx", "-g", "daemon off;"]
