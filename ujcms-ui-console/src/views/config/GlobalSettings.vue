<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { CopyDocument, TopRight } from '@element-plus/icons-vue';
import { toTree } from '@/utils/tree';
import { lockedTip } from '@/utils/commercial';
import { querySiteList } from '@/api/system';
import {
  queryConfigModel,
  queryConfig,
  queryConfigSms,
  queryConfigEmail,
  queryUploadStorage,
  queryHtmlStorage,
  queryTemplateStorage,
  queryPackageStorage,
  updateConfigBase,
  updateConfigCustoms,
  updateConfigUpload,
  updateConfigRegister,
  updateConfigSecurity,
  applyConfigSecurityL3,
  queryTwoFactorReadiness,
  updateConfigSms,
  updateConfigEmail,
  sendTestSms,
  sendTestEmail,
  updateUploadStorage,
  updateHtmlStorage,
  updateTemplateStorage,
  updatePackageStorage,
  storagePathAllowed,
  queryConfigGrey,
  updateConfigGrey,
  queryConfigOauth,
  updateConfigOauth,
} from '@/api/config';
import { htmlAllHome } from '@/api/content';
import { queryConfigAi, updateConfigAi, testConfigAi, testConfigAiMt } from '@/api/ai';
import { perm, hasPermission, currentUser, isShowTier } from '@/stores/useCurrentUser';
import { GLOBAL_SETTINGS_TABS, lockedSettingsTier, visibleSettingsTypes } from './settingsNav';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import LabelTip from '@/components/LabelTip.vue';
import FieldItem from '@/views/config/components/FieldItem.vue';
import OauthProviderCard from '@/views/config/components/OauthProviderCard.vue';
import ThemeColorSwatches from '@/components/ThemeColorSwatches.vue';
import { enumParam, useRouteState } from '@/composables/useRouteState';
import { useSettingsFieldFocus } from './settingsFocus';

defineOptions({
  name: 'GlobalSettings',
});
const { t } = useI18n();
const router = useRouter();
const sysConfig = useSysConfigStore();
const form = ref<any>();
const config = ref<any>({});
const values = ref<any>({});
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
const siteList = ref<any[]>([]);
const model = ref<any>();
const fields = computed(() => JSON.parse(model.value?.customs || '[]'));
// 第三方登录页：会员注册关闭时，首次通过第三方登录的访客无法注册用户名
const registerDisabled = computed(() => config.value.register?.enabled === false);
const handleCopy = async (text?: string) => {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success(t('success'));
  } catch {
    ElMessage.error(t('error.title'));
  }
};

// Long 字段序列化为字符串，el-input-number 需要数字
const fetchConfigAi = async () => {
  const ai = await queryConfigAi();
  ai.dailyTokenLimit = Number(ai.dailyTokenLimit ?? 0);
  return ai;
};

