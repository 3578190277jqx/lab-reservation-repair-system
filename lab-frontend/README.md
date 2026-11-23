> 2026-10-06更新：QQ邮箱验证码注册/绑定/找回已加入，手机号短信找回预留。部署与当前功能请以项目根目录README-修复与找回.md为准。

# 实验室预约与运维平台 · Vue 3 前端

2026-10-27 更新：统一样式令牌与反馈组件，资源列表拆分为 composable/展示组件，加入设备 Excel 操作和 admin 操作审计页面 `/operation-logs`。已实现的新增/修改/审核表单以当前源码为准，下文早期“后续开发”记录仅为历史说明。完整入口与当前验收见根目录 `README.md` 和 `docs/评审整改结果-20261027.md`。

项目目录：`E:\codex\lab-frontend`。使用 Vue 3、Element Plus、Vite、Pinia、Vue Router、Axios、ECharts，直接对接 `E:\codex\lab-backend` 中已实现的接口。

## 启动

需要 Node.js 22.12+、24 或 26；本次使用 Node.js 26.2.0。依赖版本在 `package.json` 中固定，`package-lock.json` 已提交以便复现。

```powershell
cd E:\codex\lab-frontend
npm ci
npm run dev
```

打开 `http://127.0.0.1:5173/login`。开发服务固定使用 5173，端口占用时会明确报错。若 PowerShell 阻止执行 `npm.ps1`，改用 `npm.cmd ci` 和 `npm.cmd run dev`。

登录页不依赖后端即可打开。真实登录、业务查询和统计需要启动已有 Spring Boot 后端及其 MySQL、Redis，并使用数据库中的真实账号；前端未内置测试账号、默认密码或演示登录绕过。

```powershell
npm run build
npm run preview
npm test
```

`build` 输出 `dist`，`preview` 在 4173 提供静态预览。开发期请求代理只在 `npm run dev` 生效，生产及静态预览的 API 联调需要对应的反向代理。

首次安装中官方 npm registry 直连出现连接重置，本次安装命令临时使用了 `https://registry.npmmirror.com`，未修改全局 npm 配置。锁文件含该镜像的下载地址及 integrity 校验值。若本地网络也有此问题，可执行：

```powershell
npm ci --registry=https://registry.npmmirror.com
```

## 需要手动调整的配置

`.env.development`：

```dotenv
VITE_APP_TITLE=实验室预约与运维平台
VITE_API_BASE_URL=/api
VITE_PROXY_TARGET=http://127.0.0.1:8080
```

**后端地址或端口变化时修改 `VITE_PROXY_TARGET`，随后重启开发服务。** 浏览器请求 `/api/user/login`，Vite 去掉一次 `/api` 后转发为后端 `/user/login`；后端健康检查本来就是 `/api/health`，因此前端使用 `/api/api/health`，只去掉外层前缀。

`.env.production` 默认仍用 `/api`，部署时由 Nginx 转发。`VITE_` 环境变量会打包进浏览器代码，不能放百度 API 密钥或数据库密码。百度识别由后端在提交报修时调用，前端没有独立的 AI 密钥或虚构的 AI 接口。

## 文件结构

```text
lab-frontend/
├── package.json / package-lock.json
├── vite.config.js
├── index.html
├── .env.development / .env.production
├── src/
│   ├── main.js / App.vue
│   ├── router/index.js
│   ├── stores/index.js / user.js
│   ├── utils/auth.js / navigation.js / request.js
│   ├── api/
│   │   ├── user.js / lab.js / equipment.js / reservation.js
│   │   └── repair.js / stats.js / file.js / health.js
│   ├── layout/index.vue
│   │   └── components/Sidebar.vue / Topbar.vue
│   ├── views/
│   │   ├── LoginView.vue / RegisterView.vue / HomeView.vue
│   │   ├── ResourceListView.vue / ProfileView.vue / StatsView.vue
│   │   └── ErrorView.vue / SessionErrorView.vue
│   └── styles/index.css
└── tests/
    ├── auth.spec.js / navigation.spec.js / request.spec.js
    └── user-store.spec.js / helpers/storage.js
```

