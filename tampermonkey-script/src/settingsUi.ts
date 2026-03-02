import { App, createApp, reactive } from 'vue';
import OverlaySettingsPanel from './ui/OverlaySettingsPanel.vue';

type PlayerOption = {
  playerId: string;
  playerName: string;
  displayLabel: string;
  teamColor: string | null;
};

type MapPlayerListItem = {
  playerId: string;
  playerName: string;
  team: string;
  teamColor: string;
  town: string;
  townColor: string;
  health: string;
  armor: string;
};

type SettingsUiDeps = {
  page: Window;
  uiStyleText: string;
  onSave: () => void;
  onSaveAdvanced: () => void;
  onSaveDisplay: () => void;
  onExportConfig: () => void;
  onImportConfig: () => void;
  onReset: () => void;
  onRefresh: () => void;
  onMarkApply: () => void;
  onMarkClear: () => void;
  onMarkClearAll: () => void;
  onServerFilterToggle: (enabled: boolean) => void;
  onTeamChanged: (team: string) => void;
  onPlayerSelectionChanged: () => void;
  onTogglePlayerList: (visible: boolean) => void;
  onFocusMapPlayer: (playerId: string) => void;
  getPlayerOptionColor?: (item: PlayerOption) => string | null;
};

type UiPage = 'main' | 'advanced' | 'display' | 'mark' | 'connection';

type OverlayFormState = {
  ADMIN_WS_URL: string;
  ROOM_CODE: string;
  RECONNECT_INTERVAL_MS: string;
  TARGET_DIMENSION: string;
  SHOW_PLAYER_ICON: boolean;
  SHOW_PLAYER_TEXT: boolean;
  SHOW_HORSE_TEXT: boolean;
  SHOW_HORSE_ENTITIES: boolean;
  SHOW_LABEL_TEAM_INFO: boolean;
  SHOW_LABEL_TOWN_INFO: boolean;
  PLAYER_ICON_SIZE: string;
  PLAYER_TEXT_SIZE: string;
  HORSE_ICON_SIZE: string;
  HORSE_TEXT_SIZE: string;
  SHOW_COORDS: boolean;
  REPORTER_STAR_ICON: boolean;
  REPORTER_VISION_CIRCLE_ENABLED: boolean;
  REPORTER_VISION_RADIUS: string;
  REPORTER_VISION_COLOR: string;
  REPORTER_VISION_OPACITY: string;
  REPORTER_CHUNK_AREA_ENABLED: boolean;
  REPORTER_CHUNK_RADIUS: string;
  REPORTER_CHUNK_COLOR: string;
  REPORTER_CHUNK_OPACITY: string;
  AUTO_TEAM_FROM_NAME: boolean;
  FRIENDLY_TAGS: string;
  ENEMY_TAGS: string;
  TEAM_COLOR_FRIENDLY: string;
  TEAM_COLOR_NEUTRAL: string;
  TEAM_COLOR_ENEMY: string;
  SHOW_WAYPOINT_ICON: boolean;
  SHOW_WAYPOINT_TEXT: boolean;
  DEBUG: boolean;
};

function createDefaultFormState(): OverlayFormState {
  return {
    ADMIN_WS_URL: '',
    ROOM_CODE: '',
    RECONNECT_INTERVAL_MS: '1000',
    TARGET_DIMENSION: 'minecraft:overworld',
    SHOW_PLAYER_ICON: true,
    SHOW_PLAYER_TEXT: true,
    SHOW_HORSE_TEXT: true,
    SHOW_HORSE_ENTITIES: true,
    SHOW_LABEL_TEAM_INFO: true,
    SHOW_LABEL_TOWN_INFO: true,
    PLAYER_ICON_SIZE: '10',
    PLAYER_TEXT_SIZE: '12',
    HORSE_ICON_SIZE: '14',
    HORSE_TEXT_SIZE: '12',
    SHOW_COORDS: false,
    REPORTER_STAR_ICON: true,
    REPORTER_VISION_CIRCLE_ENABLED: false,
    REPORTER_VISION_RADIUS: '64',
    REPORTER_VISION_COLOR: '',
    REPORTER_VISION_OPACITY: '0.1',
    REPORTER_CHUNK_AREA_ENABLED: false,
    REPORTER_CHUNK_RADIUS: '2',
    REPORTER_CHUNK_COLOR: '',
    REPORTER_CHUNK_OPACITY: '0.11',
    AUTO_TEAM_FROM_NAME: true,
    FRIENDLY_TAGS: '',
    ENEMY_TAGS: '',
    TEAM_COLOR_FRIENDLY: '#3b82f6',
    TEAM_COLOR_NEUTRAL: '#94a3b8',
    TEAM_COLOR_ENEMY: '#ef4444',
    SHOW_WAYPOINT_ICON: true,
    SHOW_WAYPOINT_TEXT: true,
    DEBUG: false,
  };
}