// 页签描述符与命令面板共用（settingsNav.ts，console-shell S5）：顺序即左侧导航顺序；data.ts 权限树的对应节点与此同序
const types: string[] = visibleSettingsTypes(GLOBAL_SETTINGS_TABS);
// 四个存储点共用同一张表单，左侧导航合为“存储设置”一项，页内以分段按钮切换。type 仍是具体的存储点，
// 故 ?type=htmlStorage 深链、权限码（config:<type>:update）与读取、保存逻辑均不变
const STORAGE_TYPES = ['uploadStorage', 'htmlStorage', 'templateStorage', 'packageStorage'];
const storageTypes = types.filter((tp) => STORAGE_TYPES.includes(tp));
const toNav = (tp: string): string => (STORAGE_TYPES.includes(tp) ? 'storage' : tp);
const navs = [...new Set(types.map(toNav))];
// 社区版锁定的页签：导航项带商业版标识
const lockedTiers = Object.fromEntries(navs.map((nav) => [nav, lockedSettingsTier(GLOBAL_SETTINGS_TABS, nav)]));
// 当前设置页存于 query.type（docs/spec/console-view-state）：刷新或重访页签时停留在原设置页，也可深链
// （站点设置“AI功能”页的“前往全局设置”直达“AI服务商”页签）；非法值、无权限的设置页回落到首个页签
const { type } = useRouteState({ type: enumParam(types[0] ?? '', types) });
// 命令面板选中某个设置项后：打开本视图对应页签、数据就绪后滚动到该项（settingsFocus.ts）
useSettingsFieldFocus('GlobalSettings', type, loading, form);
const nav = computed<string>({
  get: () => toNav(type.value),
  set: (value) => {
    type.value = value === 'storage' ? (storageTypes[0] ?? '') : value;
  },
});

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
  } else if (type.value === 'ai') {
    values.value = await fetchConfigAi();
    savedProviderCodes.value = new Set((values.value.providers ?? []).map((item: any) => item.code));
  } else if (type.value === 'grey') {
    values.value = await queryConfigGrey();
  } else if (type.value === 'oauth') {
    values.value = await queryConfigOauth();
  } else if (type.value === 'uploadStorage') {
    values.value = await queryUploadStorage();
  } else if (type.value === 'htmlStorage') {
    values.value = await queryHtmlStorage();
  } else if (type.value === 'templateStorage') {
    values.value = await queryTemplateStorage();
  } else if (type.value === 'packageStorage') {
    values.value = await queryPackageStorage();
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
      } else if (type.value === 'ai') {
        await updateConfigAi(values.value);
        values.value = await fetchConfigAi();
        savedProviderCodes.value = new Set((values.value.providers ?? []).map((item: any) => item.code));
      } else if (type.value === 'grey') {
        await updateConfigGrey(values.value);
        // 置灰样式写在首页模板里：保存后重新生成所有站点的首页静态页（与原“生成管理”页内保存的行为一致）。
        // 权限树把 generator:html 与本设置打包授予，手工改过的角色缺该权限时只保存、不生成
        if (hasPermission('generator:html')) {
          await htmlAllHome();
        }
      } else if (type.value === 'oauth') {
        await updateConfigOauth(values.value);
        // 重新读取：应用密钥只写不回显，保存后输入框清空，“库中已存密钥”的状态随之更新
        values.value = await queryConfigOauth();
      } else if (type.value === 'uploadStorage') {
        await updateUploadStorage(values.value);
      } else if (type.value === 'htmlStorage') {
        await updateHtmlStorage(values.value);
      } else if (type.value === 'templateStorage') {
        await updateTemplateStorage(values.value);
      } else if (type.value === 'packageStorage') {
        await updatePackageStorage(values.value);
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
// 打开“双因子认证”开关时先检查就绪情况：开启后登录依赖短信与登记的手机号码，条件不满足会把人锁在门外。
// 此处仅为提示与确认，真正的拦截由服务端在保存时完成。
const handleTwoFactorChange = async (enabled: string | number | boolean) => {
  if (!enabled) return;
  const readiness = await queryTwoFactorReadiness();
  const problems: string[] = [];
  if (!readiness.smsConfigured) problems.push(t('config.security.twoFactor.requiresSms'));
  if (!readiness.actorHasMobile) problems.push(t('config.security.twoFactor.requiresOwnMobile'));
  // 用户允许尝试次数与本开关在同一表单，以表单当前值为准
  if (!(values.value.userMaxAttempts > 0)) problems.push(t('config.security.twoFactor.requiresUserMaxAttempts'));
  if (problems.length > 0) {
    values.value.twoFactor = false;
    ElMessageBox.alert(problems.join('<br />'), t('config.security.twoFactor'), { type: 'warning', dangerouslyUseHTMLString: true });
    return;
  }
  const message =
    readiness.usersWithoutMobile > 0
      ? t('config.security.twoFactor.confirmWithCount', { count: readiness.usersWithoutMobile })
      : t('config.security.twoFactor.confirm');
  try {
    await ElMessageBox.confirm(message, t('config.security.twoFactor'), { type: 'warning', dangerouslyUseHTMLString: true });
  } catch {
    values.value.twoFactor = false;
  }
};
const handleApplyL3 = async () => {
  try {
    await ElMessageBox.confirm(t('config.security.applyL3.confirmContent'), t('config.security.applyL3.confirmTitle'), {
      type: 'warning',
      dangerouslyUseHTMLString: true,
    });
  } catch {
    return;
  }
  buttonLoading.value = true;
  try {
    const { result } = await applyConfigSecurityL3();
    await load();
    switchType();
    // 双因子只在短信已配置、且操作者本人已登记手机号码时开启，否则跳过并说明原因，以免把管理员锁在门外
    if (!result?.twoFactorApplied) {
      ElMessage.warning(t(result?.smsConfigured ? 'config.security.applyL3.twoFactorSkippedNoMobile' : 'config.security.applyL3.twoFactorSkipped'));
    } else if (result.usersWithoutMobile > 0) {
      ElMessage.warning(t('config.security.applyL3.usersWithoutMobile', { count: result.usersWithoutMobile }));
    } else {
      ElMessage.success(t('config.security.applyL3.success'));
    }
  } finally {
    buttonLoading.value = false;
  }
};
const invalidExtension = (extensions?: string) => {
  // 黑名单来自 application.yaml（Props），只在管理端 /backend/core/config 返回；本页已加载该配置，优先取之，
  // 其次取 sysConfigStore 里 enrichForAdmin() 合并的值。校验器可能在 required 规则之前拿到空值，需容错
  const blacklist = String(config.value?.uploadsExtensionBlacklist ?? sysConfig.base.uploadsExtensionBlacklist ?? '').split(',');
  const extensionList = (extensions ?? '').split(',').map((item) => item.trim());
  return blacklist.some((item) => item && extensionList.includes(item));
};

// AI 服务商编辑对话框。index < 0 为新增
const providerVisible = ref<boolean>(false);
const providerForm = ref<any>();
const providerIndex = ref<number>(-1);
const provider = ref<any>({});
// 已持久化的服务商标识：密钥输入框据此区分“留空表示不修改”与“可为空”
const savedProviderCodes = ref<Set<string>>(new Set());
// 服务商内部标识：自动生成、不向运营者展示；作为默认/写作/审核服务商的引用键及日志、密钥合并的匹配键
// 机器翻译引擎（后端 Config.Ai 的 mt:* 保留代码，仅翻译服务商可选）
const mtEngines = [
  { value: 'mt:baidu', label: 'config.ai.mt.baidu' },
  { value: 'mt:youdao', label: 'config.ai.mt.youdao' },
  { value: 'mt:deepl', label: 'config.ai.mt.deepl' },
];
const newProviderCode = (): string => {
  const existing = new Set((values.value.providers ?? []).map((item: any) => item.code));
  for (;;) {
    const hex = Array.from(crypto.getRandomValues(new Uint8Array(4)), (b) => b.toString(16).padStart(2, '0')).join('');
    const code = `p-${hex}`;
    if (!existing.has(code)) return code;
  }
};
// 请求附加参数预设：各厂商关闭深度思考的字段。思考 token 计入生成预算，会耗尽元信息、AI 标题、审核等小预算 JSON 调用
// key 为稳定标识（按接口地址推断时据此匹配），显示名随控制台语言
const extraParamsPresets = computed(() =>
  [
    { key: 'glm', value: '{"thinking": {"type": "disabled"}}' },
    { key: 'qwen', value: '{"enable_thinking": false}' },
    { key: 'ollama', value: '{"think": false}' },
    { key: 'vllm', value: '{"chat_template_kwargs": {"enable_thinking": false}}' },
    { key: 'openai', value: '{"reasoning_effort": "low"}' },
  ].map((preset) => ({ ...preset, label: t(`config.ai.provider.extraParams.preset.${preset.key}`) })),
);
// 按接口地址推断厂商的关闭深度思考预设（编辑表单的建议、测试检测到思考后的一键应用）。只认主机名——按模型名猜会把
// 经 vLLM 部署的 GLM/Qwen 指向厂商云端字段；识别不出时返回 undefined，由操作者在预设中自选
const suggestExtraParamsPreset = (p: { baseUrl?: string }) => {
  const url = (p.baseUrl ?? '').toLowerCase();
  const byKey = (key: string) => extraParamsPresets.value.find((preset) => preset.key === key);
  if (url.includes('bigmodel.cn') || url.includes('volces.com')) return byKey('glm');
  if (url.includes('dashscope.aliyuncs.com')) return byKey('qwen');
  if (url.includes(':11434') || url.includes('ollama')) return byKey('ollama');
  if (url.includes('api.openai.com')) return byKey('openai');
  return undefined;
};
const suggestedPreset = computed(() => (provider.value?.extraParams ? undefined : suggestExtraParamsPreset(provider.value ?? {})));
const handleProviderAdd = () => {
  providerIndex.value = -1;
  provider.value = { code: newProviderCode(), name: '', baseUrl: '', apiKey: '', model: '', timeoutSeconds: 60, temperature: undefined, extraParams: '' };
  providerVisible.value = true;
};
const handleProviderEdit = (index: number) => {
  providerIndex.value = index;
  provider.value = { ...values.value.providers[index] };
  providerVisible.value = true;
};
const handleProviderDelete = (index: number) => {
  values.value.providers.splice(index, 1);
};
const handleProviderSubmit = () => {
  providerForm.value.validate((valid: boolean) => {
    if (!valid) return;
    if (providerIndex.value < 0) {
      values.value.providers.push({ ...provider.value });
    } else {
      values.value.providers[providerIndex.value] = { ...provider.value };
    }
    providerVisible.value = false;
  });
};
// 连通性测试。以表格中的当前配置发起测试，无需先保存；密钥留空时服务端沿用同代码服务商的已存密钥。
// 服务端在响应中检测到深度思考（thinking）时：附加参数为空 → 按接口地址推荐预设，一键填入并重测；
// 已填参数仍在思考 → 提示核对字段名。否则短输出调用会在编辑器里报“返回为空”，操作者却想不到是这里
const handleTestAi = async (index: number) => {
  const row = values.value.providers[index];
  buttonLoading.value = true;
  try {
    const { ok, latencyMs, model, error, thinking } = await testConfigAi({ provider: row });
    if (!ok) {
      ElMessageBox.alert(error, t('config.ai.test.failure'));
      return;
    }
    if (!thinking) {
      ElMessage.success(`${t('config.ai.test.success')}: ${model}, ${latencyMs}ms`);
      return;
    }
    const summary = t('config.ai.test.thinking', [model, latencyMs]);
    if (row.extraParams) {
      ElMessageBox.alert(`${summary}${t('config.ai.test.thinking.stillOn')}`, t('config.ai.test.thinking.title'), { type: 'warning' });
      return;
    }
    const preset = suggestExtraParamsPreset(row);
    if (preset == null) {
      ElMessageBox.alert(`${summary}${t('config.ai.test.thinking.pick')}`, t('config.ai.test.thinking.title'), { type: 'warning' });
      return;
    }
    try {
      await ElMessageBox.confirm(`${summary}${t('config.ai.test.thinking.suggest', [preset.label])}`, t('config.ai.test.thinking.title'), {
        type: 'warning',
        confirmButtonText: t('config.ai.test.thinking.apply'),
        cancelButtonText: t('cancel'),
      });
    } catch {
      return;
    }
    row.extraParams = preset.value;
    buttonLoading.value = false;
    await handleTestAi(index);
  } finally {
    buttonLoading.value = false;
  }
};
// 机器翻译引擎连通性测试（按引擎逐个测试）。以表单当前凭证将“你好”翻译为英文，无需先保存；密钥留空时服务端沿用已存密钥
const handleTestMt = async (engine: string) => {
  buttonLoading.value = true;
  try {
    const { ok, latencyMs, sample, text, error } = await testConfigAiMt({ engine, mt: values.value.mt });
    if (ok) {
      ElMessage.success(`${t('config.ai.test.success')}: ${sample} → ${text}, ${latencyMs}ms`);
    } else {
      ElMessageBox.alert(error, t('config.ai.test.failure'));
    }
  } finally {
    buttonLoading.value = false;
  }
};
</script>

<template>
  <el-container>
    <el-aside width="180px" class="pr-3">
      <el-tabs v-model="nav" tab-position="left" stretch class="bg-white sidebar-tabs">
        <el-tab-pane v-for="item in navs" :key="item" :name="item">
          <template #label>
            {{ $t(`config.settings.${item}`) }}<commercial-badge v-if="lockedTiers[item]" :tier="lockedTiers[item]" class="ml-1" />
          </template>
        </el-tab-pane>
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
        <template v-else-if="type === 'security' && currentUser.commercialTier < 1">
          <commercial-notice :tier="1" />
        </template>
        <template v-else-if="type === 'security' && currentUser.commercialTier >= 1">
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
            <el-col :span="12">
              <el-form-item prop="forceChangePasswordOnFirstLogin">
                <template #label><label-tip message="config.security.forceChangePasswordOnFirstLogin" help /></template>
                <el-switch v-model="values.forceChangePasswordOnFirstLogin" />
              </el-form-item>
            </el-col>
            <template v-if="isShowTier(2)">
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
            <template v-if="isShowTier(3)">
              <el-divider content-position="left"></el-divider>
              <el-col :span="12">
                <el-form-item prop="twoFactor">
                  <template #label><label-tip message="config.security.twoFactor" help /></template>
                  <el-switch v-model="values.twoFactor" :disabled="currentUser.commercialTier < 3" @change="handleTwoFactorChange" />
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
        <template v-else-if="type === 'oauth' && currentUser.commercialTier < 2">
          <commercial-notice :tier="2" />
        </template>
        <template v-else-if="type === 'oauth'">
          <!-- 切换页签时 values 在请求返回前仍是上一页签的数据：三个服务商对象就绪后才渲染。
               每个服务商一张卡片（OauthProviderCard）：卡片头是启用开关，卡片体是应用凭据与须到开放平台登记的回调地址 -->
          <div v-if="values.weixin && values.qq && values.weibo">
            <el-alert type="info" :closable="false" show-icon class="mb-4" :title="$t('config.oauth.note')" />
            <!-- 首次通过第三方登录的访客要注册用户名：注册关闭时他们走不完登录，仅此时提示 -->
            <el-alert v-if="registerDisabled" type="warning" :closable="false" show-icon class="mb-4">
              <template #title>
                {{ $t('config.oauth.registerDisabled') }}
                <el-button v-if="hasPermission('config:register:update')" type="primary" link size="small" @click="() => (type = 'register')">
                  {{ $t('config.settings.register') }}
                </el-button>
              </template>
            </el-alert>
            <oauth-provider-card provider="weixin" :active="values.weixin.enabled">
              <template #title>
                <el-form-item prop="weixin.enabled">
                  <template #label><label-tip message="config.oauth.weixin.enabled" /></template>
                  <el-switch v-model="values.weixin.enabled" />
                </el-form-item>
              </template>
              <el-row>
                <el-col :span="12">
                  <el-form-item prop="weixin.clientId" :rules="{ required: values.weixin.enabled, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.oauth.weixin.clientId" /></template>
                    <el-input v-model="values.weixin.clientId" maxlength="100"></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="weixin.clientSecret" :rules="{ required: values.weixin.enabled && !values.storedSecrets?.weixin, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.oauth.weixin.clientSecret" /></template>
                    <el-input
                      v-model="values.weixin.clientSecret"
                      maxlength="200"
                      show-password
                      :placeholder="values.storedSecrets?.weixin ? $t('config.secretKeepBlank') : undefined"
                    ></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item>
                    <template #label><label-tip message="config.oauth.weixin.callbackUrl" /></template>
                    <el-input :model-value="values.callbackUrls?.weixin" readonly>
                      <template #append>
                        <el-button :icon="CopyDocument" @click="() => handleCopy(values.callbackUrls?.weixin)">{{ $t('copy') }}</el-button>
                      </template>
                    </el-input>
                    <div class="flex items-center w-full text-xs text-gray-secondary leading-5 mt-1">
                      {{ $t('config.oauth.weixin.callbackUrl.tooltip') }}
                      <el-link type="primary" underline="never" href="https://open.weixin.qq.com/" target="_blank" rel="noopener" class="ml-2 !text-xs">
                        {{ $t('config.oauth.weixin.platform') }}<el-icon class="ml-0.5"><TopRight /></el-icon>
                      </el-link>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>
            </oauth-provider-card>
            <oauth-provider-card provider="qq" :active="values.qq.enabled">
              <template #title>
                <el-form-item prop="qq.enabled">
                  <template #label><label-tip message="config.oauth.qq.enabled" /></template>
                  <el-switch v-model="values.qq.enabled" />
                </el-form-item>
              </template>
              <el-row>
                <el-col :span="12">
                  <el-form-item prop="qq.clientId" :rules="{ required: values.qq.enabled, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.oauth.qq.clientId" /></template>
                    <el-input v-model="values.qq.clientId" maxlength="100"></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="qq.clientSecret" :rules="{ required: values.qq.enabled && !values.storedSecrets?.qq, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.oauth.qq.clientSecret" /></template>
                    <el-input
                      v-model="values.qq.clientSecret"
                      maxlength="200"
                      show-password
                      :placeholder="values.storedSecrets?.qq ? $t('config.secretKeepBlank') : undefined"
                    ></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item>
                    <template #label><label-tip message="config.oauth.qq.callbackUrl" /></template>
                    <el-input :model-value="values.callbackUrls?.qq" readonly>
                      <template #append>
                        <el-button :icon="CopyDocument" @click="() => handleCopy(values.callbackUrls?.qq)">{{ $t('copy') }}</el-button>
                      </template>
                    </el-input>
                    <div class="flex items-center w-full text-xs text-gray-secondary leading-5 mt-1">
                      {{ $t('config.oauth.qq.callbackUrl.tooltip') }}
                      <el-link type="primary" underline="never" href="https://connect.qq.com/" target="_blank" rel="noopener" class="ml-2 !text-xs">
                        {{ $t('config.oauth.qq.platform') }}<el-icon class="ml-0.5"><TopRight /></el-icon>
                      </el-link>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>
            </oauth-provider-card>
            <oauth-provider-card provider="weibo" :active="values.weibo.enabled">
              <template #title>
                <el-form-item prop="weibo.enabled">
                  <template #label><label-tip message="config.oauth.weibo.enabled" /></template>
                  <el-switch v-model="values.weibo.enabled" />
                </el-form-item>
              </template>
              <el-row>
                <el-col :span="12">
                  <el-form-item prop="weibo.clientId" :rules="{ required: values.weibo.enabled, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.oauth.weibo.clientId" /></template>
                    <el-input v-model="values.weibo.clientId" maxlength="100"></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="weibo.clientSecret" :rules="{ required: values.weibo.enabled && !values.storedSecrets?.weibo, message: () => $t('v.required') }">
                    <template #label><label-tip message="config.oauth.weibo.clientSecret" /></template>
                    <el-input
                      v-model="values.weibo.clientSecret"
                      maxlength="200"
                      show-password
                      :placeholder="values.storedSecrets?.weibo ? $t('config.secretKeepBlank') : undefined"
                    ></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item>
                    <template #label><label-tip message="config.oauth.weibo.callbackUrl" /></template>
                    <el-input :model-value="values.callbackUrls?.weibo" readonly>
                      <template #append>
                        <el-button :icon="CopyDocument" @click="() => handleCopy(values.callbackUrls?.weibo)">{{ $t('copy') }}</el-button>
                      </template>
                    </el-input>
                    <div class="flex items-center w-full text-xs text-gray-secondary leading-5 mt-1">
                      {{ $t('config.oauth.weibo.callbackUrl.tooltip') }}
                      <el-link type="primary" underline="never" href="https://open.weibo.com/" target="_blank" rel="noopener" class="ml-2 !text-xs">
                        {{ $t('config.oauth.weibo.platform') }}<el-icon class="ml-0.5"><TopRight /></el-icon>
                      </el-link>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>
            </oauth-provider-card>
          </div>
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
                  <el-form-item prop="accessKeySecret">
                    <template #label><label-tip message="config.sms.accessKeySecret" /></template>
                    <el-input v-model="values.accessKeySecret" maxlength="128" show-password :placeholder="$t('config.secretKeepBlank')"></el-input>
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
                  <el-form-item prop="secretKey">
                    <template #label><label-tip message="config.sms.secretKey" /></template>
                    <el-input v-model="values.secretKey" maxlength="128" show-password :placeholder="$t('config.secretKeepBlank')"></el-input>
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
              <el-form-item prop="password">
                <template #label><label-tip message="config.email.password" help /></template>
                <el-input v-model="values.password" maxlength="50" show-password :placeholder="$t('config.secretKeepBlank')"></el-input>
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
        <template v-else-if="type === 'ai' && currentUser.commercialTier < 2">
          <commercial-notice :tier="2" />
        </template>
        <template v-else-if="type === 'ai'">
          <el-row>
            <el-col :span="24">
              <!-- 本页只管服务商、凭证与全局额度；各站点启用哪些功能在“站点设置 → AI功能”，两页标签名不同以免混淆 -->
              <el-alert type="info" :closable="false" show-icon class="mb-4">
                <template #title>
                  {{ $t('config.ai.siteNote') }}
                  <el-button
                    v-if="hasPermission('siteSettings:ai:update')"
                    type="primary"
                    link
                    size="small"
                    @click="() => router.push({ name: 'SiteSettings', query: { type: 'ai' } })"
                  >
                    {{ $t('config.ai.op.goSiteSettings') }}
                  </el-button>
                </template>
              </el-alert>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.ai.enabled" help /></template>
                <el-switch v-model="values.enabled" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="providers">
                <template #label><label-tip message="config.ai.providers" help /></template>
                <div class="w-full">
                  <el-button :disabled="perm('config:ai:update') || (values.providers?.length ?? 0) >= 10" @click="() => handleProviderAdd()">
                    {{ $t('config.ai.op.addProvider') }}
                  </el-button>
                  <el-table :data="values.providers" class="mt-2">
                    <el-table-column property="name" :label="$t('config.ai.provider.name')" min-width="200" show-overflow-tooltip></el-table-column>
                    <el-table-column property="baseUrl" :label="$t('config.ai.provider.baseUrl')" min-width="180" show-overflow-tooltip></el-table-column>
                    <el-table-column property="model" :label="$t('config.ai.provider.model')" width="150" show-overflow-tooltip></el-table-column>
                    <el-table-column :label="$t('table.action')" width="150">
                      <template #default="{ $index }">
                        <el-button type="primary" :disabled="perm('config:ai:update')" size="small" link @click="() => handleTestAi($index)">
                          {{ $t('config.ai.op.test') }}
                        </el-button>
                        <el-button type="primary" :disabled="perm('config:ai:update')" size="small" link @click="() => handleProviderEdit($index)">
                          {{ $t('edit') }}
                        </el-button>
                        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleProviderDelete($index)">
                          <template #reference>
                            <el-button type="primary" :disabled="perm('config:ai:update')" size="small" link>{{ $t('delete') }}</el-button>
                          </template>
                        </el-popconfirm>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="defaultProvider">
                <template #label><label-tip message="config.ai.defaultProvider" help /></template>
                <el-select v-model="values.defaultProvider" clearable class="w-full">
                  <el-option v-for="p in values.providers" :key="p.code" :label="p.name" :value="p.code" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="writingProvider">
                <template #label><label-tip message="config.ai.writingProvider" help /></template>
                <el-select v-model="values.writingProvider" clearable class="w-full">
                  <el-option v-for="p in values.providers" :key="p.code" :label="p.name" :value="p.code" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="moderationProvider">
                <template #label><label-tip message="config.ai.moderationProvider" help /></template>
                <el-select v-model="values.moderationProvider" clearable class="w-full">
                  <el-option v-for="p in values.providers" :key="p.code" :label="p.name" :value="p.code" />
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 占位列：保持语义行对齐（服务商一行、日志与配额一行） -->
            <el-col :span="12"></el-col>
            <el-col :span="12">
              <el-form-item prop="logRetentionDays" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.ai.logRetentionDays" help /></template>
                <el-input-number v-model="values.logRetentionDays" :min="30" :max="3650"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="dailyTokenLimit" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.ai.dailyTokenLimit" help /></template>
                <el-input-number v-model="values.dailyTokenLimit" :min="0" :max="1000000000000" :step="10000"></el-input-number>
              </el-form-item>
            </el-col>
            <el-divider content-position="left">{{ $t('config.ai.translation') }}</el-divider>
            <el-col :span="12">
              <el-form-item prop="translationProvider">
                <template #label><label-tip message="config.ai.translationProvider" help /></template>
                <el-select v-model="values.translationProvider" clearable class="w-full">
                  <el-option-group :label="$t('config.ai.providerGroup.llm')">
                    <el-option v-for="p in values.providers" :key="p.code" :label="p.name" :value="p.code" />
                  </el-option-group>
                  <el-option-group :label="$t('config.ai.providerGroup.mt')">
                    <el-option v-for="m in mtEngines" :key="m.value" :label="$t(m.label)" :value="m.value" />
                  </el-option-group>
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 占位列：翻译服务商独占一行，下方引擎凭证成对同行 -->
            <el-col :span="12"></el-col>
            <!-- 机器翻译引擎凭证：与所选翻译服务商无关，始终可配置——已填写凭证的引擎可在批量翻译中按次选用，
                 并作为栏目名称翻译的回落引擎；密钥只写不回显（留空保持不变）；测试按钮按引擎逐个测试 -->
            <template v-if="values.mt">
              <el-col :span="12">
                <el-form-item prop="mt.baiduAppId">
                  <template #label><label-tip message="config.ai.mt.baiduAppId" help /></template>
                  <el-input v-model="values.mt.baiduAppId" maxlength="50"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="mt.baiduSecret">
                  <template #label><label-tip message="config.ai.mt.baiduSecret" /></template>
                  <el-input v-model="values.mt.baiduSecret" maxlength="100" show-password :placeholder="$t('config.secretKeepBlank')">
                    <template #append>
                      <el-button :loading="buttonLoading" :disabled="perm('config:ai:update')" @click="() => handleTestMt('mt:baidu')">
                        {{ $t('config.ai.op.test') }}
                      </el-button>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="mt.youdaoAppKey">
                  <template #label><label-tip message="config.ai.mt.youdaoAppKey" help /></template>
                  <el-input v-model="values.mt.youdaoAppKey" maxlength="50"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="mt.youdaoSecret">
                  <template #label><label-tip message="config.ai.mt.youdaoSecret" /></template>
                  <el-input v-model="values.mt.youdaoSecret" maxlength="100" show-password :placeholder="$t('config.secretKeepBlank')">
                    <template #append>
                      <el-button :loading="buttonLoading" :disabled="perm('config:ai:update')" @click="() => handleTestMt('mt:youdao')">
                        {{ $t('config.ai.op.test') }}
                      </el-button>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="mt.deeplAuthKey">
                  <template #label><label-tip message="config.ai.mt.deeplAuthKey" help /></template>
                  <el-input v-model="values.mt.deeplAuthKey" maxlength="100" show-password :placeholder="$t('config.secretKeepBlank')">
                    <template #append>
                      <el-button :loading="buttonLoading" :disabled="perm('config:ai:update')" @click="() => handleTestMt('mt:deepl')">
                        {{ $t('config.ai.op.test') }}
                      </el-button>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
            </template>
          </el-row>
          <el-dialog v-model="providerVisible" :title="$t('config.ai.provider')" width="640px" append-to-body>
            <el-form ref="providerForm" :model="provider" label-width="150px">
              <el-form-item
                prop="name"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  {
                    validator: (rule: any, value: any, callback: any) => {
                      if (values.providers?.some((item: any, i: number) => i !== providerIndex && item.name === value)) {
                        callback($t('config.ai.error.nameExist'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip message="config.ai.provider.name" /></template>
                <el-input v-model="provider.name" maxlength="50"></el-input>
              </el-form-item>
              <el-form-item prop="baseUrl" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.ai.provider.baseUrl" help /></template>
                <el-input v-model="provider.baseUrl" maxlength="255" placeholder="https://api.deepseek.com/v1"></el-input>
              </el-form-item>
              <el-form-item prop="apiKey">
                <template #label><label-tip message="config.ai.provider.apiKey" help /></template>
                <el-input
                  v-model="provider.apiKey"
                  maxlength="256"
                  show-password
                  :placeholder="savedProviderCodes.has(provider.code) ? $t('config.secretKeepBlank') : $t('config.ai.provider.apiKey.optional')"
                ></el-input>
              </el-form-item>
              <el-form-item prop="model" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.ai.provider.model" /></template>
                <el-input v-model="provider.model" maxlength="100" placeholder="deepseek-chat"></el-input>
              </el-form-item>
              <el-form-item prop="timeoutSeconds" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.ai.provider.timeoutSeconds" help /></template>
                <el-input-number v-model="provider.timeoutSeconds" :min="5" :max="600"></el-input-number>
              </el-form-item>
              <el-form-item prop="temperature">
                <template #label><label-tip message="config.ai.provider.temperature" help /></template>
                <el-input-number v-model="provider.temperature" :min="0" :max="2" :step="0.1"></el-input-number>
              </el-form-item>
              <el-form-item
                prop="extraParams"
                :rules="{
                  validator: (rule: any, value: any, callback: any) => {
                    if (!value?.trim()) {
                      callback();
                      return;
                    }
                    try {
                      const parsed = JSON.parse(value);
                      if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
                        callback($t('config.ai.error.extraParamsObject'));
                        return;
                      }
                    } catch {
                      callback($t('config.ai.error.extraParamsObject'));
                      return;
                    }
                    callback();
                  },
                }"
              >
                <template #label><label-tip message="config.ai.provider.extraParams" help /></template>
                <el-input v-model="provider.extraParams" type="textarea" :rows="2" maxlength="1000" placeholder='{"thinking": {"type": "disabled"}}'></el-input>
                <!-- 按接口地址识别出厂商且附加参数为空：主动建议，而不是等到编辑器里报“返回为空” -->
                <el-alert v-if="suggestedPreset" type="warning" :closable="false" show-icon class="mt-1">
                  <template #title>
                    {{ $t('config.ai.provider.extraParams.suggest', [suggestedPreset.label]) }}
                    <el-button link type="primary" size="small" @click="() => (provider.extraParams = suggestedPreset!.value)">
                      {{ $t('config.ai.provider.extraParams.apply') }}
                    </el-button>
                  </template>
                </el-alert>
                <div class="text-xs">
                  {{ $t('config.ai.provider.extraParams.presets') }}{{ $t('punct.colon') }}
                  <el-button v-for="preset in extraParamsPresets" :key="preset.key" link type="primary" size="small" @click="() => (provider.extraParams = preset.value)">
                    {{ preset.label }}
                  </el-button>
                </div>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="providerVisible = false">{{ $t('cancel') }}</el-button>
              <el-button type="primary" @click="() => handleProviderSubmit()">{{ $t('ok') }}</el-button>
            </template>
          </el-dialog>
        </template>
        <template v-else-if="STORAGE_TYPES.includes(type)">
          <el-radio-group v-model="type" class="mb-4">
            <el-radio-button v-for="tp in storageTypes" :key="tp" :value="tp">{{ $t(`config.settings.${tp}`) }}</el-radio-button>
          </el-radio-group>
          <el-row>
            <el-col :span="24">
              <el-form-item
                prop="type"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  {
                    validator: (rule: any, value: any, callback: any) => {
                      // FTP / 对象存储为 T2：选项只在 isShowTier(2) 下出现，社区版选中时在此拦下
                      if (value !== 0 && currentUser.commercialTier < 2) {
                        callback(lockedTip(2));
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
                    v-for="n in [0, 1, 10].filter((item) => item === 0 || isShowTier(2))"
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
                  <el-input v-model="values.hostname" maxlength="160" :disabled="currentUser.commercialTier < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="port" :rules="{ type: 'number', min: 0, max: 65535, message: () => $t('v.range', { min: 0, max: 65535 }) }">
                  <template #label><label-tip message="config.storage.port" help /></template>
                  <el-input v-model.number="values.port" :disabled="currentUser.commercialTier < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="username" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.username" /></template>
                  <el-input v-model="values.username" maxlength="50" :disabled="currentUser.commercialTier < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="password">
                  <template #label><label-tip message="config.storage.password" /></template>
                  <el-input v-model="values.password" type="password" show-password maxlength="160" :disabled="currentUser.commercialTier < 1" :placeholder="$t('config.secretKeepBlank')"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="encoding">
                  <template #label><label-tip message="config.storage.encoding" help /></template>
                  <el-input v-model="values.encoding" maxlength="50" :disabled="currentUser.commercialTier < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="passive">
                  <template #label><label-tip message="config.storage.passive" help /></template>
                  <el-switch v-model="values.passive" :disabled="currentUser.commercialTier < 1" />
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
                  <el-input v-model="values.endpoint" maxlength="160" :disabled="currentUser.commercialTier < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="region" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.region" /></template>
                  <el-input v-model="values.region" maxlength="160" :disabled="currentUser.commercialTier < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="bucket" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="config.storage.bucket" /></template>
                  <el-input v-model="values.bucket" maxlength="160" :disabled="currentUser.commercialTier < 1"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item prop="accessKey">
                  <template #label><label-tip message="config.storage.accessKey" /></template>
                  <el-input v-model="values.accessKey" type="password" show-password maxlength="160" :disabled="currentUser.commercialTier < 1" :placeholder="$t('config.secretKeepBlank')"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item prop="secretKey">
                  <template #label><label-tip message="config.storage.secretKey" /></template>
                  <el-input v-model="values.secretKey" type="password" show-password maxlength="160" :disabled="currentUser.commercialTier < 1" :placeholder="$t('config.secretKeepBlank')"></el-input>
                </el-form-item>
              </el-col>
            </template>
          </el-row>
        </template>
        <template v-else-if="type === 'grey'">
          <el-row>
            <el-col :span="24">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="config.grey.enabled" /></template>
                <el-switch v-model="values.enabled" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="greyDates">
                <template #label><label-tip message="config.grey.greyDates" help /></template>
                <el-input v-model="values.greyDates" maxlength="1000"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'customs'">
          <!-- 全局设置模型没有自定义字段：指路到模型管理（全局设置模型页签），不留一个孤零零的保存按钮 -->
          <el-empty v-if="model && fields.length === 0" :description="$t('config.customs.empty')">
            <el-button v-if="hasPermission('model:page')" type="primary" @click="() => router.push({ name: 'ModelList', query: { type: 'global' } })">
              {{ $t('menu.config.model') }}
            </el-button>
          </el-empty>
          <el-row v-else>
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
            <!-- 控制台默认外观（console-shell D7）：新用户及未在“外观设置”自选的用户所见，登录页也按此着色；
                 随基础设置整行保存，保存后 sysConfig.initConfig() 重拉 /env/config，跟随默认的用户即时换色 -->
            <template v-if="values.console">
              <el-col :span="24">
                <el-divider content-position="left">{{ $t('config.console') }}</el-divider>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="console.sidebarTheme">
                  <template #label><label-tip message="config.console.sidebarTheme" help /></template>
                  <el-radio-group v-model="values.console.sidebarTheme">
                    <el-radio value="light">{{ $t('shell.appearance.sidebar.light') }}</el-radio>
                    <el-radio value="dark">{{ $t('shell.appearance.sidebar.dark') }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item prop="console.themeColor">
                  <template #label><label-tip message="config.console.themeColor" help /></template>
                  <theme-color-swatches v-model="values.console.themeColor" :label="$t('config.console.themeColor')" />
                </el-form-item>
              </el-col>
            </template>
          </el-row>
        </template>
        <div
          v-if="
            (type !== 'security' || currentUser.commercialTier >= 1) &&
            (!['ai', 'oauth'].includes(type) || currentUser.commercialTier >= 2) &&
            (type !== 'customs' || fields.length > 0)
          "
          v-loading="buttonLoading"
        >
          <el-button :disabled="perm(`config:${type}:update`)" type="primary" native-type="submit" @click.prevent="() => handleSubmit()">
            {{ $t('save') }}
          </el-button>
          <el-button v-if="type === 'sms' && values.provider !== 0" :disabled="perm('config:sms:update') || !values.testMobile?.trim()" @click="() => handleSendTestSms()">
            {{ $t('config.sms.op.testSend') }}
          </el-button>
          <el-button v-if="type === 'email'" :disabled="perm('config:email:update') || !values.testTo?.trim()" @click="() => handleSendTestEmail()">
            {{ $t('config.email.op.testSend') }}
          </el-button>
          <el-button v-if="type === 'security'" :disabled="perm('config:security:update')" @click="() => handleApplyL3()">
            {{ $t('config.security.applyL3') }}
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
