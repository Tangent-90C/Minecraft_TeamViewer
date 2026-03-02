export const UI_STYLE_TEXT = `
  #teamviewer-overlay-root {
    --nmc-bg-main: rgba(245, 252, 255, 0.98);
    --nmc-bg-panel: rgba(255, 255, 255, 0.98);
    --nmc-bg-card: rgba(219, 234, 254, 0.65);
    --nmc-border-strong: rgba(59, 130, 246, 0.35);
    --nmc-border-soft: rgba(37, 99, 235, 0.24);
    --nmc-text-main: #0f172a;
    --nmc-text-subtle: #1d4ed8;
    --nmc-text-muted: #334155;
    --nmc-primary: #3b82f6;
    --nmc-primary-hover: #2563eb;
    --nmc-danger: #dc2626;
    --nmc-danger-hover: #b91c1c;
  }
  #nodemc-overlay-fab {
    position: fixed;
    right: 18px;
    bottom: 96px;
    width: 34px;
    height: 34px;
    border-radius: 999px;
    border: 1px solid rgba(191, 219, 254, 0.6);
    background: radial-gradient(circle at 28% 22%, #93c5fd, #3b82f6 65%, #1d4ed8);
    color: #fff;
    font-size: 15px;
    font-weight: 700;
    line-height: 34px;
    text-align: center;
    cursor: pointer;
    z-index: 2147483000;
    box-shadow: 0 12px 30px rgba(29, 78, 216, 0.45), 0 8px 18px rgba(0,0,0,.35);
    user-select: none;
    touch-action: none;
    transition: transform .15s ease, box-shadow .2s ease, filter .2s ease;
  }
  #nodemc-overlay-fab:hover {
    transform: translateY(-1px) scale(1.03);
    filter: brightness(1.08);
  }
  #nodemc-overlay-fab:active {
    transform: translateY(0) scale(0.98);
  }
  #nodemc-overlay-panel {
    position: fixed;
    right: 18px;
    bottom: 160px;
    width: 350px;
    max-width: calc(100vw - 20px);
    max-height: min(82vh, 760px);
    overflow: auto;
    background:
      linear-gradient(150deg, rgba(147, 197, 253, 0.34) 0%, rgba(186, 230, 253, 0.18) 42%),
      var(--nmc-bg-main);
    border: 1px solid var(--nmc-border-strong);
    border-radius: 14px;
    color: var(--nmc-text-main);
    z-index: 2147483000;
    box-shadow: 0 16px 38px rgba(30, 64, 175, 0.26);
    padding: 12px 12px 14px;
    font-size: 12px;
    display: none;
    backdrop-filter: blur(10px);
    scrollbar-width: thin;
    scrollbar-color: rgba(148, 163, 184, .6) transparent;
  }
  #nodemc-overlay-panel::-webkit-scrollbar {
    width: 8px;
  }
  #nodemc-overlay-panel::-webkit-scrollbar-track {
    background: transparent;
  }
  #nodemc-overlay-panel::-webkit-scrollbar-thumb {
    background: rgba(148, 163, 184, 0.45);
    border-radius: 999px;
  }
  #nodemc-overlay-panel .n-title {
    font-weight: 700;
    margin-bottom: 2px;
    cursor: move;
    user-select: none;
    letter-spacing: 0.2px;
    font-size: 13px;
  }
  #nodemc-overlay-panel .n-header {
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin-bottom: 10px;
    padding: 9px 10px;
    border-radius: 10px;
    background: linear-gradient(180deg, rgba(191, 219, 254, 0.82), rgba(219, 234, 254, 0.62));
    border: 1px solid var(--nmc-border-soft);
  }
  #nodemc-overlay-panel .n-header-hint {
    color: var(--nmc-text-muted);
    font-size: 11px;
  }
  #nodemc-overlay-panel .n-page {
    display: none;
    animation: nodemc-fade-in .16s ease;
  }
  #nodemc-overlay-panel .n-page.active {
    display: block;
  }
  @keyframes nodemc-fade-in {
    from {
      opacity: 0;
      transform: translateY(2px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
  #nodemc-overlay-panel .n-card {
    margin-bottom: 10px;
    padding: 10px;
    border-radius: 10px;
    border: 1px solid var(--nmc-border-soft);
    background: linear-gradient(180deg, rgba(219, 234, 254, 0.82), rgba(239, 246, 255, 0.74));
  }
  #nodemc-overlay-panel .n-row {
    margin-bottom: 9px;
  }
  #nodemc-overlay-panel label {
    display: block;
    margin-bottom: 4px;
    color: #1e3a8a;
    line-height: 1.35;
  }
  #nodemc-overlay-panel input[type="text"],
  #nodemc-overlay-panel input[type="number"],
  #nodemc-overlay-panel select {
    width: 100%;
    box-sizing: border-box;
    border-radius: 9px;
    border: 1px solid rgba(59, 130, 246, 0.42);
    background: var(--nmc-bg-panel);
    color: var(--nmc-text-main);
    padding: 7px 9px;
    outline: none;
    transition: border-color .16s ease, box-shadow .16s ease, background-color .16s ease;
  }
  #nodemc-overlay-panel input[type="text"]:focus,
  #nodemc-overlay-panel input[type="number"]:focus,
  #nodemc-overlay-panel select:focus {
    border-color: rgba(96, 165, 250, 0.9);
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
    background: rgba(255, 255, 255, 1);
  }
  #nodemc-overlay-panel input::placeholder {
    color: rgba(148, 163, 184, 0.9);
  }
  #nodemc-overlay-panel .n-check {
    display: flex;
    gap: 7px;
    align-items: flex-start;
    margin-bottom: 7px;
    color: var(--nmc-text-main);
  }
  #nodemc-overlay-panel .n-check input[type="checkbox"] {
    margin-top: 1px;
    accent-color: var(--nmc-primary);
    transform: scale(1.05);
  }
  #nodemc-overlay-panel .n-btns {
    display: flex;
    gap: 8px;
    margin-top: 10px;
    flex-wrap: wrap;
  }
  #nodemc-overlay-panel button {
    border: 1px solid rgba(147,197,253,.48);
    background: linear-gradient(180deg, var(--nmc-primary), var(--nmc-primary-hover));
    color: #fff;
    border-radius: 9px;
    padding: 6px 10px;
    font-weight: 600;
    letter-spacing: .2px;
    cursor: pointer;
    transition: transform .12s ease, filter .16s ease, box-shadow .2s ease;
  }
  #nodemc-overlay-panel button:hover {
    transform: translateY(-1px);
    filter: brightness(1.05);
  }
  #nodemc-overlay-panel button:active {
    transform: translateY(0) scale(0.98);
  }
  #nodemc-overlay-panel .n-btn-primary {
    box-shadow: 0 8px 18px rgba(37, 99, 235, 0.28);
  }
  #nodemc-overlay-panel .n-btn-ghost {
    border: 1px solid rgba(59, 130, 246, 0.46);
    background: rgba(239, 246, 255, 0.95);
    color: #1e40af;
    box-shadow: none;
  }
  #nodemc-overlay-panel .n-btn-danger {
    border: 1px solid rgba(254, 202, 202, 0.5);
    background: linear-gradient(180deg, var(--nmc-danger), var(--nmc-danger-hover));
    color: #fff;
    box-shadow: 0 8px 18px rgba(185, 28, 28, 0.3);
  }
  #nodemc-overlay-panel .n-link-btn {
    border: 1px solid rgba(59, 130, 246, 0.46);
    background: rgba(239, 246, 255, 0.96);
    color: #1e40af;
    box-shadow: none;
  }
  #nodemc-overlay-panel .n-nav-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
    gap: 8px;
    padding: 2px 2px 0;
  }
  #nodemc-overlay-status {
    margin-top: 10px;
    color: var(--nmc-text-subtle);
    word-break: break-word;
    font-size: 11px;
    line-height: 1.45;
    padding: 8px 9px;
    border-radius: 8px;
    border: 1px dashed var(--nmc-border-soft);
    background: rgba(239, 246, 255, 0.85);
  }
  #nodemc-overlay-panel .n-subtitle {
    margin-top: 0;
    margin-bottom: 6px;
    font-weight: 700;
    color: #1e3a8a;
    letter-spacing: .25px;
  }
  @media (max-width: 430px) {
    #nodemc-overlay-panel {
      width: min(350px, calc(100vw - 12px));
      max-height: 78vh;
      padding: 10px;
      border-radius: 12px;
    }
    #nodemc-overlay-panel .n-btns button {
      flex: 1;
      min-width: 42%;
    }
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
