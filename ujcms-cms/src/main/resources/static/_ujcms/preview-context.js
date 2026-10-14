/*
 * 预览上下文携带脚本（design-review P2-1 / H1 主题预览覆盖；2026-08-23 扩展至顶层草稿预览）。由
 * PreviewBridgeInjectionFilter 在「GET + 预览参数 + 预览身份」的模板轨道响应 <head> 起始处注入
 * （阻塞脚本，先于主题自身任何脚本执行；无 <head> 的页面退回 </body> 前）。携带的预览参数：
 *   - ?_theme=<候选主题>：以候选主题渲染（不论是否被内嵌）；
 *   - ?_preview=draft：草稿预览——仅在「顶层文档」携带（构建器「新标签打开」的整站试看）。被内嵌
 *     （控制台构建器 iframe）时导航由预览桥上报宿主改写（补新随机数 _pchannel），本脚本不得插手；
 * 两者可叠加。三件事：
 *   1. 同源子请求携带预览参数：主题脚本以 fetch / XMLHttpRequest 取回的模板片段
 *      （如 ${dy}/app/user 顶栏用户块、${dy}/app/articles 翻页）同样经前台控制器渲染——不带参数会
 *      以活动主题 / 已发布内容渲染（候选主题的片段落到活动主题目录：片段缺失即 404 JSON，存在也是
 *      另一主题的片段），故对同源 GET/HEAD 一律补参数；须先于主题脚本安装补丁，这也是注入点在
 *      <head> 起始而非 </body> 的原因；
 *   2. 捕获同源链接点击与 GET 表单提交（站内搜索等），为导航目标补参数——操作员能在预览态下浏览整站，
 *      不因点击链接静默掉回活动主题 / 已发布页；
 *   3. 固定横幅标示当前预览态（候选主题未激活 / 草稿含未发布改动），并提供「退出预览」（去参回到
 *      正常渲染）。
 * 令牌过期后：主题预览的导航/子请求得到 401（预览失败要响亮，绝不静默回退）；草稿预览退回已发布页
 * 且不再注入本脚本（横幅消失即提示）。回控制台重新预览即可。零依赖 IIFE；不发请求、不写任何状态。
 */
