// auth.js — SM2 password-encryption helper for member auth pages.
// The UJCMS backend (PasswordController, RegisterController, SettingsController)
// requires SM2-encrypted passwords; raw passwords are rejected. Templates that
// post a password (login, register, password-reset, settings/account) load this
// AFTER vendor/sm-crypto/dist/sm2.js and call window.sm2Encrypt(plain) → base64.
//
// Globals after load: window.sm2Encrypt(plain) -> Promise<string>

(function () {
  'use strict';

  let publicKeyPromise = null;

  function fetchPublicKey() {
    if (publicKeyPromise) return publicKeyPromise;
    const apiBase = window.visitApi;
    if (!apiBase) {
      return Promise.reject(new Error('window.visitApi not set — inc_js must run before auth.js'));
    }
    publicKeyPromise = fetch(apiBase + '/env/client-public-key', { credentials: 'include' })
      .then(r => {
        if (!r.ok) throw new Error('public-key fetch failed: ' + r.status);
        return r.text();
      })
      .then(key => {
        // Server returns the key as a JSON string (quoted) or as raw text;
        // strip quotes either way. KEEP the leading "04" uncompressed-point
        // marker — sm-crypto 0.3.x expects the full uncompressed public key.
        let k = key.trim();
        if (k.startsWith('"') && k.endsWith('"')) k = k.slice(1, -1);
        return k;
      })
      .catch(err => { publicKeyPromise = null; throw err; });
    return publicKeyPromise;
  }

  function hexToBase64(hex) {
    let binary = '';
    for (let i = 0; i < hex.length; i += 2) {
      binary += String.fromCharCode(parseInt(hex.substr(i, 2), 16));
    }
    return btoa(binary);
  }

  async function sm2Encrypt(plain) {
    if (!window.sm2 || typeof window.sm2.doEncrypt !== 'function') {
      throw new Error('sm-crypto not loaded — script-tag order is wrong');
    }
    const key = await fetchPublicKey();
    // Mode 1 (C1C3C2) matches Secures.sm2Decrypt on the server.
    const hex = window.sm2.doEncrypt(plain, key, 1);
    return hexToBase64('04' + hex);
  }

  window.sm2Encrypt = sm2Encrypt;
})();
