export const initApi = {
  // 获取默认配置字段定义
  dmInitDefaultConfig: '/clouddm/console/api/v1/init/defaultConfig',
  // 获取原始配置文件内容（供高级选项编辑）
  dmInitRawConfig: '/clouddm/console/api/v1/init/rawConfig',
  // 测试数据库连接 + 空库检测 + 已安装检测
  dmInitTestDb: '/clouddm/console/api/v1/init/testDb',
  // 保存初始化配置（完整模式）
  dmInitApplyConfig: '/clouddm/console/api/v1/init/applyConfig',
  // 仅更新数据库配置（dbOnly 模式）
  dmInitUpdateDbConfig: '/clouddm/console/api/v1/init/updateDbConfig',
  // 触发系统重启
  dmInitRestart: '/clouddm/console/api/v1/init/restart'
};
