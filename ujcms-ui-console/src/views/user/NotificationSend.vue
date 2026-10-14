<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { sendSystemNotification, queryNotificationUserList } from '@/api/interaction';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'NotificationSend',
});
const { t } = useI18n();
const form = ref<any>();
const values = ref<any>({ userIds: [] });
const buttonLoading = ref<boolean>(false);
const userLoading = ref<boolean>(false);
const userOptions = ref<any[]>([]);

const searchUser = async (query: string) => {
  if (!query) {
    userOptions.value = [];
    return;
  }
  userLoading.value = true;
  try {
    userOptions.value = await queryNotificationUserList(query);
  } finally {
    userLoading.value = false;
  }
};

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      await sendSystemNotification({ ...values.value });
      ElMessage.success(t('notification.op.sendSuccess'));
      values.value = { userIds: [] };
      userOptions.value = [];
      form.value.resetFields();
    } finally {
      buttonLoading.value = false;
    }
  });
};
</script>

<template>
  <div>
    <div class="p-3 bg-white rounded-sm">
      <el-form ref="form" :model="values" label-width="150px" class="max-w-screen-md">
        <el-form-item prop="userIds" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="notification.userIds" help /></template>
          <el-select
            v-model="values.userIds"
            multiple
            filterable
            remote
            :remote-method="searchUser"
            :loading="userLoading"
            :multiple-limit="1000"
            class="w-full"
          >
            <el-option v-for="item in userOptions" :key="item.id" :value="item.id" :label="item.nickname ? `${item.username} (${item.nickname})` : item.username" />
          </el-select>
        </el-form-item>
        <el-form-item prop="title" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="notification.title" /></template>
          <el-input v-model="values.title" maxlength="255"></el-input>
        </el-form-item>
        <el-form-item prop="text">
          <template #label><label-tip message="notification.text" /></template>
          <el-input v-model="values.text" type="textarea" :rows="6" maxlength="1000" show-word-limit></el-input>
        </el-form-item>
        <el-form-item prop="url">
          <template #label><label-tip message="notification.url" help /></template>
          <el-input v-model="values.url" maxlength="255"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="buttonLoading" @click="handleSubmit">{{ $t('notification.op.send') }}</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>
