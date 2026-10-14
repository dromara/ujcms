<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { Cellphone } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { sendSecondFactorCode } from '@/api/login';
import { verifySecondFactor } from '@/stores/useCurrentUser';

// 双因子登录第二步。口令已通过（登录接口返回 status 1020 与 mfaToken）后打开：
// 验证码发往账号登记的手机号码，用户不输入号码，只看到脱敏提示；打开即自动发送。
defineOptions({
  name: 'SecondFactorDialog',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  mfaToken: { type: String, default: '' },
  maskedMobile: { type: String, default: '' },
});
// success: 登录完成，参数为令牌结果；expired: 挑战令牌失效，需回到用户名密码一步
const emit = defineEmits({ 'update:modelValue': null, success: null, expired: null });

/** 后端状态码：双因子挑战令牌无效或已过期 */
const STATUS_SECOND_FACTOR_EXPIRED = 1021;

const { t } = useI18n();
const form = ref<any>();
const focus = ref<any>();
const bean = ref<any>({});
const error = ref<string>();
const mobile = ref<string>('');
const shortMessageId = ref<string>();
const codeLength = ref<number>(6);
const sending = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
const cooldown = ref<number>(0);
let cooldownTimer: ReturnType<typeof setInterval> | undefined;

const stopCooldown = () => {
  if (cooldownTimer != null) {
    clearInterval(cooldownTimer);
    cooldownTimer = undefined;
  }
};

const startCooldown = (seconds: number) => {
  stopCooldown();
  cooldown.value = seconds;
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1;
    if (cooldown.value <= 0) {
      stopCooldown();
    }
  }, 1000);
};

onBeforeUnmount(stopCooldown);

const handleExpired = () => {
  emit('update:modelValue', false);
  emit('expired');
};

const sendCode = async () => {
  sending.value = true;
  try {
    const data = await sendSecondFactorCode(props.mfaToken);
    if (data.status === STATUS_SECOND_FACTOR_EXPIRED) {
      handleExpired();
      return;
    }
    if (data.status !== 0) {
      error.value = data.message;
      return;
    }
    error.value = undefined;
    shortMessageId.value = data.result.shortMessageId;
    mobile.value = data.result.maskedMobile;
    codeLength.value = data.result.codeLength;
    startCooldown(data.result.cooldownSeconds);
  } finally {
    sending.value = false;
  }
};

const handleOpened = () => {
  form.value.resetFields();
  error.value = undefined;
  shortMessageId.value = undefined;
  mobile.value = props.maskedMobile;
  stopCooldown();
  cooldown.value = 0;
  focus.value?.focus();
  sendCode();
};

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    // 验证码尚未发送成功（如短信平台失败）：没有可校验的短信，提示先获取验证码
    if (shortMessageId.value == null) {
      error.value = error.value ?? t('getShortMessage');
      return;
    }
    buttonLoading.value = true;
    try {
      const data = await verifySecondFactor({ mfaToken: props.mfaToken, shortMessageId: shortMessageId.value, code: bean.value.code });
      if (data.status === STATUS_SECOND_FACTOR_EXPIRED) {
        handleExpired();
        return;
      }
      if (data.status !== 0) {
        error.value = data.message;
        bean.value.code = '';
        focus.value?.focus();
        return;
      }
      // 先通知登录完成、再关闭：父组件据此区分“登录成功后关闭”与“用户取消”
      emit('success', data.result);
      emit('update:modelValue', false);
    } finally {
      buttonLoading.value = false;
    }
  });
};
</script>

<template>
  <el-dialog
    :title="$t('secondFactor.title')"
    :model-value="modelValue"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @opened="handleOpened"
    @closed="stopCooldown"
  >
    <el-form ref="form" :model="bean">
      <el-alert v-if="error" :title="error" type="error" class="mb-3" :closable="false" show-icon />
      <p v-if="sending" class="mb-3 text-sm text-gray-regular">{{ $t('secondFactor.sending') }}</p>
      <!-- 只有确实发出了验证码才提示“已发送至”；发送失败时由上方错误提示说明原因 -->
      <p v-else-if="shortMessageId != null" class="mb-3 text-sm text-gray-regular">{{ $t('secondFactor.sentTo', { mobile }) }}</p>
      <el-form-item prop="code" :rules="[{ required: true, message: () => $t('v.required') }]">
        <el-input
          ref="focus"
          v-model="bean.code"
          name="code"
          autocomplete="one-time-code"
          inputmode="numeric"
          :maxlength="codeLength"
          :placeholder="$t('shortMessage')"
          :prefix-icon="Cellphone"
        >
          <template #append>
            <el-button :disabled="sending || cooldown > 0" @click="() => sendCode()">
              {{ cooldown > 0 ? $t('secondFactor.resendIn', { seconds: cooldown }) : $t('getShortMessage') }}
            </el-button>
          </template>
        </el-input>
      </el-form-item>
      <div>
        <el-button :loading="buttonLoading" type="primary" native-type="submit" class="w-full" @click.prevent="handleSubmit">{{ $t('login') }}</el-button>
      </div>
      <p class="mt-3 text-xs text-gray-secondary">{{ $t('secondFactor.help') }}</p>
    </el-form>
  </el-dialog>
</template>
