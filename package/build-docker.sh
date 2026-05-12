#!/bin/bash
# ============================================================================
# build-docker.sh — 构建 CloudDM Docker 镜像（支持交叉编译）
#
# 用法:
#   ./build-docker.sh --platform=all        # 构建所有平台
#   ./build-docker.sh --platform=x86_64     # 仅构建 x86_64
#   ./build-docker.sh --platform=arm64      # 仅构建 arm64
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGE_BUILD_DIR="$SCRIPT_DIR/build"
SERVICES=(console sidecar alone)

VERSION="${1:-local}"
PLATFORMS=()
PLATFORM_SPECIFIED=0

for arg in "$@"; do
  case "$arg" in
    --platform=all)  PLATFORMS=(x86_64 arm64); PLATFORM_SPECIFIED=1 ;;
    --platform=x86_64) PLATFORMS=(x86_64); PLATFORM_SPECIFIED=1 ;;
    --platform=arm64)  PLATFORMS=(arm64); PLATFORM_SPECIFIED=1 ;;
    -h|--help)
      echo "usage: $0 VERSION --platform=x86_64|arm64|all"; exit 0 ;;
    *) ;;  # first arg is VERSION
  esac
done

[ "$PLATFORM_SPECIFIED" -eq 0 ] && PLATFORMS=(x86_64 arm64)
[ -z "$VERSION" ] && { echo "ERROR: missing VERSION"; exit 1; }

# ---- platform mapping ----
docker_platform()  { case "$1" in x86_64) echo "linux/amd64" ;; arm64) echo "linux/arm64" ;; esac }
base_image_tag()   { echo "clougence/cgdm-${1}-base:local"; }
image_tag()        { echo "${1}-${2}"; }

require_package_artifacts() {
  for file_name in cgdm-console.tar.gz cgdm-sidecar.tar.gz cgdm-alone.tar.gz; do
    [ -f "$PACKAGE_BUILD_DIR/$file_name" ] || { echo "ERROR: missing $PACKAGE_BUILD_DIR/$file_name → run package/package.sh first"; exit 1; }
  done
}

ensure_builder() {
  # Use the default docker driver (direct host engine).
  # Cross-platform is done via DOCKER_DEFAULT_PLATFORM.
  docker buildx inspect default >/dev/null 2>&1 || true
}

build_base_image() {
  local plat="$1"
  local dockerfile="$SCRIPT_DIR/docker/${plat}/base/Dockerfile"
  local tag; tag="$(base_image_tag "$plat")"
  echo "  building base image: $tag ($(docker_platform "$plat"))"
  DOCKER_DEFAULT_PLATFORM="$(docker_platform "$plat")" docker build \
    -t "$tag" \
    -f "$dockerfile" \
    "$SCRIPT_DIR"
}

build_service_image() {
  local svc="$1" plat="$2"
  local dockerfile="$SCRIPT_DIR/docker/${plat}/${svc}/Dockerfile"
  local tag; tag="clougence/cgdm-${svc}:$(image_tag "$plat" "$VERSION")"
  echo "  building $svc: $tag ($(docker_platform "$plat"))"
  DOCKER_DEFAULT_PLATFORM="$(docker_platform "$plat")" docker build \
    --build-arg BASE_IMAGE="$(base_image_tag "$plat")" \
    -t "$tag" \
    -f "$dockerfile" \
    "$SCRIPT_DIR"
}

export_service_image() {
  local svc="$1" plat="$2"
  local tag; tag="clougence/cgdm-${svc}:$(image_tag "$plat" "$VERSION")"
  local output="$PACKAGE_BUILD_DIR/docker-${svc}-$(image_tag "$plat" "$VERSION").tar"
  echo "  exporting $tag → $output"
  docker save "$tag" -o "$output"
}

generate_compose_files() {
  local plat="$1"
  local compose_src="$SCRIPT_DIR"
  for name in alone cluster; do
    local src="$compose_src/docker-${name}.yml"
    local dst="$PACKAGE_BUILD_DIR/docker-${name}-$(image_tag "$plat" "$VERSION").yml"
    if [ -f "$src" ]; then
      sed "s|\${build_version}|$(image_tag "$plat" "$VERSION")|g" "$src" > "$dst"
      echo "  generated compose: $dst"
    fi
  done
}

generate_k8s_files() {
  local plat="$1"
  local k8s_src="$SCRIPT_DIR"
  for name in alone cluster; do
    local src="$k8s_src/k8s-${name}.yml"
    local dst="$PACKAGE_BUILD_DIR/k8s-${name}-$(image_tag "$plat" "$VERSION").yml"
    if [ -f "$src" ]; then
      sed "s|\${build_version}|$(image_tag "$plat" "$VERSION")|g" "$src" > "$dst"
      echo "  generated k8s: $dst"
    fi
  done
}

# ============================================================================
# main
# ============================================================================
require_package_artifacts
ensure_builder

for plat in "${PLATFORMS[@]}"; do
  echo "=== Building platform: $plat ==="
  build_base_image "$plat"
  for svc in "${SERVICES[@]}"; do
    build_service_image "$svc" "$plat"
  done
  for svc in "${SERVICES[@]}"; do
    export_service_image "$svc" "$plat"
  done
  generate_compose_files "$plat"
  generate_k8s_files "$plat"
done

echo "Docker build completed. platforms=${PLATFORMS[*]} version=${VERSION}"
