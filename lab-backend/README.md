> 2026-10-06更新：QQ邮箱验证码注册/绑定/找回已加入，手机号短信找回预留。部署与当前功能请以项目根目录README-修复与找回.md为准。

2026-10-27：新增设备 XLSX 导入导出、traceId、受限 Actuator、操作审计和 Flyway。当前启动与部署以根目录 `README.md` 为准，存量数据库升级前阅读 `docs/数据库迁移与设备Excel.md`。本机旧库接管默认关闭；不要未备份就直接开启。

从本目录执行 `mvn clean package`，产物为 `target/lab-backend.jar`。`application.yml` 已显式导入当前工作目录的 `.env.local`，用 Java properties 格式填写私有配置，不提交该文件。Java8 保持兼容，不使用 Boot3。