(function () {
  'use strict';
  var params = new URLSearchParams(window.location.search);
  var theme = params.get('_theme');
  var token = params.get('_ptoken');
  // 草稿预览仅顶层文档携带（与预览桥的「被内嵌」激活闸门互补：framed → 桥 + 宿主；top → 本脚本）
  var draft = params.get('_preview') === 'draft' && window.parent === window;
  if (!theme && !draft) {
    return;
  }

  // 同源 http(s) 地址补预览参数；已带同名参数、跨域或非 http(s) 返回 null（不改写）
  function withPreview(raw) {
    var url;
    try {
      url = new URL(String(raw), window.location.href);
    } catch (e) {
      return null;
    }
    if (url.origin !== window.location.origin || (url.protocol !== 'http:' && url.protocol !== 'https:')) {
      return null;
    }
    var changed = false;
    if (theme && !url.searchParams.get('_theme')) {
      url.searchParams.set('_theme', theme);
      changed = true;
    }
    if (draft && !url.searchParams.has('_preview')) {
      url.searchParams.set('_preview', 'draft');
      changed = true;
    }
    if (!changed) {
      return null;
    }
    if (token) {
      url.searchParams.set('_ptoken', token);
    }
    return url;
  }

  function isSafeMethod(method) {
    return !method || /^(GET|HEAD)$/i.test(String(method));
  }

  // fetch：字符串 / URL / Request 三种入参；仅 GET/HEAD（写请求不承载预览，与后端 PreviewAuthFilter 同口径）
  if (typeof window.fetch === 'function') {
    var nativeFetch = window.fetch;
    window.fetch = function (input, init) {
      try {
        var method = (init && init.method) || (input && typeof input === 'object' && input.method) || 'GET';
        if (isSafeMethod(method)) {
          if (typeof input === 'string' || input instanceof URL) {
            var rewritten = withPreview(input);
            if (rewritten) {
              input = rewritten.toString();
            }
          } else if (input && typeof input.url === 'string' && typeof Request === 'function') {
            var rewrittenRequest = withPreview(input.url);
            if (rewrittenRequest) {
              input = new Request(rewrittenRequest.toString(), input);
            }
          }
        }
      } catch (e) {
        /* 改写失败则按原样发出 */
      }
      return nativeFetch.call(this, input, init);
    };
  }

  // XMLHttpRequest：在 open 处改写地址
  if (typeof XMLHttpRequest === 'function' && XMLHttpRequest.prototype.open) {
    var nativeOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (method, url) {
      try {
        if (isSafeMethod(method)) {
          var rewritten = withPreview(url);
          if (rewritten) {
            arguments[1] = rewritten.toString();
          }
        }
      } catch (e) {
        /* 改写失败则按原样打开 */
      }
      return nativeOpen.apply(this, arguments);
    };
  }

  // 同源链接导航携带预览参数（捕获阶段，先于站内脚本的默认行为；修饰键/新窗口/下载等交给浏览器）
  document.addEventListener(
    'click',
    function (event) {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }
      var anchor = event.target && event.target.closest ? event.target.closest('a[href]') : null;
      if (!anchor || anchor.hasAttribute('download')) {
        return;
      }
      if (anchor.target && anchor.target !== '_self') {
        return;
      }
      var url;
      try {
        url = new URL(anchor.href, window.location.href);
      } catch (e) {
        return;
      }
      // 页内锚点（同文档 hash 跳转）不改写
      if (url.pathname === window.location.pathname && url.search === window.location.search && url.hash) {
        return;
      }
      var rewritten = withPreview(url);
      if (!rewritten) {
        return;
      }
      event.preventDefault();
      window.location.href = rewritten.toString();
    },
    true
  );

  function appendHidden(form, name, value) {
    var hidden = document.createElement('input');
    hidden.type = 'hidden';
    hidden.name = name;
    hidden.value = value;
    form.appendChild(hidden);
  }

  // 同源 GET 表单提交（站内搜索等）携带预览参数：提交前补隐藏字段，不改变提交本身
  document.addEventListener(
    'submit',
    function (event) {
      var form = event.target;
      if (event.defaultPrevented || !form || typeof form.getAttribute !== 'function') {
        return;
      }
      if (!isSafeMethod(form.getAttribute('method'))) {
        return;
      }
      if (form.target && form.target !== '_self') {
        return;
      }
      var action = form.getAttribute('action');
      var url;
      try {
        url = new URL(action || window.location.href, window.location.href);
      } catch (e) {
        return;
      }
      if (url.origin !== window.location.origin || (url.protocol !== 'http:' && url.protocol !== 'https:')) {
        return;
      }
      var changed = false;
      if (theme && !form.elements.namedItem('_theme')) {
        appendHidden(form, '_theme', theme);
        changed = true;
      }
      if (draft && !form.elements.namedItem('_preview')) {
        appendHidden(form, '_preview', 'draft');
        changed = true;
      }
      if (changed && token && !form.elements.namedItem('_ptoken')) {
        appendHidden(form, '_ptoken', token);
      }
    },
    true
  );

  // 预览横幅（底部固定，最高层级；退出 = 去掉预览参数回到正常渲染）。
  // 脚本在 <head> 内阻塞执行时 body 尚不存在，推迟到 DOMContentLoaded。
  function mountBanner() {
    if (!document.body) {
      return;
    }
    // 退出 = 去掉本脚本所携带的参数（及其凭据 _ptoken）
    var exit = new URL(window.location.href);
    if (theme) {
      exit.searchParams.delete('_theme');
    }
    if (draft) {
      exit.searchParams.delete('_preview');
    }
    exit.searchParams.delete('_ptoken');
    var parts = [];
    if (theme) {
      parts.push('主题预览：' + theme + '（未激活）');
    }
    if (draft) {
      parts.push('草稿预览（含未发布的改动）');
    }
    var bar = document.createElement('div');
    bar.setAttribute('data-ujcms-preview-context', (theme ? 'theme' : '') + (theme && draft ? ' ' : '') + (draft ? 'draft' : ''));
    bar.style.cssText =
      'position:fixed;left:0;right:0;bottom:0;z-index:2147483647;display:flex;gap:16px;align-items:center;' +
      'justify-content:center;padding:8px 16px;background:#111827;color:#f9fafb;' +
      'font:13px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif;box-shadow:0 -2px 8px rgba(0,0,0,.3)';
    var label = document.createElement('span');
    label.textContent = parts.join(' · ') + '，仅本次浏览可见';
    var link = document.createElement('a');
    link.href = exit.toString();
    link.textContent = '退出预览';
    link.style.cssText = 'color:#93c5fd;text-decoration:underline';
    bar.appendChild(label);
    bar.appendChild(link);
    document.body.appendChild(bar);
  }
  if (document.body) {
    mountBanner();
  } else {
    document.addEventListener('DOMContentLoaded', mountBanner);
  }
})();
