export const USERSCRIPT_META = {
  name: '地图玩家投影 - squaremap 版',
  namespace: 'https://map.nodemc.cc/',
  version: '0.3.0',
  description: '将远程玩家信息投影到 squaremap 地图',
  author: 'Prof. Chen',
  match: [
    'https://map.nodemc.cc/*',
    'http://map.nodemc.cc/*',
    'https://map.fltown.cn/*',
    'http://map.fltown.cn/*',
    'file:///*NodeMC*时局图*.html*',
  ] as const,
  'run-at': 'document-start' as const,
  grant: ['GM_xmlhttpRequest', 'unsafeWindow'] as const,
  connect: ['*'] as const,
};

export const PROTOCOL_META = {
  adminNetworkProtocolVersion: '0.3.1',
  adminMinCompatibleNetworkProtocolVersion: '0.3.0',
};

export const APP_META = {
  storageKey: 'nodemc_player_overlay_settings_v1',
  localProgramPrefix: 'map-overlay',
};
