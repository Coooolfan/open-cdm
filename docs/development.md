# CloudDM 开发约定

## 1. 构建入口

### 开发时构建

```bash
./all_build.sh
```

### 前端快速构建

```bash
./all_build.sh web
```

### 正式打包入口

```bash
cd package && ./package.sh
```

### 版本号约定

```text
gradle.properties 属性文件中的 cg.clouddm.main.version 字段
```

## 2. 初始化与升级约定

- 初始化和升级逻辑集中在 `boot-initialization`
- migration 负责结构和必要数据演进
- fix task 负责更适合幂等修复、初始化补齐、升级后归正的逻辑
- 不要把关键初始化逻辑散落到 Docker 启动脚本中

## 4. 交付约定

- tgz 打包定义在 `package/pkg`
- Docker 模板在 `package/docker`
- 交付输出统一写到 `package/build`

## 6. 发布前建议

如果仓库准备对外开源，除 README 外还建议补齐：

- `LICENSE`
- `CONTRIBUTING.md`
- `SECURITY.md`
- Issue / PR 模板
- 版本兼容矩阵