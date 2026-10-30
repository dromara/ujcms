/*
 * UJCMS 人机验证组件（所有主题与控制台共用的唯一实现，用法见 docs/guide/captcha.zh.md）。
 *
 *   const cap = UjcmsCaptcha.mount(el, { api: '/api', scope: 'comment' });
 *   const { captcha, captchaToken } = await cap.answer();   // 随业务请求提交
 *   cap.refresh();                                          // 提交失败后换一题
 *
 * 配置可写在元素上：data-api、data-scope、data-type（auto | slider | char）、data-lang（zh | en）、data-popup、data-hidden。
 *   - 带 scope 的互动场景先问 /captcha/is-display?scope=：不需要可见验证时静默做工作量证明（pow），用户无感；
 *     需要时按全局设置出可见题（滑块 / 字符 / 第三方厂商）。
 *   - 滑块：拖动或键盘（←→、Shift 加速、Enter 提交）；松手即预检，对了锁定，错了抖动换题；可切换为字符验证码（无障碍回退）。
 *   - 厂商：按需加载厂商脚本（8 秒超时），加载不了就回落到滑块；厂商凭证作为答案原样提交，由服务端二次校验。
 *   - popup / data-popup：弹层模式。挂载时不出题，answer() 时才把滑块 / 字符题弹出在 <dialog> 里，答对即关闭并解析，
 *     关闭或取消则拒绝；答完后宿主只显示“已验证”。静默场景照旧后台做工作量证明；厂商题照旧即时渲染（厂商自带弹层，
 *     放进顶层的 <dialog> 会被盖住），挂载时经 /captcha/type 判断。参考主题的全部表单都用它：注册这类始终可见的
 *     表单点提交才出题；带 scope 的互动表单静默时无感，超额时点提交才弹出，题目不占表单位置、不在页面加载时出题。
 *   - 语言：mount 的 lang > 脚本标签的 data-lang（主题按自己的文案语言钉一次：<script src=".../captcha.js" data-lang="zh">）
 *     > <html lang>。组件文案与主题文案同处一屏，跟主题走，不跟站点语言走。
 *   - data-hidden：把答案镜像到所在表单的 <input type=hidden name=captcha|captchaToken>，供传统表单提交（/login）。
 * 样式随脚本注入一次；颜色经 CSS 变量 --ujc-primary 跟随页面主题（弹层挂在宿主内，继承同一套变量）。
 */
