// Generates snippets/icon.html from _src/icons.json by extracting SVG bodies
// from @iconify/json Material Symbols. Output is a FreeMarker macro with a
// single [#switch name] block; usage: [@icon 'search'/].
//
// Pages get the macro transitively: layout/theme.html includes
// snippets/icon.html at the top, and [#include] runs in the caller's
// namespace, so every page (and the panel snippets rendered by [@region])
// sees [@icon].

import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const themeRoot = resolve(__dirname, '..');
const config = JSON.parse(readFileSync(resolve(__dirname, 'icons.json'), 'utf8'));

const iconSetPath = resolve(themeRoot, 'node_modules/@iconify/json/json', `${config.iconSet}.json`);
const iconSet = JSON.parse(readFileSync(iconSetPath, 'utf8'));

const width = iconSet.width ?? 24;
const height = iconSet.height ?? 24;

const branches = config.icons.map((name) => {
  const icon = iconSet.icons[name];
  if (!icon) {
    throw new Error(`Icon "${name}" not found in ${config.iconSet}`);
  }
  const w = icon.width ?? width;
  const h = icon.height ?? height;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" fill="currentColor" aria-hidden="true">${icon.body}</svg>`;
  return `  [#case '${name}']${svg}[#break]`;
}).join('\n');

const out = `[#--
  GENERATED — do not edit by hand. Source: _src/icons.json
  Regenerate with: npm run build:icons
  Icons: Material Symbols by Google (https://github.com/google/material-design-icons), Apache License 2.0,
  taken unmodified from @iconify/json. See THIRD-PARTY-NOTICES.md in the theme root.
  Usage: [@icon 'search'/]
--]
[#macro icon name]
[#switch name]
${branches}
  [#default][#-- icon not in bundle --]
[/#switch]
[/#macro]
`;

writeFileSync(resolve(themeRoot, 'snippets/icon.html'), out);
console.log(`Wrote snippets/icon.html (${config.icons.length} icons from ${config.iconSet})`);
