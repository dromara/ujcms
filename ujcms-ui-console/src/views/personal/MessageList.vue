<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm, hasPermission } from '@/stores/useCurrentUser';
import { queryConversationPage, queryMessagePage, sendMessage, readConversation, deleteConversation } from '@/api/interaction';
import { useMessageStore } from '@/stores/messageStore';
import MessageForm from './MessageForm.vue';

defineOptions({
  name: 'MessageList',
});
const { t } = useI18n();
const messageStore = useMessageStore();

const conversations = ref<any[]>([]);
const conversationPage = ref<number>(1);
const conversationTotalPages = ref<number>(1);
const conversationLoading = ref<boolean>(false);
// 当前打开的会话（含 peer）
const current = ref<any>();
// 会话内消息，时间正序（旧在前，新在后）
const messages = ref<any[]>([]);
const messageLoading = ref<boolean>(false);
const hasEarlier = ref<boolean>(false);
const replyText = ref<string>('');
const sendLoading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const formBulk = ref<boolean>(false);
const messagePane = ref<any>();
let pollingTimer: ReturnType<typeof setInterval> | undefined;

const MESSAGE_PAGE_SIZE = 20;
const earliestId = computed(() => (messages.value.length > 0 ? messages.value[0].id : undefined));
const latestId = computed(() => (messages.value.length > 0 ? messages.value[messages.value.length - 1].id : undefined));

