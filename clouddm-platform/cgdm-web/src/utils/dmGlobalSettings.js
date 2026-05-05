export function getDmSystemStatus(dmGlobalSettingsRes) {
  return dmGlobalSettingsRes?.data?.systemStatus || {};
}

export function isDmSystemReady(dmGlobalSettingsRes) {
  return getDmSystemStatus(dmGlobalSettingsRes).status === 'Ready';
}

export function isDmSystemInitial(dmGlobalSettingsRes) {
  return getDmSystemStatus(dmGlobalSettingsRes).status === 'Initial';
}
