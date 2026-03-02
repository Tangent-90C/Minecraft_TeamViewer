import {
  AUTO_MARK_SYNC_INTERVAL_MS,
  AUTO_MARK_SYNC_MAX_PER_TICK,
} from './constants';
import { normalizeColor, normalizeTeam } from './overlayUtils';

type Candidate = {
  action: 'set' | 'clear';
  playerId: string;
  team?: string;
  color?: string;
};

export function createAutoMarkSyncManager(deps: {
  isWsOpen: () => boolean;
  sendAdminCommand: (message: Record<string, unknown>) => boolean;
  getConfiguredTeamColor: (team: string) => string;
}) {
  let lastAutoMarkSyncAt = 0;
  const autoMarkSyncCache = new Map<string, string>();

  function maybeSyncAutoDetectedMarks(candidates: Candidate[]) {
    if (!deps.isWsOpen()) return;
    if (!Array.isArray(candidates) || candidates.length === 0) return;

    const now = Date.now();
    if (now - lastAutoMarkSyncAt < AUTO_MARK_SYNC_INTERVAL_MS) {
      return;
    }

    let sent = 0;
    for (const item of candidates) {
      if (!item || typeof item !== 'object') continue;
      const playerId = String(item.playerId || '').trim();
      if (!playerId) continue;

      const action = String(item.action || '').trim().toLowerCase();
      if (action !== 'set' && action !== 'clear') continue;

      let cacheKey = '__clear__';
      let ok = false;

      if (action === 'set') {
        const team = normalizeTeam(item.team);
        if (team !== 'friendly' && team !== 'enemy') continue;

        const color = normalizeColor(item.color, deps.getConfiguredTeamColor(team));
        cacheKey = `set|${team}|${color}`;

        if (autoMarkSyncCache.get(playerId) === cacheKey) {
          continue;
        }

        ok = deps.sendAdminCommand({
          type: 'command_player_mark_set',
          playerId,
          team,
          color,
          source: 'auto',
        });
      } else {
        if (autoMarkSyncCache.get(playerId) === cacheKey) {
          continue;
        }

        ok = deps.sendAdminCommand({
          type: 'command_player_mark_clear',
          playerId,
        });
      }

      if (!ok) {
        continue;
      }

      autoMarkSyncCache.set(playerId, cacheKey);
      sent += 1;
      if (sent >= AUTO_MARK_SYNC_MAX_PER_TICK) {
        break;
      }
    }

    if (sent > 0) {
      lastAutoMarkSyncAt = now;
    }
  }

  function clearPlayerCache(playerId: string) {
    autoMarkSyncCache.delete(playerId);
  }

  function reset() {
    autoMarkSyncCache.clear();
    lastAutoMarkSyncAt = 0;
  }

  return {
    maybeSyncAutoDetectedMarks,
    clearPlayerCache,
    reset,
  };
}
