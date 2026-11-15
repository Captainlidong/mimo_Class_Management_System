# 架构设计文档

> 本文描述「喵班」桌面端 + 后端的整体架构、关键设计决策与工程经验。接口清单见 [API.md](API.md)，参与开发前请阅读 [CONTRIBUTING.md](../CONTRIBUTING.md)。

## 1. 总体架构（三端一后端）

```
┌──────────────────────────────────────────────────────────────┐
│                     服务器（Nginx + Spring Boot）              │
│                                                              │
│   桌面浏览器 ──►  Nginx                                       │
│                    ├── /      反代 ──► Spring Boot :8080      │
│                    │        （前端产物打包在 jar 内）           │
│                    └── /m/   静态托管移动端 H5（独立构建产物）   │
│                                            └──► MySQL 8      │
└──────────────────────────────────────────────────────────────┘
        ▲                    ▲                    ▲
   桌面 Web（本仓库）    移动 H5 / Android     同一套 /api
   Vue3 + Element Plus  uni-app Vue3         同源 Cookie 会话互通
```

- 本仓库 = **桌面 Web 前端 + 全部后端**；前端 `npm run build` 产物拷入 `backend/src/main/resources/static/`，随 jar 单文件部署
- 移动端工程见 [Class_Management_System_Mobile](https://github.com/Captainlidong/Class_Management_System_Mobile)，H5 与后端同域部署（`/m/` 子路径），APK 为 web-view 壳

## 2. 后端分层

```
web/          13 个 @RestController + AccessPasswordFilter + GlobalExceptionHandler
  │  薄控制器：参数校验、调服务、返回 ApiResponse
  ▼
service/      业务层（17 个服务）
  │  CollectionService / CheckService / LeaveService / ScholarshipService
  │  TodoService / AnnouncementService / StudentService / GroupService
  │  HistoryService / ExportService / ImageOcrService / DashboardService
  │  AuthService / CaptchaService / LoginAttemptService ……
  ▼
repo/         Spring Data JPA 仓库（15 个实体）
  ▼
MySQL 8       ddl-auto: update（结构演进免 SQL），H2 跑测试
```

约定：

- 统一响应包 `{code, message, data}`（`ApiResponse`），成功 `code=0`；错误码 = HTTP 状态码（0/400/401/404/409/429/500），由 `GlobalExceptionHandler` + `ApiException` 工厂产生
- 控制器接受 `Map<String, Object>` 或 DTO；服务层用「白名单 + textOrNull」风格做部分更新（PATCH 按传入键更新）
- 业务删除尽量软处理（如长期任务删除不级联筛查记录，历史仍可读）

## 3. 认证与安全链路

```
请求 ─► AccessPasswordFilter（OncePerRequestFilter）
        ├── 白名单放行：/api/health、/api/auth/*、OPTIONS
        ├── 未配置 app.access-password → 全放行（本机开发模式）
        └── token 校验（Cookie CMS_SESSION 优先，X-Auth-Token 兜底）
              └─► AuthService.isValid(token)   ← 会话落库（session_record 表）
                    ├── 有效 → 滑动续期（now + 7 天）→ 放行
                    └── 无效/过期 → 删除并回 401
401 → 前端 axios 拦截器 → 全局提示 + 弹登录框（1 秒去重）
登录：IP 锁定检查(429) → 图形验证码(3 分钟一次性) → 常量时间比对 → 发会话
```

- **会话持久化**：`SessionRecord` 实体 + 唯一索引 token，服务重启不掉线；`@PostConstruct` 清理过期会话；上限 10 个、超出淘汰最早过期者
- **防爆破**：`LoginAttemptService` 按 IP 记 15 分钟窗口，5 次密码错误锁定，验证码错误不计数
- 附件下载、备份导出均在过滤器保护范围内；导入恢复属高危操作，桌面端做二次确认

## 4. 名单筛查与 OCR（核心链路）

```
输入（文本 / txt·csv / xlsx / 图片）
  ├── 图片 → ImageOcrService：Windows 系统 OCR 优先（Media.Ocr，中文效果好），
  │          失败回退 Tesseract（chi_sim，psm=6，tessdata 路径可配）
  ├── xlsx → StudentImportService 解析姓名列
  └── 文本 → 原样进文本框（人工确认后再筛查，OCR 结果不直接写库）
      ▼
CheckService：NameNormalizer 清洗（序号、分隔符、去重）
  → 与基准名单比对 → participated / absent / invalid / duplicates
  → 可勾选「保存为历史记录」生成 CheckRecord 快照（只读，可改名/导出/删除）
  → 可关联 LongTask（长期任务，按期归类筛查记录）
```

设计取向：**OCR 与解析结果一律先进文本框人工确认**，不直接落库——错一个字宁可手改，不污染基准数据。

## 5. 前端架构

- **Vue 3 + Element Plus 全量引入**（内部工具场景，包体可控优先开发效率）；ECharts 按需引入（`GraphicComponent` 必须注册，否则环形图中心文字不渲染）
- **页面级 keep-alive**：12 个视图全部缓存，页面需在 `onDeactivated` 关弹层、`onActivated` 刷新数据（工作台/设置页都是这个模式）
- **请求层**：`api/client.js` 单文件封装——`code===0` 才算成功、401 全局去重提示 + 弹登录、导出走 `responseType: 'blob'` + `<a download>`
- **主题系统**：全站视觉收敛在 `styles.css` 的 CSS 变量段（奶油 `#F7F2E7` 底 / 深林绿 `#3E5C4B` 主色 / 提醒 `#D9A441` / 危险 `#C96F5E` / 线 `#E8E2D4`），换肤只改这一处；Element Plus 通过 `--el-*` 变量覆盖同化
- **图标**：`DesktopIcon.vue` 单文件线性 SVG 图标库（24 viewBox、stroke 1.8、currentColor），与移动端 AppIcon 同一套 path，两端视觉一致
- **状态色规范**：绿=完成/参与、主绿=进行中、暖黄=今日到期、红橙=逾期、灰紫=无效，一律色 + 文字双表达

## 6. 测试策略

- **集成测试为主**：70 项 `@SpringBootTest + MockMvc` 用例（H2 内存库 MODE=MySQL、create-drop），覆盖登录/锁定、名单导入、筛查快照、收集批量操作、请假、奖学金导入转换、待办重复规则、工作台聚合、导出字节级校验
- 导出类断言做到**字节级**（表头行、数据行、统计行位置），改坏格式立刻红
- 测试不依赖 MySQL：主库方言差异由 `SchemaFixConfig` 兜底（如 check_record 大字段扩容仅对 MySQL 生效）

## 7. 工程经验与踩坑记录

1. **ECharts 按需引入**漏注册 `GraphicComponent` → 环形图中心文字空白，报错都不报
2. **打包必须 `mvn clean package`**：不加 clean 时 `target/classes` 里残留旧前端产物，线上资源哈希错乱
3. **前端产物拷贝先删后拷**：`rm -rf static/* && cp -r dist/*`，防止旧哈希文件堆积
4. **derive query 必须先声明在接口上**（如 `countByDoneFalse()`），Spring Data 运行时才生成实现，编译期不声明直接报找不到符号
5. **Boolean 字段派生查询**写 `countByDoneFalse()`，不是 `countByDoneIsFalse` 也能用，但必须与字段包装类型匹配
6. **Windows 文件锁**：java 进程未退出时 `mvn clean` 删不掉 target——打包前先停本地服务
7. **H2 MODE=MySQL 不支持 `MODIFY` 列扩容**：`SchemaFixConfig` 用 try-catch 包住 MySQL 专用 DDL，测试库静默跳过
8. **部分更新白名单**：PATCH 语义下 `containsKey` 判断 + 值校验分离，避免"传了空值被当成没传"
9. **导出文件名用本地日期**（`LocalDate.now()`），别用 `toISOString()` 的 UTC 日期——差 8 小时会让文件名"穿越"
10. **会话滑动续期**写在 `isValid` 里而非定时器：校验即续期，实现最短且无并发窗口

## 8. 部署形态

- 产物 = 一个 jar（前端打包在内），`java -jar` 即起；Nginx 反代 80 上行 200MB（附件）
- 移动端 H5 为独立构建产物，放服务器静态目录由 Nginx `location /m/` 托管（见移动端仓库）
- 备份体系：应用级全量 JSON（`/api/backup/*`）+ 建议 mysqldump 定时兜底
