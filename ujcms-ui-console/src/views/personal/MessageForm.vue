<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { sendMessage, bulkSendMessage, queryMessageUserList } from '@/api/interaction';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'MessageForm',
});
const props = defineProps<{ modelValue: boolean; bulk?: boolean }>();
const emit = defineEmits<{ (e: 'update:modelValue', visible: boolean): void; (e: 'finished'): void }>();

const { t } = useI18n();
const form = ref<any>();
const values = ref<any>({ userIds: [] });
const buttonLoading = ref<boolean>(false);
const userLoading = ref<boolean>(false);
const userOptions = ref<any[]>([]);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      values.value = { userIds: [] };
      userOptions.value = [];
      form.value?.resetFields();
    }
  },
);

const searchUser = async (query: string) => {
  if (!query) {
    userOptions.value = [];
    return;
  }
  userLoading.value = true;
  try {
    userOptions.value = await queryMessageUserList(query);
  } finally {
    userLoading.value = false;
  }
};

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      const { userIds, text } = values.value;
      if (userIds.length > 1) {
        await bulkSendMessage({ userIds, text });
      } else {
        await sendMessage({ userId: userIds[0], text });
      }
      ElMessage.success(t('message.op.sendSuccess'));
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
    :title="bulk ? $t('message.op.bulkSend') : $t('message.op.send')"
    :model-value="modelValue"
    width="600px"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <el-form ref="form" :model="values" label-width="120px">
      <el-form-item prop="userIds" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="message.userIds" help /></template>
        <el-select
          v-model="values.userIds"
          multiple
          filterable
          remote
          :remote-method="searchUser"
          :loading="userLoading"
          :multiple-limit="bulk ? 100 : 1"
          class="w-full"
        >
          <el-option v-for="item in userOptions" :key="item.id" :value="item.id" :label="item.nickname ? `${item.username} (${item.nickname})` : item.username" />
        </el-select>
      </el-form-item>
      <el-form-item prop="text" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="message.text" /></template>
        <el-input v-model="values.text" type="textarea" :rows="6" maxlength="1000" show-word-limit></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="buttonLoading" @click="handleSubmit">{{ bulk ? $t('message.op.bulkSend') : $t('message.op.send') }}</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
</template>