;(function () {
    'use strict';

    var VERSION = '1';
    var VENDOR_SCRIPT_TIMEOUT = 8000;
    var SLIDER_FAIL_DELAY = 600;
    var POPUP_CLOSE_DELAY = 500;
    /** 任何场景都可请求、由本组件自己渲染的类型；其余为第三方厂商 */
    var SELF_HOSTED = { slider: true, char: true, pow: true };
    /** 脚本标签上的 data-lang：主题按自己的文案语言钉一次（见文件头） */
    var SCRIPT_LANG = document.currentScript && document.currentScript.getAttribute('data-lang') || '';

    function noop() { /* 已在界面上处理 */ }

    var TEXT = {
        zh: {
            sliderTip: '拖动拼图到缺口处', sliderHint: '向右拖动滑块', success: '验证通过', fail: '未对齐，请重试',
            refresh: '换一张', switchChar: '看不清？使用字符验证码', switchSlider: '使用滑块拼图',
            charPlaceholder: '请输入图中字符', charRefresh: '点击换一张', charLabel: '图形验证码',
            vendorButton: '点击完成验证', verified: '已验证', loading: '加载中…', working: '正在验证…',
            unavailable: '验证服务暂不可用，已切换为滑块拼图', unsolved: '请先完成人机验证', error: '验证码加载失败，请重试',
            sliderLabel: '滑块验证', popupTitle: '请完成人机验证', close: '关闭', confirm: '确定', charFail: '验证码错误，请重试'
        },
        en: {
            sliderTip: 'Drag the piece into the gap', sliderHint: 'Slide to the right', success: 'Verified', fail: 'Not quite, try again',
            refresh: 'New puzzle', switchChar: 'Use a text captcha instead', switchSlider: 'Use the slider instead',
            charPlaceholder: 'Enter the characters', charRefresh: 'Click for a new image', charLabel: 'Captcha',
            vendorButton: 'Click to verify', verified: 'Verified', loading: 'Loading…', working: 'Verifying…',
            unavailable: 'Verification service unavailable, switched to the slider', unsolved: 'Please complete the verification first',
            error: 'Could not load the captcha, please retry', sliderLabel: 'Slider captcha',
            popupTitle: 'Please complete the verification', close: 'Close', confirm: 'OK', charFail: 'Wrong characters, try again'
        }
    };

    var CSS = '.ujc{--ujc-primary:#2563eb;--ujc-radius:8px;--ujc-line:#e2e8f0;--ujc-text:#334155;--ujc-muted:#94a3b8;'
        + 'position:relative;box-sizing:border-box;width:100%;max-width:var(--ujc-max,300px);font-size:13px;color:var(--ujc-text);line-height:1.4}'
        + '.ujc *{box-sizing:border-box}'
        + '.ujc--pow{display:none}'
        + '.ujc__stage{position:relative;width:100%;overflow:hidden;border-radius:var(--ujc-radius);background:#e2e8f0;user-select:none;-webkit-user-select:none;touch-action:none}'
        + '.ujc__bg{display:block;width:100%;height:auto}'
        + '.ujc__piece{position:absolute;left:0;top:0;height:auto;will-change:left;filter:drop-shadow(0 2px 3px rgba(0,0,0,.35))}'
        + '.ujc__tip{position:absolute;left:0;right:0;bottom:0;padding:4px 8px;background:rgba(15,23,42,.45);color:#fff;font-size:12px;text-align:center;pointer-events:none;transition:opacity .2s}'
        + '.ujc--active .ujc__tip,.ujc--solved .ujc__tip{opacity:0}'
        + '.ujc__result{position:absolute;left:0;right:0;bottom:0;padding:4px 8px;font-size:12px;text-align:center;color:#fff;opacity:0;pointer-events:none;transition:opacity .2s}'
        + '.ujc--solved .ujc__result{opacity:1;background:rgba(22,163,74,.85)}'
        + '.ujc--failed .ujc__result{opacity:1;background:rgba(220,38,38,.85)}'
        + '.ujc__track{position:relative;height:40px;margin-top:8px;border:1px solid var(--ujc-line);border-radius:20px;background:#f8fafc;overflow:hidden}'
        + '.ujc__fill{position:absolute;left:0;top:0;bottom:0;width:0;background:color-mix(in srgb,var(--ujc-primary) 18%,transparent);transition:none}'
        + '.ujc__hint{position:absolute;left:0;right:0;top:0;bottom:0;display:flex;align-items:center;justify-content:center;color:var(--ujc-muted);pointer-events:none;transition:opacity .2s}'
        + '.ujc--active .ujc__hint,.ujc--solved .ujc__hint{opacity:0}'
        + '.ujc__handle{position:absolute;left:0;top:-1px;width:54px;height:40px;border-radius:20px;background:var(--ujc-primary);color:#fff;display:flex;align-items:center;justify-content:center;cursor:grab;box-shadow:0 1px 3px rgba(0,0,0,.25);touch-action:none;outline:none;border:0;padding:0}'
        + '.ujc__handle:focus-visible{box-shadow:0 0 0 3px color-mix(in srgb,var(--ujc-primary) 35%,transparent)}'
        + '.ujc--dragging .ujc__handle{cursor:grabbing}'
        + '.ujc--solved .ujc__handle{background:#16a34a;cursor:default}'
        + '.ujc--failed .ujc__handle{background:#dc2626}'
        + '.ujc__handle svg{width:20px;height:20px;fill:none;stroke:currentColor;stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}'
        + '.ujc--failed .ujc__stage{animation:ujc-shake .4s}'
        + '@keyframes ujc-shake{10%,90%{transform:translateX(-2px)}20%,80%{transform:translateX(3px)}30%,50%,70%{transform:translateX(-4px)}40%,60%{transform:translateX(4px)}}'
        + '.ujc__foot{display:flex;justify-content:space-between;gap:12px;margin-top:6px;font-size:12px;color:var(--ujc-muted)}'
        + '.ujc__link{background:none;border:0;padding:0;color:var(--ujc-muted);cursor:pointer;font:inherit;text-decoration:underline;text-underline-offset:2px}'
        + '.ujc__link:hover{color:var(--ujc-primary)}'
        + '.ujc--char .ujc__row{display:flex;gap:8px;align-items:stretch}'
        + '.ujc__input{flex:1;min-width:0;height:40px;padding:0 12px;border:1px solid var(--ujc-line);border-radius:var(--ujc-radius);font:inherit;font-size:14px;color:inherit;background:#fff;outline:none}'
        + '.ujc__input:focus{border-color:var(--ujc-primary);box-shadow:0 0 0 2px color-mix(in srgb,var(--ujc-primary) 20%,transparent)}'
        + '.ujc__img{height:40px;width:auto;min-width:110px;border:1px solid var(--ujc-line);border-radius:var(--ujc-radius);background:#f8fafc;cursor:pointer;object-fit:contain}'
        + '.ujc__vendor{min-height:40px}'
        + '.ujc__button{display:inline-flex;align-items:center;gap:8px;height:40px;padding:0 16px;border:1px solid var(--ujc-line);border-radius:var(--ujc-radius);background:#f8fafc;color:var(--ujc-text);font:inherit;cursor:pointer}'
        + '.ujc__button:hover{border-color:var(--ujc-primary);color:var(--ujc-primary)}'
        + '.ujc--solved .ujc__button{border-color:#16a34a;color:#16a34a;cursor:default}'
        + '.ujc__status{margin-top:6px;font-size:12px;color:var(--ujc-muted)}'
        + '.ujc__status--error{color:#dc2626}'
        + '.ujc__status:empty{display:none}'
        + '.ujc--failed .ujc__input{border-color:#dc2626}'
        + '.ujc--popup:empty{display:none}'
        + '.ujc__chip{display:inline-flex;align-items:center;gap:6px;font-size:13px;color:#16a34a}'
        + '.ujc__chip svg{width:16px;height:16px;fill:none;stroke:currentColor;stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}'
        + '.ujc__dialog{box-sizing:border-box;width:calc(100vw - 32px);max-width:var(--ujc-dialog,332px);margin:auto;padding:16px;border:0;border-radius:12px;background:#fff;color:var(--ujc-text);box-shadow:0 12px 40px rgba(15,23,42,.25);font-size:13px;line-height:1.4}'
        + '.ujc__dialog::backdrop{background:rgba(15,23,42,.45)}'
        + '.ujc__dialog-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:12px}'
        + '.ujc__dialog-title{font-size:14px;font-weight:600}'
        + '.ujc__close{display:inline-flex;padding:4px;border:0;border-radius:6px;background:none;color:var(--ujc-muted);cursor:pointer}'
        + '.ujc__close:hover{color:var(--ujc-text);background:#f1f5f9}'
        + '.ujc__close svg{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:2.2;stroke-linecap:round}'
        + '.ujc__host{position:relative;width:100%;max-width:var(--ujc-max,300px);margin:0 auto}'
        + '.ujc__confirm{display:block;width:100%;height:40px;margin-top:8px;border:0;border-radius:var(--ujc-radius);background:var(--ujc-primary);color:#fff;font:inherit;font-size:14px;cursor:pointer}'
        + '.ujc__confirm:hover{opacity:.9}'
        + '.ujc__sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}';

    var ARROW = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>';
    var CHECK = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12l5 5L19 7"/></svg>';
    var CROSS = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>';

    function injectStyle() {
        if (document.getElementById('ujcms-captcha-style')) return;
        var style = document.createElement('style');
        style.id = 'ujcms-captcha-style';
        style.textContent = CSS;
        document.head.appendChild(style);
    }

    /** mount 的 lang > 脚本标签的 data-lang > <html lang>；只认 en，其余为 zh */
    function pickLang(explicit) {
        var value = explicit || SCRIPT_LANG || document.documentElement.getAttribute('lang') || '';
        return /^en/i.test(value) ? 'en' : 'zh';
    }

    function el(tag, className, html) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (html !== undefined) node.innerHTML = html;
        return node;
    }

    function escapeHtml(text) {
        return String(text).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    async function getJson(url) {
        var res = await fetch(url, { credentials: 'include', headers: { Accept: 'application/json' } });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
    }

    /* ---------- SHA-256（纯 JS，工作量证明用；消息总长 < 56 字节时单块） ---------- */
    var SHA256_SRC = 'var K=[1116352408,1899447441,3049323471,3921009573,961987163,1508970993,2453635748,2870763221,3624381080,310598401,607225278,1426881987,1925078388,2162078206,2614888103,3248222580,3835390401,4022224774,264347078,604807628,770255983,1249150122,1555081692,1996064986,2554220882,2821834349,2952996808,3210313671,3336571891,3584528711,113926993,338241895,666307205,773529912,1294757372,1396182291,1695183700,1986661051,2177026350,2456956037,2730485921,2820302411,3259730800,3345764771,3516065817,3600352804,4094571909,275423344,430227734,506948616,659060556,883997877,958139571,1322822218,1537002063,1747873779,1955562222,2024104815,2227730452,2361852424,2428436474,2756734187,3204031479,3329325298];'
        + 'function sha256Words(bytes){var l=bytes.length,bl=l*8,n=((l+8)>>6)+1,W=new Uint32Array(n*16),i;for(i=0;i<l;i++)W[i>>2]|=bytes[i]<<(24-(i%4)*8);W[l>>2]|=128<<(24-(l%4)*8);W[n*16-1]=bl;var H=[1779033703,3144134277,1013904242,2773480762,1359893119,2600822924,528734635,1541459225],w=new Uint32Array(64),j;for(i=0;i<n;i++){for(j=0;j<16;j++)w[j]=W[i*16+j];for(j=16;j<64;j++){var x=w[j-15],y=w[j-2];var s0=((x>>>7)|(x<<25))^((x>>>18)|(x<<14))^(x>>>3);var s1=((y>>>17)|(y<<15))^((y>>>19)|(y<<13))^(y>>>10);w[j]=(w[j-16]+s0+w[j-7]+s1)|0}var a=H[0],b=H[1],c=H[2],d=H[3],e=H[4],f=H[5],g=H[6],h=H[7];for(j=0;j<64;j++){var S1=((e>>>6)|(e<<26))^((e>>>11)|(e<<21))^((e>>>25)|(e<<7));var ch=(e&f)^(~e&g);var t1=(h+S1+ch+K[j]+w[j])|0;var S0=((a>>>2)|(a<<30))^((a>>>13)|(a<<19))^((a>>>22)|(a<<10));var mj=(a&b)^(a&c)^(b&c);var t2=(S0+mj)|0;h=g;g=f;f=e;e=(d+t1)|0;d=c;c=b;b=a;a=(t1+t2)|0}H[0]=(H[0]+a)|0;H[1]=(H[1]+b)|0;H[2]=(H[2]+c)|0;H[3]=(H[3]+d)|0;H[4]=(H[4]+e)|0;H[5]=(H[5]+f)|0;H[6]=(H[6]+g)|0;H[7]=(H[7]+h)|0}return H}'
        + 'function leadingZeros(H){var z=0;for(var i=0;i<8;i++){var v=H[i]>>>0;if(v===0){z+=32;continue}z+=Math.clz32(v);break}return z}'
        + 'function solve(salt,difficulty,start,step,limit){var enc=new TextEncoder();var prefix=enc.encode(salt+":");for(var n=start;n<limit;n+=step){var nb=enc.encode(n.toString(36));var m=new Uint8Array(prefix.length+nb.length);m.set(prefix);m.set(nb,prefix.length);if(leadingZeros(sha256Words(m))>=difficulty)return n.toString(36)}return null}';

    var WORKER_SRC = SHA256_SRC + 'self.onmessage=function(e){var d=e.data;var r=solve(d.salt,d.difficulty,d.start,d.step,d.limit);self.postMessage(r)};';
    var inlineSolver = null;

    function solveInline(salt, difficulty) {
        if (!inlineSolver) {
            // eslint-disable-next-line no-new-func
            inlineSolver = new Function(SHA256_SRC + 'return solve;')();
        }
        return new Promise(function (resolve) {
            var n = 0, step = 20000;
            (function loop() {
                var r = inlineSolver(salt, difficulty, n, 1, n + step);
                if (r !== null) return resolve(r);
                n += step;
                setTimeout(loop, 0);
            })();
        });
    }

    function solvePow(salt, difficulty) {
        var threads = Math.min(4, Math.max(1, (navigator.hardwareConcurrency || 2) - 1));
        if (typeof Worker === 'undefined' || typeof Blob === 'undefined' || typeof URL === 'undefined') {
            return solveInline(salt, difficulty);
        }
        return new Promise(function (resolve) {
            var url, workers = [], done = false;
            function finish(result) {
                if (done) return;
                done = true;
                workers.forEach(function (w) { w.terminate(); });
                if (url) URL.revokeObjectURL(url);
                if (result === null || result === undefined) {
                    solveInline(salt, difficulty).then(resolve);
                } else {
                    resolve(result);
                }
            }
            try {
                url = URL.createObjectURL(new Blob([WORKER_SRC], { type: 'text/javascript' }));
                for (var i = 0; i < threads; i++) {
                    var w = new Worker(url);
                    w.onmessage = function (e) { finish(e.data); };
                    w.onerror = function () { finish(null); };
                    w.postMessage({ salt: salt, difficulty: difficulty, start: i, step: threads, limit: 0x7fffffff });
                    workers.push(w);
                }
            } catch (e) {
                // Worker 不可用（如 CSP 禁止 blob: worker）：主线程分片计算
                finish(null);
            }
        });
    }

    /* ---------- 厂商脚本加载 ---------- */
    var scriptPromises = {};

    function loadScript(src, ready) {
        if (scriptPromises[src]) return scriptPromises[src];
        scriptPromises[src] = new Promise(function (resolve, reject) {
            if (ready && ready()) return resolve();
            var script = document.createElement('script');
            script.src = src;
            script.async = true;
            var timer = setTimeout(function () { reject(new Error('timeout')); }, VENDOR_SCRIPT_TIMEOUT);
            function poll() {
                if (!ready || ready()) { clearTimeout(timer); resolve(); return; }
                setTimeout(poll, 50);
            }
            script.onload = poll;
            script.onerror = function () { clearTimeout(timer); reject(new Error('load')); };
            document.head.appendChild(script);
        });
        scriptPromises[src].catch(function () { delete scriptPromises[src]; });
        return scriptPromises[src];
    }

    /* ---------- 组件 ---------- */
    var uid = 0;

    function Captcha(root, options) {
        injectStyle();
        options = options || {};
        this.root = root;
        this.api = (options.api || root.getAttribute('data-api') || window.visitApi || '/api').replace(/\/$/, '');
        this.scope = options.scope || root.getAttribute('data-scope') || '';
        this.forcedType = options.type || root.getAttribute('data-type') || 'auto';
        this.lang = pickLang(options.lang || root.getAttribute('data-lang'));
        this.t = TEXT[this.lang];
        this.hidden = options.hidden !== undefined ? !!options.hidden : root.hasAttribute('data-hidden');
        this.popup = options.popup !== undefined ? !!options.popup : root.hasAttribute('data-popup');
        // 题目渲染的宿主：行内模式即 root；弹层打开期间为弹层内的盒子
        this.host = root;
        this.dialog = null;
        this.pending = null;
        this.settle = null;
        this.vendorDown = false;
        this.onChange = typeof options.onChange === 'function' ? options.onChange : null;
        this.id = 'ujc' + (++uid);
        // 宿主元素自带的 class（页面布局、控制台的主题变量）在每次重绘时保留
        this.baseClass = root.className;
        this.type = null;
        this.visible = true;
        this.solved = false;
        this.answerValue = null;
        this.token = null;
        this.powPromise = null;
        this.priming = null;
        this.generation = 0;
        this.vendorState = null;
        root.setAttribute('data-captcha-mounted', VERSION);
        this.refresh();
    }

    /** 重置为“只有宿主 class + ujc”的状态；弹层内的宿主只带自己的 class */
    Captcha.prototype.resetClass = function () {
        if (this.host !== this.root) { this.host.className = 'ujc__host'; return; }
        this.root.className = (this.baseClass ? this.baseClass + ' ' : '') + 'ujc' + (this.popup ? ' ujc--popup' : '');
    };

    Captcha.prototype.emit = function () {
        this.syncHidden();
        if (this.onChange) this.onChange({ type: this.type, solved: this.solved, visible: this.visible });
    };

    Captcha.prototype.syncHidden = function () {
        if (!this.hidden) return;
        var form = this.root.closest('form');
        if (!form) return;
        var self = this;
        ['captcha', 'captchaToken'].forEach(function (name) {
            var input = form.querySelector('input[name="' + name + '"]');
            if (!input) {
                input = document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                form.appendChild(input);
            }
            input.value = name === 'captcha' ? (self.answerValue || '') : (self.token || '');
        });
    };

    Captcha.prototype.setSolved = function (answer) {
        this.answerValue = answer;
        this.solved = true;
        this.host.classList.add('ujc--solved');
        this.host.classList.remove('ujc--failed');
        this.emit();
        // 弹层里答对：让用户看到“验证通过”后关闭，关闭即解析 answer()
        var dialog = this.dialog;
        if (dialog) setTimeout(function () { if (dialog.open) dialog.close(); }, POPUP_CLOSE_DELAY);
    };

    Captcha.prototype.clear = function () {
        this.answerValue = null;
        this.solved = false;
        this.host.classList.remove('ujc--solved', 'ujc--failed', 'ujc--active');
    };

    /** 当前答案；可见题未完成时拒绝。弹层模式下尚未出题时先弹出题目：答对后解析，关闭或取消则拒绝 */
    Captcha.prototype.answer = function () {
        var self = this;
        if (this.pending) return this.pending;
        // 弹层模式且挂载 / 重置的决定还没回来：先等它。否则静默场景里抢在 is-display 应答前的提交会白白弹出滑块
        if (this.popup && this.priming) return this.priming.then(function () { return self.answer(); });
        if (this.popup && !this.solved && this.type === null) return this.openDialog();
        if (this.type === 'pow') {
            return (this.powPromise || Promise.reject(new Error(this.t.error))).then(function (nonce) {
                return { captcha: nonce, captchaToken: self.token };
            });
        }
        if (this.type === 'char') {
            var value = (this.input && this.input.value || '').trim();
            if (!value) {
                if (this.input) this.input.focus();
                return Promise.reject(new Error(this.t.unsolved));
            }
            return Promise.resolve({ captcha: value, captchaToken: this.token });
        }
        if (!this.solved || !this.answerValue) {
            this.host.classList.add('ujc--failed');
            var handle = this.host.querySelector('.ujc__handle,.ujc__button');
            if (handle) handle.focus();
            return Promise.reject(new Error(this.t.unsolved));
        }
        return Promise.resolve({ captcha: this.answerValue, captchaToken: this.token });
    };

    Captcha.prototype.reset = function () {
        return this.refresh();
    };

    /** 带 scope 时问服务端是否需要可见验证。返回要请求的类型：'' 为服务端默认，'pow' 为静默 */
    Captcha.prototype.decide = function (typeOverride) {
        var type = typeOverride || (this.forcedType !== 'auto' ? this.forcedType : '');
        if (type || !this.scope) return Promise.resolve(type);
        return getJson(this.api + '/captcha/is-display?scope=' + encodeURIComponent(this.scope))
            .then(function (display) { return display ? '' : 'pow'; })
            .catch(function () { return ''; });
    };

    /** 出题并渲染到宿主；出不了题时渲染错误与“换一张”，并向调用方抛出 */
    Captcha.prototype.load = function (type) {
        var self = this;
        var generation = ++this.generation;
        return getJson(this.api + '/captcha' + (type ? '?type=' + encodeURIComponent(type) : '')).then(function (data) {
            if (generation !== self.generation) return;
            self.token = data.token;
            self.type = data.type;
            self.visible = data.type !== 'pow';
            self.render(data);
            self.emit();
            self.focusChallenge();
        }).catch(function (e) {
            if (generation !== self.generation) return;
            self.renderError();
            if (window.console) console.warn('captcha load failed', e);
            throw e;
        });
    };

    Captcha.prototype.renderError = function () {
        var self = this;
        this.resetClass();
        this.host.innerHTML = '';
        var status = el('div', 'ujc__status ujc__status--error', escapeHtml(this.t.error) + ' ');
        var retry = el('button', 'ujc__link', escapeHtml(this.t.refresh));
        retry.type = 'button';
        retry.addEventListener('click', function () { self.refresh(); });
        status.appendChild(retry);
        this.host.appendChild(status);
    };

    /** 重新出题；带 scope 时重新询问是否需要可见验证。弹层模式且弹层未打开时只重置，题目留到 answer() 时再出 */
    Captcha.prototype.refresh = function (typeOverride) {
        var self = this;
        this.generation++;
        this.clear();
        this.destroyVendor();
        if (this.popup && !this.dialog) return this.prime();
        return this.decide(typeOverride).then(function (type) { return self.load(type); }).catch(noop);
    };

    /** 弹层模式的挂载 / 重置：静默场景后台做工作量证明；厂商题即时渲染（厂商自带弹层）；自托管题留到 answer() 时弹出。
     *  决定期间 priming 持有这条链，answer() 先等它再决定是否弹出 */
    Captcha.prototype.prime = function () {
        var self = this;
        var generation = this.generation;
        this.type = null;
        this.token = null;
        this.renderChip();
        function stale() { return generation !== self.generation || !!self.dialog; }
        var priming = this.decide().then(function (type) {
            if (stale()) return undefined;
            if (type === 'pow') return self.load('pow');
            if (type || self.vendorDown) return undefined;
            return getJson(self.api + '/captcha/type').then(function (result) {
                if (stale() || SELF_HOSTED[result.type]) return undefined;
                return self.load('');
            });
        }).catch(noop).then(function () { if (self.priming === priming) self.priming = null; });
        this.priming = priming;
        return priming;
    };

    /** 弹层模式下宿主的内容：答完可见题后显示“已验证”，否则为空（CSS 把空宿主折叠掉） */
    Captcha.prototype.renderChip = function () {
        if (!this.popup || this.dialog) return;
        this.host = this.root;
        this.root.innerHTML = '';
        this.resetClass();
        if (!this.solved || !this.visible) return;
        this.root.appendChild(el('div', 'ujc__chip', CHECK + '<span>' + escapeHtml(this.t.verified) + '</span>'));
        this.root.classList.add('ujc--solved');
    };

    /** 弹层模式：把题目弹出在 <dialog> 里（挂在宿主内以继承主题的 CSS 变量）；答对关闭即解析 answer()，关闭或取消则拒绝 */
    Captcha.prototype.openDialog = function () {
        var self = this;
        if (this.pending) return this.pending;
        this.pending = new Promise(function (resolve, reject) {
            var t = self.t;
            var dialog = el('dialog', 'ujc__dialog');
            var head = el('div', 'ujc__dialog-head');
            var title = el('div', 'ujc__dialog-title', escapeHtml(t.popupTitle));
            title.id = self.id + '-title';
            var close = el('button', 'ujc__close', CROSS);
            close.type = 'button';
            close.setAttribute('aria-label', t.close);
            head.appendChild(title);
            head.appendChild(close);
            var host = el('div', 'ujc__host');
            dialog.setAttribute('aria-labelledby', title.id);
            dialog.appendChild(head);
            dialog.appendChild(host);
            close.addEventListener('click', function () { dialog.close(); });
            dialog.addEventListener('close', function () { self.closeDialog(); });
            self.root.appendChild(dialog);
            self.dialog = dialog;
            self.host = host;
            self.settle = { resolve: resolve, reject: reject };
            if (typeof dialog.showModal === 'function') dialog.showModal(); else dialog.setAttribute('open', '');
            var type = self.forcedType !== 'auto' ? self.forcedType : (self.vendorDown ? 'slider' : '');
            self.load(type).catch(noop);
        });
        return this.pending;
    };

    Captcha.prototype.closeDialog = function () {
        var dialog = this.dialog;
        var settle = this.settle;
        var answer = this.solved && this.answerValue ? { captcha: this.answerValue, captchaToken: this.token } : null;
        this.dialog = null;
        this.settle = null;
        this.pending = null;
        this.generation++;
        this.destroyVendor();
        if (dialog && dialog.parentNode) dialog.parentNode.removeChild(dialog);
        this.host = this.root;
        if (!answer) {
            this.clear();
            this.type = null;
            this.token = null;
        }
        this.renderChip();
        if (!settle) return;
        if (answer) settle.resolve(answer); else settle.reject(new Error(this.t.unsolved));
    };

    /** 弹层里出题后把焦点放到题目上（showModal 默认落在关闭按钮） */
    Captcha.prototype.focusChallenge = function () {
        if (!this.dialog) return;
        var target = this.host.querySelector('.ujc__handle,.ujc__input,.ujc__button');
        if (target) target.focus();
    };

    Captcha.prototype.render = function (data) {
        this.host.innerHTML = '';
        this.resetClass();
        this.input = null;
        switch (data.type) {
            case 'slider': return this.renderSlider(data);
            case 'char': return this.renderChar(data);
            case 'pow': return this.renderPow(data);
            default: return this.renderVendor(data);
        }
    };

    /* ---------- 工作量证明 ---------- */
    Captcha.prototype.renderPow = function (data) {
        this.host.classList.add('ujc--pow');
        var self = this;
        this.powPromise = solvePow(data.salt, data.difficulty).then(function (nonce) {
            self.answerValue = nonce;
            self.solved = true;
            self.emit();
            return nonce;
        });
    };

    /* ---------- 字符 ---------- */
    Captcha.prototype.renderChar = function (data) {
        var self = this;
        this.host.classList.add('ujc--char');
        var row = el('div', 'ujc__row');
        var input = el('input', 'ujc__input');
        input.type = 'text';
        input.maxLength = 10;
        input.autocomplete = 'off';
        input.spellcheck = false;
        input.placeholder = this.t.charPlaceholder;
        input.setAttribute('aria-label', this.t.charLabel);
        var img = el('img', 'ujc__img');
        img.src = 'data:image/png;base64,' + data.image;
        img.alt = this.t.charLabel;
        img.title = this.t.charRefresh;
        img.addEventListener('click', function () { self.refresh('char'); });
        row.appendChild(input);
        row.appendChild(img);
        this.host.appendChild(row);
        this.input = input;
        input.addEventListener('input', function () {
            self.host.classList.remove('ujc--failed');
            if (self.dialog) return; // 弹层里由“确定”预检后才算完成
            self.answerValue = input.value.trim();
            self.solved = !!self.answerValue;
            self.emit();
        });
        if (this.dialog) {
            // 弹层里：回车或“确定”预检，对了关闭弹层，错了提示后换一张；弹层在表单内，回车不能触发外层表单提交
            var status = el('div', 'ujc__status ujc__status--error');
            var confirm = el('button', 'ujc__confirm', escapeHtml(this.t.confirm));
            confirm.type = 'button';
            var check = function () {
                var value = input.value.trim();
                if (!value) { input.focus(); return; }
                getJson(self.api + '/captcha/try?token=' + encodeURIComponent(self.token) + '&captcha=' + encodeURIComponent(value))
                    .then(function (ok) {
                        if (ok) { self.setSolved(value); return; }
                        self.host.classList.add('ujc--failed');
                        status.textContent = self.t.charFail;
                        setTimeout(function () { self.refresh('char'); }, SLIDER_FAIL_DELAY);
                    })
                    .catch(function () { self.setSolved(value); /* 预检不可用：交给提交时校验 */ });
            };
            confirm.addEventListener('click', check);
            input.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); check(); } });
            this.host.appendChild(status);
            this.host.appendChild(confirm);
        } else {
            input.addEventListener('blur', function () {
                var value = input.value.trim();
                if (!value) return;
                getJson(self.api + '/captcha/try?token=' + encodeURIComponent(self.token) + '&captcha=' + encodeURIComponent(value))
                    .then(function (ok) { self.host.classList.toggle('ujc--failed', !ok); })
                    .catch(function () { /* 提交时服务端仍校验 */ });
            });
        }
        if (this.forcedType === 'auto') {
            var foot = el('div', 'ujc__foot');
            var back = el('button', 'ujc__link', escapeHtml(this.t.switchSlider));
            back.type = 'button';
            back.addEventListener('click', function () { self.refresh('slider'); });
            foot.appendChild(back);
            this.host.appendChild(foot);
        }
    };

    /* ---------- 滑块 ---------- */
    Captcha.prototype.renderSlider = function (data) {
        var self = this;
        var t = this.t;
        this.host.classList.add('ujc--slider');
        this.host.style.setProperty('--ujc-max', data.width + 'px');
        if (this.dialog) this.dialog.style.setProperty('--ujc-dialog', (data.width + 32) + 'px');

        var stage = el('div', 'ujc__stage');
        stage.style.aspectRatio = data.width + ' / ' + data.height;
        var bg = el('img', 'ujc__bg');
        bg.src = 'data:image/png;base64,' + data.image;
        bg.alt = '';
        bg.draggable = false;
        var piece = el('img', 'ujc__piece');
        piece.src = 'data:image/png;base64,' + data.piece;
        piece.alt = '';
        piece.draggable = false;
        var tip = el('div', 'ujc__tip', escapeHtml(t.sliderTip));
        var result = el('div', 'ujc__result');
        stage.appendChild(bg);
        stage.appendChild(piece);
        stage.appendChild(tip);
        stage.appendChild(result);

        var track = el('div', 'ujc__track');
        var fill = el('div', 'ujc__fill');
        var hint = el('div', 'ujc__hint', escapeHtml(t.sliderHint));
        var handle = el('button', 'ujc__handle', ARROW);
        handle.type = 'button';
        handle.setAttribute('role', 'slider');
        handle.setAttribute('aria-label', t.sliderLabel + ': ' + t.sliderTip);
        handle.setAttribute('aria-valuemin', '0');
        handle.setAttribute('aria-valuemax', String(data.width));
        handle.setAttribute('aria-valuenow', '0');
        track.appendChild(fill);
        track.appendChild(hint);
        track.appendChild(handle);

        var foot = el('div', 'ujc__foot');
        var refresh = el('button', 'ujc__link', escapeHtml(t.refresh));
        refresh.type = 'button';
        refresh.addEventListener('click', function () { self.refresh('slider'); });
        foot.appendChild(refresh);
        if (this.forcedType === 'auto') {
            var toChar = el('button', 'ujc__link', escapeHtml(t.switchChar));
            toChar.type = 'button';
            toChar.addEventListener('click', function () { self.refresh('char'); });
            foot.appendChild(toChar);
        }
        var live = el('div', 'ujc__sr');
        live.setAttribute('aria-live', 'polite');

        this.host.appendChild(stage);
        this.host.appendChild(track);
        this.host.appendChild(foot);
        this.host.appendChild(live);

        var ratio = 1, pieceW = 0, maxPiece = 0, maxHandle = 0, position = 0, locked = false, dragging = false, startX = 0, startPos = 0;
        function measure() {
            var stageW = stage.clientWidth || data.width;
            ratio = stageW / data.width;
            pieceW = data.pieceWidth * ratio;
            piece.style.width = pieceW + 'px';
            piece.style.top = (data.y * ratio) + 'px';
            maxPiece = Math.max(1, stageW - pieceW);
            maxHandle = Math.max(1, track.clientWidth - handle.offsetWidth);
            place(position);
        }
        function place(handleLeft) {
            position = Math.max(0, Math.min(maxHandle, handleLeft));
            var pieceLeft = position / maxHandle * maxPiece;
            handle.style.left = position + 'px';
            fill.style.width = (position + handle.offsetWidth / 2) + 'px';
            piece.style.left = pieceLeft + 'px';
            handle.setAttribute('aria-valuenow', String(Math.round(pieceLeft / ratio)));
        }
        function logicalAnswer() {
            return Math.round(position / maxHandle * maxPiece / ratio);
        }
        function fail() {
            self.host.classList.add('ujc--failed');
            self.host.classList.remove('ujc--active');
            result.textContent = t.fail;
            live.textContent = t.fail;
            locked = true;
            setTimeout(function () { self.refresh('slider'); }, SLIDER_FAIL_DELAY);
        }
        function submit() {
            if (locked) return;
            if (position < 2) { place(0); self.host.classList.remove('ujc--active'); return; }
            locked = true;
            var answer = String(logicalAnswer());
            getJson(self.api + '/captcha/try?token=' + encodeURIComponent(self.token) + '&captcha=' + encodeURIComponent(answer))
                .then(function (ok) {
                    if (ok) {
                        result.textContent = t.success;
                        live.textContent = t.success;
                        handle.innerHTML = CHECK;
                        handle.setAttribute('aria-disabled', 'true');
                        self.setSolved(answer);
                    } else {
                        fail();
                    }
                })
                .catch(fail);
        }
        function onDown(e) {
            if (locked || e.button) return;
            dragging = true;
            startX = e.clientX;
            startPos = position;
            self.host.classList.add('ujc--active', 'ujc--dragging');
            try { handle.setPointerCapture(e.pointerId); } catch (ignored) { /* synthetic or unsupported pointer */ }
            e.preventDefault();
        }
        function onMove(e) {
            if (!dragging) return;
            place(startPos + e.clientX - startX);
        }
        function onUp(e) {
            if (!dragging) return;
            dragging = false;
            self.host.classList.remove('ujc--dragging');
            try { handle.releasePointerCapture(e.pointerId); } catch (ignored) { /* already released */ }
            submit();
        }
        handle.addEventListener('pointerdown', onDown);
        handle.addEventListener('pointermove', onMove);
        handle.addEventListener('pointerup', onUp);
        handle.addEventListener('pointercancel', onUp);
        handle.addEventListener('keydown', function (e) {
            if (locked) return;
            var step = (e.shiftKey ? 10 : 2) * (maxHandle / maxPiece);
            if (e.key === 'ArrowRight' || e.key === 'ArrowUp') { self.host.classList.add('ujc--active'); place(position + step); e.preventDefault(); }
            else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') { place(position - step); e.preventDefault(); }
            else if (e.key === 'Home') { place(0); e.preventDefault(); }
            else if (e.key === 'End') { self.host.classList.add('ujc--active'); place(maxHandle); e.preventDefault(); }
            else if (e.key === 'Enter' || e.key === ' ') { submit(); e.preventDefault(); }
        });
        if (bg.complete) measure(); else bg.addEventListener('load', measure);
        if (typeof ResizeObserver !== 'undefined') {
            var ro = new ResizeObserver(function () { measure(); });
            ro.observe(stage);
            this.sliderObserver = ro;
        } else {
            measure();
        }
    };

    /* ---------- 第三方厂商 ---------- */
    Captcha.prototype.destroyVendor = function () {
        if (this.sliderObserver) { this.sliderObserver.disconnect(); this.sliderObserver = null; }
        var state = this.vendorState;
        this.vendorState = null;
        if (!state) return;
        try { if (state.destroy) state.destroy(); } catch (ignored) { /* vendor already gone */ }
    };

    Captcha.prototype.vendorFallback = function (reason) {
        var self = this;
        if (window.console) console.warn('captcha vendor unavailable, falling back to the slider', reason);
        this.vendorDown = true;
        if (this.popup && !this.dialog) {
            // 弹层模式：厂商题本是即时渲染的，加载不了就改为提交时弹出滑块
            this.refresh();
            return;
        }
        this.refresh('slider').then(function () {
            var status = el('div', 'ujc__status', escapeHtml(self.t.unavailable));
            self.host.appendChild(status);
        });
    };

    Captcha.prototype.renderVendor = function (data) {
        var self = this;
        var t = this.t;
        this.host.classList.add('ujc--vendor', 'ujc--' + data.type);
        var box = el('div', 'ujc__vendor');
        box.id = this.id + '-vendor';
        var status = el('div', 'ujc__status', escapeHtml(t.loading));
        this.host.appendChild(box);
        this.host.appendChild(status);
        var generation = this.generation;
        function alive() { return generation === self.generation; }
        function done(answer) {
            if (!alive()) return;
            status.textContent = t.verified;
            self.setSolved(answer);
        }
        var renderers = {
            geetest: function () {
                return loadScript('https://static.geetest.com/v4/gt4.js', function () { return typeof window.initGeetest4 === 'function'; }).then(function () {
                    return new Promise(function (resolve, reject) {
                        window.initGeetest4({
                            captchaId: data.captchaId, product: 'float', language: self.lang === 'en' ? 'eng' : 'zho',
                            nativeButton: { width: '100%', height: '40px' }
                        }, function (captcha) {
                            captcha.onReady(function () { if (alive()) { status.textContent = ''; } resolve({ destroy: function () { captcha.destroy(); } }); });
                            captcha.onError(function (e) { reject(e); });
                            captcha.onSuccess(function () { done(JSON.stringify(captcha.getValidate())); });
                            if (alive()) captcha.appendTo(box);
                        });
                    });
                });
            },
            tencent: function () {
                return loadScript('https://turing.captcha.qcloud.com/TJCaptcha.js', function () { return typeof window.TencentCaptcha === 'function'; }).then(function () {
                    var button = el('button', 'ujc__button', escapeHtml(t.vendorButton));
                    button.type = 'button';
                    box.appendChild(button);
                    status.textContent = '';
                    var captcha = new window.TencentCaptcha(String(data.appId), function (res) {
                        if (res && res.ret === 0) {
                            button.innerHTML = CHECK + ' ' + escapeHtml(t.verified);
                            done(JSON.stringify({ ticket: res.ticket, randstr: res.randstr }));
                        }
                    }, { userLanguage: self.lang === 'en' ? 'en' : 'zh-cn' });
                    button.addEventListener('click', function () { if (!self.solved) captcha.show(); });
                    return { destroy: function () { try { captcha.destroy(); } catch (ignored) { /* noop */ } } };
                });
            },
            aliyun: function () {
                window.AliyunCaptchaConfig = { region: data.region || 'cn', prefix: data.prefix };
                return loadScript('https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js', function () { return typeof window.initAliyunCaptcha === 'function'; }).then(function () {
                    var stageEl = el('div');
                    stageEl.id = self.id + '-aliyun';
                    var button = el('button', 'ujc__sr');
                    button.type = 'button';
                    button.id = self.id + '-aliyun-btn';
                    box.appendChild(stageEl);
                    box.appendChild(button);
                    status.textContent = '';
                    var instance = null;
                    window.initAliyunCaptcha({
                        SceneId: data.sceneId, mode: 'embed', element: '#' + stageEl.id, button: '#' + button.id,
                        success: function (captchaVerifyParam) { done(captchaVerifyParam); },
                        fail: function () { /* 厂商自动刷新 */ },
                        getInstance: function (i) { instance = i; },
                        slideStyle: { width: Math.max(320, box.clientWidth || 320), height: 40 },
                        language: self.lang === 'en' ? 'en' : 'cn'
                    });
                    return { destroy: function () { try { if (instance && instance.destroy) instance.destroy(); } catch (ignored) { /* noop */ } } };
                });
            },
            yidun: function () {
                var minute = Math.floor(Date.now() / 60000);
                return loadScript('https://cstaticdun.126.net/load.min.js?t=' + minute, function () { return typeof window.initNECaptcha === 'function'; }).then(function () {
                    return new Promise(function (resolve, reject) {
                        window.initNECaptcha({
                            captchaId: data.captchaId, element: box, mode: 'embed', width: '100%',
                            lang: self.lang === 'en' ? 'en' : 'zh-CN',
                            onVerify: function (err, result) { if (!err && result && result.validate) done(result.validate); }
                        }, function (instance) {
                            status.textContent = '';
                            resolve({ destroy: function () { try { instance.destroy(); } catch (ignored) { /* noop */ } } });
                        }, function (err) { reject(err); });
                    });
                });
            },
            turnstile: function () {
                return loadScript('https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit', function () { return window.turnstile && typeof window.turnstile.render === 'function'; }).then(function () {
                    status.textContent = '';
                    var widgetId = window.turnstile.render(box, {
                        sitekey: data.siteKey, language: self.lang === 'en' ? 'en' : 'zh-cn',
                        callback: function (token) { done(token); },
                        'expired-callback': function () { if (alive()) self.clear(); },
                        'error-callback': function () { if (alive()) self.clear(); }
                    });
                    return { destroy: function () { try { window.turnstile.remove(widgetId); } catch (ignored) { /* noop */ } } };
                });
            }
        };
        var renderer = renderers[data.type];
        if (!renderer) { this.vendorFallback('unknown type ' + data.type); return; }
        renderer().then(function (state) {
            if (!alive()) { try { state.destroy(); } catch (ignored) { /* noop */ } return; }
            self.vendorState = state;
        }).catch(function (e) {
            if (alive()) self.vendorFallback(e);
        });
    };

    Captcha.prototype.destroy = function () {
        this.generation++;
        if (this.dialog) this.closeDialog();
        this.destroyVendor();
        this.root.innerHTML = '';
        this.root.className = this.baseClass;
        this.root.removeAttribute('data-captcha-mounted');
        delete this.root.__ujcmsCaptcha;
    };

    var api = {
        version: VERSION,
        mount: function (target, options) {
            var root = typeof target === 'string' ? document.querySelector(target) : target;
            if (!root) throw new Error('captcha mount target not found');
            if (root.__ujcmsCaptcha) return root.__ujcmsCaptcha;
            var instance = new Captcha(root, options);
            root.__ujcmsCaptcha = instance;
            return instance;
        },
        get: function (target) {
            var root = typeof target === 'string' ? document.querySelector(target) : target;
            return root && root.__ujcmsCaptcha || null;
        },
        solvePow: solvePow
    };
    window.UjcmsCaptcha = api;
})();
