export const UI_STYLE_TEXT = `
  #nodemc-overlay-fab {
    position: fixed;
    right: 18px;
    bottom: 96px;
    width: 34px;
    height: 34px;
    border-radius: 999px;
    border: 1px solid rgba(255,255,255,.35);
    background: radial-gradient(circle at 30% 30%, #60a5fa, #1d4ed8 70%);
    color: #fff;
    font-size: 15px;
    line-height: 34px;
    text-align: center;
    cursor: pointer;
    z-index: 2147483000;
    box-shadow: 0 8px 18px rgba(0,0,0,.35);
    user-select: none;
    touch-action: none;
  }
  #nodemc-overlay-panel {
    position: fixed;
    right: 18px;
    bottom: 160px;
    width: 320px;
    background: rgba(15, 23, 42, .97);
    border: 1px solid rgba(148, 163, 184, .4);
    border-radius: 12px;
    color: #e2e8f0;
    z-index: 2147483000;
    box-shadow: 0 12px 28px rgba(0,0,0,.45);
    padding: 12px;
    font-size: 12px;
    display: none;
  }
  #nodemc-overlay-panel .n-title {
    font-weight: 700;
    margin-bottom: 8px;
    cursor: move;
    user-select: none;
  }
  #nodemc-overlay-panel .n-page {
    display: none;
  }
  #nodemc-overlay-panel .n-page.active {
    display: block;
  }
  #nodemc-overlay-panel .n-row {
    margin-bottom: 8px;
  }
  #nodemc-overlay-panel label {
    display: block;
    margin-bottom: 4px;
    color: #bfdbfe;
  }
  #nodemc-overlay-panel input[type="text"],
  #nodemc-overlay-panel input[type="number"] {
    width: 100%;
    box-sizing: border-box;
    border-radius: 8px;
    border: 1px solid rgba(148,163,184,.45);
    background: rgba(30,41,59,.9);
    color: #e2e8f0;
    padding: 7px 8px;
  }
  #nodemc-overlay-panel .n-check {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 6px;
  }
  #nodemc-overlay-panel .n-btns {
    display: flex;
    gap: 8px;
    margin-top: 10px;
    flex-wrap: wrap;
  }
  #nodemc-overlay-panel button {
    border: 1px solid rgba(147,197,253,.45);
    background: rgba(30,64,175,.9);
    color: #fff;
    border-radius: 8px;
    padding: 6px 10px;
    cursor: pointer;
  }
  #nodemc-overlay-panel .n-link-btn {
    border: 1px solid rgba(148,163,184,.5);
    background: rgba(15,23,42,.75);
    color: #dbeafe;
  }
  #nodemc-overlay-panel .n-nav-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
    gap: 8px;
  }
  #nodemc-overlay-status {
    margin-top: 8px;
    color: #93c5fd;
    word-break: break-word;
  }
  #nodemc-overlay-panel .n-subtitle {
    margin-top: 10px;
    margin-bottom: 6px;
    font-weight: 700;
    color: #bfdbfe;
  }
  #nodemc-overlay-panel select {
    width: 100%;
    box-sizing: border-box;
    border-radius: 8px;
    border: 1px solid rgba(148,163,184,.45);
    background: rgba(30,41,59,.9);
    color: #e2e8f0;
    padding: 7px 8px;
  }
`;

export const OVERLAY_STYLE_TEXT = `
  .nodemc-projection-label {
    background: rgba(0, 0, 0, 0.78);
    color: #fff;
    border: 1px solid rgba(255, 255, 255, 0.22);
    border-radius: 6px;
    padding: 3px 7px;
    font-size: 12px;
    line-height: 1.2;
    white-space: nowrap;
  }
  .nodemc-player-label {
    background: rgba(15, 23, 42, 0.88);
    color: #dbeafe;
    border: 1px solid rgba(147, 197, 253, 0.5);
    border-radius: 6px;
    padding: 3px 7px;
    font-size: 12px;
    line-height: 1.2;
    white-space: nowrap;
  }
  .nodemc-player-label .n-team {
    font-weight: 700;
  }
  .nodemc-player-anchor {
    position: relative;
    width: 0;
    height: 0;
    pointer-events: none;
  }
  .nodemc-player-anchor .n-icon {
    position: absolute;
    left: 0;
    top: 0;
    width: 10px;
    height: 10px;
    border-radius: 999px;
    border: 1px solid rgba(255, 255, 255, 0.9);
    transform: translate(-50%, -50%);
  }
  .nodemc-player-anchor .n-icon.is-horse {
    width: 14px;
    height: 14px;
    font-size: 10px;
    line-height: 14px;
    text-align: center;
  }
  .nodemc-player-anchor .n-label {
    position: absolute;
    top: 0;
    transform: translateY(-50%);
    background: rgba(15, 23, 42, 0.88);
    color: #dbeafe;
    border: 1px solid rgba(147, 197, 253, 0.5);
    border-radius: 6px;
    padding: 3px 7px;
    font-size: 12px;
    line-height: 1.2;
    white-space: nowrap;
  }
  .nodemc-player-anchor .n-label[data-align="with-icon"] {
    left: 10px;
  }
  .nodemc-player-anchor .n-label[data-align="left-anchor"] {
    left: 0;
  }
  .nodemc-player-anchor .n-team {
    font-weight: 700;
  }
`;
