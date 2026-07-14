import { defineStore } from 'pinia';
import { queryConversationUnreadCount } from '@/api/interaction';

/**
 * 私信未读数。AppHeader 在拥有 message:list 权限时启动轮询（60秒），组件卸载时停止
 */
export const useMessageStore = defineStore('ujcmsMessageStore', {
  state: () => ({
    unreadCount: 0,
    pollingTimer: undefined as ReturnType<typeof setInterval> | undefined,
  }),
  actions: {
    async fetchUnreadCount() {
      try {
        this.unreadCount = Number(await queryConversationUnreadCount()) || 0;
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
