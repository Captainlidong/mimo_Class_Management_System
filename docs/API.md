# API 接口文档

> 桌面端与移动端共用的后端接口清单（`backend/src/main/java/com/classmgmt/web/`）。Base URL `/api`；除下载/导出类外，响应统一为 `{code, message, data}`，`code=0` 成功，失败 code = HTTP 状态码。认证方式：`CMS_SESSION` HttpOnly Cookie（兼容 `X-Auth-Token` 头），401 表示未登录/会话过期。

## 通用约定

| 项 | 约定 |
|---|---|
| 响应包 | `{code, message, data}`；HTTP 2xx 且 code=0 才算成功 |
| 错误码 | 0 成功 · 400 参数错误 · 401 未登录 · 404 不存在 · 409 冲突 · 429 登录锁定 · 500 内部错误 |
| 时间 | ISO 8601（如 `2026-11-15T21:00:00Z`），前端集中转本地展示 |
| PATCH | 全部按传入键更新（白名单校验），未传字段保持不变 |
| 免认证 | `/api/health`、`/api/auth/*`；其余需登录 |

## 1. 认证 `/api/auth`

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | `/auth/status` | 登录态探测 | 返回 `authRequired / loggedIn / username / sessionExpiresAt` |
| GET | `/auth/captcha` | 图形验证码（一次性，3 分钟） | 返回 `{captchaId, image(dataURL)}` |
| POST | `/auth/login` | 登录 | `{username, password, captchaId, captchaCode}`；5 次失败锁 IP → 429 |
| POST | `/auth/logout` | 退出并销毁会话 | — |

## 2. 基准名单 `/api/students`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/students` | 名单 `{locked, count, students:[{id,name,studentNo}]}` |
| PUT | `/students` | 整表替换（`StudentsReplaceRequest`） |
| POST | `/students/lock` · `/students/unlock` | 锁定/解锁名单 |
| POST | `/students/parse-import` | xlsx 解析预览（multipart `file`） |
| POST | `/students/parse-image` | 截图 OCR 解析预览（60s 超时） |
| GET | `/students/export?format=xlsx\|csv` | 导出名单（字节流） |

## 3. 名单筛查 `/api/check`、`/api/check-records`

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| POST | `/check` | 执行比对 | `{text, save?, title?, taskId?}` → 参与数/未参与/无效/重复 + 明细 |
| GET | `/check-records` | 历史列表（checkedAt 倒序） | `taskId`、`q` |
| GET | `/check-records/{id}` | 历史详情（只读快照） | — |
| PATCH | `/check-records/{id}` | 改标题/备注 | 按传入键 |
| DELETE | `/check-records/{id}` | 删除 | — |
| GET | `/check-records/{id}/export` | 导出（字节流） | `format=xlsx\|csv` |

## 4. 长期任务 `/api/tasks`、分组 `/api/groups`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/tasks` | 任务列表（含 `recordCount` 筛查次数） |
| POST · PATCH · DELETE | `/tasks[/{id}]` | 任务 CRUD（name/type/remark） |
| GET | `/tasks/{id}/records` | 该任务下的筛查记录摘要 |
| GET | `/groups` | 分组列表（含成员 `[{studentId,name,studentNo}]`） |
| POST · PUT · DELETE | `/groups[/{id}]` | 分组 CRUD（`{name, remark?, studentIds?}`） |
| GET | `/groups/{id}/check-view?recordId=` | 分组 × 筛查记录参与拆分 |

## 5. 资料收集与分发 `/api/collections`

任务 `kind`：`collect`（收集，"谁没交"）/ `distribute`（分发，"谁没领"）。条目字段 `submitted / submittedAt / remark`。

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | `/collections/tasks` | 任务列表（含汇总） | — |
| POST | `/collections/tasks` | 新建 | `{name, kind, studentIds?, deadline?, remark?}` |
| GET | `/collections/tasks/{id}` | 详情 + 全部条目 | — |
| PATCH | `/collections/tasks/{id}` | 更新任务 | `name/kind/remark/deadline` |
| DELETE | `/collections/tasks/{id}` | 删除（含条目） | — |
| PATCH | `/collections/entries/{id}` | 条目更新（勾选/备注失焦保存） | `submitted`、`remark` 任意键 |
| POST | `/collections/tasks/{id}/students` | 添加学生 | `{studentIds:[]}` |
| DELETE | `/collections/tasks/{id}/students/{studentId}` | 移除单个 | — |
| POST | `/collections/tasks/{id}/students/batch-remove` | 批量移除 | `{studentIds:[]}` |
| POST | `/collections/tasks/{id}/mark` | 按姓名/学号文本标记 | `{text, submitted}` |
| POST | `/collections/tasks/{id}/mark-all` | 一键标记全部（对称开关） | `{submitted}` |
| GET | `/collections/tasks/{id}/export` | 导出（字节流） | `format`、范围、列选择、统计开关 |

