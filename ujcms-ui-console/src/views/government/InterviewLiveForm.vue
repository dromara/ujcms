<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { ArrowLeft, Edit, Link, Refresh } from '@element-plus/icons-vue';
import { perm } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import {
  queryInterview,
  beginInterview,
  endInterview,
  queryInterviewMessageList,
  createInterviewMessage,
  updateInterviewMessage,
  deleteInterviewMessage,
  queryInterviewQuestionPage,
  reviewInterviewQuestion,
  approveInterviewQuestion,
  rejectInterviewQuestion,
  deleteInterviewQuestion,
} from '@/api/interaction';
import LabelTip from '@/components/LabelTip.vue';
import InterviewSpeakerPicker from './InterviewSpeakerPicker.vue';
import { parseGuests, type SpeakerOption } from './interviewGuests';

defineOptions({
  name: 'InterviewLiveForm',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null } });
const emit = defineEmits({ 'update:modelValue': null, finished: null, edit: null });

const { t } = useI18n();
const currentSiteStore = useCurrentSiteStore();
const bean = ref<any>({});
const loading = ref<boolean>(false);
const required = { required: true, message: () => t('v.required') };

// ---- 访谈头部 ----
const statusType = (status?: number) => (status === 1 ? 'danger' : status === 2 ? 'success' : 'info');
const timeRange = computed(() => {
  if (!bean.value.beginDate) return '';
  const begin = dayjs(bean.value.beginDate).format('YYYY-MM-DD HH:mm');
  return bean.value.endDate ? t('interview.live.timeRange', { begin, end: dayjs(bean.value.endDate).format('YYYY-MM-DD HH:mm') }) : begin;
});
// 访谈详情页是动态页面，用当前站点的完整动态地址（含协议、域名、子目录）拼接，
// 控制台与前台不同源（开发期 :5173 与 :8080、生产期独立控制台域名）时也能正确打开；
// 访谈接口已校验数据属于当前站点，取当前站点即可。接口返回的 site.url 为相对路径，仅作兜底
const publicUrl = computed(() => {
  if (bean.value.id == null) return undefined;
  const base = currentSiteStore.currentSite?.dynamicFullUrl ?? bean.value.site?.url;
  if (base == null) return undefined;
  return `${String(base).replace(/\/$/, '')}/interview/${bean.value.id}`;
});

// ---- 发言人：主持人 + 嘉宾为候选项（一键切换），也允许输入其他发言人 ----
// 嘉宾来自“访谈嘉宾”文本（姓名（职务）、…），发言只记姓名，职务作为提示显示
const speakerOptions = computed<SpeakerOption[]>(() => {
  const list: SpeakerOption[] = [];
  const host = bean.value.host?.trim();
  if (host) list.push({ value: host, role: t('interviewMessage.speaker.host'), kind: 'host' });
  parseGuests(bean.value.guests).forEach((guest) => {
    if (!list.some((item) => item.value === guest.name)) {
      list.push({ value: guest.name, role: guest.title || t('interviewMessage.speaker.guest'), kind: 'guest' });
    }
  });
  return list;
});
const speakerOf = (speaker?: string) => speakerOptions.value.find((item) => item.value === speaker);

// ---- 访谈实录 ----
const messages = ref<any[]>([]);
const messageLoading = ref<boolean>(false);
const messageOrder = ref<'desc' | 'asc'>('desc');
// 接口按发言 ID 升序返回（前台也按此顺序展示）；直播时操作人员更关心最新一条，默认最新在前
const orderedMessages = computed(() => (messageOrder.value === 'desc' ? [...messages.value].reverse() : messages.value));
const messageForm = ref<any>();
const textInput = ref<any>();
const focusText = () => nextTick(() => textInput.value?.focus());
const messageValues = ref<{ speaker: string; text: string }>({ speaker: '', text: '' });
const messageSubmitting = ref<boolean>(false);
const editingId = ref<string>();
const editForm = ref<any>();
const editValues = ref<any>({});
const editSubmitting = ref<boolean>(false);

// ---- 网友提问 ----
const questionStatus = ref<number>(0);
const questions = ref<any[]>([]);
const questionLoading = ref<boolean>(false);
const questionSelection = ref<string[]>([]);
const questionPage = ref<number>(1);
const questionPageSize = ref<number>(10);
const questionTotal = ref<number>(0);
const pendingCount = ref<number>(0);
const reviewVisible = ref<boolean>(false);
const reviewForm = ref<any>();
const reviewValues = ref<any>({});
const reviewSubmitting = ref<boolean>(false);

