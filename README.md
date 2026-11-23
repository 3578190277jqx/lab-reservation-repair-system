# 实验室设备预约与故障报修系统

基于 Spring Boot + Vue 的毕业设计业务系统，包含网页端、微信原生小程序和共享后端。围绕设备台账、预约排期、审核签到、故障工单与维修记录提供管理功能。

## 功能

- 学生查看设备与排期、申请及取消预约、签到、提交报修、跟踪处理进度。
- 实验室管理员维护所属实验室设备、审核预约、受理和处理维修工单。
- 超级管理员管理用户与角色，查看统计和操作审计。
- 预约使用时间重叠 SQL 与 Redisson 锁，支持审核超时释放、爽约与黑名单。
- 故障受理通过事务和 Outbox 记录受影响预约及通知，限制跨用户和实验室访问。
- 可选的图片识别和操作助手通过第三方 API 提供辅助内容。

## 技术与目录

| 目录 | 内容 |
| --- | --- |
| `lab-backend` | Java 8、Spring Boot 2.7.18、MyBatis-Plus、Sa-Token、MySQL 8、Redis 7、Redisson、Druid、Flyway、Hutool、Knife4j、Apache POI |
| `lab-frontend` | Vue 3、Element Plus、Vite、Pinia、Vue Router、Axios、ECharts，JavaScript Composition API |
| `lab-miniprogram` | 微信原生 JS、WXML、WXSS，小程序与网页共用系统账号和接口 |
| `deploy` | MySQL 初始化、Nginx、备份恢复、Docker Compose 及 2 核 2GB 参数 |
| `docs` | 数据库迁移、导入导出说明和验证范围 |

## 本机启动

需要 JDK 8、Maven、Node.js 22.12+、MySQL 8 和 Redis 7。后端配置文件使用环境变量占位符。示例配置可直接保存在本机环境或 IDE 的运行配置中。

1. 在 MySQL 创建空数据库 `lab_management`，将 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 配置为本机数据库连接，将 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 配置为本机 Redis 连接。MySQL 和 Redis 主机默认值用于 Docker，直接本机运行时应改为实际地址。
2. 在 `lab-backend` 执行 `mvn spring-boot:run`，后端默认端口 8080。Flyway 为全新空库执行迁移。已有八表数据库的迁移方式见数据库迁移文档。
3. 在 `lab-frontend` 执行 `npm ci` 和 `npm run dev`，打开 `http://127.0.0.1:5173/login`。Vite 代理连接本机后端。
4. 在微信开发者工具导入 `lab-miniprogram`，设置自己的 AppID，并在 `config.js` 填入手机可访问的接口地址。导出配置使用测试 AppID 和本机示例地址。正式小程序需配置 HTTPS 合法域名。

本机空库迁移只创建业务结构。首次管理员初始化可使用下面的 Docker 初始化流程。注册学生账号、邮箱验证码与 AI 功能需要相应后端配置。

## Docker 首次启动

```sh
cp .env.example .env
docker build -t lab-backend:local ./lab-backend
docker build -t lab-frontend:local ./lab-frontend
```

在本机 `.env` 填入数据库、Redis 的私有密码与管理员 BCrypt 散列。可以使用镜像内的交互工具生成散列，输入所选管理员密码并确认。

```sh
docker run --rm -it --entrypoint java lab-backend:local \
  -Dloader.main=com.lab.utils.PasswordHashTool \
  -cp /app/app.jar org.springframework.boot.loader.PropertiesLauncher
```

将工具输出的散列填入 `INITIAL_ADMIN_PASSWORD_HASH`，保留单引号。MySQL 初始化会在新数据卷建立八张基础表、管理员 `admin`、实验室和设备示例数据。

本项目默认拒绝自动接管无迁移历史的非空数据库。确认是本次全新初始化的卷后，首次启动显式开启接管，完成 V1 基线与后续迁移。Linux 或 macOS 可使用下面的命令。PowerShell 可先设置 `$env:SCHEMA_ADOPT_EXISTING='true'` 后执行同一 Compose 命令。

```sh
SCHEMA_ADOPT_EXISTING=true docker compose up -d --wait
```

初次启动成功后，关闭一次性开关并重新创建后端。PowerShell 中先移除本次设置的环境变量。

```sh
docker compose up -d --no-deps --force-recreate --wait backend
```

默认网页地址为 `http://127.0.0.1:8088/login`。管理员密码为生成散列时自己选择的密码。

已有数据卷升级前先备份并核对结构，不要直接套用首次初始化命令。SQL 与 Java migration 共同组成 V1–V6，均在 `lab-backend/src/main` 中。

2 核 2GB 部署可在以上 Compose 命令追加 `-f docker-compose.yml -f deploy/compose-2g.yml`。构建宜在开发机完成。`deploy/compose-runtime.yml` 使用 `deploy/runtime/backend/app.jar` 和 `deploy/runtime/frontend/dist` 作为自行生成的运行产物。

`docker-compose.ecs.yml` 提供独立 HTTPS 网关模板，启用前设置域名、证书及 CORS。后端、MySQL 和 Redis 通过内部网络连接。

## 验证命令

```sh
# lab-backend
mvn test

# lab-frontend
npm ci
npm test
npm run lint
npm run format:check
npm run build

# lab-miniprogram
node --test tests/*.test.js
```

历史验证的范围见 `docs/验证说明.md`。生产环境的账号、上传图片、业务数据和第三方密钥通过私有环境管理。
