type SettingsUiDeps = {
  page: Window;
  panelHtml: string;
  uiStyleText: string;
  onSave: () => void;
  onSaveAdvanced: () => void;
  onSaveDisplay: () => void;
  onReset: () => void;
  onRefresh: () => void;
  onMarkApply: () => void;
  onMarkClear: () => void;
  onMarkClearAll: () => void;
  onServerFilterToggle: (enabled: boolean) => void;
  onTeamChanged: (team: string) => void;
  onPlayerSelectionChanged: () => void;
  getPlayerOptionColor?: (item: { playerId: string; playerName: string; displayLabel: string; teamColor: string | null }) => string | null;
};

export function createSettingsUi(deps: SettingsUiDeps) {
  const PAGE = deps.page;

  let uiMounted = false;
  let panelVisible = false;
  let panelPage = 'main';

  function byId<T extends HTMLElement = HTMLElement>(id: string) {
    return document.getElementById(id) as T | null;
  }

  function setPanelVisible(visible: boolean) {
    const panel = byId('nodemc-overlay-panel');
    if (!panel) return;
    panelVisible = Boolean(visible);
    panel.style.display = panelVisible ? 'block' : 'none';
  }

  function setPanelPage(nextPage: string) {
    panelPage = nextPage === 'advanced' || nextPage === 'display' || nextPage === 'mark' || nextPage === 'connection' ? nextPage : 'main';
    const mainPage = byId('nodemc-overlay-page-main');
    const advancedPage = byId('nodemc-overlay-page-advanced');
    const displayPage = byId('nodemc-overlay-page-display');
    const markPage = byId('nodemc-overlay-page-mark');
    const connectionPage = byId('nodemc-overlay-page-connection');
    if (mainPage) mainPage.classList.toggle('active', panelPage === 'main');
    if (advancedPage) advancedPage.classList.toggle('active', panelPage === 'advanced');
    if (displayPage) displayPage.classList.toggle('active', panelPage === 'display');
    if (markPage) markPage.classList.toggle('active', panelPage === 'mark');
    if (connectionPage) connectionPage.classList.toggle('active', panelPage === 'connection');
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

  function injectSettingsUi() {
    if (uiMounted || !document.body) return;
    uiMounted = true;

    const style = document.createElement('style');
    style.id = 'nodemc-overlay-ui-style';
    style.textContent = deps.uiStyleText;
    document.head.appendChild(style);

    const fab = document.createElement('div');
    fab.id = 'nodemc-overlay-fab';
    fab.textContent = '⚙';
    fab.title = 'NodeMC Overlay 设置';

    const panel = document.createElement('div');
    panel.id = 'nodemc-overlay-panel';

    const parsedPanel = new DOMParser().parseFromString(deps.panelHtml, 'text/html');
    const panelNodes = Array.from(parsedPanel.body.childNodes);
    for (const node of panelNodes) {
      panel.appendChild(node.cloneNode(true));
    }

    document.body.appendChild(fab);
    document.body.appendChild(panel);

    const initialRect = fab.getBoundingClientRect();
    setFabPosition(initialRect.left, initialRect.top);

    let dragState: any = null;
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

      if (dragState.kind === 'panel') {
        const appliedFab = setFabPosition(dragState.fabLeft + dx, dragState.fabTop + dy, false);
        const appliedDx = appliedFab.left - dragState.fabLeft;
        const appliedDy = appliedFab.top - dragState.fabTop;
        setPanelPosition(dragState.panelLeft + appliedDx, dragState.panelTop + appliedDy);
      }
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

    const titleBar = byId('nodemc-overlay-title');
    titleBar?.addEventListener('pointerdown', (event) => {
      beginDrag(event, 'panel');
      try {
        titleBar.setPointerCapture(event.pointerId);
      } catch (_) {}
    });
    titleBar?.addEventListener('pointermove', moveDrag);

    fab.addEventListener('pointerup', (event) => endDrag(event, fab));
    fab.addEventListener('pointercancel', (event) => endDrag(event, fab));
    titleBar?.addEventListener('pointerup', (event) => endDrag(event, titleBar));
    titleBar?.addEventListener('pointercancel', (event) => endDrag(event, titleBar));

    setPanelPage('main');

    fab.addEventListener('click', () => {
      if (dragMoved) return;
      setPanelVisible(!panelVisible);
      if (panelVisible) updatePanelPositionNearFab();
    });

    byId('nodemc-overlay-open-advanced')?.addEventListener('click', () => setPanelPage('advanced'));
    byId('nodemc-overlay-open-display')?.addEventListener('click', () => setPanelPage('display'));
    byId('nodemc-overlay-open-mark')?.addEventListener('click', () => setPanelPage('mark'));
    byId('nodemc-overlay-open-connection')?.addEventListener('click', () => setPanelPage('connection'));
    byId('nodemc-overlay-back-main')?.addEventListener('click', () => setPanelPage('main'));
    byId('nodemc-overlay-back-main-from-display')?.addEventListener('click', () => setPanelPage('main'));
    byId('nodemc-overlay-back-main-from-mark')?.addEventListener('click', () => setPanelPage('main'));
    byId('nodemc-overlay-back-main-from-connection')?.addEventListener('click', () => setPanelPage('main'));

    byId('nodemc-overlay-save')?.addEventListener('click', deps.onSave);
    byId('nodemc-overlay-save-advanced')?.addEventListener('click', deps.onSaveAdvanced);
    byId('nodemc-overlay-save-connection')?.addEventListener('click', deps.onSaveAdvanced);
    byId('nodemc-overlay-save-display')?.addEventListener('click', deps.onSaveDisplay);
    byId('nodemc-overlay-reset')?.addEventListener('click', deps.onReset);
    byId('nodemc-overlay-refresh')?.addEventListener('click', deps.onRefresh);

    const teamInput = byId<HTMLInputElement>('nodemc-mark-team');
    teamInput?.addEventListener('change', () => {
      deps.onTeamChanged(String(teamInput.value || 'neutral'));
    });

    const selectInput = byId<HTMLSelectElement>('nodemc-mark-player-select');
    selectInput?.addEventListener('change', deps.onPlayerSelectionChanged);

    const serverFilterInput = byId<HTMLInputElement>('nodemc-overlay-server-filter');
    serverFilterInput?.addEventListener('change', () => {
      deps.onServerFilterToggle(Boolean(serverFilterInput.checked));
    });

    byId('nodemc-mark-apply')?.addEventListener('click', deps.onMarkApply);
    byId('nodemc-mark-clear')?.addEventListener('click', deps.onMarkClear);
    byId('nodemc-mark-clear-all')?.addEventListener('click', deps.onMarkClearAll);
  }

  function mountWhenReady() {
    if (uiMounted) return;
    if (!document.body || !document.head) {
      PAGE.requestAnimationFrame(mountWhenReady);
      return;
    }
    injectSettingsUi();
  }

  function updateStatus(text: string) {
    const status = byId('nodemc-overlay-status');
    if (status) status.textContent = text;
  }

  function fillFormFromConfig(config: Record<string, any>, getConfiguredTeamColor: (team: string) => string) {
    const setValue = (id: string, value: string) => {
      const input = byId<HTMLInputElement>(id);
      if (input) input.value = value;
    };
    const setChecked = (id: string, value: boolean) => {
      const input = byId<HTMLInputElement>(id);
      if (input) input.checked = value;
    };

    setValue('nodemc-overlay-url', config.ADMIN_WS_URL);
    setValue('nodemc-overlay-room-code', config.ROOM_CODE);
    setValue('nodemc-overlay-reconnect', String(config.RECONNECT_INTERVAL_MS));
    setValue('nodemc-overlay-dim', config.TARGET_DIMENSION);
    setChecked('nodemc-overlay-show-icon', config.SHOW_PLAYER_ICON);
    setChecked('nodemc-overlay-show-text', config.SHOW_PLAYER_TEXT);
    setChecked('nodemc-overlay-show-horse-text', config.SHOW_HORSE_TEXT);
    setChecked('nodemc-overlay-show-horse-entities', config.SHOW_HORSE_ENTITIES);
    setChecked('nodemc-overlay-show-team-info', config.SHOW_LABEL_TEAM_INFO);
    setChecked('nodemc-overlay-show-town-info', config.SHOW_LABEL_TOWN_INFO);
    setValue('nodemc-overlay-player-icon-size', String(config.PLAYER_ICON_SIZE));
    setValue('nodemc-overlay-player-text-size', String(config.PLAYER_TEXT_SIZE));
    setValue('nodemc-overlay-horse-icon-size', String(config.HORSE_ICON_SIZE));
    setValue('nodemc-overlay-horse-text-size', String(config.HORSE_TEXT_SIZE));
    setChecked('nodemc-overlay-coords', config.SHOW_COORDS);
    setChecked('nodemc-overlay-auto-team', config.AUTO_TEAM_FROM_NAME);
    setChecked('nodemc-overlay-show-waypoint-icon', config.SHOW_WAYPOINT_ICON);
    setChecked('nodemc-overlay-show-waypoint-text', config.SHOW_WAYPOINT_TEXT);
    setValue('nodemc-overlay-friendly-tags', config.FRIENDLY_TAGS);
    setValue('nodemc-overlay-enemy-tags', config.ENEMY_TAGS);
    setValue('nodemc-overlay-team-friendly-color', getConfiguredTeamColor('friendly'));
    setValue('nodemc-overlay-team-neutral-color', getConfiguredTeamColor('neutral'));
    setValue('nodemc-overlay-team-enemy-color', getConfiguredTeamColor('enemy'));
    setChecked('nodemc-overlay-debug', config.DEBUG);

    const markTeamInput = byId<HTMLSelectElement>('nodemc-mark-team');
    const markColorInput = byId<HTMLInputElement>('nodemc-mark-color');
    if (markTeamInput && markColorInput) {
      markColorInput.value = getConfiguredTeamColor(String(markTeamInput.value || 'neutral'));
    }
  }

  function readFormCandidate(config: Record<string, any>) {
    const value = (id: string, fallback: any) => {
      const input = byId<HTMLInputElement>(id);
      return input ? input.value : fallback;
    };
    const checked = (id: string, fallback: any) => {
      const input = byId<HTMLInputElement>(id);
      return input ? input.checked : fallback;
    };

    return {
      ADMIN_WS_URL: value('nodemc-overlay-url', config.ADMIN_WS_URL),
      ROOM_CODE: value('nodemc-overlay-room-code', config.ROOM_CODE),
      RECONNECT_INTERVAL_MS: value('nodemc-overlay-reconnect', config.RECONNECT_INTERVAL_MS),
      TARGET_DIMENSION: value('nodemc-overlay-dim', config.TARGET_DIMENSION),
      SHOW_PLAYER_ICON: checked('nodemc-overlay-show-icon', config.SHOW_PLAYER_ICON),
      SHOW_PLAYER_TEXT: checked('nodemc-overlay-show-text', config.SHOW_PLAYER_TEXT),
      SHOW_HORSE_TEXT: checked('nodemc-overlay-show-horse-text', config.SHOW_HORSE_TEXT),
      SHOW_HORSE_ENTITIES: checked('nodemc-overlay-show-horse-entities', config.SHOW_HORSE_ENTITIES),
      SHOW_LABEL_TEAM_INFO: checked('nodemc-overlay-show-team-info', config.SHOW_LABEL_TEAM_INFO),
      SHOW_LABEL_TOWN_INFO: checked('nodemc-overlay-show-town-info', config.SHOW_LABEL_TOWN_INFO),
      PLAYER_ICON_SIZE: value('nodemc-overlay-player-icon-size', config.PLAYER_ICON_SIZE),
      PLAYER_TEXT_SIZE: value('nodemc-overlay-player-text-size', config.PLAYER_TEXT_SIZE),
      HORSE_ICON_SIZE: value('nodemc-overlay-horse-icon-size', config.HORSE_ICON_SIZE),
      HORSE_TEXT_SIZE: value('nodemc-overlay-horse-text-size', config.HORSE_TEXT_SIZE),
      SHOW_COORDS: checked('nodemc-overlay-coords', config.SHOW_COORDS),
      AUTO_TEAM_FROM_NAME: checked('nodemc-overlay-auto-team', config.AUTO_TEAM_FROM_NAME),
      FRIENDLY_TAGS: value('nodemc-overlay-friendly-tags', config.FRIENDLY_TAGS),
      ENEMY_TAGS: value('nodemc-overlay-enemy-tags', config.ENEMY_TAGS),
      TEAM_COLOR_FRIENDLY: value('nodemc-overlay-team-friendly-color', config.TEAM_COLOR_FRIENDLY),
      TEAM_COLOR_NEUTRAL: value('nodemc-overlay-team-neutral-color', config.TEAM_COLOR_NEUTRAL),
      TEAM_COLOR_ENEMY: value('nodemc-overlay-team-enemy-color', config.TEAM_COLOR_ENEMY),
      SHOW_WAYPOINT_ICON: checked('nodemc-overlay-show-waypoint-icon', config.SHOW_WAYPOINT_ICON),
      SHOW_WAYPOINT_TEXT: checked('nodemc-overlay-show-waypoint-text', config.SHOW_WAYPOINT_TEXT),
      DEBUG: checked('nodemc-overlay-debug', config.DEBUG),
    };
  }

  function refreshPlayerSelector(players: Array<{ playerId: string; playerName: string; displayLabel: string; teamColor: string | null }>) {
    const select = byId<HTMLSelectElement>('nodemc-mark-player-select');
    if (!select) return;

    const previousValue = select.value;
    while (select.firstChild) {
      select.removeChild(select.firstChild);
    }

    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = players.length ? '请选择在线玩家…' : '暂无在线玩家';
    select.appendChild(placeholder);

    for (const item of players) {
      const option = document.createElement('option');
      option.value = item.playerId;
      option.textContent = item.displayLabel || item.playerName;
      const color = deps.getPlayerOptionColor ? deps.getPlayerOptionColor(item) : item.teamColor;
      if (color) option.style.color = color;
      select.appendChild(option);
    }

    if (previousValue && players.some((item) => item.playerId === previousValue)) {
      select.value = previousValue;
    } else {
      select.value = '';
    }
  }

  function getSelectedPlayerId() {
    const select = byId<HTMLSelectElement>('nodemc-mark-player-select');
    return select ? String(select.value || '').trim() : '';
  }

  function getMarkForm() {
    const teamInput = byId<HTMLSelectElement>('nodemc-mark-team');
    const colorInput = byId<HTMLInputElement>('nodemc-mark-color');
    const labelInput = byId<HTMLInputElement>('nodemc-mark-label');

    return {
      playerId: getSelectedPlayerId(),
      team: teamInput ? String(teamInput.value || '') : 'neutral',
      color: colorInput ? String(colorInput.value || '') : '',
      label: labelInput ? String(labelInput.value || '').trim() : '',
    };
  }

  function setMarkColor(color: string) {
    const colorInput = byId<HTMLInputElement>('nodemc-mark-color');
    if (colorInput) colorInput.value = color;
  }

  function setServerFilterEnabled(enabled: boolean) {
    const serverFilterInput = byId<HTMLInputElement>('nodemc-overlay-server-filter');
    if (serverFilterInput) serverFilterInput.checked = enabled;
  }

  function cleanup() {
    try { const s = byId('nodemc-overlay-ui-style'); if (s) s.remove(); } catch (_) {}
    try { const fab = byId('nodemc-overlay-fab'); if (fab) fab.remove(); } catch (_) {}
    try { const panel = byId('nodemc-overlay-panel'); if (panel) panel.remove(); } catch (_) {}
    uiMounted = false;
  }

  return {
    mountWhenReady,
    updateStatus,
    fillFormFromConfig,
    readFormCandidate,
    refreshPlayerSelector,
    getSelectedPlayerId,
    getMarkForm,
    setMarkColor,
    setServerFilterEnabled,
    setPanelVisible,
    cleanup,
  };
}
