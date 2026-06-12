<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { toTree } from '@/utils/tree';
import { querySiteList } from '@/api/system';
import {
  queryConfigModel,
  queryConfig,
  queryConfigSms,
  queryConfigEmail,
  queryUploadStorage,
  queryHtmlStorage,
  queryTemplateStorage,
  updateConfigBase,
  updateConfigCustoms,
  updateConfigUpload,
  updateConfigRegister,
  updateConfigSecurity,
  updateConfigSms,
  updateConfigEmail,
  sendTestSms,
  sendTestEmail,
  updateUploadStorage,
  updateHtmlStorage,
  updateTemplateStorage,
  storagePathAllowed,
} from '@/api/config';
import { perm, hasPermission, currentUser } from '@/stores/useCurrentUser';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import LabelTip from '@/components/LabelTip.vue';
import FieldItem from '@/views/config/components/FieldItem.vue';

defineOptions({
  name: 'GlobalSettings',
});
const { t } = useI18n();
const sysConfig = useSysConfigStore();
const form = ref<any>();
const config = ref<any>({});
const values = ref<any>({});
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
const siteList = ref<any[]>([]);
const model = ref<any>();
const fields = computed(() => JSON.parse(model.value?.customs || '[]'));

const types: string[] = [];
if (hasPermission('config:base:update')) {
  types.push('base');
}
if (hasPermission('config:upload:update')) {
  types.push('upload');
}
if (hasPermission('config:register:update')) {
  types.push('register');
}
if (hasPermission('config:security:update') && (currentUser.epRank >= 1 || currentUser.epDisplay)) {
  types.push('security');
}
if (hasPermission('config:sms:update')) {
  types.push('sms');
}
if (hasPermission('config:email:update')) {
  types.push('email');
}
if (hasPermission('config:uploadStorage:update')) {
  types.push('uploadStorage');
}
if (hasPermission('config:htmlStorage:update')) {
  types.push('htmlStorage');
}
if (hasPermission('config:templateStorage:update')) {
  types.push('templateStorage');
}
if (hasPermission('config:customs:update')) {
  types.push('customs');
}
const type = ref<string>(types[0]);

const switchType = async () => {
  if (type.value === 'upload') {
    values.value = config.value.upload;
  } else if (type.value === 'register') {
    values.value = config.value.register;
  } else if (type.value === 'security') {
    values.value = config.value.security;
  } else if (type.value === 'customs') {
    values.value = config.value.customs;
  } else if (type.value === 'sms') {
    values.value = await queryConfigSms();
  } else if (type.value === 'email') {
    values.value = await queryConfigEmail();
  } else if (type.value === 'uploadStorage') {
    values.value = await queryUploadStorage();
  } else if (type.value === 'htmlStorage') {
    values.value = await queryHtmlStorage();
  } else if (type.value === 'templateStorage') {
    values.value = await queryTemplateStorage();
  } else {
    values.value = config.value;
  }
};

watch(type, () => switchType());

const fetchConfigModel = async () => {
  model.value = await queryConfigModel();
};
const fetchConfig = async () => {
  config.value = await queryConfig();
};
const fetchSiteList = async () => {
  siteList.value = toTree(await querySiteList());
};
onMounted(async () => {
  loading.value = true;
  try {
    await Promise.all([fetchConfig(), fetchConfigModel(), fetchSiteList()]);
    switchType();
  } finally {
    loading.value = false;
  }
});
const load = async () => {
  loading.value = true;
  try {
    await fetchConfig();
  } finally {
    loading.value = false;
  }
};
const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      if (type.value === 'upload') {
        await updateConfigUpload(values.value);
      } else if (type.value === 'register') {
        await updateConfigRegister(values.value);
      } else if (type.value === 'security') {
        await updateConfigSecurity(values.value);
      } else if (type.value === 'customs') {
        await updateConfigCustoms(values.value);
      } else if (type.value === 'sms') {
        await updateConfigSms(values.value);
      } else if (type.value === 'email') {
        await updateConfigEmail(values.value);
      } else if (type.value === 'uploadStorage') {
        await updateUploadStorage(values.value);
      } else if (type.value === 'htmlStorage') {
        await updateHtmlStorage(values.value);
      } else if (type.value === 'templateStorage') {
        await updateTemplateStorage(values.value);
      } else {
        await updateConfigBase(values.value);
      }
      sysConfig.initConfig();
      ElMessage.success(t('success'));
    } finally {
      buttonLoading.value = false;
    }
    load();
  });
};
const handleSendTestSms = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      const { status, message } = await sendTestSms(values.value);
      if (status === -1) {
        ElMessageBox.alert(message);
      } else {
        ElMessage.success(t('success'));
      }
    } finally {
      buttonLoading.value = false;
    }
    load();
  });
};
const handleSendTestEmail = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      const { status, message } = await sendTestEmail(values.value);
      if (status === -1) {
        ElMessageBox.alert(message);
      } else {
        ElMessage.success(t('success'));
      }
    } finally {
      buttonLoading.value = false;
    }
    load();
  });
};
const invalidExtension = (extensions: string) => {
  const blacklist = sysConfig.base.uploadsExtensionBlacklist.split(',');
  const extensionList = extensions.split(',');
  return blacklist.findIndex((item) => extensionList.includes(item)) >= 0;
};
</script>

