# Recorded marketplace responses (C3 contract fixtures)

Real HTTP response bodies recorded against the `ujcms-marketplace` dev server with
`X-UJCMS-Version` set to the running CMS version. Two capture sessions:

- **2026-07-18** — contract `marketplace-api.yaml` **1.0.0**, server commit `b7397ef`, `X-UJCMS-Version: 11.0.2`:
  the catalog / download / OAuth / account rows below (C3). Their wire shapes have not changed since
  (every later contract bump was additive), so they were left as recorded — a test asserting a field that
  postdates the capture (e.g. `Account.phone`, 1.6.0) asserts that the missing field maps to `null`.
- **2026-11-09** — contract **1.16.0**, server commit `e460344` (M14), `X-UJCMS-Version: 13.2.2`: the
  `instance-orders` rows (C4b) — the console's in-place purchase surface, recorded with a phone-only
  throwaway account (buyer id 18, instance 「C4b Recorder」) against the published paid item
  `m8-live-1784344936` (¥99); the `already_owned` problem and the paid order with the account that
  bought the same item through the console during the C4b live run.

`MarketplaceClientRecordedContractTest` replays them through MockRestServiceServer and asserts
`MarketplaceClient` parses/maps each one — so client DTO drift against the *served* wire shapes (not just
the hand-read yaml) breaks the build.

**Not vendored, not manifest-pinned.** Unlike everything else under `src/test/resources/marketplace/`
(see D-C1-2), these are recorded here, in this repo — `sync-marketplace-contracts.mjs` neither writes nor
deletes this directory. Re-record against a live dev server when the contract bumps a version that
changes the instance surface (recipe for the order rows: approve a device code with all four scopes,
`POST /api/v1/orders` twice, `GET`, `pay` with `mockpay`, `sync`, `cancel`, then `pay` again for the 422;
a second device code with the three M4 scopes for the 403).

Redactions (D-C3 hygiene): `oauth-device-code.json` `device_code` and `oauth-token-success.json`
`access_token`/`refresh_token` are replaced with `REDACTED-*` placeholders — they were live single-use
dev credentials at record time; shape and remaining fields are verbatim. The order rows carry no
credential (an `orderNo` is not one) and are verbatim.

**Hand-written, not recorded:** `order-pay-wechatpay-qrcode.json` — a `PaymentInitiation` of kind
`qrcode` cannot be captured in dev (no WeChat merchant account exists; `pay` with `wechatpay` answers 503
`payment_unavailable`), so it is written from the 1.16.0 `PaymentInitiation` schema (`qrContent`
`weixin://…`, `expiresAt` ≈ 2 h). Replace it with a capture once a merchant exists (the 1-cent drill in
the marketplace's `payments-go-live.md`).

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
| payment-providers.json | GET /api/v1/payment-providers (Bearer, 4 scopes) — 1.16.0 | 200 |
| orders-create-201.json | POST /api/v1/orders {item} — a new order | 201 |
| orders-create-200-folded.json | POST /api/v1/orders {item} again — the same open order (D-M14-3) | 200 |
| order-get.json | GET /api/v1/orders/{orderNo} | 200 |
| order-pay-mockpay.json | POST /api/v1/orders/{orderNo}/pay {mockpay} — kind `redirect` | 200 |
| order-pay-wechatpay-qrcode.json | POST /api/v1/orders/{orderNo}/pay {wechatpay} — kind `qrcode` (**hand-written**, see above) | 200 |
| order-sync.json | POST /api/v1/orders/{orderNo}/sync {mockpay} before paying — still `created` | 200 |
| order-cancel.json | POST /api/v1/orders/{orderNo}/cancel — `cancelled` | 200 |
| order-get-paid.json | GET /api/v1/orders/{orderNo} after MockPay settled it — `paid`, with its 发起站点 | 200 |
| problem-order-invariant.json | POST …/pay on the cancelled order (code `invariant_violation`) | 422 |
| problem-insufficient-scope.json | POST /api/v1/orders with a token approved for the three M4 scopes only (`WWW-Authenticate: Bearer error="insufficient_scope", …`) | 403 |
| problem-already-owned.json | POST /api/v1/orders {item} for an item the account already owns (code `already_owned`) | 409 |
