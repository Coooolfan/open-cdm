# CloudDM 部署指南

本文整合了 CloudDM 的在线镜像部署、中国区镜像加速、本地打包后部署三类内容，统一说明如何在不同场景下部署 CloudDM。

CloudDM 的运行模式分为 **单机模式（Alone）** 和 **集群模式（Console + Sidecar）**，部署方式支持 **安装包**、**Docker**、**Kubernetes**。

> 如果你使用源码仓库本地打包，本文涉及的安装包、Docker Compose 文件和 Kubernetes yml 会在执行 `open-cdm/package/package.sh --build --docker` 后自动生成到 `open-cdm/package/build` 目录，无需手工编写。

---

## 一、版本与部署概览

当前仓库版本：**`3.0.7`**

| 维度 | 支持内容 |
|------|----------|
| 运行模式 | Alone、Console + Sidecar |
| 部署方式 | 安装包、Docker、Kubernetes |
| 在线镜像仓库 | 国际区 `docker.io/bladepipe`<br/>中国区 `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence` |
| 本地打包产物目录 | `open-cdm/package/build` |

### 1.1 国际区镜像

国际区镜像托管在 Docker Hub，适合中国大陆以外地区：

| 组件 | 镜像 |
|------|------|
| Alone | `bladepipe/cgdm-alone:3.0.7` |
| Console | `bladepipe/cgdm-console:3.0.7` |
| Sidecar | `bladepipe/cgdm-sidecar:3.0.7` |

### 1.2 中国区镜像

中国区镜像托管在阿里云容器镜像仓库，适合中国大陆网络环境：

| 组件 | x86_64 / amd64 | arm64 |
|------|----------------|-------|
| Alone | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-alone:3.0.7-amd64` | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-alone:3.0.7-arm64` |
| Console | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-console:3.0.7-amd64` | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-console:3.0.7-arm64` |
| Sidecar | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-sidecar:3.0.7-amd64` | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-sidecar:3.0.7-arm64` |

---

## 二、本地打包与部署产物

如果你准备从源码仓库本地部署，建议先在 `open-cdm/package` 下执行打包。

### 2.1 仅生成安装包

```bash
cd open-cdm/package
./package.sh --build
```

执行后会生成：

- `cgdm-alone.tar.gz`
- `cgdm-console.tar.gz`
- `cgdm-sidecar.tar.gz`

### 2.2 生成安装包、Docker 镜像和部署清单

```bash
cd open-cdm/package

# 全部架构
./package.sh --build --docker

# 仅 x86_64
./package.sh --build --docker x86_64

# 仅 arm64
./package.sh --build --docker arm64
```

执行后，`open-cdm/package/build` 中会自动生成：

- 安装包：`cgdm-*.tar.gz`
- 离线镜像：`docker-*.tar`
- Docker Compose：`docker-alone-*.yml`、`docker-cluster-*.yml`
- Kubernetes：`k8s-alone-*.yml`、`k8s-cluster-*.yml`

典型产物如下：

```text
cgdm-alone.tar.gz
cgdm-console.tar.gz
cgdm-sidecar.tar.gz
docker-alone-x86_64-3.0.7.tar
docker-console-x86_64-3.0.7.tar
docker-sidecar-x86_64-3.0.7.tar
docker-alone-x86_64-3.0.7.yml
docker-cluster-x86_64-3.0.7.yml
k8s-alone-x86_64-3.0.7.yml
k8s-cluster-x86_64-3.0.7.yml
```

---

## 三、单机模式（Alone）部署

单机模式将 Web 控制台和 Sidecar 合并在一个容器或一个安装包中运行，适合个人体验、小团队试用和本地联调。

### 3.1 安装包部署

#### 使用本地打包生成的安装包

```bash
tar -xzf cgdm-alone.tar.gz
cd cgdm-alone
bin/startup.sh
```

首次启动后，通过浏览器访问：

```text
http://localhost:8222
```

系统会自动进入初始化向导，完成数据库初始化和管理员账号创建后即可使用。

### 3.2 Docker 部署

#### 国际区

```bash
docker run -d --name cgdm-alone \
  -p 8222:8222 \
  bladepipe/cgdm-alone:3.0.7
```

#### 中国镜像加速

```bash
docker run -d --name cgdm-alone \
  -p 8222:8222 \
  cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-alone:3.0.7-amd64

docker run -d --name cgdm-alone \
  -p 8222:8222 \
  cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-alone:3.0.7-amd64
```

#### 持久化数据卷

使用 Docker volume：

```bash
docker run -d --name cgdm-alone \
  -p 8222:8222 \
  -v cgdm_alone_conf:/root/cgdm/alone/conf \
  -v cgdm_alone_logs:/root/cgdm/alone/logs \
  -v cgdm_alone_data:/root/cgdm/alone/data \
  bladepipe/cgdm-alone:3.0.7
```

挂载到宿主机目录：

```bash
mkdir -p /data/cgdm/{conf,logs,data}

docker run -d --name cgdm-alone \
  -p 8222:8222 \
  -v /data/cgdm/conf:/root/cgdm/alone/conf \
  -v /data/cgdm/logs:/root/cgdm/alone/logs \
  -v /data/cgdm/data:/root/cgdm/alone/data \
  bladepipe/cgdm-alone:3.0.7
```

#### 本地打包后使用 Compose 部署

```bash
cd open-cdm/package/build
docker load -i docker-alone-x86_64-3.0.7.tar
docker compose -f docker-alone-x86_64-3.0.7.yml up -d
```

