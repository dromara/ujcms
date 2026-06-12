import { defineStore } from 'pinia';
import _ from 'lodash';
import { queryConfig } from '@/api/login';

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
    },
    register: {
      largeAvatarSize: 960,
    },
  }),
  actions: {
    async initConfig() {
      const config = await queryConfig();
      this.base = _.omit(config, ['upload', 'register', 'security']);
      this.upload = config.upload;
      this.register = config.register;
      this.security = config.security;
    },
  },
});
