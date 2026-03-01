export const PANEL_HTML = `
  <div class="n-title" id="nodemc-overlay-title">NodeMC Overlay 设置（可拖动）</div>
  <div class="n-page active" id="nodemc-overlay-page-main">
    <label class="n-check"><input id="nodemc-overlay-auto-team" type="checkbox" />按名字标签自动判定友敌</label>
    <div class="n-row">
      <label>友军标签（逗号分隔，按游戏中的前缀识别）</label>
      <input id="nodemc-overlay-friendly-tags" type="text" placeholder="[xxx],[队友]" />
    </div>
    <div class="n-row">
      <label>敌军标签（逗号分隔，按游戏中的前缀识别）</label>
      <input id="nodemc-overlay-enemy-tags" type="text" placeholder="[yyy],[红队]" />
    </div>
    <label class="n-check"><input id="nodemc-overlay-server-filter" type="checkbox" />同服隔离广播（服务端）</label>
    <div class="n-btns">
      <button id="nodemc-overlay-save" type="button">保存</button>
      <button id="nodemc-overlay-reset" type="button">重置</button>
      <button id="nodemc-overlay-refresh" type="button">立即重连</button>
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-open-advanced" type="button" class="n-link-btn">高级设置</button>
      <button id="nodemc-overlay-open-mark" type="button" class="n-link-btn">玩家标记/颜色</button>
    </div>
  </div>

  <div class="n-page" id="nodemc-overlay-page-advanced">
    <div class="n-nav-row">
      <div class="n-subtitle" style="margin:0;">高级设置</div>
      <button id="nodemc-overlay-back-main" type="button" class="n-link-btn">返回基础设置</button>
    </div>
    <div class="n-row">
      <label>Admin WS URL</label>
      <input id="nodemc-overlay-url" type="text" />
    </div>
    <div class="n-row">
      <label>房间号 Room Code</label>
      <input id="nodemc-overlay-room-code" type="text" placeholder="default" />
    </div>
    <div class="n-row">
      <label>重连间隔(ms)</label>
      <input id="nodemc-overlay-reconnect" type="number" min="200" max="60000" step="100" />
    </div>
    <div class="n-row">
      <label>维度过滤</label>
      <input id="nodemc-overlay-dim" type="text" placeholder="minecraft:overworld" />
    </div>
    <label class="n-check"><input id="nodemc-overlay-show-icon" type="checkbox" />显示玩家图标（图标中心对准玩家坐标）</label>
    <label class="n-check"><input id="nodemc-overlay-show-text" type="checkbox" />显示玩家文字信息（仅文字时左端对准玩家坐标）</label>
    <label class="n-check"><input id="nodemc-overlay-show-waypoint-icon" type="checkbox" />显示报点图标（图标中心对准报点坐标）</label>
    <label class="n-check"><input id="nodemc-overlay-show-waypoint-text" type="checkbox" />显示报点文字（文字左端对准报点坐标，带浅色半透明背景）</label>
    <label class="n-check"><input id="nodemc-overlay-show-horse-entities" type="checkbox" />是否显示马实体</label>
    <label class="n-check"><input id="nodemc-overlay-show-team-info" type="checkbox" />地图文字显示阵营信息</label>
    <label class="n-check"><input id="nodemc-overlay-show-town-info" type="checkbox" />地图文字显示城镇信息</label>
    <div class="n-subtitle">大小设置（玩家）</div>
    <div class="n-row">
      <label>玩家图标大小(px)</label>
      <input id="nodemc-overlay-player-icon-size" type="number" min="6" max="40" step="1" />
    </div>
    <div class="n-row">
      <label>玩家文字大小(px)</label>
      <input id="nodemc-overlay-player-text-size" type="number" min="8" max="32" step="1" />
    </div>
    <div class="n-subtitle">大小设置（马）</div>
    <div class="n-row">
      <label>马图标大小(px)</label>
      <input id="nodemc-overlay-horse-icon-size" type="number" min="6" max="40" step="1" />
    </div>
    <div class="n-row">
      <label>马文字大小(px)</label>
      <input id="nodemc-overlay-horse-text-size" type="number" min="8" max="32" step="1" />
    </div>
    <label class="n-check"><input id="nodemc-overlay-coords" type="checkbox" />显示坐标</label>
    <label class="n-check"><input id="nodemc-overlay-debug" type="checkbox" />调试日志</label>

    <div class="n-subtitle">阵营颜色</div>
    <div class="n-row">
      <label>友军颜色(#RRGGBB)</label>
      <input id="nodemc-overlay-team-friendly-color" type="text" placeholder="#3b82f6" />
    </div>
    <div class="n-btns">
      <button id="nodemc-overlay-save-advanced" type="button">保存高级设置</button>
    </div>
    <div class="n-row">
      <label>中立颜色(#RRGGBB)</label>
      <input id="nodemc-overlay-team-neutral-color" type="text" placeholder="#94a3b8" />
    </div>
    <div class="n-row">
      <label>敌军颜色(#RRGGBB)</label>
      <input id="nodemc-overlay-team-enemy-color" type="text" placeholder="#ef4444" />
    </div>

  </div>

  <div class="n-page" id="nodemc-overlay-page-mark">
    <div class="n-nav-row">
      <div class="n-subtitle" style="margin:0;">玩家标记/颜色</div>
      <button id="nodemc-overlay-back-main-from-mark" type="button" class="n-link-btn">返回基础设置</button>
    </div>
    <div class="n-subtitle">定向玩家标记/颜色</div>
    <div class="n-row">
      <label>在线玩家列表（推荐）</label>
      <select id="nodemc-mark-player-select">
        <option value="">暂无在线玩家</option>
      </select>
    </div>
    <div class="n-row">
      <label>阵营</label>
      <select id="nodemc-mark-team">
        <option value="friendly">友军</option>
        <option value="enemy">敌军</option>
        <option value="neutral" selected>中立</option>
      </select>
    </div>
    <div class="n-row">
      <label>颜色(#RRGGBB)</label>
      <input id="nodemc-mark-color" type="text" placeholder="#ef4444" />
    </div>
    <div class="n-row">
      <label>标签(可选)</label>
      <input id="nodemc-mark-label" type="text" placeholder="例如：突击组/重点观察" />
    </div>
    <div class="n-btns">
      <button id="nodemc-mark-apply" type="button">应用标记</button>
      <button id="nodemc-mark-clear" type="button">清除该玩家</button>
      <button id="nodemc-mark-clear-all" type="button">清空全部标记</button>
    </div>
  </div>

  <div id="nodemc-overlay-status"></div>
`;
