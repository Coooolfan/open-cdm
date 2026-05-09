# CloudDM 架构说明

## 1. 产品定位

按照产品文档口径，CloudDM 的定位首先不是“一个数据库管理后台”，而是一个面向团队协作的数据安全管理平台。

它服务于开发、DBA、运维、审批人等不同角色，围绕三件事提供统一能力：

- 安全访问数据库
- 团队协同推进数据库变更
- 在保证稳定和合规的前提下提升数据库使用效率

因此，CloudDM 的架构不是单纯围绕“查询”展开，而是同时承载：

- 控制台交互与对象管理
- SQL 校验、规则、脱敏和权限控制
- 工单、审批、执行与审计链路
- Worker 执行代理与跨环境连接

## 2. 架构目标

CloudDM 的核心目标不是只提供一个 Web 控制台，而是形成一套完整的数据库工作平台工程：

- 控制面负责配置、任务、认证、元数据和交互入口。
- 执行面负责真实的数据库连接、会话、执行和结果处理。
- 初始化/升级面负责新部署、升级和修复任务。
- 插件面负责数据库类型、认证 Provider 和功能能力扩展。
- 交付面负责 tgz、Docker 镜像和 compose 模板输出。

## 3. 总体分层

```text
UI / API
  -> Console
      -> metadata / auth / task / orchestration
      -> MySQL runtime store
      -> Sidecar Worker
          -> session / execute / resource manager
          -> target databases / tools
```

## 4. 核心运行角色

### 4.1 Console

Console 是控制平面，负责：

- 用户入口与 API
- 元数据与系统配置
- 任务编排和调度
- Worker 管理与连接管理
- 插件装载后的平台能力承接

主要代码位于：

- `clouddm-platform/cgdm-console`
- `clouddm-platform/cgdm-web`

### 4.2 Sidecar Worker

Sidecar 是执行平面，负责：

- 拉取执行配置
- 管理数据库会话和资源
- 承担 SQL / 工具类执行逻辑
- 处理结果、文件、回调和 Worker 状态

主要代码位于：

- `clouddm-platform/cgdm-sidecar`
- `clouddm-boot/boot-sidecar`

### 4.3 Initialization / Upgrade

初始化与升级能力单独放在 `boot-initialization` 中，负责：

- 初始化库结构
- 升级 migration
- fix task
- 初始化配置与重启前流程

这部分是部署生命周期的核心，不应把关键初始化逻辑散落到 Docker 脚本或业务启动器里。

## 5. 运行形态

### 5.1 Alone

适合单体部署或嵌入式场景，Console 和 Worker 能力以更紧凑的方式组合。

### 5.2 Cluster

标准形态是 Console + Sidecar 分离：

- Console 负责控制与调度
- Sidecar 负责执行与连接落地

这种设计更适合私有化和扩展部署，也更容易管理执行风险边界。

## 6. 插件架构

CloudDM 通过插件扩展数据库类型和平台能力，主要分为：

- DS 插件：数据库类型能力
- Provider 插件：认证或外部接入能力
- Feature 插件：平台功能扩展
- Inner 插件：内部基础能力

插件相关主目录：

- `clouddm-plugins/clouddm-ds`
- `clouddm-plugins/clouddm-provider`
- `clouddm-plugins/clouddm-features`
- `clouddm-plugins/clouddm-inner`

## 7. 交付架构

交付相关逻辑位于 `package/`：

- `pkg/`：tgz 打包定义
- `docker/`：镜像与 compose 模板
- `build/`：输出物目录

整体交付流程通常是：

1. 先通过 Gradle 生成服务产物
2. 生成 console / sidecar / alone 的 tgz 包
3. 以 tgz 为输入构建 Docker 镜像
4. 生成带版本号的 compose 文件

## 8. 相关文档

- 模块说明：[modules.md](modules.md)
- 开发约定：[development.md](development.md)