业务列表已提供实际分页查询、加载状态、空态与失败重试。实验室、设备、预约、工单、用户等页面的新增、编辑、审核及维修表单属于后续业务页面开发；本次已将这些后端操作完整封装到 API 模块中。个人信息页面为只读，未提供后端尚不存在的修改接口。

统计页展示真实总览、预约趋势和工单状态图；不会用虚构数字填充接口失败或空结果。其余统计 API 已封装，可用于后续扩展。

## 路由与角色

| 路由 | 权限 |
| --- | --- |
| `/login`、`/register` | 公开；注册仅创建学生账号 |
| `/home`、`/labs`、`/equipment`、`/repairs`、`/profile` | 所有已登录角色 |
| `/reservations` | student，查询自己的预约 |
| `/reservation-review`、`/stats` | admin、lab_admin |
| `/users` | admin |
| `/403`、未知路径 | 无权限页、404 页 |
| `/session-error` | 用户信息加载网络失败时的重试页 |

Pinia 在 Router 之前安装，守卫和组件共用同一个实例。侧栏按角色显示菜单，直接输入不允许的 URL 仍由守卫拦截；最终权限与实验室范围始终由后端校验，前端隐藏菜单不能替代服务端授权。

## 预约与报修操作（2026-10-03 补齐）

- 学生在设备列表点击“申请预约”，或在“我的预约”页点击“申请预约”，选择设备、未来时间段及用途后提交。设备标为已预约时仍可申请其他空闲时段，后端执行冲突检查。
- 学生可取消开始前、未签到的待审核或已通过预约；已通过预约在开始后30分钟内且结束前显示签到入口。
- 管理员在“预约审核”中处理待审核记录，拒绝时必须填写意见。管理员账号不提供代学生申请预约功能。
- 所有角色可从设备行或“故障工单”页提交报修，支持可选的 JPEG/PNG 图片上传和 AI 辅助提示。
- 管理员按“受理工单→开始维修→完成维修”处理工单，完成时填写结果及费用；继续处理操作限定受理人或超级管理员。实验室管理范围由后端再次校验。
- 工单详情展示故障描述、AI提示、图片及维修流水。附件使用带令牌请求转换为 Blob 后显示。
- 提交过程中禁用重复操作；弹窗与打开时的会话绑定，切换账号或路由会使旧弹窗失效。设备选择支持名称搜索和加载更多。

实验室、设备的新增/编辑/删除以及用户角色分配接口已经封装，但这些网页表单仍未补齐。本次补齐的是预约和报修流程。

## Token 与请求封装

- 登录调用 `/user/login`，保存返回的令牌和过期时间，然后携带令牌调用 `/user/info` 获取权威用户信息和角色。
- 只在 `sessionStorage` 保存 Token 与 `expiresAt`，不保存密码、用户资料或可信角色；刷新页面后重新取用户信息。后端令牌有效期为 7 天，浏览器标签会话的存储生命周期独立于后端。
- Axios 自动发送 `Authorization: Bearer <token>`，`code === 200` 时解包 `Result.data`。分页调用直接得到 `{ records, total, current, size, ... }`。
- 当前会话的 401 会清理状态并返回登录页；自然到期也会触发退出。旧请求的迟到 401 不会清理新登录的账号。
- 403、409、网络错误和超时会返回明确错误，网络故障不会误清令牌。取消请求不弹错误通知。
- 登录/注册使用 `skipAuth: true`。支持 `silent: true` 静默处理、AbortSignal 取消以及 Blob 图片响应；Blob 形式的后端 JSON 错误会被解析。
- 退出先请求后端注销，随后清理当前浏览器会话；若网络失败，会显示错误并完成本地退出，服务器端令牌可能继续有效至过期。
- `safeRedirect` 只接受站内路径，拒绝外部 URL、控制字符、反斜杠以及回到认证页面的循环跳转。

请求示例：

