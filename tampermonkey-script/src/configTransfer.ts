import {
  LOCAL_PROGRAM_VERSION,
  STORAGE_KEY,
} from './constants';
import { sanitizeConfig } from './overlayUtils';

const EXPORT_KIND = 'nodemc_overlay_config_export';
const EXPORT_SCHEMA_VERSION = 1;

type ExportPayload = {
  kind: string;
  schemaVersion: number;
  compatVersion: string;
  programVersion: string;
  storageKey: string;
  exportedAt: string;
  config: Record<string, unknown>;
};

type ParseResult = {
  ok: boolean;
  config?: Record<string, unknown>;
  error?: string;
};

function getCurrentCompatVersion() {
  const text = String(LOCAL_PROGRAM_VERSION || '').trim();
  if (!text) return 'unknown';
  const match = text.match(/(\d+)\.(\d+)/);
  if (!match) return text;
  return `${match[1]}.${match[2]}`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

export function createConfigExportPayload(config: Record<string, unknown>): ExportPayload {
  return {
    kind: EXPORT_KIND,
    schemaVersion: EXPORT_SCHEMA_VERSION,
    compatVersion: getCurrentCompatVersion(),
    programVersion: LOCAL_PROGRAM_VERSION,
    storageKey: STORAGE_KEY,
    exportedAt: new Date().toISOString(),
    config: sanitizeConfig(config),
  };
}

export function parseImportedConfigText(rawText: string): ParseResult {
  const text = String(rawText || '').trim();
  if (!text) {
    return { ok: false, error: '导入内容为空' };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch (_) {
    return { ok: false, error: '导入失败：文件不是合法 JSON' };
  }

  if (!isRecord(parsed)) {
    return { ok: false, error: '导入失败：配置结构无效' };
  }

  const compatVersion = getCurrentCompatVersion();

  if (parsed.kind === EXPORT_KIND) {
    const payload = parsed as Record<string, unknown>;
    const schemaVersion = Number(payload.schemaVersion);
    if (!Number.isFinite(schemaVersion) || schemaVersion > EXPORT_SCHEMA_VERSION) {
      return { ok: false, error: '导入失败：配置导出格式版本过新，请先更新脚本版本' };
    }

    const payloadCompatVersion = String(payload.compatVersion || '').trim();
    if (!payloadCompatVersion || payloadCompatVersion !== compatVersion) {
      return {
        ok: false,
        error: `导入失败：配置版本不兼容（当前 ${compatVersion}，文件 ${payloadCompatVersion || 'unknown'}）`,
      };
    }

    const payloadConfig = payload.config;
    if (!isRecord(payloadConfig)) {
      return { ok: false, error: '导入失败：配置内容缺失或损坏' };
    }

    return {
      ok: true,
      config: sanitizeConfig(payloadConfig),
    };
  }

  return {
    ok: true,
    config: sanitizeConfig(parsed),
  };
}

export function buildExportFileName() {
  const compatVersion = getCurrentCompatVersion().replace(/[^0-9a-zA-Z._-]/g, '_');
  const stamp = new Date().toISOString().replace(/[:]/g, '-');
  return `nodemc-overlay-config-v${compatVersion}-${stamp}.json`;
}

export function getConfigCompatVersion() {
  return getCurrentCompatVersion();
}
