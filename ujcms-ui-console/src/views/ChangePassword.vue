<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { sm2Encrypt } from '@/utils/sm';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { queryClientPublicKey, updatePassword } from '@/api/login';

defineOptions({
  name: 'ChangePassword',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  username: { type: String, default: '' },
  forced: { type: Boolean, default: false },
  reason: { type: String, default: '' },
});
const emit = defineEmits({ 'update:modelValue': null, success: null });
const sysConfig = useSysConfigStore();
const values = ref<any>({});
const form = ref<any>();
const focus = ref<any>();
const passwordFocus = ref<any>();
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
      // resetFields 会清空 values，需先保留用户名与新口令，供父组件改密后自动登录
      const username = values.value.username;
      const newPasswordRaw = values.value.newPassword;
      form.value.resetFields();
      emit('update:modelValue', false);
      // 改密成功后交由父组件处理后续（首次登录/密码过期场景下自动登录或提示重新登录）
      emit('success', { username, password: newPasswordRaw });
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
        form.resetFields();
        values.username = props.username ?? '';
        (props.forced ? passwordFocus : focus)?.focus();
      }
    "
  >
    <el-form ref="form" v-loading="loading" :model="values" label-width="150px" label-position="right">
      <el-alert v-if="forced && reason" :title="reason" type="warning" class="mb-3" :closable="false" show-icon />
      <el-alert v-if="error" :title="error" type="error" class="mb-3" :closable="false" show-icon />
      <el-form-item prop="username" :label="$t('user.username')" :rules="[{ required: true, message: () => $t('v.required') }]">
        <el-input ref="focus" v-model="values.username" maxlength="30" :disabled="forced"></el-input>
      </el-form-item>
      <el-form-item prop="password" :label="$t('user.origPassword')" :rules="[{ required: true, message: () => $t('v.required') }]">
        <el-input ref="passwordFocus" v-model="values.password" maxlength="50" show-password></el-input>
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