let timer: ReturnType<typeof setInterval> | undefined;

const fetchBean = async () => {
  bean.value = await queryInterview(props.beanId as string);
};
const fetchMessages = async () => {
  messageLoading.value = true;
  try {
    messages.value = await queryInterviewMessageList({ interviewId: props.beanId });
  } finally {
    messageLoading.value = false;
  }
};
const fetchQuestions = async () => {
  questionLoading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryInterviewQuestionPage({
      Q_EQ_interviewId_Long: props.beanId,
      Q_EQ_status_Short: questionStatus.value !== -1 ? questionStatus.value : undefined,
      page: questionPage.value,
      pageSize: questionPageSize.value,
    });
    questions.value = content;
    questionTotal.value = Number(totalElements);
    questionSelection.value = questionSelection.value.filter((id) => content.some((row: any) => row.id === id));
  } finally {
    questionLoading.value = false;
  }
};
// 待审核数量始终显示在标题旁，不受当前筛选状态影响
const fetchPendingCount = async () => {
  const {
    page: { totalElements },
  } = await queryInterviewQuestionPage({ Q_EQ_interviewId_Long: props.beanId, Q_EQ_status_Short: 0, page: 1, pageSize: 1 });
  pendingCount.value = Number(totalElements);
};
const refreshQuestions = () => Promise.all([fetchQuestions(), fetchPendingCount()]);
const refreshAll = async () => {
  if (!props.modelValue || props.beanId == null) return;
  await fetchBean();
  await Promise.all([fetchMessages(), refreshQuestions()]);
};

// 直播中每 10 秒刷新提问；实录同步刷新以便多人协作，但正在改写某条发言时不打断
const stopTimer = () => {
  if (timer != null) {
    clearInterval(timer);
    timer = undefined;
  }
};
const resetTimer = () => {
  stopTimer();
  if (props.modelValue && bean.value.status === 1) {
    timer = setInterval(() => {
      refreshQuestions();
      if (editingId.value == null) fetchMessages();
    }, 10000);
  }
};
onBeforeUnmount(stopTimer);

watch(
  () => props.modelValue,
  async () => {
    if (props.modelValue && props.beanId != null) {
      loading.value = true;
      try {
        questionStatus.value = 0;
        questionPage.value = 1;
        questionSelection.value = [];
        editingId.value = undefined;
        messageOrder.value = 'desc';
        await fetchBean();
        messageValues.value = { speaker: bean.value.host?.trim() ?? '', text: '' };
        await Promise.all([fetchMessages(), refreshQuestions()]);
        resetTimer();
        focusText();
      } finally {
        loading.value = false;
      }
    } else {
      stopTimer();
    }
  },
  // immediate：组件在对话框已打开时挂载（如热更新、父组件重建）也能完成加载
  { immediate: true },
);

// 发言框中有未发布内容时先确认，避免误关闭丢失长篇发言
const close = async () => {
  if (messageValues.value.text.trim()) {
    await ElMessageBox.confirm(t('interviewMessage.unsent.confirm'), { type: 'warning' });
  }
  emit('update:modelValue', false);
};

const handleBegin = async () => {
  await ElMessageBox.confirm(t('interview.op.begin.confirm'), { type: 'warning' });
  await beginInterview(bean.value.id);
  await fetchBean();
  resetTimer();
  emit('finished');
  ElMessage.success(t('success'));
};
const handleEnd = async () => {
  await ElMessageBox.confirm(t('interview.op.end.confirm'), { type: 'warning' });
  await endInterview(bean.value.id);
  await fetchBean();
  resetTimer();
  emit('finished');
  ElMessage.success(t('success'));
};

