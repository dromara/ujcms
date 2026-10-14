import { defineStore } from 'pinia';
import { queryNotificationUnreadCount } from '@/api/interaction';

/**
 * 通知未读数（当前登录用户本人的收件箱）。AppHeader 在拥有 notification:page 权限时启动轮询（60秒），组件卸载时停止
 */
export const useNotificationStore = defineStore('ujcmsNotificationStore', {
  state: () => ({
    unreadCount: 0,
    pollingTimer: undefined as ReturnType<typeof setInterval> | undefined,
  }),
  actions: {
    async fetchUnreadCount() {
      try {
        this.unreadCount = Number(await queryNotificationUnreadCount()) || 0;
      } catch {
        // 轮询失败保持原值，等待下一次轮询
      }
    },
    startPolling() {
      if (this.pollingTimer != null) {
        return;
      }
      this.fetchUnreadCount();
      this.pollingTimer = setInterval(() => this.fetchUnreadCount(), 60000);
    },
    stopPolling() {
      if (this.pollingTimer != null) {
        clearInterval(this.pollingTimer);
        this.pollingTimer = undefined;
      }
    },
  },
});
