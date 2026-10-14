# Recorded marketplace responses (C3 contract fixtures)

Real HTTP response bodies recorded 2026-07-18 against the `ujcms-marketplace` dev server
(contract `marketplace-api.yaml` **1.0.0**, server commit `b7397ef`) with
`X-UJCMS-Version: 11.0.2`. `MarketplaceClientRecordedContractTest` replays them through
MockRestServiceServer and asserts `MarketplaceClient` parses/maps each one — so client DTO
drift against the *served* wire shapes (not just the hand-read yaml) breaks the build.

**Not vendored, not manifest-pinned.** Unlike everything else under
`src/test/resources/marketplace/` (see D-C1-2), these are recorded here, in this repo —
`sync-marketplace-contracts.mjs` neither writes nor deletes this directory. Re-record against
a live dev server when the contract bumps a version that changes the instance surface.

Redactions (noted per D-C3 hygiene): `oauth-device-code.json` `device_code` and
`oauth-token-success.json` `access_token`/`refresh_token` are replaced with
`REDACTED-*` placeholders — they were live single-use dev credentials at record time; shape
and remaining fields are verbatim.

| File | Endpoint | Status |
|---|---|---|
| catalog-items-page.json | GET /api/v1/catalog/items?page=1&pageSize=100 | 200 |
| catalog-item-detail.json | GET /api/v1/catalog/items/edu-campus | 200 |
| catalog-item-versions.json | GET /api/v1/catalog/items/edu-campus/versions | 200 |
| catalog-update-check.json | POST /api/v1/catalog/update-check | 200 |
| download-grant-free.json | POST /api/v1/downloads (anonymous, free item) | 201 |
| problem-item-not-found.json | GET /api/v1/catalog/items/no-such-item | 404 |
| problem-not-entitled.json | POST /api/v1/downloads (anonymous, paid item) | 403 |
| problem-rate-limited.json | catalog read past the per-IP bucket (`Retry-After: 1`) | 429 |
| problem-url-expired.json | GET signed package URL after its 5 min TTL | 410 |
| problem-invalid-signature.json | GET signed package URL with tampered `sig` | 403 |
| oauth-device-code.json | POST /api/v1/oauth/device/code | 200 |
| oauth-token-pending.json | POST /api/v1/oauth/token before approval | 400 |
| oauth-token-success.json | POST /api/v1/oauth/token after approval | 200 |
| me.json | GET /api/v1/me (Bearer) | 200 |
| entitlements.json | GET /api/v1/me/entitlements?page=1&pageSize=100 (Bearer) | 200 |