const handleMessageSubmit = () => {
  messageForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    messageSubmitting.value = true;
    try {
      await createInterviewMessage({ interviewId: bean.value.id, speaker: messageValues.value.speaker, text: messageValues.value.text });
      messageValues.value.text = '';
      await fetchMessages();
      // 清空内容会异步触发 change 校验并显示“必填”，待列表刷新（校验早已完成）后再清除提示
      messageForm.value?.clearValidate('text');
      ElMessage.success(t('success'));
      focusText();
    } finally {
      messageSubmitting.value = false;
    }
  });
};
// Ctrl/Cmd + Enter 发布，Enter 仍为换行
const handleComposerKeydown = (event: Event) => {
  const keyboardEvent = event as KeyboardEvent;
  if (keyboardEvent.ctrlKey || keyboardEvent.metaKey) {
    event.preventDefault();
    handleMessageSubmit();
  }
};
const startEdit = (row: any) => {
  editValues.value = { id: row.id, interviewId: row.interviewId, speaker: row.speaker, text: row.text };
  editingId.value = row.id;
};
const cancelEdit = () => {
  editingId.value = undefined;
};
const saveEdit = () => {
  editForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    editSubmitting.value = true;
    try {
      await updateInterviewMessage(editValues.value);
      editingId.value = undefined;
      await fetchMessages();
      ElMessage.success(t('success'));
    } finally {
      editSubmitting.value = false;
    }
  });
};
const handleMessageDelete = async (ids: string[]) => {
  await deleteInterviewMessage(ids);
  if (editingId.value != null && ids.includes(editingId.value)) editingId.value = undefined;
  await fetchMessages();
  ElMessage.success(t('success'));
};

const handleQuestionStatusChange = () => {
  questionPage.value = 1;
  fetchQuestions();
};
const handleReview = (row: any) => {
  reviewValues.value = { id: row.id, status: row.status === 0 ? 1 : row.status, answer: row.answer };
  reviewVisible.value = true;
};
const handleReviewSubmit = () => {
  reviewForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    reviewSubmitting.value = true;
    try {
      await reviewInterviewQuestion(reviewValues.value);
      reviewVisible.value = false;
      await refreshQuestions();
      ElMessage.success(t('success'));
    } finally {
      reviewSubmitting.value = false;
    }
  });
};
const handleApprove = async (ids: string[]) => {
  await approveInterviewQuestion(ids);
  await refreshQuestions();
  ElMessage.success(t('success'));
};
const handleQuestionReject = async (ids: string[]) => {
  await rejectInterviewQuestion(ids);
  await refreshQuestions();
  ElMessage.success(t('success'));
};
const handleQuestionDelete = async (ids: string[]) => {
  await deleteInterviewQuestion(ids);
  await refreshQuestions();
  ElMessage.success(t('success'));
};
// 主持人在实录中转述网友提问：把提问填入发言框（发言人切回主持人），由操作人员确认后发布
const handleQuote = (row: any) => {
  const host = bean.value.host?.trim();
  if (host) messageValues.value.speaker = host;
  messageValues.value.text = t('interviewQuestion.quote.text', { name: row.name, text: row.text });
  focusText();
};

