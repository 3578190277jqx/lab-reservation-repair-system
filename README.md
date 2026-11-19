# 实验室设备预约与故障报修系统

基于 Spring Boot、Vue 3 和微信小程序的毕业设计，覆盖设备管理、预约审核、签到、故障报修、权限和统计。开发过程中使用 Codex 等 AI 工具辅助；本仓库是筛选后的源代码快照。

- `lab-backend`：Spring Boot / MyBatis-Plus / MySQL / Redis / Redisson / Sa-Token。
- `lab-frontend`：Vue 3 / Vite / Pinia / Element Plus。
- `lab-miniprogram`：微信小程序客户端。
- 预约写入按学生锁、设备锁、数据库事务的顺序处理，并校验时间冲突。
- 助手接入兼容 Chat Completions 的接口回答操作问题；业务提交由用户确认。

后端需 JDK 8、Maven、MySQL 8 和 Redis，按 `lab-backend/src/main/resources/application.yml` 配置环境变量后运行 `mvn spring-boot:run`。网页在 `lab-frontend` 目录运行 `npm ci` 与 `npm run dev`。小程序需配置自己的 AppID 和可访问接口，正式发布需要符合微信要求的 HTTPS 域名。

公开快照不含本机配置与运行数据，小程序地址改为本地示例。项目用于学习和作品展示，不代表学校已正式使用；公开快照尚未进行完整环境部署验收。
