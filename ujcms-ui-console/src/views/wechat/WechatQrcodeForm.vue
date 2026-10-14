<script setup lang="ts">
import { nextTick, ref, watch, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { createWechatQrcode, updateWechatQrcode } from '@/api/wechat';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'WechatQrcodeForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  accountList: { type: Array as PropType<any[]>, required: true },
  accountId: { type: String, default: null },
  // 编辑的二维码行数据。为空时为新增
  bean: { type: Object as PropType<any>, default: null },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const form = ref<any>();
const values = ref<any>({});
const buttonLoading = ref<boolean>(false);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      values.value = props.bean != null ? { id: props.bean.id, name: props.bean.name } : { accountId: props.accountId ?? undefined, type: 0, expireSeconds: 2592000 };
      nextTick(() => form.value?.clearValidate());
    }
  },
);

const handleSubmit = async () => {
  await form.value?.validate();
  buttonLoading.value = true;
  try {
    if (values.value.id != null) {
      await updateWechatQrcode({ id: values.value.id, name: values.value.name });
    } else {
      const { accountId, name, type, sceneId, sceneStr, expireSeconds } = values.value;
      await createWechatQrcode(type === 0 ? { accountId, name, type, sceneId, expireSeconds } : { accountId, name, type, sceneStr });
    }
    emit('update:modelValue', false);
    emit('finished');
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
</script>

<template>
  <el-dialog :model-value="modelValue" :title="bean != null ? $t('edit') : $t('add')" :width="512" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <el-form ref="form" :model="values" label-position="top">
      <el-form-item v-if="values.id == null" prop="accountId" :label="$t('wechatQrcode.account')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-select v-model="values.accountId" :placeholder="$t('wechat.selectAccount')" class="w-full">
          <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="wechatQrcode.name" help /></template>
        <el-input v-model="values.name" maxlength="30"></el-input>
      </el-form-item>
      <template v-if="values.id == null">
        <el-form-item prop="type" :label="$t('wechatQrcode.type')">
          <el-radio-group v-model="values.type">
            <el-radio-button v-for="n in [0, 1]" :key="n" :value="n">{{ $t(`wechatQrcode.type.${n}`) }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="values.type === 0">
          <el-form-item prop="sceneId">
            <template #label><label-tip message="wechatQrcode.sceneId" help /></template>
            <el-input-number
              v-model="values.sceneId"
              :min="1"
              :max="4294967295"
              :step="1"
              step-strictly
              :controls="false"
              :placeholder="$t('wechatQrcode.sceneId.placeholder')"
              class="!w-full"
            />
          </el-form-item>
          <el-form-item prop="expireSeconds" :label="$t('wechatQrcode.expireSeconds')">
            <el-select v-model="values.expireSeconds" class="w-full">
              <el-option v-for="n in [1800, 86400, 604800, 2592000]" :key="n" :value="n" :label="$t(`wechatQrcode.expireSeconds.${n}`)"></el-option>
            </el-select>
          </el-form-item>
        </template>
        <el-form-item v-else prop="sceneStr" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="wechatQrcode.sceneStr" help /></template>
          <el-input v-model="values.sceneStr" maxlength="64"></el-input>
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="buttonLoading" @click="() => handleSubmit()">{{ $t('save') }}</el-button>
    </template>
  </el-dialog>
</template>
