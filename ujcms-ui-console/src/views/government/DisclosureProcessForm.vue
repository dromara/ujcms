<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { queryDisclosure, acceptDisclosure, correctDisclosure, correctReceivedDisclosure, replyDisclosure } from '@/api/interaction';
import Tinymce from '@/components/Tinymce';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'DisclosureProcessForm',
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

// 按状态给出默认办理动作：待受理→受理，已补正告知→补正收到，其余→答复
const defaultAction = (status: number): string => {
  if (status === 0 && !perm('disclosure:accept')) return 'accept';
  if (status === 2 && !perm('disclosure:correctReceived')) return 'correctReceived';
  if (!perm('disclosure:reply')) return 'reply';
  return 'correct';
};

watch(
  () => props.modelValue,
  async () => {
    if (props.modelValue && props.beanId != null) {
      loading.value = true;
      try {
        bean.value = await queryDisclosure(props.beanId);
        action.value = defaultAction(bean.value.status);
        values.value = { replyType: bean.value.replyType ?? 2, replyText: bean.value.replyText, correctText: bean.value.correctText };
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
        await acceptDisclosure([bean.value.id]);
      } else if (action.value === 'correctReceived') {
        await correctReceivedDisclosure([bean.value.id]);
      } else if (action.value === 'correct') {
        await correctDisclosure(bean.value.id, values.value.correctText);
      } else {
        await replyDisclosure({ id: bean.value.id, replyType: values.value.replyType, replyText: values.value.replyText });
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
    :title="`${$t('disclosure.op.process')} - ${bean.title ?? ''}`"
    :model-value="modelValue"
    :close-on-click-modal="false"
    width="768px"
    top="5vh"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <el-form ref="form" v-loading="loading" :model="values" label-position="top">
      <el-alert v-if="bean.correctText" type="warning" :closable="false" class="mb-3">
        <div><strong>{{ $t('disclosure.correctText') }}:</strong> {{ bean.correctText }}</div>
        <div v-if="bean.supplementText" class="mt-1"><strong>{{ $t('disclosure.supplementText') }}:</strong> {{ bean.supplementText }}</div>
      </el-alert>
      <el-form-item :label="$t('disclosure.op.action')">
        <el-radio-group v-model="action">
          <el-radio-button :disabled="perm('disclosure:accept') || bean.status !== 0" value="accept">{{ $t('disclosure.op.accept') }}</el-radio-button>
          <el-radio-button :disabled="perm('disclosure:correctReceived') || bean.status !== 2" value="correctReceived">
            {{ $t('disclosure.op.correctReceived') }}
          </el-radio-button>
          <el-radio-button :disabled="perm('disclosure:correct') || bean.status === 2" value="correct">{{ $t('disclosure.op.correct') }}</el-radio-button>
          <el-radio-button :disabled="perm('disclosure:reply')" value="reply">{{ $t('disclosure.op.reply') }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="action === 'accept'">
        <p class="text-sm text-gray-secondary">{{ $t('disclosure.op.accept.confirm') }}</p>
      </template>
      <template v-else-if="action === 'correctReceived'">
        <p class="text-sm text-gray-secondary">{{ $t('disclosure.op.correctReceived.tooltip') }}</p>
      </template>
      <template v-else-if="action === 'correct'">
        <el-form-item prop="correctText" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="disclosure.correctText" /></template>
          <el-input v-model="values.correctText" type="textarea" :rows="6" maxlength="1000"></el-input>
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item prop="replyType" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="disclosure.replyType" /></template>
          <el-select v-model="values.replyType" class="w-full">
            <el-option v-for="n in [1, 2, 3, 4, 5, 6, 7, 8]" :key="n" :label="$t(`disclosure.replyType.${n}`)" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item prop="replyText">
          <template #label><label-tip message="disclosure.replyText" /></template>
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
