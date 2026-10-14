// build-vendor.mjs — copy the runtime subset of third-party libraries from
// node_modules into assets/vendor/<lib>/ so theme templates can load them via
// {{ '...' | asset_url }}. Run via `npm run build:vendor`.
//
// assets/vendor/ is .gitignored (see ujcms-starter/.gitignore). Operators
// regenerate vendor files locally with `npm install && npm run build`.

import { mkdirSync, cpSync, rmSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';

// Each entry: { srcRoot, destRoot, targets: [relPath, ...], licenses: [[src, name], ...] }
//   srcRoot  — root of the package inside node_modules
//   destRoot — root inside assets/vendor/
//   targets  — files (or directories) to copy, paths relative to both roots
//   licenses — licence files that travel with the copied code: [path from the
//              theme root, file name under destRoot]. Unlike a missing target,
//              a missing licence file fails the build: a vendor directory must
//              never be produced without the notices of the code inside it.
//              (cmaps/ and standard_fonts/ carry their own licence files.)
const bundles = [
  {
    // pdfjs-dist — wenku article viewer (templates/article_wenku.liquid)
    srcRoot: 'node_modules/pdfjs-dist',
    destRoot: 'assets/vendor/pdfjs-dist',
    targets: [
      'build/pdf.min.mjs',
      'build/pdf.worker.min.mjs',
      'build/pdf.sandbox.min.mjs',
      'web/pdf_viewer.mjs',
      'web/pdf_viewer.css',
      'web/images',         // SVG icons referenced by pdf_viewer.css
      'cmaps',              // CJK character maps (required for Chinese PDFs)
      'standard_fonts',     // PDF standard-font fallbacks
    ],
    licenses: [
      ['node_modules/pdfjs-dist/LICENSE', 'LICENSE'],
    ],
  },
  {
    // sm-crypto — SM2 password encryption for login / register / settings.
    // The backend (PasswordController, SettingsController, RegisterController)
    // calls Secures.sm2Decrypt and rejects any other format, so the password
    // MUST be SM2-encrypted client-side before submit.
    srcRoot: 'node_modules/sm-crypto',
    destRoot: 'assets/vendor/sm-crypto',
    targets: [
      'dist/sm2.js',        // UMD bundle exposing window.sm2
    ],
    licenses: [
      ['node_modules/sm-crypto/LICENCE_MIT', 'LICENCE_MIT'],
      ['node_modules/jsbn/LICENSE', 'jsbn-LICENSE'],   // dist/sm2.js bundles jsbn
    ],
  },
  {
    // cropperjs — avatar crop UI (templates/mem_settings_avatar.liquid)
    srcRoot: 'node_modules/cropperjs',
    destRoot: 'assets/vendor/cropperjs',
    targets: [
      'dist/cropper.min.js',
      'dist/cropper.min.css',
    ],
    licenses: [
      ['node_modules/cropperjs/LICENSE', 'LICENSE'],
    ],
  },
];

for (const bundle of bundles) {
  if (!existsSync(bundle.srcRoot)) {
    console.error(`[build:vendor] ${bundle.srcRoot} not found — run \`npm install\` first.`);
    process.exit(1);
  }

  // Checked before anything is deleted, so a failure leaves the old copy in place.
  const missingLicenses = bundle.licenses.map(([src]) => src).filter((src) => !existsSync(src));
  if (missingLicenses.length > 0) {
    console.error(`[build:vendor] licence file not found: ${missingLicenses.join(', ')} — ${bundle.destRoot} is not built without it.`);
    process.exit(1);
  }

  try { rmSync(bundle.destRoot, { recursive: true, force: true }); } catch { /* ignore */ }
  mkdirSync(bundle.destRoot, { recursive: true });

  for (const t of bundle.targets) {
    const srcPath = join(bundle.srcRoot, t);
    const destPath = join(bundle.destRoot, t);
    if (!existsSync(srcPath)) {
      console.warn(`[build:vendor] missing in node_modules: ${srcPath}`);
      continue;
    }
    mkdirSync(dirname(destPath), { recursive: true });
    cpSync(srcPath, destPath, { recursive: true });
  }

  for (const [src, name] of bundle.licenses) {
    cpSync(src, join(bundle.destRoot, name));
  }

  console.log(`[build:vendor] ${bundle.srcRoot} → ${bundle.destRoot}`);
}
