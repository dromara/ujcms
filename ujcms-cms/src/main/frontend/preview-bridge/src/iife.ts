/**
 * IIFE entry for the template track (FreeMarker / Liquid). Bundled by `build.mjs` into
 * `ujcms-cms/src/main/resources/static/_ujcms/preview-bridge.js` and injected into preview
 * responses by `PreviewBridgeInjectionFilter`. No framework soft refresh exists on this track,
 * so `refresh` is omitted — the core falls back to a full reload with scroll restore.
 */
import { initPreviewBridge } from './bridge-core';

const boot = (): void => {
  initPreviewBridge({ track: 'template' });
};

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', boot, { once: true });
} else {
  boot();
}
