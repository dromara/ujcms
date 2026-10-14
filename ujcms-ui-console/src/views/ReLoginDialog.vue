<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Lock } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { sm2Encrypt } from '@/utils/sm';
import { queryClientPublicKey } from '@/api/login';
import { useAppStateStore } from '@/stores/appStateStore';
import { currentUser, login } from '@/stores/useCurrentUser';
import SecondFactorDialog from '@/views/SecondFactorDialog.vue';

// 会话超时后就地重新登录，保留当前页面未保存的内容。挂在 App.vue 下（构建器等全屏路由不经过 Layout），
// 由 appState.loginBoxDisplay 驱动；开启双因子认证时，口令通过后继续由 SecondFactorDialog 完成第二步。
// 两步期间 loginBoxDisplay 始终为 true：request.ts 据此不因后台请求的 401 刷新页面。
defineOptions({
  name: 'ReLoginDialog',
});

/** 后端状态码：口令已通过，还需完成第二因子验证 */
const STATUS_SECOND_FACTOR_REQUIRED = 1020;

const { t } = useI18n();
const appState = useAppStateStore();
const form = ref<any>();
const focus = ref<any>();
const bean = ref<any>({});
const error = ref<string>();
const buttonLoading = ref<boolean>(false);
const secondFactorVisible = ref<boolean>(false);
const mfaToken = ref<string>('');
const maskedMobile = ref<string>('');

// 第二步对话框打开时收起口令框，第二步取消后回到口令框
const visible = computed(() => appState.loginBoxDisplay && !secondFactorVisible.value);

// 每次会话超时弹出时清掉上一次遗留的错误提示（第二步失效回到口令框时 loginBoxDisplay 不变，提示得以保留）
watch(
  () => appState.loginBoxDisplay,
  (display) => {
    if (display) error.value = undefined;
  },
);

const backToLogin = () => {
  window.location.reload();
};

const finish = () => {
  secondFactorVisible.value = false;
  appState.setLoginBoxDisplay(false);
};

const handleOpened = () => {
  form.value.resetFields();
  if (import.meta.env.MODE === 'development') {
    bean.value.password = 'password';
  }
  focus.value?.focus();
};

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      const publicKey = await queryClientPublicKey();
      const password = sm2Encrypt(bean.value.password, publicKey);
      const data = await login({ username: currentUser.username ?? '', password });
      if (data.status === STATUS_SECOND_FACTOR_REQUIRED) {
        error.value = undefined;
        mfaToken.value = data.result.mfaToken;
        maskedMobile.value = data.result.maskedMobile;
        secondFactorVisible.value = true;
        return;
      }
      // 登录失败，显示错误信息。需先修改密码（1010 / 1011）等情况无法就地处理，由用户返回登录页
      if (data.status !== 0) {
        error.value = data.message;
        return;
      }
      error.value = undefined;
      finish();
    } finally {
      buttonLoading.value = false;
    }
  });
};

// 挑战令牌失效：回到口令框重新开始
const handleSecondFactorExpired = () => {
  error.value = t('secondFactor.expired');
  bean.value.password = '';
};
</script>

<template>
  <div>
    <el-dialog
      :title="$t('enterPassword')"
      :model-value="visible"
      width="420px"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      @opened="handleOpened"
    >
      <el-form ref="form" :model="bean">
        <el-alert v-if="error" :title="error" type="error" class="mb-3" :closable="false" show-icon />
        <p class="mb-3 text-sm text-gray-regular">{{ currentUser.username }}</p>
        <el-form-item prop="password" :rules="[{ required: true, message: () => $t('v.required') }]">
          <el-input ref="focus" v-model="bean.password" type="password" name="password" maxlength="64" :placeholder="$t('password')" :prefix-icon="Lock" show-password />
        </el-form-item>
        <div class="text-right">
          <el-button @click="() => backToLogin()">{{ $t('backToLogin') }}</el-button>
          <el-button :loading="buttonLoading" type="primary" native-type="submit" @click.prevent="handleSubmit">{{ $t('login') }}</el-button>
        </div>
      </el-form>
    </el-dialog>
    <second-factor-dialog v-model="secondFactorVisible" :mfa-token="mfaToken" :masked-mobile="maskedMobile" @success="finish" @expired="handleSecondFactorExpired" />
  </div>
</template>