## 6. 请假 `/api/leaves`

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | `/leaves` | 列表 | `studentId/status/type/from/to` |
| GET | `/leaves/stats` | 统计（月度人次、逐生汇总） | — |
| POST | `/leaves` | 登记（可多学生） | `{studentIds, leaveType(sick/personal/official/other), startTime, endTime, reason?, approver?, remark?}` |
| PATCH | `/leaves/{id}` | 编辑 | 按传入键 |
| POST | `/leaves/{id}/close` · `/reopen` | 销假 / 重开 | — |
| DELETE | `/leaves/{id}` | 删除 | — |
| GET | `/leaves/export` | 导出（字节流） | `format` |

## 7. 待办 `/api/todos`

排序（服务端保证）：未完成（`dueAt` 升序，null 在后）→ 已完成（`doneAt` 倒序）。条目附 `overdue` / `dueToday` 计算标记。

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | `/todos` | 全量列表 | — |
| POST | `/todos` | 新建 | `title` 必填；`detail / dueAt / priority(high/mid/low) / category(教学/活动/资料/通知/财务/其他) / repeatRule(none/daily/weekly/monthly)` |
| PATCH | `/todos/{id}` | 更新（按传入键） | 同上 + `done`；**完成重复待办时自动生成下期**（dueAt 顺延 1 天/周/月） |
| DELETE | `/todos/{id}` | 删除 | — |

## 8. 奖学金 `/api/scholarships`

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET · POST · PATCH · DELETE | `/scholarships/batches[/{id}]` | 批次 CRUD | `{name, remark}` |
| GET | `/scholarships/batches/{id}` | 批次详情 + 获奖明细 | — |
| POST | `/scholarships/batches/{id}/awards` | 添加获奖（单条或 `rows:[]` 批量） | `{studentId, awardName, amount, received?, remark?}` |
| PATCH · DELETE | `/scholarships/awards/{id}` | 获奖条目维护 | 按传入键 |
| POST | `/scholarships/awards/{id}/mark-received` | 标记交表 | `{received}` |
| POST | `/scholarships/batches/{id}/import/parse` | Excel 解析预览 | `file` + 列配置（headerRow/nameCol/…） |
| POST | `/scholarships/batches/{id}/import/confirm` | 确认导入 | 解析结果回传 |
| POST | `/scholarships/batches/{id}/convert-to-collection` | **一键转为收集任务**（状态平移，批次保留） | → `{taskId, name, total}` |

## 9. 群发素材 `/api/announcements`

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | `/announcements` | 列表 | `q/category/status` |
| GET · POST · PUT · DELETE | `/announcements[/{id}]` | 详情/创建/更新/删除 | — |
| PATCH | `/announcements/{id}/pin` | 置顶 | `{pinned}` |
| POST | `/announcements/{id}/versions/{versionId}/restore` | 恢复历史版本 | — |
| POST | `/announcements/{id}/files` | 上传附件（multipart，字段 `files`） | — |
| GET · DELETE | `/announcements/{id}/files/{fileId}` | 附件下载/删除 | — |
| POST | `/announcements/{id}/mark-sent` · `/unmark-sent` | 发送标记 | — |

## 10. 工作台与系统 `/api/dashboard`、`/api/system`、`/api/health`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/dashboard/summary` | 工作台聚合：`{todos:{pending,overdue,dueToday}, collections:{tasks,totalEntries,submittedEntries}, leaves:{active}, students:{count,locked}, lastCheck{title,checkedAt,absentCount,baselineCount}|null}` |
| GET | `/health` | 免认证健康检查：`{status, students, passwordRequired, time}` |
| GET | `/backup/export` | 全量 JSON 备份（裸 JSON，13 组实体） |
| POST | `/backup/import` | 覆盖式恢复（裸 JSON；旧 id→新 id 外键重建；子表父缺失自动跳过） |

## 11. 下载类接口

导出与附件为字节流响应（无响应包），前端用 `responseType:'blob'` + `<a download>` 保存（Cookie 自动携带）。