```javascript
import { getEquipmentPage } from '@/api/equipment'
import { uploadImage, getImageBlob } from '@/api/file'
import { submitRepair } from '@/api/repair'

const page = await getEquipmentPage({ current: 1, size: 10, labId: 1 })
console.log(page.records)

// 在用户选择文件并主动提交报修时调用；这里的 file 为浏览器 File 对象。
const uploaded = await uploadImage(file)
const order = await submitRepair({ equipmentId: 1, description: '设备无法启动', imageUrl: uploaded.imageUrl, analyzeImage: true })
const blob = await getImageBlob(order.imageUrl)
const imageObjectUrl = URL.createObjectURL(blob)
// 页面不再使用图片时调用 URL.revokeObjectURL(imageObjectUrl)。
```

上传使用 FormData，由浏览器生成 multipart boundary。图片只允许请求本系统返回的路径，不能传入任意第三方地址；查看图片同样携带 Token。

## 生产部署要点

Vue Router 使用 History 模式，生产 Nginx 需要静态页面回退及 API 代理，例如：

```nginx
location /api/ {
    proxy_pass http://backend:8080/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 10m;
}

location / {
    try_files $uri $uri/ /index.html;
}
```

`backend` 需替换为实际后端主机名或后续 Compose 的服务名；`proxy_pass` 末尾 `/` 用于去掉 `/api/`。不要让 API 请求回退到 `index.html`。

## 验证边界

2026-10-03：`npm run build` 通过；5 个测试文件共 **34 项单元测试全部通过**。新增7项测试覆盖预约与报修的角色、状态、签到时间窗口、DTO、审核拒绝、金额和图片校验。

本轮另完成13项真实浏览器联调检查，连接本地 Spring Boot、MySQL 与 Redis，验证学生登录、设备行和预约页提交、管理员通过与拒绝、学生取消、图片上传、报修提交、受理、开始维修、完成维修、设备状态变化和四次维修流水。使用独立测试账号及设备，验证后已逻辑删除测试数据并失效设备缓存。记录位于 `E:\codex\workflow-fix-20261003`。实时签到入口已由单元测试验证窗口规则，本轮没有进行真实签到；AI外部识别与Docker镜像部署也未在本轮联调中验证。构建有大分块提示，未阻止构建。

配置方式参照 [Vite 官方文档](https://vite.dev/guide/)、[Vue Router 导航守卫](https://router.vuejs.org/guide/advanced/navigation-guards.html)和 [Pinia 在组件外使用 Store](https://pinia.vuejs.org/core-concepts/outside-component-usage.html)。


## 代码检查与格式

- `npm run lint`：检查整个前端项目，覆盖 JavaScript 与 Vue 模板；警告按失败处理。
- `npm test`：运行行为测试；`npm run build`：检查生产构建。
- `npm run format` / `npm run format:check`：渐进式格式化，本次覆盖资源列表拆分、审计、设备 Excel、共享请求安全边界及新增测试。命令中的路径明确列出范围；未将既有视觉组件和全局样式的纯格式变化混入本次整改。
- 统一 Prettier 配置已提供。后续修改其他文件时可运行 `npx prettier --write 文件路径`，逐步纳入上述命令范围。

## 设备 Excel 与操作审计

- 管理员、实验室管理员在「设备台账」可下载导入模板、导入新增设备、按已提交的列表筛选导出。文件仅支持 `.xlsx`，最多 500 行、2 MiB；状态不从文件导入，由业务流程维护。后端校验实验室管理范围、设备编号和字段，整体事务提交；校验失败显示具体错误并保留当前列表。
- 导出及模板下载复用 Axios 登录快照与过期处理，并检查 Excel 的 ZIP 文件头；JSON/HTML 错误不会被下载成 Excel。前端不会把令牌拼入文件 URL。
- 「操作审计」仅系统管理员可进入，支持模块和成功/失败筛选、分页，展示操作人、接口、结果、链路编号与耗时；不会展示请求体、密码和 API 密钥。API 与最终数据范围仍由后端控制。
