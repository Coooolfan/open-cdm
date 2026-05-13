# CloudDM Deployment Guide

This document consolidates CloudDM online image deployment, China registry acceleration, and local package deployment into a single guide for different deployment scenarios.

CloudDM supports two runtime modes: **Standalone (Alone)** and **Cluster (Console + Sidecar)**. Supported deployment methods include **install package**, **Docker**, and **Kubernetes**.

> If you build from source locally, the install packages, Docker Compose files, and Kubernetes yml files described in this document will be generated automatically in `open-cdm/package/build` after running `open-cdm/package/package.sh --build --docker`.

---

## 1. Version and Deployment Overview

Current repository version: **`3.0.7`**

| Dimension | Supported Content |
|-----------|-------------------|
| Runtime modes | Alone, Console + Sidecar |
| Deployment methods | Install package, Docker, Kubernetes |
| Online image registries | Global `docker.io/bladepipe`<br/>China `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence` |
| Local build output directory | `open-cdm/package/build` |

### 1.1 Global Images

Global images are hosted on Docker Hub and are suitable for regions outside mainland China:

| Component | Image |
|-----------|-------|
| Alone | `bladepipe/cgdm-alone:3.0.7` |
| Console | `bladepipe/cgdm-console:3.0.7` |
| Sidecar | `bladepipe/cgdm-sidecar:3.0.7` |

### 1.2 China Images

China images are hosted on Alibaba Cloud Container Registry and are better suited for mainland China network environments:

| Component | x86_64 / amd64 | arm64 |
|-----------|----------------|-------|
| Alone | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-alone:3.0.7-amd64` | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-alone:3.0.7-arm64` |
| Console | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-console:3.0.7-amd64` | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-console:3.0.7-arm64` |
| Sidecar | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-sidecar:3.0.7-amd64` | `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-sidecar:3.0.7-arm64` |

---

## 2. Local Packaging and Deployment Artifacts

If you plan to deploy from the source repository, start by packaging under `open-cdm/package`.

### 2.1 Generate Install Packages Only

```bash
cd open-cdm/package
./package.sh --build
```

This generates:

- `cgdm-alone.tar.gz`
- `cgdm-console.tar.gz`
- `cgdm-sidecar.tar.gz`

### 2.2 Generate Install Packages, Docker Images, and Deployment Manifests

```bash
cd open-cdm/package

# All supported architectures
./package.sh --build --docker

# x86_64 only
./package.sh --build --docker x86_64

# arm64 only
./package.sh --build --docker arm64
```

After execution, `open-cdm/package/build` will contain:

- Install packages: `cgdm-*.tar.gz`
- Offline Docker images: `docker-*.tar`
- Docker Compose files: `docker-alone-*.yml`, `docker-cluster-*.yml`
- Kubernetes manifests: `k8s-alone-*.yml`, `k8s-cluster-*.yml`

Typical artifacts:

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

## 3. Standalone (Alone) Deployment

Standalone mode runs the web console and sidecar together in a single container or package. It is suitable for personal evaluation, small teams, and local integration testing.

### 3.1 Install Package Deployment

#### Use the locally built package

```bash
tar -xzf cgdm-alone.tar.gz
cd cgdm-alone
bin/startup.sh
```

After the first startup, open:

```text
http://localhost:8222
```

The initialization wizard will guide you through database setup and administrator account creation.

### 3.2 Docker Deployment

#### Global registry

```bash
docker run -d --name cgdm-alone \
  -p 8222:8222 \
  bladepipe/cgdm-alone:3.0.7
```

> This mode does not mount an external metadata database. The image uses an embedded H2 in-memory database, so data is lost after restart. It is suitable only for quick evaluation.

#### China registry acceleration

```bash
docker run -d --name cgdm-alone \
  -p 8222:8222 \
  cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-alone:3.0.7-amd64
```

#### Persistent data volumes

Using Docker volumes:

```bash
docker run -d --name cgdm-alone \
  -p 8222:8222 \
  -v cgdm_alone_conf:/root/cgdm/alone/conf \
  -v cgdm_alone_logs:/root/cgdm/alone/logs \
  -v cgdm_alone_data:/root/cgdm/alone/data \
  bladepipe/cgdm-alone:3.0.7
```

Mounting host directories:

```bash
mkdir -p /data/cgdm/{conf,logs,data}

docker run -d --name cgdm-alone \
  -p 8222:8222 \
  -v /data/cgdm/conf:/root/cgdm/alone/conf \
  -v /data/cgdm/logs:/root/cgdm/alone/logs \
  -v /data/cgdm/data:/root/cgdm/alone/data \
  bladepipe/cgdm-alone:3.0.7
