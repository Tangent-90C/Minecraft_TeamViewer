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
  #nodemc-overlay-panel .n-card {
    margin-bottom: 10px;
    padding: 10px;
    border-radius: 10px;
    border: 1px solid var(--nmc-border-soft);
    background: linear-gradient(180deg, rgba(219, 234, 254, 0.82), rgba(239, 246, 255, 0.74));
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px 10px;
    align-items: start;
  }
  #nodemc-overlay-panel .n-row {
    margin: 0;
  }

  /* allow explicit full-width rows (e.g. Admin WS URL) */
  #nodemc-overlay-panel .n-row.full-width {
    grid-column: 1 / -1;
  }
  /* subtitles should span full width */
  #nodemc-overlay-panel .n-subtitle {
    grid-column: 1 / -1;
    margin-top: 0;
    margin-bottom: 6px;
  }
  /* make nav, button groups and popups occupy full row so inputs pair only with inputs */
  #nodemc-overlay-panel .n-card .n-btns,
  #nodemc-overlay-panel .n-card .n-nav-row,
  #nodemc-overlay-panel .n-card .n-player-list-popup {
    grid-column: 1 / -1;
  }
  /* allow checks to be full width when needed via .full-width */
  #nodemc-overlay-panel .n-card .n-check.full-width {
    grid-column: 1 / -1;
  }
  /* ensure .n-row keeps label above control */
  #nodemc-overlay-panel .n-row {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  /* compact spacing for small inputs when side-by-side */
  #nodemc-overlay-panel .n-row input[type="number"],
  #nodemc-overlay-panel .n-row input[type="text"],
  #nodemc-overlay-panel .n-row select {
    padding: 6px 8px;
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
  #nodemc-overlay-panel .n-config-menu {
    margin-top: 8px;
    border: 1px solid var(--nmc-border-soft);
    border-radius: 10px;
    background: linear-gradient(180deg, rgba(219, 234, 254, 0.82), rgba(239, 246, 255, 0.74));
    padding: 8px;
  }
  #nodemc-overlay-panel .n-config-menu-items {
    margin-top: 0;
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
  #nodemc-overlay-panel button:disabled {
    opacity: 0.55;
    cursor: not-allowed;
    transform: none;
    filter: none;
    box-shadow: none;
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
  #nodemc-overlay-panel .n-player-list-popup {
    margin-top: 10px;
    border: 1px solid var(--nmc-border-soft);
    border-radius: 10px;
    background: linear-gradient(180deg, rgba(219, 234, 254, 0.82), rgba(239, 246, 255, 0.74));
    overflow: hidden;
  }
  #nodemc-overlay-panel .n-player-list-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 8px 10px;
    border-bottom: 1px solid var(--nmc-border-soft);
    background: rgba(191, 219, 254, 0.55);
  }
  #nodemc-overlay-panel .n-player-list-title {
    font-weight: 700;
    color: #1e3a8a;
  }
  #nodemc-overlay-panel .n-player-list-close {
    border: 1px solid rgba(59, 130, 246, 0.46);
    background: rgba(239, 246, 255, 0.96);
    color: #1e40af;
    box-shadow: none;
    padding: 4px 9px;
  }
  #nodemc-overlay-panel .n-player-list-table-wrap {
    max-height: 260px;
    overflow: auto;
  }
  #nodemc-overlay-panel .n-help-content {
    padding: 10px;
    color: var(--nmc-text-main);
    line-height: 1.5;
  }
  #nodemc-overlay-panel .n-help-list {
    margin: 0;
    padding-left: 18px;
    display: grid;
    gap: 6px;
  }
  #nodemc-overlay-panel .n-help-list li {
    color: var(--nmc-text-main);
  }
  #nodemc-overlay-panel .n-help-tip {
    margin-top: 10px;
    padding: 8px 9px;
    border-radius: 8px;
    border: 1px dashed rgba(59, 130, 246, 0.45);
    background: rgba(219, 234, 254, 0.65);
    color: #1e40af;
    font-size: 11px;
  }
  #nodemc-overlay-panel .n-player-list-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 11px;
  }
  #nodemc-overlay-panel .n-player-list-table th,
  #nodemc-overlay-panel .n-player-list-table td {
    padding: 6px 8px;
    border-bottom: 1px solid rgba(59, 130, 246, 0.2);
    text-align: left;
    color: var(--nmc-text-main);
    white-space: nowrap;
  }
  #nodemc-overlay-panel .n-player-list-table th {
    position: sticky;
    top: 0;
    z-index: 1;
    background: rgba(219, 234, 254, 0.95);
    color: #1e3a8a;
    font-weight: 700;
  }
  #nodemc-overlay-panel .n-player-list-row {
    cursor: pointer;
    transition: background-color .15s ease;
  }
  #nodemc-overlay-panel .n-player-list-row:hover {
    background: rgba(191, 219, 254, 0.45);
  }
  #nodemc-overlay-panel .n-player-list-empty {
    text-align: center;
    color: var(--nmc-text-muted);
  }
  #nodemc-overlay-panel .n-team-chip,
  #nodemc-overlay-panel .n-town-chip {
    display: inline-flex;
    align-items: center;
    max-width: 120px;
    padding: 2px 7px;
    border: 1px solid transparent;
    border-radius: 999px;
    font-weight: 700;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
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
  #nodemc-overlay-panel .n-dirty-hint {
    margin-top: 6px;
    color: #1e40af;
    font-size: 11px;
    line-height: 1.4;
    background: rgba(219, 234, 254, 0.65);
    border: 1px dashed rgba(59, 130, 246, 0.45);
    border-radius: 8px;
    padding: 6px 8px;
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
    background: rgba(15, 23, 42, 0.55);
    color: #dbeafe;
    border: 1px solid rgba(147, 197, 253, 0.45);
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
  .nodemc-player-anchor .n-icon.is-reporter-star {
    font-weight: 900;
    text-align: center;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    letter-spacing: -0.4px;
    -webkit-text-stroke: 1.8px rgba(255, 255, 255, 0.98);
    paint-order: stroke fill;
    text-shadow:
      0 0 1px rgba(15, 23, 42, 0.85),
      0 0 5px rgba(15, 23, 42, 0.55);
  }
  .nodemc-player-anchor .n-label {
    position: absolute;
    top: 0;
    transform: translateY(-50%);
    background: rgba(15, 23, 42, 0.55);
    color: #dbeafe;
    border: 1px solid rgba(147, 197, 253, 0.45);
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
