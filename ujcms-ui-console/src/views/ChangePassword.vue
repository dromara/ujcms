<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { sm2Encrypt } from '@/utils/sm';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { queryClientPublicKey, updatePassword } from '@/api/login';

defineOptions({
  name: 'ChangePassword',
});
defineProps({ modelValue: { type: Boolean, required: true } });
const emit = defineEmits({ 'update:modelValue': null });
const { t } = useI18n();
const sysConfig = useSysConfigStore();
const values = ref<any>({});
const form = ref<any>();
const focus = ref<any>();
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
const publicKey = ref<string>('');
const error = ref<string>();

onMounted(async () => {
  loading.value = true;
  try {
    publicKey.value = await queryClientPublicKey();
  } finally {
    loading.value = false;
  }
});

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      const password = sm2Encrypt(values.value.password, publicKey.value);
      const newPassword = sm2Encrypt(values.value.newPassword, publicKey.value);
      const data = await updatePassword({ ...values.value, password, newPassword, passwordAgain: undefined });
      // 登录失败，显示错误信息
      if (data.status !== 0) {
        error.value = data.message;
        return;
      }
      error.value = undefined;
      form.value.resetFields();
      ElMessage.success(t('success'));
      emit('update:modelValue', false);
    } finally {
      buttonLoading.value = false;
    }
  });
};
</script>

<template>
  <el-dialog
    :title="$t('changePassword')"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @opened="
      () => {
        focus?.focus();
        form.resetFields();
      }
    "
  >
    <el-form ref="form" v-loading="loading" :model="values" label-width="150px" label-position="right">
      <el-alert v-if="error" :title="error" type="error" class="mb-3" :closable="false" show-icon />
      <el-form-item prop="username" :label="$t('user.username')" :rules="[{ required: true, message: () => $t('v.required') }]">
        <el-input ref="focus" v-model="values.username" maxlength="30"></el-input>
      </el-form-item>
      <el-form-item prop="password" :label="$t('user.origPassword')" :rules="[{ required: true, message: () => $t('v.required') }]">
        <el-input v-model="values.password" maxlength="50" show-password></el-input>
      </el-form-item>
      <el-form-item
        prop="newPassword"
        :label="$t('user.newPassword')"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            min: sysConfig.security.passwordMinLength,
            max: sysConfig.security.passwordMaxLength,
            message: () => $t('user.error.passwordLength', { min: sysConfig.security.passwordMinLength, max: sysConfig.security.passwordMaxLength }),
          },
          { pattern: new RegExp(sysConfig.security.passwordPattern), message: () => $t(`user.error.passwordPattern.${sysConfig.security.passwordStrength}`) },
        ]"
      >
        <el-input v-model="values.newPassword" :maxlength="sysConfig.security.passwordMaxLength" show-password></el-input>
      </el-form-item>
      <el-form-item
        prop="passwordAgain"
        :label="$t('user.passwordAgain')"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            validator: (rule: any, value: any, callback: any) => {
              if (value !== values.newPassword) {
                callback($t('user.error.passwordNotMatch'));
                return;
              }
              callback();
            },
          },
        ]"
      >
        <el-input v-model="values.passwordAgain" maxlength="50" show-password></el-input>
      </el-form-item>
      <div>
        <el-button :loading="buttonLoading" type="primary" native-type="submit" @click.prevent="handleSubmit">{{ $t('submit') }}</el-button>
      </div>
    </el-form>
  </el-dialog>
</template>
