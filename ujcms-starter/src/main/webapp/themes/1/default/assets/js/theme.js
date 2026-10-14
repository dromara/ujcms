// theme.js — UJCMS Liquid default theme runtime helpers.
// Globals: window.fetchCsrf, window.request, window.displaySuccess,
// window.displayAlert, window.getCookie, window.setCookie, window.deleteCookie.

(function () {
  'use strict';

  const csrfMeta = () => ({
    header: document.querySelector('meta[name="_csrf_header"]')?.content,
    token: document.querySelector('meta[name="_csrf"]')?.content,
  });

  function setCsrfMeta(headerName, token) {
    const headerEl = document.querySelector('meta[name="_csrf_header"]');
    const tokenEl = document.querySelector('meta[name="_csrf"]');
    if (headerEl) headerEl.setAttribute('content', headerName);
    if (tokenEl) tokenEl.setAttribute('content', token);
  }

  async function fetchCsrf() {
    const apiBase = window.visitApi;
    if (!apiBase) throw new Error('window.visitApi not set — inc_js must run before fetchCsrf');
    const res = await fetch(apiBase + '/env/csrf', { credentials: 'include' });
    if (!res.ok) throw new Error('CSRF fetch failed: ' + res.status);
    const data = await res.json();
    if (!data || !data.headerName || !data.token) {
      throw new Error('CSRF response missing headerName/token');
    }
    setCsrfMeta(data.headerName, data.token);
    return data;
  }

  async function request(method, url, body) {
    const opts = { method, credentials: 'include', headers: {} };
    const m = method.toUpperCase();
    if (m !== 'GET' && m !== 'HEAD') {
      const csrf = csrfMeta();
      if (csrf.header && csrf.token) {
        opts.headers[csrf.header] = csrf.token;
      }
    }
    if (body !== undefined && body !== null) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = text; }
    if (!res.ok) {
      const err = new Error((data && data.message) || res.statusText || 'Request failed');
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  function displaySuccess(msg) {
    window.dispatchEvent(new CustomEvent('toast', { detail: { kind: 'success', msg } }));
  }

  function displayAlert(msg) {
    window.dispatchEvent(new CustomEvent('toast', { detail: { kind: 'alert', msg } }));
  }

  function getCookie(name) {
    const prefix = name + '=';
    for (const pair of document.cookie.split('; ')) {
      if (pair.startsWith(prefix)) {
        return decodeURIComponent(pair.slice(prefix.length));
      }
    }
    return null;
  }

  // Client-readable helper cookies — JS must read them, so HttpOnly is not
  // applicable. Always emit SameSite=Lax; add Secure only over HTTPS (Secure on
  // plain HTTP makes the browser drop the cookie).
  function cookieFlags() {
    return '; path=/; SameSite=Lax' + (location.protocol === 'https:' ? '; Secure' : '');
  }

  function setCookie(name, value, days) {
    let expires = '';
    if (days) {
      const d = new Date();
      d.setTime(d.getTime() + days * 86400000);
      expires = '; expires=' + d.toUTCString();
    }
    document.cookie = name + '=' + encodeURIComponent(value) + expires + cookieFlags();
  }

  function deleteCookie(name) {
    document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT' + cookieFlags();
  }

  function pad2(n) {
    return n < 10 ? '0' + n : String(n);
  }

  function formatDateTime(value, withTime) {
    if (!value) return '';
    const d = new Date(value);
    if (isNaN(d.getTime())) return String(value);
    let s = d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
    if (withTime !== false) s += ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
    return s;
  }

  function escapeHtml(text) {
    return String(text)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  // 将用户文本安全转为可作 x-html 输出的 HTML：先整体转义，再把 @提及 替换为
  // 个人主页链接（/users/name/{username} 由后端 302 到 /users/{id}）。
  // 除本函数产出的 <a> 外不产生任何标签，因此可安全用于 x-html。
  function linkifyMentions(text, defBase) {
    const escaped = escapeHtml(text == null ? '' : text);
    const base = defBase || '';
    return escaped.replace(/@([\w一-龥-]{2,50})/g, function (_match, name) {
      return '<a href="' + base + '/users/name/' + encodeURIComponent(name) + '" class="cm-link">@' + name + '</a>';
    });
  }

  window.fetchCsrf = fetchCsrf;
  window.request = request;
  window.displaySuccess = displaySuccess;
  window.displayAlert = displayAlert;
  window.getCookie = getCookie;
  window.setCookie = setCookie;
  window.deleteCookie = deleteCookie;
  window.formatDateTime = formatDateTime;
  window.escapeHtml = escapeHtml;
  window.linkifyMentions = linkifyMentions;
})();
