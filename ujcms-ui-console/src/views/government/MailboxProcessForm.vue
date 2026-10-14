<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { queryMailbox, acceptMailbox, rejectMailbox, replyMailbox } from '@/api/interaction';
import Tinymce from '@/components/Tinymce';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'MailboxProcessForm',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null } });
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { t } = useI18n();
const form = ref<any>();
const bean = ref<any>({});
const values = ref<any>({});
const action = ref<string>('reply');
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);

// 按状态给出默认办理动作：待受理→受理，其余→答复
const defaultAction = (status: number): string => {
  if (status === 0 && !perm('mailbox:accept')) return 'accept';
  if (!perm('mailbox:reply')) return 'reply';
  return 'reject';
};

watch(
  () => props.modelValue,
  async () => {
    if (props.modelValue && props.beanId != null) {
      loading.value = true;
      try {
        bean.value = await queryMailbox(props.beanId);
        action.value = defaultAction(bean.value.status);
        values.value = { replyText: bean.value.replyText, rejectText: bean.value.rejectText };
        form.value?.resetFields();
      } finally {
        loading.value = false;
      }
    }
  },
);

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      if (action.value === 'accept') {
        await acceptMailbox([bean.value.id]);
      } else if (action.value === 'reject') {
        await rejectMailbox(bean.value.id, values.value.rejectText);
      } else {
        await replyMailbox({ id: bean.value.id, replyText: values.value.replyText });
      }
      ElMessage.success(t('success'));
      emit('update:modelValue', false);
      emit('finished');
    } finally {
      buttonLoading.value = false;
    }
  });
};
</script>

<template>
  <el-dialog
    :title="`${$t('mailbox.op.process')} - ${bean.title ?? ''}`"
    :model-value="modelValue"
    :close-on-click-modal="false"
    width="768px"
    top="5vh"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <el-form ref="form" v-loading="loading" :model="values" label-position="top">
      <el-alert v-if="bean.rejectText" type="warning" :closable="false" class="mb-3">
        <div><strong>{{ $t('mailbox.rejectText') }}:</strong> {{ bean.rejectText }}</div>
      </el-alert>
      <el-form-item :label="$t('mailbox.op.action')">
        <el-radio-group v-model="action">
          <el-radio-button :disabled="perm('mailbox:accept') || bean.status !== 0" value="accept">{{ $t('mailbox.op.accept') }}</el-radio-button>
          <el-radio-button :disabled="perm('mailbox:reject') || (bean.status !== 0 && bean.status !== 1)" value="reject">{{ $t('mailbox.op.reject') }}</el-radio-button>
          <el-radio-button :disabled="perm('mailbox:reply')" value="reply">{{ $t('mailbox.op.reply') }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="action === 'accept'">
        <p class="text-sm text-gray-secondary">{{ $t('mailbox.op.accept.confirm') }}</p>
      </template>
      <template v-else-if="action === 'reject'">
        <el-form-item prop="rejectText" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="mailbox.rejectText" /></template>
          <el-input v-model="values.rejectText" type="textarea" :rows="6" maxlength="1000" :placeholder="$t('mailbox.op.reject.tooltip')"></el-input>
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item prop="replyText" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="mailbox.replyText" /></template>
          <tinymce v-model="values.replyText" class="w-full" />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="buttonLoading" @click="() => handleSubmit()">{{ $t('submit') }}</el-button>
    </template>
  </el-dialog>
</template>
