<script setup lang="ts">
import { onMounted, ref, watchEffect } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { LocationQueryValue } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { User, Lock } from '@element-plus/icons-vue';

import { sm2Encrypt } from '@/utils/sm';
import { removeAccessToken, removeRefreshToken } from '@/utils/auth';
import { queryClientPublicKey, queryIsDisplayCaptcha } from '@/api/login';
import { login } from '@/stores/useCurrentUser';
import CaptchaWidget from '@/components/CaptchaWidget.vue';
import ChangePassword from '@/views/ChangePassword.vue';
import SecondFactorDialog from '@/views/SecondFactorDialog.vue';

const { t } = useI18n();
const form = ref<any>();
const bean = ref<any>({});
const focus = ref<any>();
const isDisplayCaptcha = ref<boolean>(false);
// 共用的人机验证组件（/static/captcha.js）：按全局设置出滑块 / 字符 / 厂商题，答案在登录时取
const captchaWidget = ref<InstanceType<typeof CaptchaWidget>>();
const error = ref<string>();
const buttonLoading = ref<boolean>(false);
const redirect = ref<string | null>();
const route = useRoute();
const router = useRouter();
const title = import.meta.env.VITE_APP_NAME || 'UJCMS';
const envMode = import.meta.env.MODE;

const changePasswordVisible = ref<boolean>(false);
const passwordChangeReason = ref<string>('');
// 双因子登录第二步：口令通过后，登录接口返回 status 1020 及挑战令牌、脱敏手机号码
const STATUS_SECOND_FACTOR_REQUIRED = 1020;
const secondFactorVisible = ref<boolean>(false);
const mfaToken = ref<string>('');
const maskedMobile = ref<string>('');

// 先删除accessToken，以免登录失败
removeAccessToken();
removeRefreshToken();

if (envMode === 'development') {
  bean.value = { username: 'admin', password: 'password' };
}

// 需要验证码时组件挂载即出题；已挂载时换一题（登录失败会消费令牌）
const fetchIsDisplayCaptcha = async () => {
  isDisplayCaptcha.value = await queryIsDisplayCaptcha();
  if (isDisplayCaptcha.value) {
    captchaWidget.value?.refresh();
  }
};

onMounted(async () => {
  focus.value.focus();
  focus.value.select();
  fetchIsDisplayCaptcha();
  
});

watchEffect(() => {
  redirect.value = route.query.redirect as LocationQueryValue;
});

// 登录完成（直接登录成功，或第二因子验证通过）
let loggedIn = false;
const finishLogin = (result: any) => {
  loggedIn = true;
  if (result.remainingDays != null) {
    ElMessageBox.alert(t('passwordRemaingDays', { remainingDays: result.remainingDays }), { type: 'warning' });
  }
  if (redirect.value) {
    router.push(redirect.value);
  } else {
    // 从 403 页面跳转到登录页面时，使用 router.push 会继续回到 403 页面，可能是页面缓存所致
    location.reload();
  }
};

const handleLogin = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      let answer: { captcha?: string; captchaToken?: string } = {};
      if (isDisplayCaptcha.value) {
        try {
          answer = (await captchaWidget.value?.answer()) ?? {};
        } catch (e: any) {
          // 可见题未完成：组件已高亮，提示后留在登录页
          error.value = e?.message;
          return;
        }
      }
      const publicKey = await queryClientPublicKey();
      const password = sm2Encrypt(bean.value.password, publicKey);
      const data = await login({ ...bean.value, password, captcha: answer.captcha, captchaToken: answer.captchaToken });
      // 需先修改密码（首次登录强制改密 1010 / 密码已过期 1011）：弹出修改密码对话框，弹窗内提示原因
      if (data.status === 1010 || data.status === 1011) {
        error.value = undefined;
        passwordChangeReason.value = data.message;
        changePasswordVisible.value = true;
        return;
      }
      // 口令已通过，还需完成第二因子验证：验证码发往账号登记的手机号码，由对话框完成发送与校验
      if (data.status === STATUS_SECOND_FACTOR_REQUIRED) {
        error.value = undefined;
        mfaToken.value = data.result.mfaToken;
        maskedMobile.value = data.result.maskedMobile;
        secondFactorVisible.value = true;
        return;
      }
      // 登录失败，显示错误信息
      if (data.status !== 0) {
        fetchIsDisplayCaptcha();
        error.value = data.message;
        return;
      }
      finishLogin(data.result);
    } finally {
      buttonLoading.value = false;
    }
  });
};

// 第二步对话框关闭（取消或挑战令牌失效）：图形验证码已被第一步消耗，需重新获取
const handleSecondFactorClosed = (visible: boolean) => {
  secondFactorVisible.value = visible;
  if (!visible && !loggedIn) {
    fetchIsDisplayCaptcha();
  }
};

const handleSecondFactorExpired = () => {
  error.value = t('secondFactor.expired');
};

// 首次登录强制改密 / 密码过期 改密成功后：用新口令无缝自动登录（开启双因子时会接着弹出第二步对话框）；
// 需要图形验证码时，验证码无法静默重放，提示用户用新口令重新登录。
const handlePasswordChanged = ({ username, password }: { username: string; password: string }) => {
  error.value = undefined;
  bean.value.username = username;
  if (!isDisplayCaptcha.value) {
    bean.value.password = password;
    handleLogin();
  } else {
    bean.value.password = '';
    ElMessage.success(t('passwordChangedRelogin'));
  }
};
</script>
<template>
  <div class="h-full p-3 bg-gray-100">
    <h3 class="py-5 text-3xl font-bold text-center text-primary">{{ title }}</h3>
    <el-form ref="form" :model="bean" class="mx-auto md:max-w-xs">
      <el-alert v-if="error" :title="error" type="error" class="mb-3" :closable="false" show-icon />
      <el-form-item prop="username" :rules="[{ required: true, message: () => $t('v.required') }]">
        <el-input
          ref="focus"
          v-model="bean.username"
          name="username"
          maxlength="30"
          :placeholder="$t('username')"
          :prefix-icon="User"
          autocomplete="on"
        />
      </el-form-item>
      <el-form-item prop="password" :rules="[{ required: true, message: () => $t('v.required') }]">
        <el-input
          ref="password"
          v-model="bean.password"
          type="password"
          name="password"
          maxlength="64"
          :placeholder="$t('password')"
          :prefix-icon="Lock"
          show-password
        />
      </el-form-item>
      <el-form-item v-if="isDisplayCaptcha" class="captcha">
        <captcha-widget ref="captchaWidget" />
      </el-form-item>
      <div>
        <el-button type="primary" native-type="submit" class="w-full" :loading="buttonLoading" @click.prevent="handleLogin">{{ $t('login') }}</el-button>
      </div>
      
    </el-form>
    
    <change-password v-model="changePasswordVisible" :username="bean.username" :forced="true" :reason="passwordChangeReason" @success="handlePasswordChanged" />
    <second-factor-dialog
      :model-value="secondFactorVisible"
      :mfa-token="mfaToken"
      :masked-mobile="maskedMobile"
      @update:model-value="handleSecondFactorClosed"
      @success="finishLogin"
      @expired="handleSecondFactorExpired"
    />
  </div>
</template>

<style lang="scss" scoped>
:deep(.captcha .el-form-item__content) {
  display: block;
}
</style>
