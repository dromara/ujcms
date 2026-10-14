#!/usr/bin/env node
/**
 * Preview-bridge build (visual-builder §3) — one source, one delivery target:
 * esbuild-bundles `src/iife.ts` (importing `src/bridge-core.ts`) into the IIFE committed at
 * `ujcms-cms/src/main/resources/static/_ujcms/preview-bridge.js`, with a banner carrying the
 * SHA-256 of `bridge-core.ts` (the committed IIFE must be rebuilt whenever the core changes).
 *
 * The former SSR byte-copies (React/Vue) were retired by ssr-reduction P2/P3 — the template
 * track is the sole consumer, and the backend that renders the page also serves the bridge,
 * so version skew is structurally impossible.
 *
 * Run from this directory: `pnpm install && pnpm build`.
 */
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildSync } from 'esbuild';

const here = dirname(fileURLToPath(import.meta.url));
// here = ujcms-cms/src/main/frontend/preview-bridge → module root is 4 levels up.
const cmsModule = resolve(here, '..', '..', '..', '..');

const corePath = join(here, 'src', 'bridge-core.ts');
const coreSource = readFileSync(corePath, 'utf8');
const coreSha = createHash('sha256').update(coreSource).digest('hex');

// IIFE for the template track (served by the rendering backend — can never version-skew).
const iifePath = join(cmsModule, 'src', 'main', 'resources', 'static', '_ujcms', 'preview-bridge.js');
const banner = `/*! ujcms-preview-bridge v2 | built from bridge-core.ts sha256:${coreSha} | DO NOT EDIT — rebuild via ujcms-cms/src/main/frontend/preview-bridge/build.mjs */`;
const result = buildSync({
  entryPoints: [join(here, 'src', 'iife.ts')],
  bundle: true,
  format: 'iife',
  target: 'es2020',
  minify: true,
  write: false,
  banner: { js: banner },
});
mkdirSync(dirname(iifePath), { recursive: true });
writeFileSync(iifePath, result.outputFiles[0].text);
console.log(`[preview-bridge] IIFE -> ${iifePath} (${result.outputFiles[0].text.length} bytes, core sha256 ${coreSha.slice(0, 12)}…)`);
