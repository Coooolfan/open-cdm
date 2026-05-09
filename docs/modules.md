# CloudDM 模块说明

## 1. 仓库顶层模块

### `clouddm-base`

基础模块，承载跨平台的底层能力。

### `clouddm-boot`

启动与生命周期模块，包含：

- `boot-initialization`
- `boot-console`
- `boot-sidecar`
- `boot-alone`

### `clouddm-platform`

平台核心模块，包含：

- `cgdm-console`
- `cgdm-sidecar`
- `cgdm-web`
- `cgdm-pluginloader`
- `cgdm-plugin-sdk`
- `cgdm-share-libs`

### `clouddm-plugins`

平台扩展能力，分为：

- `clouddm-ds`
- `clouddm-provider`
- `clouddm-features`
- `clouddm-inner`

### `clouddm-utils`

通用工具、底层协议和支撑模块。

### `package`

交付层模块，负责：

- tgz 包构建
- Docker 镜像构建
- compose 模板输出

### `tests`

测试与样例模块。

## 2. 关键运行模块

### `cgdm-console`

控制平面核心后端服务。

### `cgdm-sidecar`

执行平面核心服务，负责 Worker 侧的会话、资源和执行逻辑。

### `cgdm-web`

前端资源工程。

### `boot-initialization`

初始化、升级、修复任务集中承载模块。

## 3. 打包模块

这些模块只有在 `-Ptarget=all` 时才会被包含进 Gradle 工程：

- `pkg-console`
- `pkg-sidecar`
- `pkg-alone`

## 4. 模块选择建议

- 改初始化、升级、fix task：优先进入 `boot-initialization`
- 改控制台业务逻辑：优先进入 `cgdm-console`
- 改 Worker 执行逻辑：优先进入 `cgdm-sidecar`
- 改前端页面：优先进入 `cgdm-web`
- 改交付流程：优先进入 `package/`
- 改数据库类型扩展：优先进入 `clouddm-plugins/clouddm-ds`