const isReceived = (message: any) => String(message.senderId) === String(current.value?.peer?.id);
const formatTime = (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm');

const fetchConversations = async (page = 1) => {
  conversationLoading.value = true;
  try {
    const {
      content,
      page: { totalPages },
    } = await queryConversationPage({ page, pageSize: 20 });
    conversations.value = page === 1 ? content : [...conversations.value, ...content];
    conversationPage.value = page;
    conversationTotalPages.value = Number(totalPages);
  } finally {
    conversationLoading.value = false;
  }
};

const scrollToBottom = () => {
  setTimeout(() => {
    const pane = messagePane.value;
    if (pane) pane.scrollTop = pane.scrollHeight;
  });
};

const markRead = async (conversation: any) => {
  await readConversation(conversation.peer.id);
  conversation.unread = 0;
  messageStore.fetchUnreadCount();
};

const openConversation = async (conversation: any) => {
  current.value = conversation;
  messages.value = [];
  replyText.value = '';
  messageLoading.value = true;
  try {
    const {
      content,
      page: { totalPages },
    } = await queryMessagePage({ peerId: conversation.peer.id, page: 1, pageSize: MESSAGE_PAGE_SIZE });
    // 接口按ID倒序返回，展示时正序（旧在前，新在后）
    messages.value = content.slice().reverse();
    hasEarlier.value = Number(totalPages) > 1;
  } finally {
    messageLoading.value = false;
  }
  scrollToBottom();
  markRead(conversation);
};

const loadEarlier = async () => {
  if (current.value == null || earliestId.value == null) return;
  const {
    content,
    page: { totalPages },
  } = await queryMessagePage({ peerId: current.value.peer.id, beforeId: earliestId.value, page: 1, pageSize: MESSAGE_PAGE_SIZE });
  messages.value = [...content.slice().reverse(), ...messages.value];
  hasEarlier.value = Number(totalPages) > 1;
};

// 打开会话期间每30秒轮询新消息
const pollNewMessages = async () => {
  if (current.value == null || latestId.value == null) return;
  const { content } = await queryMessagePage({ peerId: current.value.peer.id, afterId: latestId.value, page: 1, pageSize: 100 });
  if (content.length > 0) {
    messages.value = [...messages.value, ...content.slice().reverse()];
    scrollToBottom();
    markRead(current.value);
    fetchConversations();
  }
};

const handleReply = async () => {
  const text = replyText.value.trim();
  if (!text || current.value == null) return;
  sendLoading.value = true;
  try {
    const sent = await sendMessage({ userId: current.value.peer.id, text });
    messages.value = [...messages.value, sent];
    replyText.value = '';
    scrollToBottom();
    fetchConversations();
  } finally {
    sendLoading.value = false;
  }
};

const handleDeleteConversation = async () => {
  if (current.value == null) return;
  await ElMessageBox.confirm(t('message.confirmDeleteConversation'), { type: 'warning' });
  await deleteConversation(current.value.peer.id);
  ElMessage.success(t('success'));
  current.value = undefined;
  messages.value = [];
  fetchConversations();
  messageStore.fetchUnreadCount();
};

const openForm = (bulk: boolean) => {
  formBulk.value = bulk;
  formVisible.value = true;
};

const handleSent = () => {
  fetchConversations();
  if (current.value != null) {
    openConversation(current.value);
  }
};

onMounted(() => {
  fetchConversations();
  pollingTimer = setInterval(pollNewMessages, 30000);
});
onUnmounted(() => {
  if (pollingTimer != null) clearInterval(pollingTimer);
});
</script>

<template>
  <div>
    <div class="mb-3 space-x-2">
      <el-button type="primary" :disabled="perm('message:send')" @click="() => openForm(false)">{{ $t('message.op.send') }}</el-button>
      <el-button v-if="hasPermission('message:bulkSend')" @click="() => openForm(true)">{{ $t('message.op.bulkSend') }}</el-button>
    </div>
    <div class="flex bg-white rounded-sm" style="height: calc(100vh - 220px)">
      <div class="w-72 border-r overflow-y-auto flex-none">
        <div v-loading="conversationLoading">
          <div v-if="conversations.length <= 0" class="p-3 text-gray-secondary">{{ $t('message.noConversation') }}</div>
          <div
            v-for="item in conversations"
            :key="item.id"
            class="p-3 border-b cursor-pointer hover:bg-gray-50"
            :class="{ 'bg-gray-100': current?.id === item.id }"
            @click="() => openConversation(item)"
          >
            <div class="flex items-center">
              <el-badge :value="Number(item.unread)" :hidden="Number(item.unread) <= 0" :max="99">
                <el-avatar :size="36" :src="item.peer?.avatar">{{ (item.peer?.nickname ?? item.peer?.username ?? '?').charAt(0) }}</el-avatar>
              </el-badge>
              <div class="ml-2 min-w-0 flex-1">
                <div class="flex items-baseline justify-between">
                  <span class="truncate">{{ item.peer?.nickname ?? item.peer?.username }}</span>
                  <span class="ml-1 text-xs text-gray-400 flex-none">{{ formatTime(item.lastTime) }}</span>
                </div>
                <div class="text-xs text-gray-400 truncate">{{ item.lastText }}</div>
              </div>
            </div>
          </div>
          <div v-if="conversationPage < conversationTotalPages" class="p-2 text-center">
            <el-button link size="small" @click="() => fetchConversations(conversationPage + 1)">{{ $t('message.op.loadMore') }}</el-button>
          </div>
        </div>
      </div>
      <div class="flex-1 flex flex-col min-w-0">
        <template v-if="current != null">
          <div class="flex items-center justify-between px-3 py-2 border-b">
            <span>{{ current.peer?.nickname ?? current.peer?.username }}</span>
            <el-button link type="danger" :disabled="perm('message:delete')" @click="handleDeleteConversation">
              {{ $t('message.op.deleteConversation') }}
            </el-button>
          </div>
          <div ref="messagePane" v-loading="messageLoading" class="flex-1 overflow-y-auto p-3">
            <div v-if="hasEarlier" class="text-center mb-2">
              <el-button link size="small" @click="loadEarlier">{{ $t('message.op.loadEarlier') }}</el-button>
            </div>
            <div v-for="item in messages" :key="item.id" class="mb-3 flex" :class="isReceived(item) ? 'justify-start' : 'justify-end'">
              <div class="max-w-[70%]">
                <div class="text-xs text-gray-400 mb-1" :class="isReceived(item) ? 'text-left' : 'text-right'">{{ formatTime(item.created) }}</div>
                <div class="px-3 py-2 rounded whitespace-pre-wrap break-words" :class="isReceived(item) ? 'bg-gray-100' : 'bg-blue-100'">{{ item.text }}</div>
              </div>
            </div>
          </div>
          <div class="border-t p-3">
            <el-input v-model="replyText" type="textarea" :rows="3" maxlength="1000" show-word-limit></el-input>
            <div class="mt-2 text-right">
              <el-button type="primary" :loading="sendLoading" :disabled="perm('message:send') || !replyText.trim()" @click="handleReply">
                {{ $t('message.op.send') }}
              </el-button>
            </div>
          </div>
        </template>
        <div v-else class="flex-1 flex items-center justify-center text-gray-400">{{ $t('message.selectConversation') }}</div>
      </div>
    </div>
    <message-form v-model="formVisible" :bulk="formBulk" @finished="handleSent" />
  </div>
</template>
