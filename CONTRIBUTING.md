# 贡献指南

感谢关注「喵班」！本文帮你搭起开发环境并遵守项目规范。

## 🛠 环境搭建

| 依赖 | 版本 |
|---|---|
| JDK | 19+ |
| Maven | 3.6+ |
| Node.js | ≥ 18 |
| MySQL | 8（测试用 H2 内存库，不依赖） |

```bash
git clone https://github.com/Captainlidong/mimo_Class_Management_System.git
cd mimo_Class_Management_System

# 1. 数据库：MySQL 8 建库 class_mgmt，连接配置在
#    backend/src/main/resources/application.yml
#    （本机专属覆盖可建 application-local.yml，该文件不入库）

# 2. 启动后端（默认 8080）
cd backend && mvn spring-boot:run

# 3. 启动前端（5173，/api 已代理到 8080）
cd ../frontend && npm install && npm run dev
```

首次启动若名单为空会自动写入 36 名示例学生（`app.seed-sample=false` 关闭），在「基准名单」页解锁后即可替换为真实名单。

启用登录保护（生产建议）：启动参数追加 `--app.access-username=你的账号 --app.access-password=你的密码`。

## 🔀 开发工作流

1. 从 `main` 拉分支：`git checkout -b feat/xxx`（或 `fix/xxx`）
2. 开发 + 自测（见下方清单）
3. PR 到 `main`，说明改动点、动机与自测证据（UI 改动附截图）

### 提交信息规范（Conventional Commits）

| 前缀 | 用途 | 示例 |
|---|---|---|
| `feat` | 新功能 | `feat: dashboard home with five metric cards` |
| `fix` | 修复 | `fix: sidebar menu scrolls independently when viewport is short` |
| `docs` | 文档 | `docs: add architecture guide` |
| `refactor` | 重构（不改行为） | `refactor: unify donut chart colors with theme tokens` |
| `chore` | 杂项 | `chore: bump version` |

一行说清"做什么 + 为什么"，中英文皆可。

## 📐 代码规范

### 后端

- **新功能必须带集成测试**（`@SpringBootTest + MockMvc`，H2 内存库）： happy path + 参数校验 + 关键边界；当前基线 70 项全绿
- 控制器薄、服务层厚；响应一律走 `ApiResponse`，业务异常用 `GlobalExceptionHandler.badRequest/notFound/conflict`
- PATCH 类接口遵循「白名单 containsKey + 值校验」模式，未传字段保持不变
- Spring Data 派生查询方法**必须先在 Repository 接口声明**
- 数据结构演进依赖 `ddl-auto: update` 自动加列，**不写迁移 SQL**；MySQL 专用 DDL 放 `SchemaFixConfig` 并 try-catch 兜底

### 前端

- **颜色一律用 `styles.css` 顶部 CSS 变量**（`--c-primary` 等），禁止页面内硬编码色值；需要新颜色先加令牌
- 图标只用 `DesktopIcon.vue`（新增图标：单 path、24 viewBox、stroke 1.8、currentColor，与移动端 AppIcon 同步补）
- 页面被 keep-alive 缓存：记得 `onDeactivated` 关弹层、`onActivated` 刷数据
- 请求一律走 `api/client.js`（401 拦截、code===0 解包）；导出统一 blob + `<a download>` 范式
- Element Plus 覆盖样式集中在 `styles.css`，不在页面里散写

## ✅ 提交前验证清单

- [ ] `mvn test` 全绿（当前 70 项）
- [ ] `npm run build` 无报错
- [ ] 浏览器走一遍受影响模块：新建 → 编辑 → 删除 → 导出；登录 → 401 路径
- [ ] 新颜色走了令牌、新图标进了 DesktopIcon、新端点补进 `docs/API.md`
- [ ] 打包验证：`rm -rf ../backend/src/main/resources/static/* && cp -r dist/* ../backend/src/main/resources/static/ && mvn clean package` 后 `java -jar` 可正常打开

## 🐛 报告 Issue

请附：复现步骤（步骤 → 期望 → 实际）、截图（UI 问题必备）、浏览器/环境信息、后端日志片段（如有报错）。

功能建议请描述**班级场景的哪个痛点**，比直接给方案更有价值。

## 💬 行为准则

保持友善、对新手耐心；讨论对事不对人。班级场景的真实需求优先于技术炫技。
