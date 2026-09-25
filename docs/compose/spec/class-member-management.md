---
feature: class-member-management
status: delivered
updated: 2026-09-21
branch: feature/class-member-management
commits: 44a7c949aaff52d09647aa8c0d08eb54b1a5ae22..48f53f4331985e170491251976c6ea1304e6d0a4
---

# Class Member Management System

## Report

**What was built** — 班长专属「班级成员管理系统」：Vue3 + Element Plus 前端，Spring Boot 3 / Java 19 / JPA 后端，MySQL `class_mgmt` 持久化。支持基准名单批量导入与锁定/解锁、粘贴名单一键筛查（已参与/未参与/无效/重复）、历史快照（可改名称与备注、名单只读）、长期任务分期、自定义分组及「按分组查看核对结果」、xlsx/csv 导出、JSON 备份导出/导入（含 ID 重映射）、默认 `127.0.0.1` 绑定与可选访问口令。前端 build 产物已放入 `backend/src/main/resources/static`，可单 jar 运行；也可在 IDEA 中打开 monorepo 分别运行前后端。

**Verification** — 
- `cd backend && mvn test` → PASS（Tests run: 13, Failures: 0, Errors: 0）
- `cd frontend && npm.cmd run build` → PASS（vite build）
- 运行时冒烟：`GET /api/health` status=UP students=36；精确名匹配 participated 正确；`张三丰` 类模糊名进入 invalid 不误匹配；`PATCH /api/check-records/{id}` 可改 title/remark；`GET /api/groups/{id}/check-view` 返回组内已/未参与；`POST /api/backup/import` code=0 且 students/checkRecords 恢复
- IDEA：已通过 `idea64.exe` 打开工程根目录

**Journey log** —
1. 并行生成过两套包结构（controller/entity/repository vs web/domain/repo），编译冲突；已删除旧树，保留单一实现。
2. Java 正则 `\p{IsSymbol}` 非法导致 NameNormalizer 静态初始化失败；改为 `\p{P}\p{S}`。
3. Windows 下 MockMvc/源码中文乱码：surefire + servlet encoding 强制 UTF-8，集成测试改用 UTF-8 读响应。
4. 路径含空格导致 `java -jar` 启动失败；交付时用无空格路径或 IDEA 运行。
5. 独立评审指出：模糊 contains 匹配、分组筛查无 UI、历史备注不可编辑、备份导入 ID 未映射——均已修复并复测。

## [S1] Problem

班长久依赖第三方小程序与人工名单对账完成抽签核对、报名统计、作业/考勤筛查，耗时且易漏。需要班长专属 Web 系统：锁定基准名单后，粘贴任意来源名单，秒级输出已参与/未参与/无效三栏，并留存可导出的历史记录。需求依据：`docs/班级成员管理系统需求分析文档.md` v2.0。

## [S2] Design

### 技术契约

| 项 | 选型 |
|----|------|
| 工作区 | `D:\AI application development\XiaomiMiMo\mimo_Class_Management_System`（用户指定） |
| 分支 | `feature/class-member-management` |
| 后端 | Spring Boot 3.2.5 + Java 19 + Maven + Spring Data JPA |
| 数据库 | MySQL 8（`class_mgmt`），连接见 `backend/src/main/resources/application.yml` |
| 前端 | Vue 3 + Vite + Vue Router + Element Plus + Axios |
| 结构 | monorepo：`backend/` + `frontend/` + `docs/` |
| 导出 | Apache POI（xlsx）+ csv/txt |
| 部署 | 默认 `127.0.0.1:8080`；生产静态资源在 `backend/.../static` |

### 领域规则

1. **解析**：按换行/逗号/顿号/分号/制表符/空白切分；去首尾空白；全角转半角；剥离行首序号前缀。
2. **比对**：规范化后**姓名全称精确匹配**，或**学号全称精确匹配**；禁止 contains/子串模糊命中。
3. **三栏结果**：participated / absent / invalid + duplicates 计数；无效项可见。
4. **基准名单**：默认可解锁修改；锁定时 PUT 名单返回 409；整表替换时清空 group_member 防孤儿。
5. **历史**：快照只读；PATCH 仅 title/remark；前端提供「改名/备注」。
6. **分组筛查**：`GET /api/groups/{id}/check-view?recordId=` + 前端「按分组筛查」对话框。
7. **备份**：export/import JSON；导入时 student/group/task ID 重映射到 group_member 与 check_record.taskId。
8. **访问**：`server.address=127.0.0.1`；`app.access-password` 非空时校验 `X-Access-Password`；前端顶栏可填口令，401 有提示。

### REST API（`{code,message,data}`，成功 code=0）

见 README「常用 API」及 controllers：`/api/students`、`/api/check`、`/api/check-records`、`/api/tasks`、`/api/groups`、`/api/backup/*`、`/api/health`。

### 测试边界

- `NameNormalizerTest`：全半角、序号、分隔符、符号
- `CheckServiceTest`：三栏、去重、空基准、**拒绝模糊姓名/学号子串**
- `ApiIntegrationTest`：名单锁定 409、筛查落库、历史改名备注、分组 CRUD

## [S3] Out of Scope

- 抽签/报名/签到采集、随机数
- 多用户注册登录、RBAC
- 成绩/缴费/教务对接
- 模糊姓名纠错
- 公网多租户部署

## Tasks

- [x] T1: Workspace 脚手架 — acceptance: git 分支存在，monorepo 目录、.gitignore、需求文档副本就位 (covers: S2)
- [x] T2: Spec 文档 — acceptance: `docs/compose/spec/class-member-management.md` (covers: S2)
- [x] T3: 后端领域与比对引擎 — acceptance: 实体/仓库 + NameNormalizer + CheckService 单测通过 (covers: S2)
- [x] T4: 后端 REST API — acceptance: students/check/tasks/groups/records/export/backup/health；锁定与快照规则生效 (covers: S2)
- [x] T5: 前端 Vue3 界面 — acceptance: 名单/筛查/历史/任务/分组页面调用真实 API，含分组筛查与备注编辑 (covers: S2)
- [x] T6: 联调与 IDEA — acceptance: `mvn test` 通过；前端 build 成功；idea64 打开工程 (covers: S2)
