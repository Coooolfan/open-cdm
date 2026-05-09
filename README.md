# CloudDM

CloudDM 是一款开源的企业级数据库管理工具，它支持多种类型数据库并提供统一 Web 控制台访问数据库。



可以让您的团队在一个安全可控制的环境中操作
旨在帮助数据库管理员和开发人员在一个平台上统一管理不同类型的数据库管理应用程序数据库模式的整个生命周期。

CloudDM 是一个面向团队数据库协作与数据安全治理场景的数据库工作平台源码仓，服务于开发、DBA、运维、审批人等不同角色，强调安全性、稳定性和用户体验。





它关注的不只是“执行 SQL”，而是如何让团队在统一的平台中安全访问数据库、协同推进变更、沉淀规则和流程，并把这些能力稳定地交付到企业环境中。

## 什么是 CloudDM?

- 安全访问数据库：避免人员直接持有数据库连接方式，把访问、执行、审查和审计统一收敛到平台内。
- 团队协同变更：通过工单、审批流、执行流把数据库变更从个人操作转成团队流程。
- 兼顾效率与治理：提供查询控制台、对象浏览器、可视化操作、DDL 获取和转换等效率工具，同时用规则和规范约束风险。
- 融入企业体系：支持角色、资源权限、统一认证、插件扩展和分层部署，便于接入企业现有管理与交付体系。

## 核心能力

### 数据访问

- 统一 Web 控制台访问数据库，支持多类自建数据库和云数据库。
- 提供查询编辑器、语法高亮、智能提示、执行计划、结果导出等能力。
- 通过 Sidecar Worker 承担实际连接、会话和执行，降低直接暴露数据库的风险。

### 数据治理

- 支持查询规则、安全规范、规则脚本扩展和数据脱敏。
- 支持在控制台查询和工单递交阶段对 SQL 做预检、提示或阻塞。
- 支持角色、资源权限、审计和统一认证能力。

### 协同与流程

- 支持 SQL 工单、权限工单和变更流程。
- 支持内置流程引擎，也支持对接外部流程系统承接审批。
- 支持把数据库变更过程沉淀为可追踪、可复盘的审计链路。

### 平台与交付

- 支持 alone 形态和 console + sidecar 集群形态。
- 内置初始化、升级、fix task 和 Docker 交付流程。
- 通过插件体系扩展数据源、认证 Provider 和功能能力。

## 快速开始

### 环境要求

- JDK 21
- 推荐使用仓库自带的 `./gradlew`
- 如需完整构建前端，需准备 Node.js 与 npm
- 如需构建镜像或运行 compose，需安装 Docker 与 Docker Compose

### 本地构建

执行全量构建：

```bash
./all_build.sh
```

仅更新前端资源：

```bash
./all_build.sh web
```

仅检查 Gradle 工程是否可用：

```bash
./gradlew help -Ptarget=none
```

### 生成交付包

生成 tgz 包：

```bash
cd package
./package.sh
```

生成 tgz 包并构建 Docker 镜像：

```bash
cd package
./package.sh --docker
```

指定架构构建 Docker：

```bash
cd package
./package.sh --docker=arm64
./package.sh --docker=x86_64
```

### 启动 Docker 集群模式

```bash
cd package
./package.sh --docker=arm64

cd build
docker load -i docker-console-arm64-<version>.tar
docker load -i docker-sidecar-arm64-<version>.tar
docker compose -f docker-cluster-arm64-<version>.yml up -d
```

### 启动 Docker Alone 模式

```bash
cd package
./package.sh --docker=arm64

cd build
docker load -i docker-alone-arm64-<version>.tar
docker compose -f docker-alone-arm64-<version>.yml up -d
```

## 仓库结构

- `clouddm-platform`：Console、Web、Sidecar、插件装载和共享平台能力。
- `clouddm-boot`：console、sidecar、alone 启动入口，以及初始化升级模块。
- `clouddm-plugins`：数据源插件、认证 Provider、功能插件和内部扩展。
- `package`：tgz 打包、Docker 镜像、compose 模板和交付输出。

详细模块边界请见 [docs/modules.md](../open-cdm-operations/docs/modules.md)。

## 文档

- 架构说明：[docs/architecture.md](../open-cdm-operations/docs/architecture.md)
- 模块说明：[docs/modules.md](../open-cdm-operations/docs/modules.md)
- 开发约定：[docs/development.md](../open-cdm-operations/docs/development.md)

README 只保留项目首页级信息；详细架构、模块边界和开发约定已拆分到单独文档。

## 开源协议

当前仓库根目录下尚未提供正式的许可证文件，因此本 README 不对许可证做默认推定。

如果该仓库计划以标准开源项目方式发布，建议至少补齐：

- 根目录 `LICENSE`
- `CONTRIBUTING.md`
- `SECURITY.md`
- 版本兼容说明与发布说明

在正式许可证补充之前，请不要把当前仓库视为已经完成标准开源发布。