export function createSettingsUi(deps: SettingsUiDeps) {
  const PAGE = deps.page;
  const ROOT_HOST_ID = 'teamviewer-overlay-root';

  let uiMounted = false;
  let panelVisible = false;

  let vueApp: App<Element> | null = null;

  const state = reactive({
    page: 'main' as UiPage,
    statusText: '',
    sameServerFilterEnabled: false,
    players: [] as PlayerOption[],
    mapPlayers: [] as MapPlayerListItem[],
    selectedPlayerId: '',
    playerListVisible: false,
    mark: {
      team: 'neutral',
      color: '#94a3b8',
      label: '',
    },
    form: createDefaultFormState(),
  });

  function getRootHost() {
    return document.getElementById(ROOT_HOST_ID);
  }

  function getShadowRoot() {
    const host = getRootHost();
    return host ? host.shadowRoot : null;
  }

  function byId<T extends HTMLElement = HTMLElement>(id: string) {
    const root = getShadowRoot();
    if (!root) return null;
    return root.getElementById(id) as T | null;
  }

  function setPanelVisible(visible: boolean) {
    const panel = byId('nodemc-overlay-panel');
    if (!panel) return;
    panelVisible = Boolean(visible);
    panel.style.display = panelVisible ? 'block' : 'none';
    if (!panelVisible) {
      state.playerListVisible = false;
    }
  }

  function updatePanelPositionNearFab() {
    const fab = byId('nodemc-overlay-fab');
    const panel = byId('nodemc-overlay-panel');
    if (!fab || !panel) return;

    const fabRect = fab.getBoundingClientRect();
    const panelWidth = panel.offsetWidth || 320;
    const panelHeight = panel.offsetHeight || 280;
    const margin = 10;

    let left = fabRect.left - panelWidth + fabRect.width;
    let top = fabRect.top - panelHeight - margin;

    if (left < margin) left = margin;
    if (left + panelWidth > window.innerWidth - margin) left = window.innerWidth - panelWidth - margin;
    if (top < margin) top = Math.min(window.innerHeight - panelHeight - margin, fabRect.bottom + margin);
    if (top < margin) top = margin;

    panel.style.left = `${Math.round(left)}px`;
    panel.style.top = `${Math.round(top)}px`;
    panel.style.right = 'auto';
    panel.style.bottom = 'auto';
  }

  function clampPosition(el: HTMLElement, left: number, top: number) {
    const width = el.offsetWidth || 34;
    const height = el.offsetHeight || 34;
    const margin = 6;
    const minLeft = margin;
    const minTop = margin;
    const maxLeft = Math.max(minLeft, window.innerWidth - width - margin);
    const maxTop = Math.max(minTop, window.innerHeight - height - margin);
    return {
      left: Math.min(maxLeft, Math.max(minLeft, left)),
      top: Math.min(maxTop, Math.max(minTop, top)),
    };
  }

  function setFabPosition(left: number, top: number, syncPanel = true) {
    const fab = byId('nodemc-overlay-fab');
    if (!fab) return { left, top };
    const clamped = clampPosition(fab, left, top);
    fab.style.left = `${Math.round(clamped.left)}px`;
    fab.style.top = `${Math.round(clamped.top)}px`;
    fab.style.right = 'auto';
    fab.style.bottom = 'auto';
    if (panelVisible && syncPanel) updatePanelPositionNearFab();
    return clamped;
  }

  function setPanelPosition(left: number, top: number) {
    const panel = byId('nodemc-overlay-panel');
    if (!panel) return { left, top };
    const clamped = clampPosition(panel, left, top);
    panel.style.left = `${Math.round(clamped.left)}px`;
    panel.style.top = `${Math.round(clamped.top)}px`;
    panel.style.right = 'auto';
    panel.style.bottom = 'auto';
    return clamped;
  }

  function mountVueUi(shadowRoot: ShadowRoot) {
    const mountPoint = document.createElement('div');
    mountPoint.id = 'nodemc-overlay-vue-root';
    const panel = byId('nodemc-overlay-panel');
    if (!panel) return;

    panel.appendChild(mountPoint);
    vueApp = createApp(OverlaySettingsPanel, {
      state,
      actions: {
        onSave: deps.onSave,
        onSaveAdvanced: deps.onSaveAdvanced,
        onSaveDisplay: deps.onSaveDisplay,
        onExportConfig: deps.onExportConfig,
        onImportConfig: deps.onImportConfig,
        onReset: deps.onReset,
        onRefresh: deps.onRefresh,
        onMarkApply: deps.onMarkApply,
        onMarkClear: deps.onMarkClear,
        onMarkClearAll: deps.onMarkClearAll,
        onServerFilterToggle: deps.onServerFilterToggle,
        onTeamChanged: deps.onTeamChanged,
        onPlayerSelectionChanged: deps.onPlayerSelectionChanged,
        onTogglePlayerList: deps.onTogglePlayerList,
        onFocusMapPlayer: deps.onFocusMapPlayer,
      },
      getPlayerOptionColor: deps.getPlayerOptionColor,
    });
    vueApp.mount(mountPoint);
  }

  function injectSettingsUi() {
    if (uiMounted || !document.body) return;
    uiMounted = true;

    let host = getRootHost();
    if (!host) {
      host = document.createElement('div');
      host.id = ROOT_HOST_ID;
      document.body.appendChild(host);
    }
    const shadowRoot = host.shadowRoot || host.attachShadow({ mode: 'open' });

    const style = document.createElement('style');
    style.id = 'nodemc-overlay-ui-style';
    style.textContent = deps.uiStyleText;
    shadowRoot.appendChild(style);

    const fab = document.createElement('div');
    fab.id = 'nodemc-overlay-fab';
    fab.textContent = '⚙';
    fab.title = 'NodeMC Overlay 设置';

    const panel = document.createElement('div');
    panel.id = 'nodemc-overlay-panel';

    shadowRoot.appendChild(fab);
    shadowRoot.appendChild(panel);

    mountVueUi(shadowRoot);

    const initialRect = fab.getBoundingClientRect();
    setFabPosition(initialRect.left, initialRect.top);

    let dragState: {
      pointerId: number;
      kind: 'fab' | 'panel';
      startX: number;
      startY: number;
      fabLeft: number;
      fabTop: number;
      panelLeft: number;
      panelTop: number;
    } | null = null;
    let dragMoved = false;

    const beginDrag = (event: PointerEvent, kind: 'fab' | 'panel') => {
      dragState = {
        pointerId: event.pointerId,
        kind,
        startX: event.clientX,
        startY: event.clientY,
        fabLeft: fab.offsetLeft,
        fabTop: fab.offsetTop,
        panelLeft: panel.offsetLeft,
        panelTop: panel.offsetTop,
      };
      dragMoved = false;
    };

    const moveDrag = (event: PointerEvent) => {
      if (!dragState || event.pointerId !== dragState.pointerId) return;
      const dx = event.clientX - dragState.startX;
      const dy = event.clientY - dragState.startY;
      if (!dragMoved && (Math.abs(dx) > 3 || Math.abs(dy) > 3)) {
        dragMoved = true;
      }

      if (dragState.kind === 'fab') {
        setFabPosition(dragState.fabLeft + dx, dragState.fabTop + dy, true);
        return;
      }

      const appliedFab = setFabPosition(dragState.fabLeft + dx, dragState.fabTop + dy, false);
      const appliedDx = appliedFab.left - dragState.fabLeft;
      const appliedDy = appliedFab.top - dragState.fabTop;
      setPanelPosition(dragState.panelLeft + appliedDx, dragState.panelTop + appliedDy);
    };

    const endDrag = (event: PointerEvent, sourceElement: HTMLElement) => {
      if (!dragState || event.pointerId !== dragState.pointerId) return;
      try {
        sourceElement.releasePointerCapture(event.pointerId);
      } catch (_) {}
      dragState = null;
      setTimeout(() => {
        dragMoved = false;
      }, 0);
    };

    fab.addEventListener('pointerdown', (event) => {
      beginDrag(event, 'fab');
      try {
        fab.setPointerCapture(event.pointerId);
      } catch (_) {}
    });
    fab.addEventListener('pointermove', moveDrag);

    const bindTitleDrag = () => {
      const titleBar = byId('nodemc-overlay-title');
      if (!titleBar) {
        PAGE.requestAnimationFrame(bindTitleDrag);
        return;
      }
      titleBar.addEventListener('pointerdown', (event) => {
        beginDrag(event, 'panel');
        try {
          titleBar.setPointerCapture(event.pointerId);
        } catch (_) {}
      });
      titleBar.addEventListener('pointermove', moveDrag);
      titleBar.addEventListener('pointerup', (event) => endDrag(event, titleBar));
      titleBar.addEventListener('pointercancel', (event) => endDrag(event, titleBar));
    };

    bindTitleDrag();

    fab.addEventListener('pointerup', (event) => endDrag(event, fab));
    fab.addEventListener('pointercancel', (event) => endDrag(event, fab));

    fab.addEventListener('click', () => {
      if (dragMoved) return;
      setPanelVisible(!panelVisible);
      if (panelVisible) updatePanelPositionNearFab();
    });

    state.page = 'main';
  }

  function mountWhenReady() {
    if (uiMounted) return;
    if (!document.body) {
      PAGE.requestAnimationFrame(mountWhenReady);
      return;
    }
    injectSettingsUi();
  }

  function isMounted() {
    return Boolean(byId('nodemc-overlay-panel'));
  }

  function updateStatus(text: string) {
    state.statusText = text;
  }

  function fillFormFromConfig(config: Record<string, any>, getConfiguredTeamColor: (team: string) => string) {
    state.form.ADMIN_WS_URL = String(config.ADMIN_WS_URL ?? '');
    state.form.ROOM_CODE = String(config.ROOM_CODE ?? '');
    state.form.RECONNECT_INTERVAL_MS = String(config.RECONNECT_INTERVAL_MS ?? '1000');
    state.form.TARGET_DIMENSION = String(config.TARGET_DIMENSION ?? 'minecraft:overworld');
    state.form.SHOW_PLAYER_ICON = Boolean(config.SHOW_PLAYER_ICON);
    state.form.SHOW_PLAYER_TEXT = Boolean(config.SHOW_PLAYER_TEXT);
    state.form.SHOW_HORSE_TEXT = Boolean(config.SHOW_HORSE_TEXT);
    state.form.SHOW_HORSE_ENTITIES = Boolean(config.SHOW_HORSE_ENTITIES);
    state.form.SHOW_LABEL_TEAM_INFO = Boolean(config.SHOW_LABEL_TEAM_INFO);
    state.form.SHOW_LABEL_TOWN_INFO = Boolean(config.SHOW_LABEL_TOWN_INFO);
    state.form.PLAYER_ICON_SIZE = String(config.PLAYER_ICON_SIZE ?? 10);
    state.form.PLAYER_TEXT_SIZE = String(config.PLAYER_TEXT_SIZE ?? 12);
    state.form.HORSE_ICON_SIZE = String(config.HORSE_ICON_SIZE ?? 14);
    state.form.HORSE_TEXT_SIZE = String(config.HORSE_TEXT_SIZE ?? 12);
    state.form.SHOW_COORDS = Boolean(config.SHOW_COORDS);
    state.form.REPORTER_STAR_ICON = Boolean(config.REPORTER_STAR_ICON);
    state.form.REPORTER_VISION_CIRCLE_ENABLED = Boolean(config.REPORTER_VISION_CIRCLE_ENABLED);
    state.form.REPORTER_VISION_RADIUS = String(config.REPORTER_VISION_RADIUS ?? 64);
    state.form.REPORTER_VISION_COLOR = String(config.REPORTER_VISION_COLOR ?? '');
    state.form.REPORTER_VISION_OPACITY = String(config.REPORTER_VISION_OPACITY ?? 0.1);
    state.form.REPORTER_CHUNK_AREA_ENABLED = Boolean(config.REPORTER_CHUNK_AREA_ENABLED);
    state.form.REPORTER_CHUNK_RADIUS = String(config.REPORTER_CHUNK_RADIUS ?? 2);
    state.form.REPORTER_CHUNK_COLOR = String(config.REPORTER_CHUNK_COLOR ?? '');
    state.form.REPORTER_CHUNK_OPACITY = String(config.REPORTER_CHUNK_OPACITY ?? 0.11);
    state.form.AUTO_TEAM_FROM_NAME = Boolean(config.AUTO_TEAM_FROM_NAME);
    state.form.SHOW_WAYPOINT_ICON = Boolean(config.SHOW_WAYPOINT_ICON);
    state.form.SHOW_WAYPOINT_TEXT = Boolean(config.SHOW_WAYPOINT_TEXT);
    state.form.FRIENDLY_TAGS = String(config.FRIENDLY_TAGS ?? '');
    state.form.ENEMY_TAGS = String(config.ENEMY_TAGS ?? '');
    state.form.TEAM_COLOR_FRIENDLY = String(getConfiguredTeamColor('friendly'));
    state.form.TEAM_COLOR_NEUTRAL = String(getConfiguredTeamColor('neutral'));
    state.form.TEAM_COLOR_ENEMY = String(getConfiguredTeamColor('enemy'));
    state.form.DEBUG = Boolean(config.DEBUG);

    state.mark.color = getConfiguredTeamColor(String(state.mark.team || 'neutral'));
  }

  function readFormCandidate(config: Record<string, any>) {
    return {
      ADMIN_WS_URL: state.form.ADMIN_WS_URL || config.ADMIN_WS_URL,
      ROOM_CODE: state.form.ROOM_CODE || config.ROOM_CODE,
      RECONNECT_INTERVAL_MS: state.form.RECONNECT_INTERVAL_MS || config.RECONNECT_INTERVAL_MS,
      TARGET_DIMENSION: state.form.TARGET_DIMENSION || config.TARGET_DIMENSION,
      SHOW_PLAYER_ICON: state.form.SHOW_PLAYER_ICON,
      SHOW_PLAYER_TEXT: state.form.SHOW_PLAYER_TEXT,
      SHOW_HORSE_TEXT: state.form.SHOW_HORSE_TEXT,
      SHOW_HORSE_ENTITIES: state.form.SHOW_HORSE_ENTITIES,
      SHOW_LABEL_TEAM_INFO: state.form.SHOW_LABEL_TEAM_INFO,
      SHOW_LABEL_TOWN_INFO: state.form.SHOW_LABEL_TOWN_INFO,
      PLAYER_ICON_SIZE: state.form.PLAYER_ICON_SIZE || config.PLAYER_ICON_SIZE,
      PLAYER_TEXT_SIZE: state.form.PLAYER_TEXT_SIZE || config.PLAYER_TEXT_SIZE,
      HORSE_ICON_SIZE: state.form.HORSE_ICON_SIZE || config.HORSE_ICON_SIZE,
      HORSE_TEXT_SIZE: state.form.HORSE_TEXT_SIZE || config.HORSE_TEXT_SIZE,
      SHOW_COORDS: state.form.SHOW_COORDS,
      REPORTER_STAR_ICON: state.form.REPORTER_STAR_ICON,
      REPORTER_VISION_CIRCLE_ENABLED: state.form.REPORTER_VISION_CIRCLE_ENABLED,
      REPORTER_VISION_RADIUS: state.form.REPORTER_VISION_RADIUS || config.REPORTER_VISION_RADIUS,
      REPORTER_VISION_COLOR: state.form.REPORTER_VISION_COLOR,
      REPORTER_VISION_OPACITY: state.form.REPORTER_VISION_OPACITY || config.REPORTER_VISION_OPACITY,
      REPORTER_CHUNK_AREA_ENABLED: state.form.REPORTER_CHUNK_AREA_ENABLED,
      REPORTER_CHUNK_RADIUS: state.form.REPORTER_CHUNK_RADIUS || config.REPORTER_CHUNK_RADIUS,
      REPORTER_CHUNK_COLOR: state.form.REPORTER_CHUNK_COLOR,
      REPORTER_CHUNK_OPACITY: state.form.REPORTER_CHUNK_OPACITY || config.REPORTER_CHUNK_OPACITY,
      AUTO_TEAM_FROM_NAME: state.form.AUTO_TEAM_FROM_NAME,
      FRIENDLY_TAGS: state.form.FRIENDLY_TAGS,
      ENEMY_TAGS: state.form.ENEMY_TAGS,
      TEAM_COLOR_FRIENDLY: state.form.TEAM_COLOR_FRIENDLY,
      TEAM_COLOR_NEUTRAL: state.form.TEAM_COLOR_NEUTRAL,
      TEAM_COLOR_ENEMY: state.form.TEAM_COLOR_ENEMY,
      SHOW_WAYPOINT_ICON: state.form.SHOW_WAYPOINT_ICON,
      SHOW_WAYPOINT_TEXT: state.form.SHOW_WAYPOINT_TEXT,
      DEBUG: state.form.DEBUG,
    };
  }

  function refreshPlayerSelector(players: PlayerOption[]) {
    const previous = state.selectedPlayerId;
    state.players = Array.isArray(players) ? players : [];
    if (previous && state.players.some((item) => item.playerId === previous)) {
      state.selectedPlayerId = previous;
      return;
    }
    state.selectedPlayerId = '';
  }

  function getSelectedPlayerId() {
    return String(state.selectedPlayerId || '').trim();
  }

  function refreshMapPlayerList(players: MapPlayerListItem[]) {
    state.mapPlayers = Array.isArray(players) ? players : [];
    if (state.playerListVisible && state.mapPlayers.length <= 0) {
      state.playerListVisible = false;
      deps.onTogglePlayerList(false);
    }
  }

  function setPlayerListVisible(visible: boolean) {
    const nextVisible = Boolean(visible);
    if (nextVisible && state.mapPlayers.length <= 0) {
      state.playerListVisible = false;
      deps.onTogglePlayerList(false);
      return;
    }
    state.playerListVisible = nextVisible;
    deps.onTogglePlayerList(nextVisible);
  }

  function getMarkForm() {
    return {
      playerId: getSelectedPlayerId(),
      team: String(state.mark.team || 'neutral'),
      color: String(state.mark.color || ''),
      label: String(state.mark.label || '').trim(),
    };
  }

  function setMarkColor(color: string) {
    state.mark.color = String(color || '').trim();
  }

  function setServerFilterEnabled(enabled: boolean) {
    state.sameServerFilterEnabled = Boolean(enabled);
  }

  function cleanup() {
    try {
      if (vueApp) {
        vueApp.unmount();
        vueApp = null;
      }
    } catch (_) {}
    try {
      const host = getRootHost();
      if (host) host.remove();
    } catch (_) {}
    try {
      const s = document.getElementById('nodemc-overlay-ui-style');
      if (s) s.remove();
    } catch (_) {}
    try {
      const fab = document.getElementById('nodemc-overlay-fab');
      if (fab) fab.remove();
    } catch (_) {}
    try {
      const panel = document.getElementById('nodemc-overlay-panel');
      if (panel) panel.remove();
    } catch (_) {}
    uiMounted = false;
  }

  return {
    mountWhenReady,
    isMounted,
    updateStatus,
    fillFormFromConfig,
    readFormCandidate,
    refreshPlayerSelector,
    refreshMapPlayerList,
    getSelectedPlayerId,
    getMarkForm,
    setMarkColor,
    setServerFilterEnabled,
    setPlayerListVisible,
    setPanelVisible,
    cleanup,
  };
}
