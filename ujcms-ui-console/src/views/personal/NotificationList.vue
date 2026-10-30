<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { pageSizes, pageLayout } from '@/utils/common';
import { useRouteState, enumParam } from '@/composables/useRouteState';
import { queryNotificationPage, readNotifications, readAllNotifications, deleteReadNotifications } from '@/api/interaction';
import { useNotificationStore } from '@/stores/notificationStore';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';

defineOptions({
  name: 'NotificationList',
});
const { t } = useI18n();
const notificationStore = useNotificationStore();
const currentSiteStore = useCurrentSiteStore();

// 导航上下文（状态行 全部 / 未读）存于路由 query：刷新、页签往返后恢复（console-view-state）
const { status } = useRouteState({ status: enumParam('all', ['all', 'unread']) });

const list = ref<any[]>([]);
const loading = ref<boolean>(false);
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryNotificationPage({ page: currentPage.value, pageSize: pageSize.value, isRead: status.value === 'unread' ? false : undefined });
    list.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
const handleStatusChange = () => {
  currentPage.value = 1;
  fetchData();
};

// 平铺键：te() 对 'a.b.c' 形式的键不可靠，以 t(key) !== key 判断是否有译文
const typeLabel = (type: number): string => {
  const key = `notification.type.${type}`;
  const label = t(key);
  return label !== key ? label : t('notification.type.other');
};
// sender 为空是系统发出；ID 为 0 是匿名网友（与会员中心一致）
const senderName = (item: any): string => {
  if (!item.sender) return t('notification.inbox.system');
  if (String(item.sender.id) === '0') return t('notification.inbox.anonymous');
  return item.sender.nickname || item.sender.username;
};
// 引用类通知（评论回复、关注……）按关联对象取地址，系统公告、好友、投稿退回用显式 url。
// 控制台与前台不一定同源（开发期 :5173 对 :8080、多域名站点）：关联对象取完整地址（与页头网站首页、最近更新一致）；
// 显式 url 是站内相对路径（主题同源直接可用），按当前站点的动态完整地址补全，关联对象的相对地址同样兜底补全；只放行 http(s)
const linkOf = (item: any): string | undefined => {
  const link = item.article?.fullUrl || item.channel?.fullUrl || item.article?.url || item.channel?.url || item.url;
  if (!link) return undefined;
  const site = currentSiteStore.currentSite;
  const base = site?.dynamicFullUrl || site?.fullUrl || window.location.origin;
  try {
    const url = new URL(link, base);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : undefined;
  } catch {
    return undefined;
  }
};
const formatTime = (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm');

const handleOpen = async (item: any) => {
  const link = linkOf(item);
  // 先在用户手势内打开新页（await 之后再 open 会被弹窗拦截），再标记已读
  if (link) window.open(link, '_blank');
  if (item.read) return;
  await readNotifications([item.id]);
  item.read = true;
  notificationStore.fetchUnreadCount();
  if (status.value === 'unread') fetchData();
};
const handleReadAll = async () => {
  await readAllNotifications();
  ElMessage.success(t('notification.inbox.readAllSuccess'));
  notificationStore.fetchUnreadCount();
  fetchData();
};
const handleClearRead = async () => {
  await ElMessageBox.confirm(t('notification.inbox.confirmClearRead'), { type: 'warning' });
  await deleteReadNotifications();
  ElMessage.success(t('notification.inbox.clearReadSuccess'));
  currentPage.value = 1;
  fetchData();
};

onMounted(() => {
  fetchData();
});
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-3">
      <el-radio-group v-model="status" @change="handleStatusChange">
        <el-radio-button value="all">{{ $t('all') }}</el-radio-button>
        <el-radio-button value="unread">{{ $t('notification.inbox.unread') }}</el-radio-button>
      </el-radio-group>
      <div class="space-x-2">
        <el-button :disabled="notificationStore.unreadCount <= 0" @click="handleReadAll">{{ $t('notification.inbox.readAll') }}</el-button>
        <el-button type="danger" plain @click="handleClearRead">{{ $t('notification.inbox.clearRead') }}</el-button>
      </div>
    </div>
    <div class="bg-white rounded-sm">
      <div v-loading="loading">
        <div v-if="list.length <= 0" class="px-4 py-12 text-center text-gray-400">{{ $t('notification.inbox.empty') }}</div>
        <div v-for="item in list" :key="item.id" class="px-4 py-3 border-b cursor-pointer hover:bg-gray-50" @click="() => handleOpen(item)">
          <div class="flex items-center space-x-2">
            <el-tag v-if="!item.read" type="danger" size="small" disable-transitions>{{ $t('notification.inbox.unread') }}</el-tag>
            <el-tag type="info" size="small" disable-transitions>{{ typeLabel(item.type) }}</el-tag>
            <span class="truncate" :class="item.read ? 'text-gray-secondary' : 'font-semibold'">{{ item.title }}</span>
          </div>
          <div v-if="item.text" class="mt-1 text-sm truncate" :class="item.read ? 'text-gray-400' : 'text-gray-secondary'">{{ item.text }}</div>
          <div class="mt-1 text-xs text-gray-400 space-x-2">
            <span>{{ senderName(item) }}</span>
            <span>{{ formatTime(item.created) }}</span>
          </div>
        </div>
      </div>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="pageSizes"
        :layout="pageLayout"
        size="small"
        background
        class="justify-end px-3 py-2"
        @size-change="() => fetchData()"
        @current-change="() => fetchData()"
      />
    </div>
  </div>
</template>
