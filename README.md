# 班级成员管理系统

班长专属的班级名单核对工具：粘贴第三方名单，秒级输出 **已参与 / 未参与 / 无效**，并留存可导出的历史记录。

## 技术栈

| 层级 | 选型 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus |
| 后端 | Spring Boot 3.2 + Java 19 + Spring Data JPA |
| 数据库 | MySQL 8（库名 `class_mgmt`） |
| IDE | IntelliJ IDEA 2022.3.2 Ultimate |
| 构建 | Maven 3.6.1 / npm |

## 目录结构

```
mimo_Class_Management_System/
├── backend/                 # Spring Boot 后端
├── frontend/                # Vue3 前端
├── docs/
│   ├── compose/spec/        # Compose 特性规格
│   └── 班级成员管理系统需求分析文档.md
├── open-in-idea.bat         # 用 IDEA 打开本工程
└── README.md
```

## 快速开始

### 1. 数据库

创建数据库：`class_mgmt`（MySQL 8，默认 `127.0.0.1:3306`）。

连接配置见 `backend/src/main/resources/application.yml`（请按需修改账号密码）。

### 2. 启动后端

方式 A（IDEA）：打开工程 → 运行 `ClassMemberApplication`。

方式 B（命令行）：

```powershell
cd backend
mvn spring-boot:run
```

后端默认：`http://127.0.0.1:8080`

### 3. 启动前端

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

浏览器打开：`http://127.0.0.1:5173`

开发模式下前端已代理 `/api` 到后端 8080。

### 4. 一键用 IDEA 打开

双击运行项目根目录 `open-in-idea.bat`。脚本会在常见安装位置自动查找 IntelliJ IDEA；若未自动找到，编辑脚本中的 `IDEA_EXE` 为你的 `idea64.exe` 完整路径即可。

## 核心功能

- 基准名单：批量导入、锁定/解锁、预览
- 名单筛查：粘贴文本 → 已参与/未参与/无效/重复统计
- 历史记录：只读快照、改名、删除、导出 xlsx/csv
- 长期任务：按期核对（如青年大学习）
- 自定义分组：卫生组/学习组等
- 备份：`/api/backup/export` 与 `/api/backup/import`
- 访问控制：默认 `127.0.0.1`；可配置 `app.access-password`

首次启动若名单为空，会写入 36 名示例学生，可在「基准名单」页解锁后替换为真实名单。

## 常用 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/students` | 基准名单 |
| POST | `/api/check` | 名单比对 |
| GET | `/api/check-records` | 历史记录 |
| GET | `/api/check-records/{id}/export?format=xlsx` | 导出 |
| GET | `/api/health` | 健康检查 |

## 测试

```powershell
cd backend
mvn test
```

## 需求文档

见 `docs/班级成员管理系统需求分析文档.md`（v2.0）。