<template>
  <el-container>
    <el-aside width="180px" class="pr-3">
      <el-tabs v-model="type" tab-position="left" stretch class="bg-white">
        <el-tab-pane v-for="tp in types" :key="tp" :name="tp" :label="$t(`config.settings.${tp}`)"></el-tab-pane>
      </el-tabs>
    </el-aside>
    <el-main class="p-3 app-block">
      <el-form ref="form" v-loading="loading" :model="values" label-width="160px">
        <template v-if="type === 'upload'">
          <el-row>
            <el-col :span="12">
              <el-form-item
                prop="imageTypes"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  {
                    validator: (rule: any, value: any, callback: any) => {
                      if (invalidExtension(value)) {
                        callback($t('config.upload.error.extensionNotAllowd'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip message="config.upload.imageTypes" help /></template>
                <el-input v-model="values.imageTypes" maxlength="255"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="imageLimit" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.upload.imageLimit" help /></template>
                <el-input-number v-model="values.imageLimit" :min="0" :max="65535"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="videoTypes"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  {
                    validator: (rule: any, value: any, callback: any) => {
                      if (invalidExtension(value)) {
                        callback($t('config.upload.error.extensionNotAllowd'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip message="config.upload.videoTypes" help /></template>
                <el-input v-model="values.videoTypes" maxlength="255"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="videoLimit" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.upload.videoLimit" help /></template>
                <el-input-number v-model="values.videoLimit" :min="0" :max="65535"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="audioTypes"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  {
                    validator: (rule: any, value: any, callback: any) => {
                      if (invalidExtension(value)) {
                        callback($t('config.upload.error.extensionNotAllowd'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip message="config.upload.audioTypes" help /></template>
                <el-input v-model="values.audioTypes" maxlength="255"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="audioLimit" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.upload.audioLimit" help /></template>
                <el-input-number v-model="values.audioLimit" :min="0" :max="65535"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="libraryTypes"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  {
                    validator: (rule: any, value: any, callback: any) => {
                      if (invalidExtension(value)) {
                        callback($t('config.upload.error.extensionNotAllowd'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip message="config.upload.libraryTypes" help /></template>
                <el-input v-model="values.libraryTypes" maxlength="255"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="libraryLimit" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.upload.libraryLimit" help /></template>
                <el-input-number v-model="values.libraryLimit" :min="0" :max="65535"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="docTypes"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  {
                    validator: (rule: any, value: any, callback: any) => {
                      if (invalidExtension(value)) {
                        callback($t('config.upload.error.extensionNotAllowd'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip message="config.upload.docTypes" help /></template>
                <el-input v-model="values.docTypes" maxlength="255"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="docLimit" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.upload.docLimit" help /></template>
                <el-input-number v-model="values.docLimit" :min="0" :max="65535"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="fileTypes"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  {
                    validator: (rule: any, value: any, callback: any) => {
                      if (invalidExtension(value)) {
                        callback($t('config.upload.error.extensionNotAllowd'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip message="config.upload.fileTypes" help /></template>
                <el-input v-model="values.fileTypes" maxlength="255"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="fileLimit" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.upload.fileLimit" help /></template>
                <el-input-number v-model="values.fileLimit" :min="0" :max="65535"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="imageMaxWidth" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.upload.imageMaxWidth" help /></template>
                <el-input-number v-model="values.imageMaxWidth" :min="0" :max="65535"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="imageMaxHeight" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.upload.imageMaxHeight" help /></template>
                <el-input-number v-model="values.imageMaxHeight" :min="0" :max="65535"></el-input-number>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'register'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.register.enabled" /></template>
                <el-switch v-model="values.enabled" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="verifyMode" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.register.verifyMode" /></template>
                <el-select v-model="values.verifyMode">
                  <el-option v-for="n in [1, 2, 3, 4]" :key="n" :label="t(`config.register.verifyMode.${n}`)" :value="n" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="usernameMinLength" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.register.usernameMinLength" /></template>
                <el-input-number v-model="values.usernameMinLength" :min="1" :max="12"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="usernameMaxLength" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.register.usernameMaxLength" /></template>
                <el-input-number v-model="values.usernameMaxLength" :min="1" :max="30"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="usernameRegex" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.register.usernameRegex" help /></template>
                <el-input v-model="values.usernameRegex" maxlength="100"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="smallAvatarSize" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.register.smallAvatarSize" /></template>
                <el-input-number v-model="values.smallAvatarSize" :min="1" :max="9999"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="mediumAvatarSize" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.register.mediumAvatarSize" /></template>
                <el-input-number v-model="values.mediumAvatarSize" :min="1" :max="9999"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="largeAvatarSize" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.register.largeAvatarSize" /></template>
                <el-input-number v-model="values.largeAvatarSize" :min="1" :max="9999"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="avatar" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.register.avatar" /></template>
                <el-input v-model="values.avatar" maxlength="255"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'security' && currentUser.epRank < 1">
          <el-alert type="warning" :closable="false" :show-icon="true">
            <!-- eslint-disable-next-line vue/no-v-html -->
            <template #title><span v-html="$t('error.enterprise')"></span></template>
          </el-alert>
        </template>
        <template v-else-if="type === 'security' && currentUser.epRank >= 1">
          <el-row>
            <el-col :span="24">
              <el-form-item prop="passwordStrength">
                <template #label><label-tip message="config.security.passwordStrength" /></template>
                <el-select v-model="values.passwordStrength" class="w-full">
                  <el-option v-for="n in [0, 1, 2, 3, 4]" :key="n" :value="n" :label="$t(`config.security.passwordStrength.${n}`)" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="passwordMinLength">
                <template #label><label-tip message="config.security.passwordMinLength" help /></template>
                <el-input-number v-model="values.passwordMinLength" :min="0" :max="16" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="passwordMaxLength">
                <template #label><label-tip message="config.security.passwordMaxLength" help /></template>
                <el-input-number v-model="values.passwordMaxLength" :min="16" :max="64" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="userMaxAttempts">
                <template #label><label-tip message="config.security.userMaxAttempts" help /></template>
                <el-input-number v-model="values.userMaxAttempts" :min="0" :max="100" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="userLockMinutes">
                <template #label><label-tip message="config.security.userLockMinutes" help /></template>
                <el-input-number v-model="values.userLockMinutes" :min="1" :max="1440" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="ipCaptchaAttempts">
                <template #label><label-tip message="config.security.ipCaptchaAttempts" help /></template>
                <el-input-number v-model="values.ipCaptchaAttempts" :min="0" :max="100" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="ipMaxAttempts">
                <template #label><label-tip message="config.security.ipMaxAttempts" help /></template>
                <el-input-number v-model="values.ipMaxAttempts" :min="0" :max="999" />
              </el-form-item>
            </el-col>
            <template v-if="currentUser.epRank >= 2 || currentUser.epDisplay">
              <el-divider content-position="left"></el-divider>
              <el-col :span="12">
                <el-form-item prop="passwordMinDays">
                  <template #label><label-tip message="config.security.passwordMinDays" help /></template>
                  <el-input-number v-model="values.passwordMinDays" :min="0" :max="998" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="passwordMaxDays">
                  <template #label><label-tip message="config.security.passwordMaxDays" help /></template>
                  <el-input-number v-model="values.passwordMaxDays" :min="0" :max="999" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="passwordWarnDays">
                  <template #label><label-tip message="config.security.passwordWarnDays" help /></template>
                  <el-input-number v-model="values.passwordWarnDays" :min="0" :max="90" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="passwordMaxHistory">
                  <template #label><label-tip message="config.security.passwordMaxHistory" help /></template>
                  <el-input-number v-model="values.passwordMaxHistory" :min="0" :max="24" />
                </el-form-item>
              </el-col>
            </template>
            <template v-if="currentUser.epRank >= 3 || currentUser.epDisplay">
              <el-divider content-position="left"></el-divider>
              <el-col :span="12">
                <el-form-item prop="twoFactor">
                  <template #label><label-tip message="config.security.twoFactor" help /></template>
                  <el-switch v-model="values.twoFactor" :disabled="currentUser.epRank < 3" />
                </el-form-item>
              </el-col>
            </template>
            <el-col :span="24">
              <el-divider content-position="left"></el-divider>
              <el-form-item prop="ssrfWhiteList">
                <template #label><label-tip message="config.security.ssrfWhiteList" help /></template>
                <el-input v-model="values.ssrfWhiteList" :rows="12" type="textarea" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'sms'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="provider" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.sms.provider" /></template>
                <el-radio-group v-model="values.provider">
                  <el-radio-button v-for="n in [0, 1, 2]" :key="n" :value="n">{{ $t(`config.sms.provider.${n}`) }}</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <template v-if="values.provider !== 0">
              <el-col :span="12">
                <el-form-item prop="maxPerIp">
                  <template #label><label-tip message="config.sms.maxPerIp" /></template>
                  <el-input-number v-model="values.maxPerIp" :min="1" :max="99999" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="codeLength">
                  <template #label><label-tip message="config.sms.codeLength" /></template>
                  <el-input-number v-model="values.codeLength" :min="4" :max="6" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="codeExpires">
                  <template #label><label-tip message="config.sms.codeExpires" help /></template>
                  <el-input-number v-model="values.codeExpires" :min="3" :max="30" />
                </el-form-item>
              </el-col>
              <template v-if="values.provider === 1">
                <el-col :span="12">
                  <el-form-item prop="accessKeyId" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.sms.accessKeyId" /></template>
                    <el-input v-model="values.accessKeyId" maxlength="128" show-password></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="accessKeySecret" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.sms.accessKeySecret" /></template>
                    <el-input v-model="values.accessKeySecret" maxlength="128" show-password></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="templateCode" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.sms.templateCode" /></template>
                    <el-input v-model="values.templateCode" maxlength="32"></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="codeName" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.sms.codeName" /></template>
                    <el-input v-model="values.codeName" maxlength="20"></el-input>
                  </el-form-item>
                </el-col>
              </template>
              <template v-else-if="values.provider === 2">
                <el-col :span="12">
                  <el-form-item prop="secretId" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.sms.secretId" /></template>
                    <el-input v-model="values.secretId" maxlength="128" show-password></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="secretKey" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.sms.secretKey" /></template>
                    <el-input v-model="values.secretKey" maxlength="128" show-password></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="templateId" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.sms.templateId" /></template>
                    <el-input v-model="values.templateId" maxlength="32"></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="sdkAppId" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.sms.sdkAppId" /></template>
                    <el-input v-model="values.sdkAppId" maxlength="64"></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="region" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.sms.region" help /></template>
                    <el-input v-model="values.region" maxlength="64"></el-input>
                  </el-form-item>
                </el-col>
              </template>
              <el-col :span="12">
                <el-form-item prop="signName" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.sms.signName" help /></template>
                  <el-input v-model="values.signName" maxlength="50"></el-input>
                </el-form-item>
              </el-col>
              <el-divider content-position="left">{{ $t('config.sms.test') }}</el-divider>
              <el-col :span="12">
                <el-form-item prop="testMobile">
                  <template #label><label-tip message="config.sms.testMobile" /></template>
                  <el-input v-model="values.testMobile" maxlength="30"></el-input>
                </el-form-item>
              </el-col>
            </template>
          </el-row>
        </template>
        <template v-else-if="type === 'email'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="host" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.email.host" help /></template>
                <el-input v-model="values.host" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="port" :rules="{ type: 'number', min: 0, max: 65535, message: () => $t('v.range', { min: 0, max: 65535 }) }">
                <template #label><label-tip message="config.email.port" help /></template>
                <el-input v-model.number="values.port"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="username" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.email.username" help /></template>
                <el-input v-model="values.username" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="password" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.email.password" help /></template>
                <el-input v-model="values.password" maxlength="50" show-password></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="from" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.email.from" help /></template>
                <el-input v-model="values.from" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="timeout" :rules="{ type: 'number', min: 0, max: 65535, message: () => $t('v.range', { min: 0, max: 65535 }) }">
                <template #label><label-tip message="config.email.timeout" help /></template>
                <el-input v-model.number="values.timeout"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="ssl">
                <template #label><label-tip message="config.email.ssl" help /></template>
                <el-switch v-model="values.ssl" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="maxPerIp">
                <template #label><label-tip message="config.email.maxPerIp" /></template>
                <el-input-number v-model="values.maxPerIp" :min="1" :max="99999" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="codeLength">
                <template #label><label-tip message="config.email.codeLength" /></template>
                <el-input-number v-model="values.codeLength" :min="4" :max="6" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="codeExpires">
                <template #label><label-tip message="config.email.codeExpires" help /></template>
                <el-input-number v-model="values.codeExpires" :min="3" :max="30" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="subject" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.email.subject" /></template>
                <el-input v-model="values.subject" maxlength="100"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="text" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.email.text" help /></template>
                <el-input v-model="values.text" :rows="3" type="textarea" maxlength="400"></el-input>
              </el-form-item>
            </el-col>
            <el-divider content-position="left">{{ $t('config.email.testEmail') }}</el-divider>
            <el-col :span="24">
              <el-form-item prop="testTo">
                <template #label><label-tip message="config.email.testTo" /></template>
                <el-input v-model="values.testTo" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'uploadStorage' || type === 'htmlStorage' || type === 'templateStorage'">
          <el-row>
            <el-col :span="24">
              <el-form-item
                prop="type"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  {
                    validator: (rule: any, value: any, callback: any) => {
                      if (value !== 0 && currentUser.epRank < 1) {
                        callback($t('error.enterprise.short'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip message="config.storage.type" /></template>
                <el-select v-model="values.type" class="w-full">
                  <el-option
                    v-for="n in [0, 1, 10].filter((item) => item === 0 || currentUser.epRank >= 2 || currentUser.epDisplay)"
                    :key="n"
                    :value="n"
                    :label="$t(`config.storage.type.${n}`)"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item
                prop="path"
                :rules="[
                  { pattern: /^(?!.*\.\.).*$/, message: () => $t('v.url') },
                  {
                    asyncValidator: async (rule, value, callback) => {
                      if (!(await storagePathAllowed(value))) {
                        callback($t('config.error.storagePathAllowed'));
                        return;
                      }
                      callback();
                    },
                    trigger: 'blur',
                  },
                ]"
              >
                <template #label><label-tip message="config.storage.path" /></template>
                <el-input v-model="values.path" maxlength="160"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="url" :rules="{ pattern: /^(?!.*\.\.).*$/, message: () => $t('v.url') }">
                <template #label><label-tip message="config.storage.url" /></template>
                <el-input v-model="values.url" maxlength="160"></el-input>
              </el-form-item>
            </el-col>
            <template v-if="values.type === 1">
              <el-col :span="12">
                <el-form-item prop="hostname" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.hostname" /></template>
                  <el-input v-model="values.hostname" maxlength="160" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="port" :rules="{ type: 'number', min: 0, max: 65535, message: () => $t('v.range', { min: 0, max: 65535 }) }">
                  <template #label><label-tip message="config.storage.port" help /></template>
                  <el-input v-model.number="values.port" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="username" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.username" /></template>
                  <el-input v-model="values.username" maxlength="50" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="password" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.password" /></template>
                  <el-input v-model="values.password" type="password" show-password maxlength="160" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="encoding">
                  <template #label><label-tip message="config.storage.encoding" help /></template>
                  <el-input v-model="values.encoding" maxlength="50" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="passive">
                  <template #label><label-tip message="config.storage.passive" help /></template>
                  <el-switch v-model="values.passive" :disabled="currentUser.epRank < 1" />
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item prop="encryption" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.encryption" /></template>
                  <el-select v-model="values.encryption" class="w-full">
                    <el-option v-for="n in [0, 1, 2]" :key="n" :label="$t(`config.storage.encryption.${n}`)" :value="n" />
                  </el-select>
                </el-form-item>
              </el-col>
            </template>
            <template v-if="values.type === 10">
              <el-col :span="24">
                <el-form-item prop="endpoint" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.endpoint" /></template>
                  <el-input v-model="values.endpoint" maxlength="160" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="region" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.region" /></template>
                  <el-input v-model="values.region" maxlength="160" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="bucket" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.bucket" /></template>
                  <el-input v-model="values.bucket" maxlength="160" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item prop="accessKey" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.accessKey" /></template>
                  <el-input v-model="values.accessKey" type="password" show-password maxlength="160" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item prop="secretKey" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.secretKey" /></template>
                  <el-input v-model="values.secretKey" type="password" show-password maxlength="160" :disabled="currentUser.epRank < 1"></el-input>
                </el-form-item>
              </el-col>
            </template>
          </el-row>
        </template>
        <template v-else-if="type === 'customs'">
          <el-row>
            <el-col v-for="field in fields" :key="field.code" :span="field.double ? 12 : 24">
              <el-form-item :prop="field.code" :rules="field.required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="field.name" /></template>
                <field-item v-model="values[field.code]" v-model:model-key="values[field.code + 'Key']" :field="field"></field-item>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else>
          <el-row>
            <el-col :span="12">
              <el-form-item prop="port" :rules="{ type: 'number', min: 0, max: 65535, message: () => $t('v.range', { min: 0, max: 65535 }) }">
                <template #label><label-tip message="config.port" help /></template>
                <el-input v-model.number="values.port"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="contextPath">
                <template #label><label-tip message="config.contextPath" help /></template>
                <el-input v-model="values.contextPath" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="channelUrl" :rules="{ pattern: /^\/[\w-]+$/, message: () => $t('config.error.channelUrlPattern') }">
                <template #label><label-tip message="config.channelUrl" help /></template>
                <el-input v-model="values.channelUrl" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="articleUrl">
                <template #label><label-tip message="config.articleUrl" help /></template>
                <el-input v-model="values.articleUrl" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="defaultSiteId" :label="$t('config.defaultSite')">
                <el-tree-select
                  v-model="values.defaultSiteId"
                  :data="siteList"
                  node-key="id"
                  :props="{ label: 'name' }"
                  class="w-full"
                  :render-after-expand="false"
                  check-strictly
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="multiDomain">
                <template #label><label-tip message="config.multiDomain" help /></template>
                <el-switch v-model="values.multiDomain" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <div v-if="type !== 'security' || currentUser.epRank >= 1" v-loading="buttonLoading">
          <el-button :disabled="perm(`config:${type}:update`)" type="primary" native-type="submit" @click.prevent="() => handleSubmit()">
            {{ $t('save') }}
          </el-button>
          <el-button v-if="type === 'sms' && values.provider !== 0" :disabled="perm('config:sms:update') || !values.testMobile?.trim()" @click="() => handleSendTestSms()">
            {{ $t('config.sms.op.testSend') }}
          </el-button>
          <el-button v-if="type === 'email'" :disabled="perm('config:email:update') || !values.testTo?.trim()" @click="() => handleSendTestEmail()">
            {{ $t('config.email.op.testSend') }}
          </el-button>
        </div>
      </el-form>
    </el-main>
  </el-container>
</template>

<style lang="scss" scoped>
.el-tabs {
  :deep(.el-tabs__header) {
    margin-right: 1px;
  }
  :deep(.el-tabs__content) {
    flex-grow: 0;
  }
}
</style>