如果部署机与打包机相同，且镜像尚未清理，也可以直接执行 `docker compose`。

#### 常用目录与初始化说明

| 路径 | 用途 |
|------|------|
| `/root/cgdm/alone/conf` | 配置文件（`alone.properties`） |
| `/root/cgdm/alone/logs` | 运行日志 |
| `/root/cgdm/alone/data` | 运行时数据 |

环境变量中的 `DB_*` 参数会作为初始化向导的默认值。首次访问 Web 页面时，你可以在向导中检查和修改数据库连接信息，确认后系统会自动建表并创建管理员账号。

### 3.3 Kubernetes 部署

#### 使用本地打包自动生成的 yml

```bash
cd open-cdm/package/build

# x86_64
kubectl apply -f k8s-alone-x86_64-3.0.7.yml

# arm64
kubectl apply -f k8s-alone-arm64-3.0.7.yml
```

自动生成的清单默认会创建：

- `cgdm` 命名空间
- MySQL Service 与 StatefulSet
- Alone 的 PVC、Service、Deployment

> 默认 Service 类型为 `ClusterIP`。如果需要集群外访问，请结合环境调整为 `NodePort`、`LoadBalancer` 或 Ingress。

---

## 四、集群模式（Console + Sidecar）部署

集群模式由 **Console（控制台）** 和 **Sidecar** 两个独立组件组成，适合团队协作、大规模数据源管理和多节点接入。

### 4.1 安装包部署

#### 安装 Console

```bash
tar -xzf cgdm-console.tar.gz
cd cgdm-console
bin/startup.sh
```

#### 配置并安装 Sidecar

```bash
tar -xzf cgdm-sidecar.tar.gz
cd cgdm-sidecar
bin/startup.sh
```

部署顺序建议如下：

1. 先启动 Console
2. 登录 Console 完成初始化
3. 在 Console 中添加 Sidecar 机器并生成 `AK / SK / WSN`
4. 将生成的参数配置到 Sidecar 后再启动或重启 Sidecar

### 4.2 Docker 部署

#### 在线镜像部署

集群模式推荐使用 Docker Compose。国际区镜像如下：

```yaml
services:
  dm_mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: cdmgr
      MYSQL_ROOT_PASSWORD: 请修改为你的密码

  dm_console:
    image: bladepipe/cgdm-console:3.0.7
    ports:
      - "8222:8222"
    environment:
      APP_WEB_PORT: 8222
      APP_WEB_JWT: "请生成一段随机字符串替换此处"
      APP_SERVE_NAME: dm_console
      APP_SERVE_PORT: 8008
      DB_HOST: dm_mysql
      DB_PORT: 3306
      DB_DATABASE: cdmgr
      DB_USERNAME: root
      DB_PASSWORD: 请修改为你的密码

  dm_sidecar:
    image: bladepipe/cgdm-sidecar:3.0.7
    environment:
      APP_WEB_PORT: 8080
      DM_CLIENT_AK: "在 Console 中创建 Sidecar 后获取"
      DM_CLIENT_SK: "在 Console 中创建 Sidecar 后获取"
      DM_CLIENT_WSN: "在 Console 中创建 Sidecar 后获取"
      APP_SERVE_NAME: dm_console
      APP_SERVE_PORT: 8008
```

中国区部署时，只需将镜像替换为：

- `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-console:3.0.7-amd64`
- `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-sidecar:3.0.7-amd64`

#### 本地打包后使用 Compose 部署

```bash
cd open-cdm/package/build
docker load -i docker-console-x86_64-3.0.7.tar
docker load -i docker-sidecar-x86_64-3.0.7.tar
docker compose -f docker-cluster-x86_64-3.0.7.yml up -d
```

默认会启动：

- `dm_mysql`
- `dm_console`
- `dm_sidecar`

> 自动生成的 `docker-cluster-*.yml` 已包含基础端口、卷和环境变量，但其中 `DM_CLIENT_AK / SK / WSN` 仍应替换为实际从 Console 获取的值，生产环境也应同步修改密码和 JWT。

### 4.3 Kubernetes 部署

#### 使用本地打包自动生成的 yml

```bash
cd open-cdm/package/build

# x86_64
kubectl apply -f k8s-cluster-x86_64-3.0.7.yml

# arm64
kubectl apply -f k8s-cluster-arm64-3.0.7.yml
```

自动生成的清单默认会创建：

- `cgdm` 命名空间
- MySQL Service 与 StatefulSet
- Console 的 PVC、Service、Deployment
- Sidecar 的 PVC、Service、Deployment

默认情况下，Console Web 服务以 `ClusterIP` 方式暴露，端口为 `8222`。如需外部访问，请结合环境调整 Service 或 Ingress 配置。

---

## 五、访问与初始化

无论是 Alone 还是 Cluster，Web 控制台默认访问地址均为：

```text
http://localhost:8222
```

首次访问会进入初始化向导。完成数据库初始化和管理员账号创建后，系统即可进入完整业务应用。

---

## 六、部署建议

- 本地体验或快速验证：优先使用 **Alone + Docker**
- 团队使用、多个节点接入：优先使用 **Cluster + Docker Compose / Kubernetes**
- 预发或生产环境：优先使用 **本地打包后生成的 Compose / Kubernetes 清单**，再按环境调整镜像仓库、存储、密码和 Service 暴露方式
- 中国大陆环境：优先使用阿里云镜像地址，减少拉取失败和超时问题