<script setup lang="ts">
import { computed, ref } from 'vue';

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

type OverlayUiState = {
  page: 'main' | 'advanced' | 'display' | 'mark' | 'connection';
  statusText: string;
  dirty: {
    mainText: boolean;
    connection: boolean;
    displayInputs: boolean;
  };
  sameServerFilterEnabled: boolean;
  players: PlayerOption[];
  mapPlayers: MapPlayerListItem[];
  selectedPlayerId: string;
  playerListVisible: boolean;
  mark: {
    team: string;
    color: string;
    label: string;
  };
  form: {
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
    BLOCK_MAP_LEFT_RIGHT_CLICK: boolean;
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
};

type OverlayUiActions = {
  onAutoApply: () => void;
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
};

const props = defineProps<{
  state: OverlayUiState;
  actions: OverlayUiActions;
  getPlayerOptionColor?: (item: PlayerOption) => string | null;
}>();

const hasPlayers = computed(() => props.state.players.length > 0);
const hasMapPlayers = computed(() => props.state.mapPlayers.length > 0);
const configMenuVisible = ref(false);
const helpVisible = ref(false);

function setPage(nextPage: OverlayUiState['page']) {
  configMenuVisible.value = false;
  helpVisible.value = false;
  props.state.page = nextPage;
}

function getOptionColor(item: PlayerOption) {
  return props.getPlayerOptionColor ? props.getPlayerOptionColor(item) : item.teamColor;
}

function onTeamChanged() {
  props.actions.onTeamChanged(String(props.state.mark.team || 'neutral'));
}

function onServerFilterChange() {
  props.actions.onServerFilterToggle(Boolean(props.state.sameServerFilterEnabled));
}

function onPlayerSelectionChanged() {
  props.actions.onPlayerSelectionChanged();
}

function triggerAutoApply() {
  props.actions.onAutoApply();
}

function markMainTextDirty() {
  props.state.dirty.mainText = true;
}

function markConnectionDirty() {
  props.state.dirty.connection = true;
}

function markDisplayInputsDirty() {
  props.state.dirty.displayInputs = true;
}

function saveMainText() {
  props.actions.onSave();
  props.state.dirty.mainText = false;
}

function saveConnectionSettings() {
  props.actions.onSaveAdvanced();
  props.state.dirty.connection = false;
}

function saveDisplayInputs() {
  props.actions.onSaveDisplay();
  props.state.dirty.displayInputs = false;
}

function togglePlayerList() {
  props.actions.onTogglePlayerList(!props.state.playerListVisible);
}

function closePlayerList() {
  props.actions.onTogglePlayerList(false);
}

function focusMapPlayer(playerId: string) {
  props.actions.onFocusMapPlayer(playerId);
}

function toggleConfigMenu() {
  configMenuVisible.value = !configMenuVisible.value;
}

function onResetFromMenu() {
  props.actions.onReset();
  configMenuVisible.value = false;
}

function onExportConfigFromMenu() {
  props.actions.onExportConfig();
  configMenuVisible.value = false;
}

function onImportConfigFromMenu() {
  props.actions.onImportConfig();
  configMenuVisible.value = false;
}

function toggleHelp() {
  helpVisible.value = !helpVisible.value;
}

function closeHelp() {
  helpVisible.value = false;
}
</script>

<template>
  <div class="n-header">
    <div class="n-title" id="nodemc-overlay-title">NodeMC Overlay 设置</div>
  </div>

  <div class="n-page" :class="{ active: state.page === 'main' }" id="nodemc-overlay-page-main">
    <div class="n-btns">
      <button id="nodemc-overlay-open-config-menu" type="button" class="n-btn-ghost" @click="toggleConfigMenu">
        {{ configMenuVisible ? '收起配置设置' : '配置设置' }}
      </button>
    </div>
    <div v-if="configMenuVisible" class="n-config-menu" id="nodemc-overlay-config-menu">
      <div class="n-btns n-config-menu-items">
        <button id="nodemc-overlay-reset" type="button" class="n-btn-ghost" @click="onResetFromMenu">重置</button>
        <button id="nodemc-overlay-export-config" type="button" class="n-btn-ghost" @click="onExportConfigFromMenu">导出配置</button>
        <button id="nodemc-overlay-import-config" type="button" class="n-btn-ghost" @click="onImportConfigFromMenu">导入配置</button>
      </div>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-open-advanced" type="button" class="n-link-btn" @click="setPage('advanced')">高级设置</button>
      <button id="nodemc-overlay-open-connection" type="button" class="n-link-btn" @click="setPage('connection')">连接设置</button>
      <button id="nodemc-overlay-open-display" type="button" class="n-link-btn" @click="setPage('display')">显示设置</button>
      <button id="nodemc-overlay-open-mark" type="button" class="n-link-btn" @click="setPage('mark')">玩家标记/颜色</button>
      <button id="nodemc-overlay-open-player-list" type="button" class="n-link-btn" @click="togglePlayerList">玩家列表</button>
      <button id="nodemc-overlay-open-help" type="button" class="n-link-btn" @click="toggleHelp">使用帮助</button>
    </div>

    <div v-if="helpVisible" class="n-player-list-popup" id="nodemc-overlay-help-popup">
      <div class="n-player-list-header">
        <div class="n-player-list-title">使用教程</div>
        <button id="nodemc-overlay-close-help" type="button" class="n-player-list-close" @click="closeHelp">关闭</button>
      </div>
      <div class="n-help-content">
        <ol class="n-help-list">
          <li>先进入“连接设置”，填写 <b>Admin WS URL</b> 和房间号，点击“应用连接设置”。</li>
          <li>进入“高级设置”，按需开启玩家图标、文字、马实体、坐标等显示项。</li>
          <li>进入“显示设置”，调整图标/文字大小与阵营颜色，最后点击“保存显示输入项”。</li>
          <li>进入“玩家标记/颜色”，从在线玩家列表选择目标并设置阵营、颜色、标签后点击“应用标记”。</li>
          <li>一级页面可随时打开“玩家列表”查看地图玩家信息，点击某行可快速聚焦该玩家。</li>
          <li>所有需要手动保存的输入项都会出现“已修改”提示，请记得保存后再实战使用。</li>
        </ol>
        <div class="n-help-tip">
          <li>小贴士：在Tab玩家列表中看到的玩家名称前缀，就是标签，可开启“按名字标签自动判定友敌”</li>
          <li>如“[法兰西]ydxc”的标签是“[法兰西]”，将其设置为友军标签即可自动识别。</li>
        </div>
      </div>
    </div>

    <div v-if="state.playerListVisible" class="n-player-list-popup" id="nodemc-overlay-player-list-popup">
      <div class="n-player-list-header">
        <div class="n-player-list-title">地图玩家列表</div>
        <button id="nodemc-overlay-close-player-list" type="button" class="n-player-list-close" @click="closePlayerList">关闭</button>
      </div>
      <div class="n-player-list-table-wrap">
        <table class="n-player-list-table">
          <thead>
            <tr>
              <th>玩家名称</th>
              <th>阵营</th>
              <th>城镇</th>
              <th>血量</th>
              <th>盔甲值</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in state.mapPlayers"
              :key="item.playerId"
              class="n-player-list-row"
              @click="focusMapPlayer(item.playerId)"
            >
              <td>{{ item.playerName }}</td>
              <td>
                <span class="n-team-chip" :style="{ color: item.teamColor, borderColor: `${item.teamColor}66`, background: `${item.teamColor}20` }">
                  {{ item.team }}
                </span>
              </td>
              <td>
                <span class="n-town-chip" :style="{ color: item.townColor, borderColor: `${item.townColor}66`, background: `${item.townColor}1f` }">
                  {{ item.town }}
                </span>
              </td>
              <td>{{ item.health }}</td>
              <td>{{ item.armor }}</td>
            </tr>
            <tr v-if="!hasMapPlayers">
              <td colspan="5" class="n-player-list-empty">当前地图暂无可显示玩家</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <div class="n-page" :class="{ active: state.page === 'advanced' }" id="nodemc-overlay-page-advanced">
    <div class="n-nav-row">
      <div class="n-subtitle" style="margin: 0">高级设置</div>
      <button id="nodemc-overlay-back-main" type="button" class="n-link-btn" @click="setPage('main')">返回基础设置</button>
    </div>
    <div class="n-card">
      <label class="n-check"><input v-model="state.form.SHOW_PLAYER_ICON" @change="triggerAutoApply" id="nodemc-overlay-show-icon" type="checkbox" />显示玩家图标（图标中心对准玩家坐标）</label>
      <label class="n-check"><input v-model="state.form.SHOW_PLAYER_TEXT" @change="triggerAutoApply" id="nodemc-overlay-show-text" type="checkbox" />显示玩家文字信息（仅文字时左端对准玩家坐标）</label>
      <label class="n-check"><input v-model="state.form.SHOW_WAYPOINT_ICON" @change="triggerAutoApply" id="nodemc-overlay-show-waypoint-icon" type="checkbox" />显示报点图标（图标中心对准报点坐标）</label>
      <label class="n-check"><input v-model="state.form.SHOW_WAYPOINT_TEXT" @change="triggerAutoApply" id="nodemc-overlay-show-waypoint-text" type="checkbox" />显示报点文字（文字左端对准报点坐标，带浅色半透明背景）</label>
      <label class="n-check"><input v-model="state.form.SHOW_HORSE_ENTITIES" @change="triggerAutoApply" id="nodemc-overlay-show-horse-entities" type="checkbox" />是否显示马实体</label>
      <label class="n-check"><input v-model="state.form.SHOW_HORSE_TEXT" @change="triggerAutoApply" id="nodemc-overlay-show-horse-text" type="checkbox" />显示马文字信息</label>
      <label class="n-check"><input v-model="state.form.SHOW_LABEL_TEAM_INFO" @change="triggerAutoApply" id="nodemc-overlay-show-team-info" type="checkbox" />地图文字显示阵营信息</label>
      <label class="n-check"><input v-model="state.form.SHOW_LABEL_TOWN_INFO" @change="triggerAutoApply" id="nodemc-overlay-show-town-info" type="checkbox" />地图文字显示城镇信息</label>
      <label class="n-check"><input v-model="state.form.BLOCK_MAP_LEFT_RIGHT_CLICK" @change="triggerAutoApply" id="nodemc-overlay-block-map-click" type="checkbox" />屏蔽原网页地图左/右键功能（保留拖拽与滚轮缩放）</label>
      <label class="n-check"><input v-model="state.form.SHOW_COORDS" @change="triggerAutoApply" id="nodemc-overlay-coords" type="checkbox" />显示坐标</label>
      <label class="n-check"><input v-model="state.form.DEBUG" @change="triggerAutoApply" id="nodemc-overlay-debug" type="checkbox" />调试日志</label>
      <label class="n-check"><input v-model="state.sameServerFilterEnabled" @change="onServerFilterChange" id="nodemc-overlay-server-filter" type="checkbox" />同服隔离广播（服务端）</label>
    </div>
  </div>

  <div class="n-page" :class="{ active: state.page === 'display' }" id="nodemc-overlay-page-display">
    <div class="n-nav-row">
      <div class="n-subtitle" style="margin: 0">显示设置</div>
      <button id="nodemc-overlay-back-main-from-display" type="button" class="n-link-btn" @click="setPage('main')">返回基础设置</button>
    </div>
    <div class="n-card">
      <div class="n-subtitle">大小设置（玩家）</div>
      <div class="n-row">
        <label>玩家图标大小(px)</label>
        <input v-model="state.form.PLAYER_ICON_SIZE" @input="markDisplayInputsDirty" id="nodemc-overlay-player-icon-size" type="number" min="6" max="40" step="1" />
      </div>
      <div class="n-row">
        <label>玩家文字大小(px)</label>
        <input v-model="state.form.PLAYER_TEXT_SIZE" @input="markDisplayInputsDirty" id="nodemc-overlay-player-text-size" type="number" min="8" max="32" step="1" />
      </div>
    </div>
    <div class="n-card">
      <div class="n-subtitle">大小设置（马）</div>
      <div class="n-row">
        <label>马图标大小(px)</label>
        <input v-model="state.form.HORSE_ICON_SIZE" @input="markDisplayInputsDirty" id="nodemc-overlay-horse-icon-size" type="number" min="6" max="40" step="1" />
      </div>
      <div class="n-row">
        <label>马文字大小(px)</label>
        <input v-model="state.form.HORSE_TEXT_SIZE" @input="markDisplayInputsDirty" id="nodemc-overlay-horse-text-size" type="number" min="8" max="32" step="1" />
      </div>
    </div>
    <div class="n-card">
      <div class="n-subtitle">阵营颜色</div>
      <div class="n-row">
        <label>友军颜色(#RRGGBB)</label>
        <input v-model="state.form.TEAM_COLOR_FRIENDLY" @input="markDisplayInputsDirty" id="nodemc-overlay-team-friendly-color" type="text" placeholder="#3b82f6" />
      </div>
      <div class="n-row">
        <label>中立颜色(#RRGGBB)</label>
        <input v-model="state.form.TEAM_COLOR_NEUTRAL" @input="markDisplayInputsDirty" id="nodemc-overlay-team-neutral-color" type="text" placeholder="#94a3b8" />
      </div>
      <div class="n-row">
        <label>敌军颜色(#RRGGBB)</label>
        <input v-model="state.form.TEAM_COLOR_ENEMY" @input="markDisplayInputsDirty" id="nodemc-overlay-team-enemy-color" type="text" placeholder="#ef4444" />
      </div>
    </div>
    <div class="n-card">
      <div class="n-subtitle">上报玩家特殊显示</div>
      <div class="n-row">
        <label>视野圆圈半径 r（方块）</label>
        <input v-model="state.form.REPORTER_VISION_RADIUS" @input="markDisplayInputsDirty" id="nodemc-overlay-reporter-vision-radius" type="number" min="8" max="4096" step="1" />
      </div>
      <div class="n-row">
        <label>视野圆圈颜色（#RRGGBB，留空跟随阵营色）</label>
        <input v-model="state.form.REPORTER_VISION_COLOR" @input="markDisplayInputsDirty" id="nodemc-overlay-reporter-vision-color" type="text" placeholder="#3b82f6" />
      </div>
      <div class="n-row">
        <label>视野圆圈透明度（0.02 ~ 0.9）</label>
        <input v-model="state.form.REPORTER_VISION_OPACITY" @input="markDisplayInputsDirty" id="nodemc-overlay-reporter-vision-opacity" type="number" min="0.02" max="0.9" step="0.01" />
      </div>
      <div class="n-row">
        <label>区块半径 l（按玩家所在区块向外）</label>
        <input v-model="state.form.REPORTER_CHUNK_RADIUS" @input="markDisplayInputsDirty" id="nodemc-overlay-reporter-chunk-radius" type="number" min="0" max="64" step="1" />
      </div>
      <div class="n-row">
        <label>区块范围颜色（#RRGGBB，留空跟随阵营色）</label>
        <input v-model="state.form.REPORTER_CHUNK_COLOR" @input="markDisplayInputsDirty" id="nodemc-overlay-reporter-chunk-color" type="text" placeholder="#ef4444" />
      </div>
      <div class="n-row">
        <label>区块范围透明度（0.02 ~ 0.9）</label>
        <input v-model="state.form.REPORTER_CHUNK_OPACITY" @input="markDisplayInputsDirty" id="nodemc-overlay-reporter-chunk-opacity" type="number" min="0.02" max="0.9" step="0.01" />
      </div>
      <label class="n-check"><input v-model="state.form.REPORTER_STAR_ICON" @change="triggerAutoApply" id="nodemc-overlay-reporter-star" type="checkbox" />上报玩家图标使用五角星（替换圆点）</label>
      <label class="n-check"><input v-model="state.form.REPORTER_VISION_CIRCLE_ENABLED" @change="triggerAutoApply" id="nodemc-overlay-reporter-vision-circle" type="checkbox" />显示上报玩家视野圆圈</label>
      <label class="n-check"><input v-model="state.form.REPORTER_CHUNK_AREA_ENABLED" @change="triggerAutoApply" id="nodemc-overlay-reporter-chunk-area" type="checkbox" />显示上报玩家区块范围</label>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-save-display" type="button" class="n-btn-primary" :disabled="!state.dirty.displayInputs" @click="saveDisplayInputs">保存显示输入项</button>
    </div>
    <div v-if="state.dirty.displayInputs" class="n-dirty-hint">显示页输入框已修改，点击“保存显示输入项”后生效</div>
  </div>

  <div class="n-page" :class="{ active: state.page === 'connection' }" id="nodemc-overlay-page-connection">
    <div class="n-nav-row">
      <div class="n-subtitle" style="margin: 0">连接设置</div>
      <button id="nodemc-overlay-back-main-from-connection" type="button" class="n-link-btn" @click="setPage('main')">返回基础设置</button>
    </div>
    <div class="n-card">
      <div class="n-row full-width">
        <label>Admin WS URL</label>
        <input v-model="state.form.ADMIN_WS_URL" @input="markConnectionDirty" id="nodemc-overlay-url" type="text" />
      </div>
      <div class="n-row">
        <label>房间号 Room Code</label>
        <input v-model="state.form.ROOM_CODE" @input="markConnectionDirty" id="nodemc-overlay-room-code" type="text" placeholder="default" />
      </div>
      <div class="n-row">
        <label>重连间隔(ms)</label>
        <input v-model="state.form.RECONNECT_INTERVAL_MS" @input="markConnectionDirty" id="nodemc-overlay-reconnect" type="number" min="200" max="60000" step="100" />
      </div>
      <div class="n-row">
        <label>维度过滤</label>
        <input v-model="state.form.TARGET_DIMENSION" @input="markConnectionDirty" id="nodemc-overlay-dim" type="text" placeholder="minecraft:overworld" />
      </div>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-save-connection" type="button" class="n-btn-primary" :disabled="!state.dirty.connection" @click="saveConnectionSettings">应用连接设置</button>
      <button id="nodemc-overlay-refresh" type="button" class="n-btn-ghost" @click="actions.onRefresh">立即重连</button>
    </div>
    <div v-if="state.dirty.connection" class="n-dirty-hint">连接输入项已修改，点击“应用连接设置”后保存并重连</div>
  </div>

  <div class="n-page" :class="{ active: state.page === 'mark' }" id="nodemc-overlay-page-mark">
    <div class="n-nav-row">
      <div class="n-subtitle" style="margin: 0">玩家标记/颜色</div>
      <button id="nodemc-overlay-back-main-from-mark" type="button" class="n-link-btn" @click="setPage('main')">返回基础设置</button>
    </div>
    <div class="n-card">
      <div class="n-subtitle">定向玩家标记/颜色</div>
      <div class="n-row">
        <label>在线玩家列表（推荐）</label>
        <select id="nodemc-mark-player-select" v-model="state.selectedPlayerId" @change="onPlayerSelectionChanged">
          <option value="">{{ hasPlayers ? '请选择在线玩家…' : '暂无在线玩家' }}</option>
          <option
            v-for="item in state.players"
            :key="item.playerId"
            :value="item.playerId"
            :style="{ color: getOptionColor(item) || undefined }"
          >
            {{ item.displayLabel || item.playerName }}
          </option>
        </select>
      </div>
      <div class="n-row">
        <label>阵营</label>
        <select id="nodemc-mark-team" v-model="state.mark.team" @change="onTeamChanged">
          <option value="friendly">友军</option>
          <option value="enemy">敌军</option>
          <option value="neutral">中立</option>
        </select>
      </div>
      <div class="n-row">
        <label>颜色(#RRGGBB)</label>
        <input id="nodemc-mark-color" v-model="state.mark.color" type="text" placeholder="#ef4444" />
      </div>
      <div class="n-row">
        <label>标签(可选)</label>
        <input id="nodemc-mark-label" v-model="state.mark.label" type="text" placeholder="例如：突击组/重点观察" />
      </div>
    </div>

    <div class="n-card">
      <div class="n-subtitle">标签设置</div>
      <div class="n-row">
        <label>友军标签（逗号分隔，按游戏中的前缀识别）</label>
        <input v-model="state.form.FRIENDLY_TAGS" @input="markMainTextDirty" id="nodemc-overlay-friendly-tags" type="text" placeholder="[xxx],[队友]" />
      </div>
      <div class="n-row">
        <label>敌军标签（逗号分隔，按游戏中的前缀识别）</label>
        <input v-model="state.form.ENEMY_TAGS" @input="markMainTextDirty" id="nodemc-overlay-enemy-tags" type="text" placeholder="[yyy],[红队]" />
      </div>
      <label class="n-check"><input v-model="state.form.AUTO_TEAM_FROM_NAME" @change="triggerAutoApply" id="nodemc-overlay-auto-team" type="checkbox" />按名字标签自动判定友敌</label>
      <div class="n-btns">
        <button id="nodemc-overlay-save" type="button" class="n-btn-primary" :disabled="!state.dirty.mainText" @click="saveMainText">保存设置</button>
        <div v-if="state.dirty.mainText" class="n-dirty-hint">已修改，点击“保存设置”后生效</div>
      </div>
    </div>

    <div class="n-btns">
      <button id="nodemc-mark-apply" type="button" class="n-btn-primary" @click="actions.onMarkApply">应用标记</button>
      <button id="nodemc-mark-clear" type="button" class="n-btn-ghost" @click="actions.onMarkClear">清除该玩家</button>
      <button id="nodemc-mark-clear-all" type="button" class="n-btn-danger" @click="actions.onMarkClearAll">清空全部标记</button>
    </div>
  </div>

  <div id="nodemc-overlay-status">{{ state.statusText }}</div>
</template>
