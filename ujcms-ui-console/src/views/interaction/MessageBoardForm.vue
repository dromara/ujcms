<script setup lang="ts">
import { computed, ref, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { queryMessageBoard, createMessageBoard, updateMessageBoard, deleteMessageBoard } from '@/api/interaction';
import { aiReplyDraft, updateAiLogAccepted } from '@/api/ai';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import Tinymce from '@/components/Tinymce';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'MessageBoardForm',
});
// 留言类型由列表页传入：类型就在同一屏（“类型管理”）增删，挂载时自取一次会过期
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  types: { type: Array as PropType<any[]>, required: true },
});
defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const focus = ref<any>();
const values = ref<any>({});
const messageBoardTypeList = computed(() => props.types);

// AI 拟稿（docs/spec/ai/ugc-moderation.md §6）：仅填入答复编辑框供修改，绝不自动发送；
// 发送仍是既有的人工答复动作。工作人员提交含拟稿的答复时标记采纳（客户端启发式）
const currentSiteStore = useCurrentSiteStore();
const aiDraftAvailable = computed(() => currentUser.commercialTier >= 2 && currentSiteStore.currentSite?.ai?.replyDraft === true && !perm('ai:replyDraft'));
const aiDraftLoading = ref<boolean>(false);
const aiDraftLogId = ref<string>();
const escapeHtml = (text: string) =>
  text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const handleAiDraft = async () => {
  aiDraftLoading.value = true;
  try {
    const { draft, logId } = await aiReplyDraft({ type: 'messageBoard', refId: values.value.id });
    values.value.replyText = draft
      .split(/\n+/)
      .filter((line: string) => line.trim() !== '')
      .map((line: string) => `<p>${escapeHtml(line)}</p>`)
      .join('');
    aiDraftLogId.value = logId;
    ElMessage.success(t('success'));
  } finally {
    aiDraftLoading.value = false;
  }
};
const handleBeforeSubmit = (formValues: any) => {
  // 拟稿已填入且答复非空时视为采纳（docs/spec/ai/ai-foundation.md §5 的采纳标记）
  if (aiDraftLogId.value != null && formValues.replyText != null && formValues.replyText !== '') {
    updateAiLogAccepted(aiDraftLogId.value).catch(() => undefined);
    aiDraftLogId.value = undefined;
  }
};
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('messageBoard.label')"
    :query-bean="queryMessageBoard"
    :create-bean="createMessageBoard"
    :update-bean="updateMessageBoard"
    :delete-bean="deleteMessageBoard"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ typeId: messageBoardTypeList[0]?.id, userType: 1, open: true, status: 0 })"
    :to-values="(bean) => ({ ...bean })"
    perms="messageBoard"
    :model-value="modelValue"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @before-submit="handleBeforeSubmit"
    @finished="() => $emit('finished')"
  >
    <template #header-status="{ isEdit }">
      <template v-if="isEdit">
        <span v-if="values.status != null">
          <el-tag v-if="values.status === 0" type="success" size="small" disable-transitions>{{ $t(`messageBoard.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 1" type="info" size="small" disable-transitions>{{ $t(`messageBoard.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 2" type="danger" size="small" disable-transitions>{{ $t(`messageBoard.status.${values.status}`) }}</el-tag>
          <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
        </span>
      </template>
    </template>
    <template #default="{ isEdit }">
      <el-row>
        <el-col :span="24">
          <el-form-item prop="typeId" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="messageBoard.type" /></template>
            <el-select v-model="values.typeId" class="w-full">
              <el-option v-for="item in messageBoardTypeList" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="title" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="messageBoard.title" /></template>
            <el-input ref="focus" v-model="values.title" maxlength="150"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="text" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="messageBoard.text" /></template>
            <el-input v-model="values.text" type="textarea" :rows="8" maxlength="65535"></el-input>
          </el-form-item>
        </el-col>
        <el-col v-if="isEdit" :span="24">
          <el-form-item prop="replyText">
            <template #label><label-tip message="messageBoard.replyText" /></template>
            <div v-if="aiDraftAvailable" class="mb-1 w-full">
              <el-button size="small" :loading="aiDraftLoading" @click="() => handleAiDraft()">{{ $t('aiModeration.op.draft') }}</el-button>
            </div>
            <tinymce v-model="values.replyText" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="contact">
            <template #label><label-tip message="messageBoard.contact" /></template>
            <el-input v-model="values.contact" maxlength="30"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="nickname">
            <template #label><label-tip message="messageBoard.nickname" /></template>
            <el-input v-model="values.nickname" maxlength="30"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="phone">
            <template #label><label-tip message="messageBoard.phone" /></template>
            <el-input v-model="values.phone" maxlength="30"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="email">
            <template #label><label-tip message="messageBoard.email" /></template>
            <el-input v-model="values.email" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="address">
            <template #label><label-tip message="messageBoard.address" /></template>
            <el-input v-model="values.address" maxlength="150"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="open">
            <template #label><label-tip message="messageBoard.open" /></template>
            <el-switch v-model="values.open" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="recommended">
            <template #label><label-tip message="messageBoard.recommended" /></template>
            <el-switch v-model="values.recommended" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="replied">
            <template #label><label-tip message="messageBoard.replied" /></template>
            <el-switch :model-value="values.replied" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="ip">
            <template #label><label-tip message="messageBoard.ip" /></template>
            <el-input :model-value="values.ip" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="user">
            <template #label><label-tip message="messageBoard.user" /></template>
            <el-input :model-value="values.user?.username" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="created">
            <template #label><label-tip message="messageBoard.created" /></template>
            <el-date-picker :model-value="values.created" type="datetime" class="w-full" disabled></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="replyUser">
            <template #label><label-tip message="messageBoard.replyUser" /></template>
            <el-input :model-value="values.replyUser?.username" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="replyDate">
            <template #label><label-tip message="messageBoard.replyDate" /></template>
            <el-date-picker :model-value="values.replyDate" type="datetime" class="w-full" disabled></el-date-picker>
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>