defineExpose({ refresh: refreshAll });
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    fullscreen
    destroy-on-close
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    class="interview-live-dialog"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <template #header>
      <!-- 工具栏与编辑对话框一致：操作按钮在左，状态标签在右 -->
      <div class="toolbar flex flex-wrap items-center gap-2">
        <el-button :icon="ArrowLeft" @click="close">{{ $t('back') }}</el-button>
        <el-button v-if="bean.status !== 1" type="primary" :disabled="perm('interview:live')" @click="() => handleBegin()">{{ $t('interview.op.begin') }}</el-button>
        <el-button v-else type="danger" :disabled="perm('interview:live')" @click="() => handleEnd()">{{ $t('interview.op.end') }}</el-button>
        <el-button :icon="Edit" :disabled="perm('interview:update')" @click="() => $emit('edit', bean.id)">{{ $t('edit') }}</el-button>
        <el-button v-if="publicUrl" tag="a" :href="publicUrl" target="_blank" rel="noopener" :icon="Link">{{ $t('interview.live.publicPage') }}</el-button>
        <span class="flex-1"></span>
        <el-tag v-if="bean.status != null" :type="statusType(bean.status)" disable-transitions>{{ $t(`interview.status.${bean.status}`) }}</el-tag>
        <el-tag v-if="bean.id != null" :type="bean.published ? 'success' : 'info'" disable-transitions>
          {{ $t(bean.published ? 'interview.published.yes' : 'interview.published.no') }}
        </el-tag>
        <el-tag v-if="bean.id != null" :type="bean.questionEnabled ? 'success' : 'info'" disable-transitions>
          {{ $t(bean.questionEnabled ? 'interview.questionEnabled.on' : 'interview.questionEnabled.off') }}
        </el-tag>
      </div>
      <div class="mt-3 text-lg font-bold leading-tight">{{ bean.title }}</div>
      <div class="mt-1 flex flex-wrap gap-x-4 text-sm text-gray-secondary">
        <span v-if="bean.org">{{ $t('interview.org') }}{{ $t('punct.colon') }}{{ bean.org }}</span>
        <span v-if="bean.host">{{ $t('interview.host') }}{{ $t('punct.colon') }}{{ bean.host }}</span>
        <span v-if="bean.guests">{{ $t('interview.guests') }}{{ $t('punct.colon') }}{{ bean.guests }}</span>
        <span v-if="timeRange">{{ $t('interview.live.time') }}{{ $t('punct.colon') }}{{ timeRange }}</span>
      </div>
      <el-alert
        v-if="bean.status != null"
        :type="bean.status === 1 ? 'warning' : 'info'"
        :title="$t(`interview.live.hint.${bean.status}`)"
        :closable="false"
        show-icon
        class="live-hint mt-2"
      />
    </template>
    <div v-loading="loading" class="live-body">
      <!-- 访谈实录：发言框在上，时间线在下 -->
      <section class="pane flex-1 min-w-0">
        <div class="pane-header">
          <span class="font-bold">{{ $t('interview.tab.message') }}</span>
          <span class="text-sm text-gray-secondary">{{ $t('interviewMessage.count', { count: messages.length }) }}</span>
          <span class="flex-1"></span>
          <el-radio-group v-model="messageOrder" size="small">
            <el-radio-button value="desc">{{ $t('interviewMessage.order.desc') }}</el-radio-button>
            <el-radio-button value="asc">{{ $t('interviewMessage.order.asc') }}</el-radio-button>
          </el-radio-group>
        </div>
        <el-form ref="messageForm" :model="messageValues" :disabled="perm('interviewMessage:create')" class="composer">
          <el-form-item prop="text" :rules="required" class="!mb-4">
            <el-input
              ref="textInput"
              v-model="messageValues.text"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 10 }"
              maxlength="65535"
              :placeholder="$t('interviewMessage.text.placeholder')"
              @keydown.enter="handleComposerKeydown"
            ></el-input>
          </el-form-item>
          <div class="flex items-center gap-2">
            <span class="text-sm text-gray-regular">{{ $t('interviewMessage.speaker') }}</span>
            <el-form-item prop="speaker" :rules="required" :show-message="false" class="!mb-0">
              <interview-speaker-picker v-model="messageValues.speaker" :options="speakerOptions" />
            </el-form-item>
            <span class="flex-1 text-xs text-gray-secondary">{{ $t('interviewMessage.sendHint') }}</span>
            <el-button type="primary" :loading="messageSubmitting" @click="() => handleMessageSubmit()">{{ $t('interviewMessage.op.send') }}</el-button>
          </div>
        </el-form>
        <div v-loading="messageLoading" class="pane-body">
          <div v-if="messages.length <= 0" class="empty">{{ $t('interviewMessage.empty') }}</div>
          <div v-for="row in orderedMessages" :key="row.id" class="message-item">
            <div class="avatar" :class="`avatar--${speakerOf(row.speaker)?.kind ?? 'other'}`">{{ (row.speaker ?? '').slice(0, 1) }}</div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 text-sm">
                <span class="font-bold">{{ row.speaker }}</span>
                <el-tag v-if="speakerOf(row.speaker)" :type="speakerOf(row.speaker)?.kind === 'host' ? 'primary' : 'success'" size="small" disable-transitions>
                  {{ speakerOf(row.speaker)?.role }}
                </el-tag>
                <span class="text-gray-secondary">{{ dayjs(row.created).format('MM-DD HH:mm:ss') }}</span>
                <span class="flex-1"></span>
                <template v-if="editingId !== row.id">
                  <el-button type="primary" :disabled="perm('interviewMessage:update')" size="small" link @click="() => startEdit(row)">{{ $t('edit') }}</el-button>
                  <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleMessageDelete([row.id])">
                    <template #reference>
                      <el-button type="primary" :disabled="perm('interviewMessage:delete')" size="small" link>{{ $t('delete') }}</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </div>
              <div v-if="editingId !== row.id" class="mt-1 whitespace-pre-line break-words leading-relaxed">{{ row.text }}</div>
              <!-- v-for 内的字符串 ref 会被收集为数组，改用函数 ref；同一时刻只有一条发言处于编辑态 -->
              <el-form v-else :ref="(el) => (editForm = el)" :model="editValues" class="mt-2">
                <el-form-item prop="text" :rules="required" class="!mb-4">
                  <el-input v-model="editValues.text" type="textarea" :autosize="{ minRows: 2, maxRows: 10 }" maxlength="65535"></el-input>
                </el-form-item>
                <div class="flex items-center gap-2">
                  <span class="text-sm text-gray-regular">{{ $t('interviewMessage.speaker') }}</span>
                  <el-form-item prop="speaker" :rules="required" :show-message="false" class="!mb-0">
                    <interview-speaker-picker v-model="editValues.speaker" :options="speakerOptions" size="small" />
                  </el-form-item>
                  <span class="flex-1"></span>
                  <el-button type="primary" :loading="editSubmitting" @click="() => saveEdit()">{{ $t('save') }}</el-button>
                  <el-button @click="() => cancelEdit()">{{ $t('cancel') }}</el-button>
                </div>
              </el-form>
            </div>
          </div>
        </div>
      </section>
      <!-- 网友提问：与实录并列，直播时无需切换标签即可边发言边审核 -->
      <aside class="pane question-pane shrink-0">
        <div class="pane-header">
          <span class="font-bold">{{ $t('interview.tab.question') }}</span>
          <el-tag v-if="pendingCount > 0" type="danger" size="small" round disable-transitions>{{ $t('interviewQuestion.status.0') }} {{ pendingCount }}</el-tag>
          <span class="flex-1"></span>
          <el-button :icon="Refresh" circle size="small" :loading="questionLoading" @click="() => refreshQuestions()"></el-button>
        </div>
        <div class="px-3 pt-2">
          <el-radio-group v-model="questionStatus" size="small" @change="handleQuestionStatusChange">
            <el-radio-button :value="0">{{ $t('interviewQuestion.status.0') }}</el-radio-button>
            <el-radio-button :value="1">{{ $t('interviewQuestion.status.1') }}</el-radio-button>
            <el-radio-button :value="2">{{ $t('interviewQuestion.status.2') }}</el-radio-button>
            <el-radio-button :value="-1">{{ $t('all') }}</el-radio-button>
          </el-radio-group>
          <div v-if="bean.status === 1" class="mt-1 text-xs text-gray-secondary">{{ $t('interviewQuestion.autoRefresh') }}</div>
        </div>
        <div v-if="questionSelection.length > 0" class="batch-bar">
          <span class="text-sm">{{ $t('interviewQuestion.selected', { count: questionSelection.length }) }}</span>
          <el-button size="small" :disabled="perm('interviewQuestion:review')" @click="() => handleApprove(questionSelection)">{{ $t('interviewQuestion.op.approve') }}</el-button>
          <el-button size="small" :disabled="perm('interviewQuestion:review')" @click="() => handleQuestionReject(questionSelection)">{{
            $t('interviewQuestion.op.reject')
          }}</el-button>
          <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleQuestionDelete(questionSelection)">
            <template #reference>
              <el-button size="small" :disabled="perm('interviewQuestion:delete')">{{ $t('delete') }}</el-button>
            </template>
          </el-popconfirm>
          <el-button size="small" link @click="() => (questionSelection = [])">{{ $t('cancel') }}</el-button>
        </div>
        <div v-loading="questionLoading" class="pane-body">
          <div v-if="questions.length <= 0" class="empty">{{ $t(`interviewQuestion.empty.${questionStatus === -1 ? 'all' : questionStatus}`) }}</div>
          <el-checkbox-group v-model="questionSelection" class="block">
            <div v-for="row in questions" :key="row.id" class="question-item">
              <el-checkbox :value="row.id" class="question-check"></el-checkbox>
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 text-sm">
                  <span class="font-bold truncate">{{ row.name }}</span>
                  <span class="text-gray-secondary shrink-0">{{ dayjs(row.created).format('MM-DD HH:mm') }}</span>
                  <span class="flex-1"></span>
                  <el-tag v-if="row.status === 0" type="warning" size="small" disable-transitions>{{ $t('interviewQuestion.status.0') }}</el-tag>
                  <el-tag v-else-if="row.status === 1" type="success" size="small" disable-transitions>{{ $t('interviewQuestion.status.1') }}</el-tag>
                  <el-tag v-else-if="row.status === 2" type="danger" size="small" disable-transitions>{{ $t('interviewQuestion.status.2') }}</el-tag>
                </div>
                <div class="mt-1 whitespace-pre-line break-words leading-relaxed">{{ row.text }}</div>
                <div v-if="row.answer" class="mt-1 text-sm text-primary whitespace-pre-line break-words">{{ $t('interviewQuestion.answer') }}{{ $t('punct.colon') }}{{ row.answer }}</div>
                <div class="mt-1 flex flex-wrap gap-x-1">
                  <el-button v-if="row.status !== 1" type="primary" :disabled="perm('interviewQuestion:review')" size="small" link @click="() => handleApprove([row.id])">
                    {{ $t('interviewQuestion.op.approve') }}
                  </el-button>
                  <el-button v-if="row.status !== 2" type="primary" :disabled="perm('interviewQuestion:review')" size="small" link @click="() => handleQuestionReject([row.id])">
                    {{ $t('interviewQuestion.op.reject') }}
                  </el-button>
                  <el-button type="primary" :disabled="perm('interviewQuestion:review')" size="small" link @click="() => handleReview(row)">
                    {{ $t('interviewQuestion.op.review') }}
                  </el-button>
                  <el-button type="primary" :disabled="perm('interviewMessage:create')" size="small" link @click="() => handleQuote(row)">
                    {{ $t('interviewQuestion.op.quote') }}
                  </el-button>
                  <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleQuestionDelete([row.id])">
                    <template #reference>
                      <el-button type="primary" :disabled="perm('interviewQuestion:delete')" size="small" link>{{ $t('delete') }}</el-button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>
            </div>
          </el-checkbox-group>
        </div>
        <el-pagination
          v-model:current-page="questionPage"
          v-model:page-size="questionPageSize"
          :total="questionTotal"
          :pager-count="5"
          layout="total, prev, pager, next"
          size="small"
          background
          class="justify-end px-3 py-2"
          @current-change="() => fetchQuestions()"
        ></el-pagination>
      </aside>
    </div>
    <el-dialog v-model="reviewVisible" :title="$t('interviewQuestion.op.review')" width="640px" append-to-body>
      <el-form ref="reviewForm" :model="reviewValues" label-position="top">
        <el-form-item prop="status" :rules="required">
          <template #label><label-tip message="interviewQuestion.status" /></template>
          <el-radio-group v-model="reviewValues.status">
            <el-radio-button :value="1">{{ $t('interviewQuestion.status.1') }}</el-radio-button>
            <el-radio-button :value="2">{{ $t('interviewQuestion.status.2') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item prop="answer">
          <template #label><label-tip message="interviewQuestion.answer" help /></template>
          <el-input v-model="reviewValues.answer" type="textarea" :rows="4" maxlength="1000"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="() => (reviewVisible = false)">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="() => handleReviewSubmit()">{{ $t('submit') }}</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<style lang="scss">
// 对话框经 teleport 渲染，scoped 的 :deep 无法作用到 .el-dialog 本身，故用组件独有的 class 写全局规则。
// 全屏对话框改为纵向弹性布局：头部自适应，主体占满剩余高度，避免用魔法数字计算高度
.interview-live-dialog.el-dialog.is-fullscreen {
  display: flex;
  flex-direction: column;
  .el-dialog__body {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
    overflow: auto;
    padding-top: 8px;
  }
}
</style>

<style lang="scss" scoped>
// 工具栏用 gap 控制间距，去掉 Element Plus 相邻按钮的默认左边距，保证间距一致
.toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
.live-hint {
  padding: 6px 12px;
}
.live-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 12px;
}
.pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.pane-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.pane-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 12px;
}
.composer {
  // 上下留白与发言人一行的上间距（16px）对齐；发言人为空只以输入框红框提示，不再在下方悬挂文字
  padding: 12px 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 12px 0;
  padding: 6px 10px;
  border-radius: 4px;
  background: var(--el-color-primary-light-9);
}
.message-item,
.question-item {
  display: flex;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  &:last-child {
    border-bottom: 0;
  }
}
.question-check {
  height: 20px;
  margin-right: 0;
  margin-top: 2px;
}
.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 9999px;
  font-size: 14px;
  color: #fff;
  background: var(--el-color-info);
  &--host {
    background: var(--el-color-primary);
  }
  &--guest {
    background: var(--el-color-success);
  }
}
.empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
@media (min-width: 992px) {
  .live-body {
    flex-direction: row;
  }
  .question-pane {
    width: 420px;
  }
}
</style>