```

If you use the China registry, replace the image with:

```text
cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-alone:3.0.7-amd64
```

#### Compose deployment using locally built artifacts

```bash
cd open-cdm/package/build
docker load -i docker-alone-x86_64-3.0.7.tar
docker compose -f docker-alone-x86_64-3.0.7.yml up -d
```

If the build machine and deployment machine are the same and the image still exists locally, you can run `docker compose` directly.

#### Common directories and initialization behavior

| Path | Purpose |
|------|---------|
| `/root/cgdm/alone/conf` | Configuration files (`alone.properties`) |
| `/root/cgdm/alone/logs` | Runtime logs |
| `/root/cgdm/alone/data` | Runtime data |

The `DB_*` environment variables are used as default values in the initialization wizard. On first access, you can review and modify the database connection settings before the system initializes the schema and creates the administrator account.

### 3.3 Kubernetes Deployment

#### Use the yml files generated by local packaging

```bash
cd open-cdm/package/build

# x86_64
kubectl apply -f k8s-alone-x86_64-3.0.7.yml

# arm64
kubectl apply -f k8s-alone-arm64-3.0.7.yml
```

The generated manifest creates:

- `cgdm` namespace
- MySQL Service and StatefulSet
- Alone PVC, Service, and Deployment

> The default Service type is `ClusterIP`. If you need external access, adjust it to `NodePort`, `LoadBalancer`, or Ingress based on your environment.

---

## 4. Cluster (Console + Sidecar) Deployment

Cluster mode consists of **Console** and **Sidecar** as separate components. It is suitable for team collaboration, large-scale data source management, and multi-node access.

### 4.1 Install Package Deployment

#### Install Console

```bash
tar -xzf cgdm-console.tar.gz
cd cgdm-console
bin/startup.sh
```

#### Configure and install Sidecar

```bash
tar -xzf cgdm-sidecar.tar.gz
cd cgdm-sidecar
bin/startup.sh
```

Recommended deployment order:

1. Start Console first
2. Sign in to Console and complete initialization
3. Add a Sidecar machine in Console and generate `AK / SK / WSN`
4. Configure those values for Sidecar, then start or restart it

### 4.2 Docker Deployment

#### Online image deployment

For cluster mode, Docker Compose is recommended. Example using global images:

```yaml
services:
  dm_mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: cdmgr
      MYSQL_ROOT_PASSWORD: Replace with your password

  dm_console:
    image: bladepipe/cgdm-console:3.0.7
    ports:
      - "8222:8222"
    environment:
      APP_WEB_PORT: 8222
      APP_WEB_JWT: "Replace with a random string"
      APP_SERVE_NAME: dm_console
      APP_SERVE_PORT: 8008
      DB_HOST: dm_mysql
      DB_PORT: 3306
      DB_DATABASE: cdmgr
      DB_USERNAME: root
      DB_PASSWORD: Replace with your password

  dm_sidecar:
    image: bladepipe/cgdm-sidecar:3.0.7
    environment:
      APP_WEB_PORT: 8080
      DM_CLIENT_AK: "Obtain from Console after creating Sidecar"
      DM_CLIENT_SK: "Obtain from Console after creating Sidecar"
      DM_CLIENT_WSN: "Obtain from Console after creating Sidecar"
      APP_SERVE_NAME: dm_console
      APP_SERVE_PORT: 8008
```

For China deployment, replace the images with:

- `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-console:3.0.7-amd64`
- `cloudcanal-registry.cn-shanghai.cr.aliyuncs.com/clougence/cgdm-sidecar:3.0.7-amd64`

#### Compose deployment using locally built artifacts

```bash
cd open-cdm/package/build
docker load -i docker-console-x86_64-3.0.7.tar
docker load -i docker-sidecar-x86_64-3.0.7.tar
docker compose -f docker-cluster-x86_64-3.0.7.yml up -d
```

By default this starts:

- `dm_mysql`
- `dm_console`
- `dm_sidecar`

> The generated `docker-cluster-*.yml` already includes base ports, volumes, and environment variables. You still need to replace `DM_CLIENT_AK / SK / WSN` with the actual values issued by Console. In production, also replace default passwords and JWT values.

### 4.3 Kubernetes Deployment

#### Use the yml files generated by local packaging

```bash
cd open-cdm/package/build

# x86_64
kubectl apply -f k8s-cluster-x86_64-3.0.7.yml

# arm64
kubectl apply -f k8s-cluster-arm64-3.0.7.yml
```

The generated manifest creates:

- `cgdm` namespace
- MySQL Service and StatefulSet
- Console PVC, Service, and Deployment
- Sidecar PVC, Service, and Deployment

By default, the Console web service is exposed as `ClusterIP` on port `8222`. For external access, adjust the Service or configure Ingress based on your environment.

---

## 5. Access and Initialization

For both Alone and Cluster modes, the default web console address is:

```text
http://localhost:8222
```

The first visit launches the initialization wizard. After database initialization and administrator account creation are completed, the full application becomes available.

---

## 6. Deployment Recommendations

- For local evaluation or quick validation: prefer **Alone + Docker**
- For team usage and multi-node access: prefer **Cluster + Docker Compose / Kubernetes**
- For staging or production: prefer the Compose and Kubernetes manifests generated by local packaging, then adjust the registry, storage, passwords, and service exposure to match your environment
- For mainland China network environments: prefer Alibaba Cloud images to reduce pull failures and timeouts