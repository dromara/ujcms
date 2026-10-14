import { defineStore } from 'pinia';
import { omit, pick } from 'lodash-es';
import { queryConfig as queryEnvConfig } from '@/api/login';
import { queryConfig as queryAdminConfig } from '@/api/config';
import { usePreferencesStore } from '@/stores/preferencesStore';

export const useSysConfigStore = defineStore('ujcmsSysConfigStore', {
  state: () => ({
    base: {
      uploadUrlPrefix: '/uploads',
      filesExtensionBlacklist: 'exe,com,bat,jsp,jspx,asp,aspx,php',
      uploadsExtensionBlacklist: 'exe,com,bat,jsp,jspx,asp,aspx,php,html,htm,xhtml,xml,shtml,shtm',
    } as any,
    upload: {
      imageTypes: 'jpg,jpeg,png,gif',
      imageInputAccept: '.jpg,.jpeg,.png,.gif',
      videoInputAccept: '.mp4,.m3u8',
      audioInputAccept: '.mp3,.ogg,.wav',
      mediaInputAccept: '.mp4,.m3u8,.mp3,.ogg,.wav',
      libraryInputAccept: '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx',
      docInputAccept: '.doc,.docx,.xls,.xlsx',
      fileInputAccept: '.zip,.7z,.gz,.bz2,.iso,.rar,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.jpg,.jpeg,.png,.gif,.mp4,.m3u8,.mp3,.ogg',
      imageLimitByte: 0,
      videoLimitByte: 0,
      audioLimitByte: 0,
      mediaLimitByte: 0,
      libraryLimitByte: 0,
      docLimitByte: 0,
      fileLimitByte: 0,
    },
    security: {
      passwordMinLength: 0,
      passwordMaxLength: 64,
      passwordStrength: 0,
      passwordPattern: '.*',
      ssrfList: [],
    } as any,
    register: {
      largeAvatarSize: 960,
    },
  }),
  actions: {
    async initConfig() {
      const config = await queryEnvConfig();
      // Merge rather than replace: /env/config is an anonymous whitelist projection that
      // strips the upload blacklists and the admin-only security fields. Replacing would
      // drop the defaults above and anything enrichForAdmin() already pulled in (this also
      // runs again after every 全局设置 save).
      this.base = { ...this.base, ...omit(config, ['upload', 'register', 'security']) };
      this.upload = config.upload;
      this.register = config.register;
      this.security = { ...this.security, ...config.security };
      // 控制台默认外观（console-shell D7）：登录前即按安装默认着色；全局设置保存后本方法重跑，跟随默认的用户即时换色
      usePreferencesStore().seedDefaults(config.console);
    },
    // Pull the admin-only fields hidden from /env/config (upload/file extension
    // blacklists, ssrfList, password aging policy, login-attempt thresholds).
    // Called from fetchCurrentUser once the session is authenticated; failures
    // are swallowed because the public defaults already populate the store.
    async enrichForAdmin() {
      try {
        const full = await queryAdminConfig();
        if (!full) return;
        this.base = { ...this.base, ...pick(full, ['uploadsExtensionBlacklist', 'filesExtensionBlacklist']) };
        if (full.security) {
          this.security = { ...this.security, ...full.security };
        }
      } catch {
        // ignore — the public bits in this.base / this.security are still valid
      }
    },
  },
});
