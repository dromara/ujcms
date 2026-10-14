/*
 * UJCMS 访问统计脚本（所有主题共用的唯一实现）。
 *
 * 设计要点：
 *  - 状态存于 localStorage（而非 Cookie）：这些数据仅供浏览器端读取，无需随请求
 *    回传，因此不应占用 Cookie，也无 HttpOnly 之说。localStorage 不参与跨站发送，
 *    天然规避 SameSite / CSRF 顾虑，且不会出现在服务器访问日志中。
 *  - CSRF token 在脚本初始化时一次性预取并缓存：静态化页面中 <meta> 内的 token 为
 *    生成时刻的陈旧值，不可靠；运行时请求 /env/csrf 对静态页与动态页一致有效，且能
 *    避免在页面卸载（visibilitychange:hidden）阶段再发起异步请求导致信标丢失。
 *  - 隐私同意：默认 off（不提供同意功能，与多数站点习惯一致）。通过脚本标签的
 *    data-consent="optional|required" 一键启用，启用后弹出参考同意条并尊重 GPC/DNT。
 *  - 服务器契约保持不变：请求 URL 与 count=1/count=0 语义与历史完全一致。
 */
;(function () {
    'use strict';

    // ── 配置：优先读取 <script data-*>，兼容历史的 window 全局变量 ──────────────
    var me = document.currentScript ||
        document.querySelector('script[src*="visit.js"][data-site-id]') ||
        document.querySelector('script[src*="visit.js"]');
    var API = (me && me.dataset.api) || window.visitApi || '/api';
    var SITE_ID = (me && me.dataset.siteId) || window.visitSiteId;
    if (!SITE_ID) {
        return;
    }
    // 隐私同意策略：off（默认，不统计前不弹条）| optional（默认开启，可拒绝）| required（同意后才统计）
    var CONSENT_POLICY = ((me && me.dataset.consent) || 'off').toLowerCase();

    // 自动化/爬虫（带 webdriver 标记）不纳入统计，减少噪声
    if (navigator.webdriver) {
        return;
    }

    // ── 存储键（沿用历史名称，仅迁移至 localStorage）──────────────────────────
    var KEY_UV = 'ujcms-visit-unique-visitor';
    var KEY_SI = 'ujcms-visit-session-id';
    var KEY_ENTRY = 'ujcms-visit-entry-url';
    var KEY_NEW = 'ujcms-visit-new-visitor';
    var KEY_START = 'ujcms-visit-start';
    var KEY_DURATION = 'ujcms-visit-duration';
    var KEY_OPENS = 'ujcms-visit-opens';
    var KEY_HIDDEN = 'ujcms-visit-hidden';
    var KEY_CONSENT = 'ujcms-visit-consent';
    var SESSION_KEYS = [KEY_UV, KEY_SI, KEY_ENTRY, KEY_NEW, KEY_START, KEY_DURATION, KEY_OPENS, KEY_HIDDEN];

    // ── 存储层：localStorage，不可用（隐私模式/禁用/超额）时退化为内存 ──────────
    var memStore = {};
    var rawLs = (function () {
        try {
            var k = '__ujcms_visit_test__';
            window.localStorage.setItem(k, '1');
            window.localStorage.removeItem(k);
            return window.localStorage;
        } catch (e) {
            return null;
        }
    })();
    // persistent：是否允许持久化（长期 uv、写 localStorage）。由同意状态决定；
    // 非持久化时所有读写走内存，页面卸载即丢，等同匿名统计。
    var persistent = true;

    function backend() {
        return (persistent && rawLs) ? rawLs : null;
    }
    function readItem(key) {
        var be = backend();
        if (be) {
            try { return be.getItem(key); } catch (e) { /* ignore */ }
        }
        return Object.prototype.hasOwnProperty.call(memStore, key) ? memStore[key] : null;
    }
    function writeItem(key, value) {
        var be = backend();
        if (be) {
            try { be.setItem(key, value); return; } catch (e) { /* 降级至内存 */ }
        }
        memStore[key] = value;
    }
    function removeItem(key) {
        if (rawLs) {
            try { rawLs.removeItem(key); } catch (e) { /* ignore */ }
        }
        delete memStore[key];
    }
    // 同意标记属于“严格必要”，始终持久化（若 localStorage 可用），不受 persistent 限制。
    function readConsent() {
        if (rawLs) {
            try { return rawLs.getItem(KEY_CONSENT); } catch (e) { /* ignore */ }
        }
        return memStore[KEY_CONSENT] || null;
    }
    function writeConsent(value) {
        if (rawLs) {
            try { rawLs.setItem(KEY_CONSENT, value); return; } catch (e) { /* 降级至内存 */ }
        }
        memStore[KEY_CONSENT] = value;
    }

    // ── 隐私信号：GPC / DNT 视为隐含拒绝 ─────────────────────────────────────
    function privacySignal() {
        return navigator.globalPrivacyControl === true ||
            navigator.doNotTrack === '1' || window.doNotTrack === '1' ||
            navigator.msDoNotTrack === '1';
    }

    // enabled：是否发送统计；persistent：是否持久化标识。依据策略与同意状态计算。
    var enabled = true;
    function evaluateConsent() {
        var c = readConsent(); // 'granted' | 'denied' | null
        if (CONSENT_POLICY === 'optional') {
            // 选择退出：默认统计，拒绝或检测到隐私信号则转为匿名（不持久化）
            enabled = true;
            persistent = !(c === 'denied' || privacySignal());
        } else if (CONSENT_POLICY === 'required') {
            // 选择加入：同意前不进行任何统计
            persistent = (c === 'granted');
            enabled = persistent;
        } else {
            // off：维持历史行为，完整统计
            enabled = true;
            persistent = true;
        }
    }

    // ── 随机标识：优先 crypto，保证落在安全整数范围内的正整数 ────────────────
    function randomId() {
        try {
            var buf = new Uint32Array(2);
            crypto.getRandomValues(buf);
            // (21 位) * 2^32 + (32 位) ≤ 2^53 - 1
            return String((buf[0] % 0x200000) * 0x100000000 + buf[1]);
        } catch (e) {
            return String(Math.floor(Math.random() * Number.MAX_SAFE_INTEGER));
        }
    }

    // ── 访客 / 会话状态 ──────────────────────────────────────────────────────
    function getUv() {
        var uv = readItem(KEY_UV);
        if (!uv) {
            uv = randomId();
            writeItem(KEY_UV, uv);
            writeItem(KEY_NEW, 'true');
        }
        return uv;
    }
    function getNewVisitor() {
        return readItem(KEY_NEW) === 'true';
    }
    function getSi() {
        var si = readItem(KEY_SI);
        if (!si) {
            si = randomId();
            writeItem(KEY_SI, si);
        }
        return si;
    }
    function getEntryUrl() {
        var entry = readItem(KEY_ENTRY);
        if (!entry) {
            entry = document.location.href;
            writeItem(KEY_ENTRY, entry);
        }
        return entry;
    }
    function getStart() {
        var v = readItem(KEY_START);
        return v ? parseInt(v, 10) : undefined;
    }
    function setStart(t) {
        if (t != null) { writeItem(KEY_START, String(t)); } else { removeItem(KEY_START); }
    }
    function getHidden() {
        var v = readItem(KEY_HIDDEN);
        return v ? parseInt(v, 10) : undefined;
    }
    function setHidden(t) {
        if (t != null) { writeItem(KEY_HIDDEN, String(t)); } else { removeItem(KEY_HIDDEN); }
    }
    function getDuration() {
        var v = readItem(KEY_DURATION);
        return v ? parseInt(v, 10) : 0;
    }
    function setDuration(d) {
        writeItem(KEY_DURATION, String(d));
    }
    // 超过 2 小时无任何打开/可见动作，视为新一次访问会话
    function resetSession() {
        writeItem(KEY_NEW, 'false');
        removeItem(KEY_SI);
        removeItem(KEY_START);
        removeItem(KEY_DURATION);
    }
    // 跨标签页共享的“可见标签计数”：任一标签可见即计时
    function plusOpens() {
        var hidden = getHidden();
        if (hidden != null && Date.now() - hidden > 2 * 60 * 60 * 1000) {
            resetSession();
        }
        setHidden(null);
        var opens = parseInt(readItem(KEY_OPENS) || '0', 10) + 1;
        writeItem(KEY_OPENS, String(opens));
        if (opens > 0 && getStart() == null) {
            setStart(Date.now());
        }
        return opens;
    }
    function minusOpens() {
        var opens = parseInt(readItem(KEY_OPENS) || '0', 10) - 1;
        if (opens < 0) { opens = 0; }
        writeItem(KEY_OPENS, String(opens));
        var start = getStart();
        if (opens <= 0 && start != null) {
            setDuration(getDuration() + Math.floor((Date.now() - start) / 1000));
            setStart(null);
            setHidden(Date.now());
        }
        return opens;
    }
    function currentDuration() {
        var start = getStart();
        if (start != null) {
            return getDuration() + Math.floor((Date.now() - start) / 1000);
        }
        return getDuration();
    }
    // 请求 URL 格式与服务器契约保持完全一致
    function buildUrl(count) {
        var newVisitor = count >= 1 ? String(getNewVisitor()) : 'false';
        return API + '/visit/' + SITE_ID +
            '?url=' + encodeURIComponent(document.location.href) +
            '&entryUrl=' + encodeURIComponent(getEntryUrl()) +
            '&referrer=' + encodeURIComponent(document.referrer) +
            '&si=' + getSi() +
            '&uv=' + getUv() +
            '&newVisitor=' + newVisitor +
            '&duration=' + currentDuration() +
            '&count=' + count;
    }

    // ── CSRF：初始化时预取一次并缓存，卸载阶段同步取用 ────────────────────────
    var csrf = null;
    var csrfPromise = null;
    function loadCsrf() {
        if (csrf) { return Promise.resolve(csrf); }
        if (!csrfPromise) {
            csrfPromise = fetch(API + '/env/csrf', { credentials: 'include' })
                .then(function (res) {
                    if (!res.ok) { throw new Error('csrf ' + res.status); }
                    return res.json();
                })
                .then(function (data) {
                    if (data && data.parameterName && data.token) {
                        csrf = data;
                        return data;
                    }
                    throw new Error('csrf payload');
                });
        }
        return csrfPromise;
    }
    function sendPageview() {
        loadCsrf().then(function (token) {
            var form = new FormData();
            form.append(token.parameterName, token.token);
            return fetch(buildUrl(1), { method: 'POST', body: form, credentials: 'include' });
        }).catch(function () { /* 统计失败静默 */ });
    }
    function sendEnd() {
        // 卸载阶段不能等待异步：使用已缓存 token 同步发送信标
        if (!csrf || !navigator.sendBeacon) { return; }
        var form = new FormData();
        form.append(csrf.parameterName, csrf.token);
        try { navigator.sendBeacon(buildUrl(0), form); } catch (e) { /* ignore */ }
    }

    // ── 统计生命周期 ─────────────────────────────────────────────────────────
    var started = false;
    function startTracking() {
        if (started || !enabled) { return; }
        started = true;
        loadCsrf(); // 提前获取 token，规避卸载竞态
        document.addEventListener('visibilitychange', function () {
            if (document.visibilityState === 'hidden') {
                minusOpens();
                sendEnd();
            } else if (document.visibilityState === 'visible') {
                plusOpens();
            }
        });
        if (document.visibilityState === 'visible') {
            plusOpens();
        }
        sendPageview();
    }

    // ── 隐私同意 API（供主题自定义同意条调用）────────────────────────────────
    function applyConsent(decision) {
        writeConsent(decision);
        if (decision === 'denied') {
            SESSION_KEYS.forEach(removeItem); // 拒绝即清除既有持久标识
        }
        evaluateConsent();
        removeBanner();
        if (enabled) { startTracking(); }
    }
    window.ujcmsVisit = {
        getConsent: function () { return readConsent(); },
        grantConsent: function () { applyConsent('granted'); },
        denyConsent: function () { applyConsent('denied'); }
    };

    // ── 参考同意条：仅在启用同意且尚未决策、且页面未自带同意条时注入 ──────────
    // 样式通过 JS 逐项设置（非内联 style 属性），避免严格 CSP 拦截。
    var bannerEl = null;
    function removeBanner() {
        if (bannerEl && bannerEl.parentNode) { bannerEl.parentNode.removeChild(bannerEl); }
        bannerEl = null;
    }
    function txt(name, fallback) {
        return (me && me.dataset[name]) || fallback;
    }
    function makeButton(label, bg, fg, onClick) {
        var b = document.createElement('button');
        b.type = 'button';
        b.textContent = label;
        var s = b.style;
        s.cursor = 'pointer';
        s.padding = '6px 16px';
        s.borderRadius = '6px';
        s.border = '0';
        s.background = bg;
        s.color = fg;
        s.font = 'inherit';
        b.addEventListener('click', onClick);
        return b;
    }
    function showBanner() {
        if (document.querySelector('[data-ujcms-consent-banner]')) { return; }
        var bar = document.createElement('div');
        bar.setAttribute('data-ujcms-consent-banner', '');
        bar.setAttribute('role', 'region');
        bar.setAttribute('aria-label', txt('consentLabel', '隐私同意'));
        var s = bar.style;
        s.position = 'fixed';
        s.left = '0';
        s.right = '0';
        s.bottom = '0';
        s.zIndex = '2147483647';
        s.display = 'flex';
        s.flexWrap = 'wrap';
        s.alignItems = 'center';
        s.gap = '10px';
        s.padding = '12px 16px';
        s.background = 'rgba(17, 24, 39, 0.96)';
        s.color = '#fff';
        s.font = '14px/1.5 system-ui, -apple-system, sans-serif';
        s.boxShadow = '0 -2px 10px rgba(0, 0, 0, 0.25)';

        var text = document.createElement('span');
        text.style.flex = '1 1 240px';
        text.textContent = txt('consentText', '本站使用本地存储进行匿名访问统计，以改进内容与体验。');
        var link = me && me.dataset.consentLink;
        if (link) {
            var a = document.createElement('a');
            a.href = link;
            a.textContent = txt('consentLinkText', '了解更多');
            a.style.marginLeft = '6px';
            a.style.color = '#93c5fd';
            text.appendChild(a);
        }

        var reject = makeButton(txt('consentReject', '拒绝'), 'transparent', '#fff', function () {
            applyConsent('denied');
        });
        reject.style.border = '1px solid rgba(255, 255, 255, 0.6)';
        var accept = makeButton(txt('consentAccept', '同意'), '#2563eb', '#fff', function () {
            applyConsent('granted');
        });

        bar.appendChild(text);
        bar.appendChild(reject);
        bar.appendChild(accept);
        (document.body || document.documentElement).appendChild(bar);
        bannerEl = bar;
    }
    function whenBodyReady(fn) {
        if (document.body) { fn(); }
        else { document.addEventListener('DOMContentLoaded', fn); }
    }

    // ── 启动 ─────────────────────────────────────────────────────────────────
    evaluateConsent();
    if (CONSENT_POLICY !== 'off' && readConsent() == null) {
        whenBodyReady(showBanner);
    }
    if (enabled) {
        startTracking();
    }
}());
