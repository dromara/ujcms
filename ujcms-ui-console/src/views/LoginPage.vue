<script setup lang="ts">
import { onMounted, ref, watchEffect, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { LocationQueryValue } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { User, Lock, Picture, Cellphone } from '@element-plus/icons-vue';

import { sm2Encrypt } from '@/utils/sm';
import { removeAccessToken, removeRefreshToken } from '@/utils/auth';
import { queryClientPublicKey, queryIsDisplayCaptcha, queryIsMfaLogin, queryCaptcha, tryCaptcha } from '@/api/login';
import { login } from '@/stores/useCurrentUser';
import ChangePassword from '@/views/ChangePassword.vue';
import GetShortMessage from '@/views/GetShortMessage.vue';

const { t } = useI18n();
const form = ref<any>();
const bean = ref<any>({});
const focus = ref<any>();
const isDisplayCaptcha = ref<boolean>(false);
const isDisplayShortMessage = ref<boolean>(false);
const captchaData = ref<string>();
const captchaToken = ref<string>();
const shortMessageId = ref<string>();
const error = ref<string>();
const buttonLoading = ref<boolean>(false);
const redirect = ref<string | null>();
const route = useRoute();
const router = useRouter();
const title = import.meta.env.VITE_APP_NAME || 'UJCMS';
const envMode = import.meta.env.MODE;

const changePasswordVisible = ref<boolean>(false);
const getShortMessageVisible = ref<boolean>(false);
const shortMessageTimer = ref<number>(60);
const shortMessageText = ref<string>(t('getShortMessage'));

// 先删除accessToken，以免登录失败
removeAccessToken();
removeRefreshToken();

if (envMode === 'development') {
  bean.value = { username: 'admin', password: 'password' };
}

const fetchCaptcha = async () => {
  const { token, image } = await queryCaptcha();
  captchaData.value = 'data:image/png;base64,' + image;
  captchaToken.value = token;
};

const fetchIsDisplayCaptcha = async () => {
  isDisplayCaptcha.value = await queryIsDisplayCaptcha();
  if (isDisplayCaptcha.value) {
    fetchCaptcha();
  }
};

const fetchIsMfaLogin = async () => {
  isDisplayShortMessage.value = await queryIsMfaLogin();
};

onMounted(async () => {
  focus.value.focus();
  focus.value.select();
  fetchIsDisplayCaptcha();
  fetchIsMfaLogin();
  
});

watchEffect(() => {
  redirect.value = route.query.redirect as LocationQueryValue;
});

const finishGetShortMessage = (val: string) => {
  shortMessageId.value = val;
  shortMessageTimer.value -= 1;
  shortMessageText.value = String(shortMessageTimer.value);
  const timerInterval = setInterval(() => {
    shortMessageTimer.value -= 1;
    shortMessageText.value = String(shortMessageTimer.value);
    if (shortMessageTimer.value <= 0) {
      shortMessageText.value = t('getShortMessage');
      shortMessageTimer.value = 60;
      clearInterval(timerInterval);
    }
  }, 1000);
};

const handleLogin = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      const publicKey = await queryClientPublicKey();
      const password = sm2Encrypt(bean.value.password, publicKey);
      const data = await login({ ...bean.value, password, captchaToken: captchaToken.value, shortMessageId: shortMessageId.value });
      // 登录失败，显示错误信息
      if (data.status !== 0) {
        fetchIsDisplayCaptcha();
        error.value = data.message;
        return;
      }
      if (data.result.remainingDays != null) {
        ElMessageBox.alert(t('passwordRemaingDays', { remainingDays: data.result.remainingDays }), { type: 'warning' });
      }
      if (redirect.value) {
        router.push(redirect.value);
      } else {
        // 从 403 页面跳转到登录页面时，使用 router.push 会继续回到 403 页面，可能是页面缓存所致
        location.reload();
      }
    } finally {
      buttonLoading.value = false;
    }
  });
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
      <el-form-item v-if="isDisplayShortMessage" prop="shortMessageValue" :rules="[{ required: true, message: () => $t('v.required') }]">
        <el-input
          v-model="bean.shortMessageValue"
          name="shortMessageValue"
          maxlength="10"
          :placeholder="$t('shortMessage')"
          :prefix-icon="Cellphone"
        >
          <template #append>
            <el-button :disabled="shortMessageTimer < 60" @click="() => (getShortMessageVisible = true)">{{ shortMessageText }}</el-button>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item
        v-if="isDisplayCaptcha"
        prop="captcha"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            asyncValidator: async (rule, value, callback) => {
              if (captchaToken == null || !(await tryCaptcha(captchaToken, value))) {
                callback($t('captchaIncorrect'));
                return;
              }
              callback();
            },
            trigger: 'blur',
          },
        ]"
        class="captcha"
      >
        <el-input v-model="bean.captcha" name="captcha" maxlength="10" :placeholder="$t('captcha')" :prefix-icon="Picture">
          <template #append>
            <el-image
              :src="captchaData"
              style="height: 30px; margin-right: 1px"
              class="rounded-r cursor-pointer"
              :title="$t('clickToRetrieveCaptcha')"
              @click="() => fetchCaptcha()"
            />
          </template>
        </el-input>
      </el-form-item>
      <div>
        <el-button type="primary" native-type="submit" class="w-full" :loading="buttonLoading" @click.prevent="handleLogin">{{ $t('login') }}</el-button>
      </div>
      
      <div class="mt-2 text-right">
        <el-button type="primary" link @click="() => (changePasswordVisible = true)">{{ $t('changePassword') }}</el-button>
      </div>
    </el-form>
    <div v-if="envMode === 'staging'" class="mt-5 text-sm text-center text-gray-secondary">
      <p>为避免数据被删改，演示用户登录后只拥有浏览后台功能，操作数据会显示无权访问（403）</p>
    </div>
    <change-password v-model="changePasswordVisible" />
    <get-short-message v-model="getShortMessageVisible" @finish="finishGetShortMessage" />
  </div>
</template>

<style lang="scss" scoped>
:deep(.captcha .el-input-group__append) {
  padding: 0;
}
</style>
