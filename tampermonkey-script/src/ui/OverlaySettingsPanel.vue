<script setup lang="ts">
import { computed } from 'vue';

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

function setPage(nextPage: OverlayUiState['page']) {
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

function togglePlayerList() {
  props.actions.onTogglePlayerList(!props.state.playerListVisible);
}

function closePlayerList() {
  props.actions.onTogglePlayerList(false);
}

function focusMapPlayer(playerId: string) {
  props.actions.onFocusMapPlayer(playerId);
}
</script>

<template>
  <div class="n-header">
    <div class="n-title" id="nodemc-overlay-title">NodeMC Overlay 设置</div>
  </div>

  <div class="n-page" :class="{ active: state.page === 'main' }" id="nodemc-overlay-page-main">
    <div class="n-card">
      <div class="n-subtitle">基础策略</div>
      <label class="n-check"><input v-model="state.form.AUTO_TEAM_FROM_NAME" id="nodemc-overlay-auto-team" type="checkbox" />按名字标签自动判定友敌</label>
      <label class="n-check"><input v-model="state.sameServerFilterEnabled" @change="onServerFilterChange" id="nodemc-overlay-server-filter" type="checkbox" />同服隔离广播（服务端）</label>
      <div class="n-row">
        <label>友军标签（逗号分隔，按游戏中的前缀识别）</label>
        <input v-model="state.form.FRIENDLY_TAGS" id="nodemc-overlay-friendly-tags" type="text" placeholder="[xxx],[队友]" />
      </div>
      <div class="n-row">
        <label>敌军标签（逗号分隔，按游戏中的前缀识别）</label>
        <input v-model="state.form.ENEMY_TAGS" id="nodemc-overlay-enemy-tags" type="text" placeholder="[yyy],[红队]" />
      </div>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-save" type="button" class="n-btn-primary" @click="actions.onSave">保存</button>
      <button id="nodemc-overlay-reset" type="button" class="n-btn-ghost" @click="actions.onReset">重置</button>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-export-config" type="button" class="n-btn-ghost" @click="actions.onExportConfig">导出配置</button>
      <button id="nodemc-overlay-import-config" type="button" class="n-btn-ghost" @click="actions.onImportConfig">导入配置</button>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-open-advanced" type="button" class="n-link-btn" @click="setPage('advanced')">高级设置</button>
      <button id="nodemc-overlay-open-connection" type="button" class="n-link-btn" @click="setPage('connection')">连接设置</button>
      <button id="nodemc-overlay-open-display" type="button" class="n-link-btn" @click="setPage('display')">显示设置</button>
      <button id="nodemc-overlay-open-mark" type="button" class="n-link-btn" @click="setPage('mark')">玩家标记/颜色</button>
      <button id="nodemc-overlay-open-player-list" type="button" class="n-link-btn" @click="togglePlayerList">玩家列表</button>
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
      <label class="n-check"><input v-model="state.form.SHOW_PLAYER_ICON" id="nodemc-overlay-show-icon" type="checkbox" />显示玩家图标（图标中心对准玩家坐标）</label>
      <label class="n-check"><input v-model="state.form.SHOW_PLAYER_TEXT" id="nodemc-overlay-show-text" type="checkbox" />显示玩家文字信息（仅文字时左端对准玩家坐标）</label>
      <label class="n-check"><input v-model="state.form.SHOW_WAYPOINT_ICON" id="nodemc-overlay-show-waypoint-icon" type="checkbox" />显示报点图标（图标中心对准报点坐标）</label>
      <label class="n-check"><input v-model="state.form.SHOW_WAYPOINT_TEXT" id="nodemc-overlay-show-waypoint-text" type="checkbox" />显示报点文字（文字左端对准报点坐标，带浅色半透明背景）</label>
      <label class="n-check"><input v-model="state.form.SHOW_HORSE_ENTITIES" id="nodemc-overlay-show-horse-entities" type="checkbox" />是否显示马实体</label>
      <label class="n-check"><input v-model="state.form.SHOW_HORSE_TEXT" id="nodemc-overlay-show-horse-text" type="checkbox" />显示马文字信息</label>
      <label class="n-check"><input v-model="state.form.SHOW_LABEL_TEAM_INFO" id="nodemc-overlay-show-team-info" type="checkbox" />地图文字显示阵营信息</label>
      <label class="n-check"><input v-model="state.form.SHOW_LABEL_TOWN_INFO" id="nodemc-overlay-show-town-info" type="checkbox" />地图文字显示城镇信息</label>
      <label class="n-check"><input v-model="state.form.SHOW_COORDS" id="nodemc-overlay-coords" type="checkbox" />显示坐标</label>
      <label class="n-check"><input v-model="state.form.DEBUG" id="nodemc-overlay-debug" type="checkbox" />调试日志</label>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-save-advanced" type="button" class="n-btn-primary" @click="actions.onSaveAdvanced">保存高级设置</button>
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
        <input v-model="state.form.PLAYER_ICON_SIZE" id="nodemc-overlay-player-icon-size" type="number" min="6" max="40" step="1" />
      </div>
      <div class="n-row">
        <label>玩家文字大小(px)</label>
        <input v-model="state.form.PLAYER_TEXT_SIZE" id="nodemc-overlay-player-text-size" type="number" min="8" max="32" step="1" />
      </div>
    </div>
    <div class="n-card">
      <div class="n-subtitle">大小设置（马）</div>
      <div class="n-row">
        <label>马图标大小(px)</label>
        <input v-model="state.form.HORSE_ICON_SIZE" id="nodemc-overlay-horse-icon-size" type="number" min="6" max="40" step="1" />
      </div>
      <div class="n-row">
        <label>马文字大小(px)</label>
        <input v-model="state.form.HORSE_TEXT_SIZE" id="nodemc-overlay-horse-text-size" type="number" min="8" max="32" step="1" />
      </div>
    </div>
    <div class="n-card">
      <div class="n-subtitle">阵营颜色</div>
      <div class="n-row">
        <label>友军颜色(#RRGGBB)</label>
        <input v-model="state.form.TEAM_COLOR_FRIENDLY" id="nodemc-overlay-team-friendly-color" type="text" placeholder="#3b82f6" />
      </div>
      <div class="n-row">
        <label>中立颜色(#RRGGBB)</label>
        <input v-model="state.form.TEAM_COLOR_NEUTRAL" id="nodemc-overlay-team-neutral-color" type="text" placeholder="#94a3b8" />
      </div>
      <div class="n-row">
        <label>敌军颜色(#RRGGBB)</label>
        <input v-model="state.form.TEAM_COLOR_ENEMY" id="nodemc-overlay-team-enemy-color" type="text" placeholder="#ef4444" />
      </div>
    </div>
    <div class="n-card">
      <div class="n-subtitle">上报玩家特殊显示</div>
      <label class="n-check"><input v-model="state.form.REPORTER_STAR_ICON" id="nodemc-overlay-reporter-star" type="checkbox" />上报玩家图标使用五角星（替换圆点）</label>
      <label class="n-check"><input v-model="state.form.REPORTER_VISION_CIRCLE_ENABLED" id="nodemc-overlay-reporter-vision-circle" type="checkbox" />显示上报玩家视野圆圈</label>
      <label class="n-check"><input v-model="state.form.REPORTER_CHUNK_AREA_ENABLED" id="nodemc-overlay-reporter-chunk-area" type="checkbox" />显示上报玩家区块范围</label>
      <div class="n-row">
        <label>视野圆圈半径 r（方块）</label>
        <input v-model="state.form.REPORTER_VISION_RADIUS" id="nodemc-overlay-reporter-vision-radius" type="number" min="8" max="4096" step="1" />
      </div>
      <div class="n-row">
        <label>视野圆圈颜色（#RRGGBB，留空跟随阵营色）</label>
        <input v-model="state.form.REPORTER_VISION_COLOR" id="nodemc-overlay-reporter-vision-color" type="text" placeholder="#3b82f6" />
      </div>
      <div class="n-row">
        <label>视野圆圈透明度（0.02 ~ 0.9）</label>
        <input v-model="state.form.REPORTER_VISION_OPACITY" id="nodemc-overlay-reporter-vision-opacity" type="number" min="0.02" max="0.9" step="0.01" />
      </div>
      <div class="n-row">
        <label>区块半径 l（按玩家所在区块向外）</label>
        <input v-model="state.form.REPORTER_CHUNK_RADIUS" id="nodemc-overlay-reporter-chunk-radius" type="number" min="0" max="64" step="1" />
      </div>
      <div class="n-row">
        <label>区块范围颜色（#RRGGBB，留空跟随阵营色）</label>
        <input v-model="state.form.REPORTER_CHUNK_COLOR" id="nodemc-overlay-reporter-chunk-color" type="text" placeholder="#ef4444" />
      </div>
      <div class="n-row">
        <label>区块范围透明度（0.02 ~ 0.9）</label>
        <input v-model="state.form.REPORTER_CHUNK_OPACITY" id="nodemc-overlay-reporter-chunk-opacity" type="number" min="0.02" max="0.9" step="0.01" />
      </div>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-save-display" type="button" class="n-btn-primary" @click="actions.onSaveDisplay">保存显示设置</button>
    </div>
  </div>

  <div class="n-page" :class="{ active: state.page === 'connection' }" id="nodemc-overlay-page-connection">
    <div class="n-nav-row">
      <div class="n-subtitle" style="margin: 0">连接设置</div>
      <button id="nodemc-overlay-back-main-from-connection" type="button" class="n-link-btn" @click="setPage('main')">返回基础设置</button>
    </div>
    <div class="n-card">
      <div class="n-row full-width">
        <label>Admin WS URL</label>
        <input v-model="state.form.ADMIN_WS_URL" id="nodemc-overlay-url" type="text" />
      </div>
      <div class="n-row">
        <label>房间号 Room Code</label>
        <input v-model="state.form.ROOM_CODE" id="nodemc-overlay-room-code" type="text" placeholder="default" />
      </div>
      <div class="n-row">
        <label>重连间隔(ms)</label>
        <input v-model="state.form.RECONNECT_INTERVAL_MS" id="nodemc-overlay-reconnect" type="number" min="200" max="60000" step="100" />
      </div>
      <div class="n-row">
        <label>维度过滤</label>
        <input v-model="state.form.TARGET_DIMENSION" id="nodemc-overlay-dim" type="text" placeholder="minecraft:overworld" />
      </div>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-save-connection" type="button" class="n-btn-primary" @click="actions.onSaveAdvanced">保存连接设置</button>
      <button id="nodemc-overlay-refresh" type="button" class="n-btn-ghost" @click="actions.onRefresh">立即重连</button>
    </div>
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
    <div class="n-btns">
      <button id="nodemc-mark-apply" type="button" class="n-btn-primary" @click="actions.onMarkApply">应用标记</button>
      <button id="nodemc-mark-clear" type="button" class="n-btn-ghost" @click="actions.onMarkClear">清除该玩家</button>
      <button id="nodemc-mark-clear-all" type="button" class="n-btn-danger" @click="actions.onMarkClearAll">清空全部标记</button>
    </div>
  </div>

  <div id="nodemc-overlay-status">{{ state.statusText }}</div>
</template>
