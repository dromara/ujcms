<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { WarningFilled } from '@element-plus/icons-vue';
import { toTree } from '@/utils/tree';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import {
  querySiteSettings,
  updateSiteBaseSettings,
  updateSiteWatermarkSettings,
  updateSiteEditorSettings,
  updateSiteMessageBoardSettings,
  updateSiteCommentSettings,
  updateSiteContributeSettings,
  updateSiteDisclosureSettings,
  updateSiteMailboxSettings,
  updateSiteInterviewSettings,
  updateSiteAccessibilitySettings,
  updateSitePermalinkSettings,
  updateSiteHtmlSettings,
  updateSiteCensusSettings,
  updateSiteGeoSettings,
  previewRobotsTxt,
  querySearchEnginePushStatus,
  testSearchEnginePush,
  updateSiteCustomsSettings,
  queryCurrentSiteThemeList,
  queryModelList,
  queryDictListByAlias,
} from '@/api/config';
import { queryChannelList, htmlAll } from '@/api/content';
import { queryOrgList } from '@/api/user';
import { updateSiteAiSettings, queryAiReadiness } from '@/api/ai';
import { perm, hasPermission, currentUser } from '@/stores/useCurrentUser';
import { SITE_SETTINGS_TABS, lockedSettingsTier, visibleSettingsTypes } from './settingsNav';
import LabelTip from '@/components/LabelTip.vue';
import { ImageUpload } from '@/components/Upload';
import FieldItem from '@/views/config/components/FieldItem.vue';
import { enumParam, useRouteState } from '@/composables/useRouteState';
import { useSettingsFieldFocus } from './settingsFocus';

defineOptions({
  name: 'SiteSettings',
});
defineEmits({ 'update:modelValue': null, finished: null });

const { t } = useI18n();
// 一键排版的下拉选项：显示名随控制台语言，值为写入 HTML 的实际 font-family / font-size
const fontFamilyOptions = computed(() => [
  { value: '', label: t('site.editor.typesetting.fontFamily.default') },
  { value: 'SimSun', label: t('site.editor.typesetting.fontFamily.simsun') },
  { value: 'Microsoft YaHei', label: t('site.editor.typesetting.fontFamily.yahei') },
  { value: 'SimKai,KaiTi', label: t('site.editor.typesetting.fontFamily.kaiti') },
  { value: 'SimHei', label: t('site.editor.typesetting.fontFamily.simhei') },
  { value: 'SimLi,LiSu', label: t('site.editor.typesetting.fontFamily.lisu') },
  { value: 'andale mono,times', label: 'Andale Mono' },
  { value: 'arial,helvetica,sans-serif', label: 'Arial' },
  { value: 'arial black,avant garde', label: 'Arial Black' },
  { value: 'comic sans ms,sans-serif', label: 'Comic Sans MS' },
  { value: 'helvetica', label: 'Helvetica' },
  { value: 'impact,chicago', label: 'Impact' },
  { value: 'times new roman,times', label: 'Times New Roman' },
]);
const fontSizeOptions = computed(() => [
  { value: '', label: t('site.editor.typesetting.fontSize.default') },
  ...[12, 14, 16, 18, 20, 22, 24, 28].map((px) => ({ value: `${px}px`, label: t(`site.editor.typesetting.fontSize.${px}`) })),
]);
const emptyLineOptions = computed(() => ['remain', 'one', 'remove'].map((value) => ({ value, label: t(`site.editor.typesetting.emptyLine.${value}`) })));
const router = useRouter();
const currentSiteStore = useCurrentSiteStore();
const form = ref<any>();
const site = ref<any>();
const values = ref<any>({});
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
const orgList = ref<any[]>([]);
const themeList = ref<string[]>([]);
const modelList = ref<any[]>([]);
const modelId = ref<string>();
const model = computed(() => modelList.value.find((item) => item.id === modelId.value));
const fields = computed(() => JSON.parse(model.value?.customs || '[]'));
const channelList = ref<any[]>([]);

// AI爬虫目录（GEO拒绝名单勾选项，docs/spec/ai/geo.md §6）。按用途分组：训练爬虫是屏蔽的常见对象，AI搜索/引用爬虫是 GEO 要放行的，
// 用户实时代取多数不遵守 robots.txt。仅是自由文本列表的UI便利，新爬虫无需改动后端；不在目录里的历史项另列一组便于取消
const aiCrawlerGroups = [
  { key: 'training', agents: ['GPTBot', 'ClaudeBot', 'CCBot', 'Google-Extended', 'Applebot-Extended', 'meta-externalagent', 'Bytespider', 'Amazonbot'] },
  { key: 'search', agents: ['OAI-SearchBot', 'Claude-SearchBot', 'PerplexityBot'] },
  { key: 'user', agents: ['ChatGPT-User', 'Claude-User', 'Perplexity-User', 'meta-externalfetcher'] },
];
const knownAiAgents = aiCrawlerGroups.flatMap((group) => group.agents);
const extraAiAgents = computed<string[]>(() => (values.value?.disallowedAiAgents ?? []).filter((agent: string) => !knownAiAgents.includes(agent)));
// 自定义内容非空时后端原样输出，勾选项不生效
const robotsCustomActive = computed<boolean>(() => !!values.value?.robotsCustom?.trim());
// IndexNow 密钥：32 位十六进制（协议要求 8-128 位字母数字连字符）。密钥文件由后端 /indexnow-{key}.txt 自动提供
const generateIndexNowKey = () => {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  values.value.indexNowKey = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
};
const indexNowKeyLocation = computed(
  () => `${site.value?.dynamicFullUrl ?? site.value?.fullUrl ?? ''}/indexnow-${values.value?.indexNowKey ?? ''}.txt`,
);
// 保存与预览共用的 GEO 提交值：空数组代表未指定栏目，回落为 null（默认取前两级导航栏目）；空串回落为 null（后端 @Pattern 不接受空串）
const geoPayload = () => ({
  ...values.value,
  llmsTxtChannelIds: values.value.llmsTxtChannelIds?.length > 0 ? values.value.llmsTxtChannelIds : null,
  baiduPushUrl: values.value.baiduPushUrl?.trim() || null,
  indexNowKey: values.value.indexNowKey?.trim() || null,
});
// 搜索引擎推送状态：后端进程内存中每引擎最近一次推送结果；推送测试按已保存的设置即时提交站点首页
const pushStatus = ref<any>({});
const pushTesting = ref<string>('');
const fetchPushStatus = async () => {
  try {
    pushStatus.value = await querySearchEnginePushStatus();
  } catch {
    pushStatus.value = {};
  }
};
const pushResultText = (result: any): string => {
  if (!result) return t('site.geo.push.none');
  const outcome = result.ok
    ? t('site.geo.push.ok', { count: result.submitted })
    : t('site.geo.push.failed', { status: result.status, message: result.message ?? '' });
  return t('site.geo.push.last', { time: dayjs(result.time).format('YYYY-MM-DD HH:mm:ss'), outcome });
};
const handlePushTest = async (engine: string) => {
  pushTesting.value = engine;
  try {
    const result = await testSearchEnginePush(engine);
    pushStatus.value = { ...pushStatus.value, [engine]: result };
    if (result.ok) {
      ElMessage.success(t('site.geo.push.testOk'));
    } else {
      ElMessage.error(t('site.geo.push.failed', { status: result.status, message: result.message ?? '' }));
    }
  } finally {
    pushTesting.value = '';
  }
};

// 页签描述符与命令面板共用（settingsNav.ts，console-shell S5）：顺序即左侧导航顺序；data.ts 权限树的对应节点与此同序
const types: string[] = visibleSettingsTypes(SITE_SETTINGS_TABS);
// 社区版锁定的页签：导航项带商业版标识
const lockedTiers = Object.fromEntries(types.map((tp) => [tp, lockedSettingsTier(SITE_SETTINGS_TABS, tp)]));
// 当前设置页存于 query.type（docs/spec/console-view-state）：刷新或重访页签时停留在原设置页，也可深链
// （全局设置“AI服务商”页的“前往站点设置”直达“AI功能”页签）；非法值、无权限的设置页回落到首个页签
const { type } = useRouteState({ type: enumParam(types[0] ?? '', types) });
// 命令面板选中某个设置项后：打开本视图对应页签、数据就绪后滚动到该项（settingsFocus.ts）
useSettingsFieldFocus('SiteSettings', type, loading, form);

// 全局 AI 就绪状态（全局开关、各功能服务商是否可解析）。站点管理员多半没有 config:ai:show、看不到全局设置，
// 本页据此说明哪些开关不生效；读取失败不阻断页面，只是不显示提示
const aiReadiness = ref<any>();
const fetchAiReadiness = async () => {
  try {
    aiReadiness.value = await queryAiReadiness();
  } catch {
    aiReadiness.value = undefined;
  }
};
// 功能 → 依赖的全局服务商，与后端各控制器的 AiProviders.require 调用一致：
// 智能SEO/内容筛查用默认服务商，写作助手/回复拟稿用写作服务商，AI审核用审核服务商，AI预翻译用翻译服务商默认路径
const READINESS_KEY: Record<string, string> = {
  seoAssist: 'defaultProvider',
  screening: 'defaultProvider',
  writingAssist: 'writingProvider',
  replyDraft: 'writingProvider',
  moderationMode: 'moderationProvider',
  translate: 'translationProvider',
};
// 开关已打开但全局服务商不可解析时逐项提示“不生效”；全局未开启由顶部横幅统一说明，不再逐项提示
const providerMissing = (feature: string): boolean => {
  const ready = aiReadiness.value;
  if (!ready?.enabled) return false;
  const on = feature === 'moderationMode' ? values.value.moderationMode !== 0 : Boolean(values.value[feature]);
  const key = READINESS_KEY[feature];
  return on && key != null && !ready[key];
};

// 固定链接：文章链接形态。value 为后端枚举值，key 对应本地化文案，shape 为语言无关的地址样式（含花括号，不入 i18n 文件）
const permalinkOptions = [
  { value: 'id', key: 'id', shape: '/article/{id}' },
  { value: 'id-slug', key: 'idSlug', shape: '/article/{id}-{slug}' },
  { value: 'slug', key: 'slug', shape: '/article/{slug}' },
];
// 静态化：栏目列表静态化页数。末项为 Integer.MAX_VALUE，代表全部分页
const htmlListPagesOptions = computed(() => [
  ...[1, 3, 10, 100, 1000].map((n) => ({ label: String(n), value: n })),
  { label: t('all'), value: 2147483647 },
]);

const switchType = () => {
  if (type.value === 'watermark') {
    values.value = site.value.watermark;
  } else if (type.value === 'permalink') {
    values.value = site.value.permalink ?? { article: 'id-slug', pinyin: false };
  } else if (type.value === 'html') {
    values.value = site.value.html;
  } else if (type.value === 'editor') {
    values.value = site.value.editor;
    if (values.value.typesetting == null) {
      values.value.typesetting = {
        fontFamily: '',
        fontSize: '',
        lineHeight: '',
        textIndent: false,
        imageCenterAlign: true,
        tableFullWidth: true,
        emptyLine: 'one',
        halfWidthCharConversion: true,
      };
    }
  } else if (type.value === 'messageBoard') {
    values.value = site.value.messageBoard;
  } else if (type.value === 'comment') {
    values.value = site.value.comment;
  } else if (type.value === 'contribute') {
    values.value = site.value.contribute;
  } else if (type.value === 'disclosure') {
    values.value = site.value.disclosure;
  } else if (type.value === 'mailbox') {
    values.value = site.value.mailbox;
  } else if (type.value === 'interview') {
    values.value = site.value.interview;
  } else if (type.value === 'census') {
    values.value = site.value.census;
  } else if (type.value === 'accessibility') {
    values.value = site.value.accessibility;
  } else if (type.value === 'ai') {
    values.value = site.value.ai;
    // Long 字段序列化为字符串，el-input-number 需要数字
    values.value.dailyTokenQuota = Number(values.value.dailyTokenQuota ?? 0);
    fetchAiReadiness();
  } else if (type.value === 'geo') {
    values.value = site.value.geo;
    // null 代表默认取前两级导航栏目，树选择器需要数组
    values.value.llmsTxtChannelIds = values.value.llmsTxtChannelIds ?? [];
    values.value.disallowedAiAgents = values.value.disallowedAiAgents ?? [];
    fetchChannelList();
    fetchPushStatus();
  } else if (type.value === 'customs') {
    values.value = site.value.customs;
  } else {
    values.value = site.value;
  }
};

watch(type, () => switchType());

// const tabClick = (paneName?: string | number) => {};

const fetchThemeList = async () => {
  themeList.value = await queryCurrentSiteThemeList();
};
const fetchChannelList = async () => {
  // 网站普查与 GEO 设置需要栏目列表
  if (channelList.value.length > 0) {
    return;
  }
  try {
    channelList.value = toTree(await queryChannelList());
  } catch {
    // 无 channel:list 权限时栏目选择器留空，不阻断 GEO/普查设置页
  }
};
// robots.txt 效果预览：由后端按表单当前值生成，与前台实际响应同一实现（控制台不再复制生成规则，子目录站点的 Sitemap 行也一致）
const robotsPreview = ref<string>('');
let robotsPreviewTimer: ReturnType<typeof setTimeout> | undefined;
const fetchRobotsPreview = async () => {
  if (type.value !== 'geo' || !values.value?.robotsEnabled) {
    robotsPreview.value = '';
    return;
  }
  try {
    robotsPreview.value = (await previewRobotsTxt(geoPayload())).body ?? '';
  } catch {
    robotsPreview.value = '';
  }
};
watch(
  () => (type.value === 'geo' ? [values.value?.robotsEnabled, values.value?.robotsCustom, (values.value?.disallowedAiAgents ?? []).join(',')] : null),
  () => {
    clearTimeout(robotsPreviewTimer);
    robotsPreviewTimer = setTimeout(fetchRobotsPreview, 300);
  },
  { immediate: true },
);
const fetchOrgList = async () => {
  orgList.value = toTree(await queryOrgList());
};
const fetchModelList = async () => {
  modelList.value = await queryModelList({ type: 'site' });
};
const fetchSiteSetting = async () => {
  site.value = await querySiteSettings();
  modelId.value = site.value.modelId;
};
// 站点语言下拉取自字典 sys_site_lang（docs/spec/dictionary P4）；缺 dict:list 或请求失败时降级为自由输入，
// 且不参与下方 Promise.all——失败不得阻断页面初始化
const langOptions = ref<Array<{ value: string; label: string }>>([]);
const fetchLangOptions = async () => {
  if (!hasPermission('dict:list')) return;
  try {
    const list = await queryDictListByAlias('sys_site_lang', undefined, true);
    langOptions.value = list.map((item: any) => ({ value: item.value, label: `${item.name} (${item.value})` }));
  } catch {
    langOptions.value = [];
  }
};
onMounted(async () => {
  fetchLangOptions();
  loading.value = true;
  try {
    // 仅网站普查设置需要在加载时获取栏目列表，GEO 设置切换标签时按需获取
    await Promise.all([fetchThemeList(), fetchOrgList(), fetchModelList(), types.includes('census') ? fetchChannelList() : Promise.resolve(), fetchSiteSetting()]);
    switchType();
  } finally {
    loading.value = false;
  }
});
const load = async () => {
  loading.value = true;
  try {
    await fetchSiteSetting();
  } finally {
    loading.value = false;
  }
};
const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    let htmlGenerating = false;
    try {
      if (type.value === 'watermark') {
        await updateSiteWatermarkSettings(values.value);
      } else if (type.value === 'permalink') {
        await updateSitePermalinkSettings(values.value);
      } else if (type.value === 'html') {
        await updateSiteHtmlSettings(values.value);
        // 静态化设置变更后须重新生成全部静态页（与原“生成管理”页内保存的行为一致）；进度见生成管理的任务列表。
        // 权限树把 generator:html 与本设置打包授予，手工改过的角色缺该权限时只保存、不生成
        if (hasPermission('generator:html')) {
          await htmlAll();
          htmlGenerating = true;
        }
      } else if (type.value === 'editor') {
        await updateSiteEditorSettings(values.value);
      } else if (type.value === 'messageBoard') {
        await updateSiteMessageBoardSettings(values.value);
      } else if (type.value === 'comment') {
        await updateSiteCommentSettings(values.value);
      } else if (type.value === 'contribute') {
        await updateSiteContributeSettings(values.value);
      } else if (type.value === 'disclosure') {
        await updateSiteDisclosureSettings(values.value);
      } else if (type.value === 'mailbox') {
        await updateSiteMailboxSettings(values.value);
      } else if (type.value === 'interview') {
        await updateSiteInterviewSettings(values.value);
      } else if (type.value === 'census') {
        await updateSiteCensusSettings(values.value);
      } else if (type.value === 'accessibility') {
        await updateSiteAccessibilitySettings(values.value);
      } else if (type.value === 'ai') {
        await updateSiteAiSettings(values.value);
      } else if (type.value === 'geo') {
        await updateSiteGeoSettings(geoPayload());
      } else if (type.value === 'customs') {
        await updateSiteCustomsSettings(values.value);
      } else {
        await updateSiteBaseSettings(values.value);
      }
      currentSiteStore.currentSite = await querySiteSettings();
      ElMessage.success(htmlGenerating ? t('site.html.generating', { menu: t('menu.system.generator') }) : t('success'));
    } finally {
      buttonLoading.value = false;
    }
    load();
  });
};
</script>

<template>
  <el-container>
    <el-aside width="180px" class="pr-3">
      <el-tabs v-model="type" tab-position="left" stretch class="bg-white sidebar-tabs">
        <el-tab-pane v-for="tp in types" :key="tp" :name="tp">
          <template #label>
            {{ $t(`site.settings.${tp}`) }}<commercial-badge v-if="lockedTiers[tp]" :tier="lockedTiers[tp]" class="ml-1" />
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-aside>
    <el-main class="p-3 app-block">
      <el-form ref="form" v-loading="loading" :model="values" label-width="auto">
        <template v-if="type === 'watermark'">
          <el-row>
            <el-col :span="24">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.watermark.enabled" help /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="overlay" :label="$t('site.watermark.overlay')" :rules="values.enabled ? { required: true, message: () => $t('v.required') } : {}">
                <image-upload v-model="values.overlay"></image-upload>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="position" :label="$t('site.watermark.position')" :rules="{ required: true, message: () => $t('v.required') }">
                <el-radio-group v-model="values.position">
                  <div class="watermark-position">
                    <el-radio v-for="n in 9" :key="n" :value="n" :title="$t(`site.watermark.position.${n}`)"><span></span></el-radio>
                  </div>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="dissolve" :label="$t('site.watermark.dissolve')" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.watermark.dissolve" help /></template>
                <el-slider v-model="values.dissolve" :min="0" :max="100" show-input></el-slider>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="minWidth" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.watermark.minWidth" help /></template>
                <el-input v-model.number="values.minWidth" :min="1" :max="65535"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="minHeight" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.watermark.minHeight" help /></template>
                <el-input v-model.number="values.minHeight" :min="1" :max="65535"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'permalink'">
          <el-row>
            <el-col :span="24">
              <el-form-item prop="article" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.permalink.article" /></template>
                <el-radio-group v-model="values.article" class="permalink-options">
                  <el-radio v-for="opt in permalinkOptions" :key="opt.value" :value="opt.value">
                    <span>{{ $t(`site.permalink.article.${opt.key}`) }}</span>
                    <code class="permalink-shape">{{ opt.shape }}</code>
                    <span class="text-xs text-gray-secondary">{{ $t(`site.permalink.article.${opt.key}.description`) }}</span>
                  </el-radio>
                </el-radio-group>
                <div class="w-full text-xs text-gray-secondary leading-5">
                  <div>{{ $t('site.permalink.reindexTip') }}</div>
                  <div>{{ $t('site.permalink.htmlTip') }}</div>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="pinyin" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.permalink.pinyin" help /></template>
                <el-switch v-model="values.pinyin"></el-switch>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'html'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.html.enabled" help /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="auto" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.html.auto" help /></template>
                <el-switch v-model="values.auto"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="channel" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.html.channel" help /></template>
                <el-input v-model="values.channel" maxlength="100"><template #append>.html</template></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="article" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.html.article" help /></template>
                <el-input v-model="values.article" maxlength="100"><template #append>.html</template></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="listPages" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.html.listPages" help /></template>
                <el-select v-model="values.listPages" :placeholder="$t('select')">
                  <el-option v-for="item in htmlListPagesOptions" :key="item.value" :label="item.label" :value="item.value"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'editor' && currentUser.commercialTier < 2">
          <commercial-notice :tier="2" />
        </template>
        <template v-else-if="type === 'editor' && currentUser.commercialTier >= 2">
          <el-divider>{{ $t('site.editor.typesetting') }}</el-divider>
          <!-- 刷新或深链直达本页签时，首次渲染早于站点设置加载（values 仍为空对象），须待 typesetting 就绪再渲染 -->
          <el-row v-if="values.typesetting">
            <el-col :span="12">
              <el-form-item prop="typesetting.fontFamily">
                <template #label><label-tip message="site.editor.typesetting.fontFamily" /></template>
                <el-select v-model="values.typesetting.fontFamily">
                  <el-option
                    v-for="item in fontFamilyOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.fontSize">
                <template #label><label-tip message="site.editor.typesetting.fontSize" /></template>
                <el-select v-model="values.typesetting.fontSize">
                  <el-option
                    v-for="item in fontSizeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.lineHeight">
                <template #label><label-tip message="site.editor.typesetting.lineHeight" /></template>
                <el-select v-model="values.typesetting.lineHeight">
                  <el-option
                    v-for="item in [
                      { value: '', label: $t('site.editor.typesetting.fontSize.default') },
                      { value: '1.0', label: '1.0' },
                      { value: '1.1', label: '1.1' },
                      { value: '1.2', label: '1.2' },
                      { value: '1.3', label: '1.3' },
                      { value: '1.4', label: '1.4' },
                      { value: '1.5', label: '1.5' },
                      { value: '2.0', label: '2.0' },
                    ]"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.emptyLine">
                <template #label><label-tip message="site.editor.typesetting.emptyLine" /></template>
                <el-select v-model="values.typesetting.emptyLine">
                  <el-option
                    v-for="item in emptyLineOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.textIndent">
                <template #label><label-tip message="site.editor.typesetting.textIndent" /></template>
                <el-switch v-model="values.typesetting.textIndent" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.imageCenterAlign">
                <template #label><label-tip message="site.editor.typesetting.imageCenterAlign" /></template>
                <el-switch v-model="values.typesetting.imageCenterAlign" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.tableFullWidth">
                <template #label><label-tip message="site.editor.typesetting.tableFullWidth" /></template>
                <el-switch v-model="values.typesetting.tableFullWidth" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.halfWidthCharConversion">
                <template #label><label-tip message="site.editor.typesetting.halfWidthCharConversion" /></template>
                <el-switch v-model="values.typesetting.halfWidthCharConversion" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'messageBoard'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.messageBoard.enabled" /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="loginRequired" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.messageBoard.loginRequired" /></template>
                <el-switch v-model="values.loginRequired"></el-switch>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'comment'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.comment.enabled" /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="loginRequired" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.comment.loginRequired" /></template>
                <el-switch v-model="values.loginRequired"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="auditRequired" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.comment.auditRequired" /></template>
                <el-switch v-model="values.auditRequired"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="imageEnabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.comment.imageEnabled" help /></template>
                <el-switch v-model="values.imageEnabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="imageMaxCount" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.comment.imageMaxCount" /></template>
                <el-input-number v-model="values.imageMaxCount" :min="1" :max="9"></el-input-number>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'contribute'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.contribute.enabled" help /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="dailyLimit" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.contribute.dailyLimit" help /></template>
                <el-input-number v-model="values.dailyLimit" :min="0" :max="1000"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="imageEnabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.contribute.imageEnabled" help /></template>
                <el-switch v-model="values.imageEnabled"></el-switch>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="['disclosure', 'mailbox', 'interview', 'census', 'accessibility'].includes(type) && currentUser.commercialTier < 4">
          <commercial-notice :tier="4" />
        </template>
        <template v-else-if="type === 'disclosure'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.disclosure.enabled" /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="loginRequired" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.disclosure.loginRequired" /></template>
                <el-switch v-model="values.loginRequired"></el-switch>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'mailbox'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.mailbox.enabled" /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="loginRequired" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.mailbox.loginRequired" /></template>
                <el-switch v-model="values.loginRequired"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="handlingDays" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.mailbox.handlingDays" help /></template>
                <el-input-number v-model="values.handlingDays" :min="1" :max="60"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="extendedDays" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.mailbox.extendedDays" help /></template>
                <el-input-number v-model="values.extendedDays" :min="1" :max="120"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="smsEnabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.mailbox.smsEnabled" help /></template>
                <el-switch v-model="values.smsEnabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="smsTemplateCode" :rules="values.smsEnabled ? { required: true, message: () => $t('v.required') } : {}">
                <template #label><label-tip message="site.mailbox.smsTemplateCode" help /></template>
                <el-input v-model="values.smsTemplateCode" maxlength="32"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="smsCodeName">
                <template #label><label-tip message="site.mailbox.smsCodeName" help /></template>
                <el-input v-model="values.smsCodeName" maxlength="20"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'interview'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.interview.enabled" /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="loginRequired" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.interview.loginRequired" /></template>
                <el-switch v-model="values.loginRequired"></el-switch>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'accessibility'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.accessibility.enabled" /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="entryButton" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.accessibility.entryButton" help /></template>
                <el-switch v-model="values.entryButton"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="defaultOpen" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.accessibility.defaultOpen" help /></template>
                <el-switch v-model="values.defaultOpen"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="helpUrl" :rules="{ max: 255, message: () => $t('v.max', { max: 255 }) }">
                <template #label><label-tip message="site.accessibility.helpUrl" help /></template>
                <el-input v-model="values.helpUrl" maxlength="255"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="elderUrl" :rules="{ max: 255, message: () => $t('v.max', { max: 255 }) }">
                <template #label><label-tip message="site.accessibility.elderUrl" help /></template>
                <el-input v-model="values.elderUrl" maxlength="255"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'census'">
          <el-row>
            <el-col :span="24">
              <el-form-item prop="newsChannelIds">
                <template #label><label-tip message="site.census.newsChannelIds" help /></template>
                <el-tree-select
                  v-model="values.newsChannelIds"
                  :data="channelList"
                  node-key="id"
                  :props="{ label: 'name' }"
                  multiple
                  check-strictly
                  clearable
                  :render-after-expand="false"
                  class="w-full"
                />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="policyChannelIds">
                <template #label><label-tip message="site.census.policyChannelIds" help /></template>
                <el-tree-select
                  v-model="values.policyChannelIds"
                  :data="channelList"
                  node-key="id"
                  :props="{ label: 'name' }"
                  multiple
                  check-strictly
                  clearable
                  :render-after-expand="false"
                  class="w-full"
                />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="excludedChannelIds">
                <template #label><label-tip message="site.census.excludedChannelIds" help /></template>
                <el-tree-select
                  v-model="values.excludedChannelIds"
                  :data="channelList"
                  node-key="id"
                  :props="{ label: 'name' }"
                  multiple
                  check-strictly
                  clearable
                  :render-after-expand="false"
                  class="w-full"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="staleMonths" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.census.staleMonths" help /></template>
                <el-input-number v-model="values.staleMonths" :min="1" :max="120"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="linkCheckEnabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.census.linkCheckEnabled" help /></template>
                <el-switch v-model="values.linkCheckEnabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="linkTimeoutMs" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.census.linkTimeoutMs" /></template>
                <el-input-number v-model="values.linkTimeoutMs" :min="1000" :max="30000" :step="1000"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="maxUrls" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.census.maxUrls" help /></template>
                <el-input-number v-model="values.maxUrls" :min="100" :max="100000" :step="100"></el-input-number>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'ai' && currentUser.commercialTier < 2">
          <commercial-notice :tier="2" />
        </template>
        <template v-else-if="type === 'ai'">
          <el-row>
            <!-- AI功能 页签（全部 AI 功能自 2026-09-04 起均为 T2）：编辑助手开关、额度与 AI 预翻译开关，下方为内容审核。
                 服务商、凭证与全局额度在“全局设置 → AI服务商”；全局未就绪时横幅说明原因，开关旁逐项标出不生效者 -->
            <el-col v-if="aiReadiness && (!aiReadiness.enabled || !aiReadiness.defaultProvider)" :span="24">
              <el-alert type="warning" :closable="false" show-icon class="mb-4">
                <template #title>
                  {{ $t(aiReadiness.enabled ? 'site.ai.readiness.noDefaultProvider' : 'site.ai.readiness.disabled') }}
                  <el-button
                    v-if="hasPermission('config:ai:show')"
                    type="primary"
                    link
                    size="small"
                    @click="() => router.push({ name: 'GlobalSettings', query: { type: 'ai' } })"
                  >
                    {{ $t('site.ai.op.goGlobalSettings') }}
                  </el-button>
                </template>
              </el-alert>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="seoAssist" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.ai.seoAssist" help /></template>
                <el-switch v-model="values.seoAssist"></el-switch>
                <el-tooltip v-if="providerMissing('seoAssist')" :content="$t('site.ai.readiness.providerMissing')" placement="top">
                  <el-icon class="ml-2 text-warning"><WarningFilled /></el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="writingAssist" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.ai.writingAssist" help /></template>
                <el-switch v-model="values.writingAssist"></el-switch>
                <el-tooltip v-if="providerMissing('writingAssist')" :content="$t('site.ai.readiness.providerMissing')" placement="top">
                  <el-icon class="ml-2 text-warning"><WarningFilled /></el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="screening" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.ai.screening" help /></template>
                <el-switch v-model="values.screening"></el-switch>
                <el-tooltip v-if="providerMissing('screening')" :content="$t('site.ai.readiness.providerMissing')" placement="top">
                  <el-icon class="ml-2 text-warning"><WarningFilled /></el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="translate" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.ai.translate" help /></template>
                <el-switch v-model="values.translate"></el-switch>
                <el-tooltip v-if="providerMissing('translate')" :content="$t('site.ai.readiness.translationMissing')" placement="top">
                  <el-icon class="ml-2 text-warning"><WarningFilled /></el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="relatedAssist" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.ai.relatedAssist" help /></template>
                <el-switch v-model="values.relatedAssist"></el-switch>
                <el-tooltip v-if="providerMissing('relatedAssist')" :content="$t('site.ai.readiness.providerMissing')" placement="top">
                  <el-icon class="ml-2 text-warning"><WarningFilled /></el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="dailyTokenQuota" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.ai.dailyTokenQuota" help /></template>
                <el-input-number v-model="values.dailyTokenQuota" :min="0" :max="1000000000000" :step="10000"></el-input-number>
              </el-form-item>
            </el-col>
            <el-divider content-position="left">{{ $t('site.ai.moderation') }}</el-divider>
            <el-col :span="12">
              <el-form-item prop="moderationMode" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.ai.moderationMode" help /></template>
                <el-radio-group v-model="values.moderationMode">
                  <el-radio-button v-for="n in [0, 1, 2]" :key="n" :value="n">{{ $t(`site.ai.moderationMode.${n}`) }}</el-radio-button>
                </el-radio-group>
                <el-tooltip v-if="providerMissing('moderationMode')" :content="$t('site.ai.readiness.providerMissing')" placement="top">
                  <el-icon class="ml-2 text-warning"><WarningFilled /></el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="replyDraft" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.ai.replyDraft" help /></template>
                <el-switch v-model="values.replyDraft"></el-switch>
                <el-tooltip v-if="providerMissing('replyDraft')" :content="$t('site.ai.readiness.providerMissing')" placement="top">
                  <el-icon class="ml-2 text-warning"><WarningFilled /></el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
            <template v-if="values.moderationMode === 2">
              <el-col :span="24">
                <el-form-item prop="autoApproveBelow">
                  <template #label><label-tip message="site.ai.autoApproveBelow" help /></template>
                  <el-slider v-model="values.autoApproveBelow" :min="0" :max="100" show-input></el-slider>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item
                  prop="autoBlockAbove"
                  :rules="{
                    validator: (rule: any, value: any, callback: any) => {
                      if (values.autoApproveBelow >= value) {
                        callback($t('site.ai.error.threshold'));
                        return;
                      }
                      callback();
                    },
                  }"
                >
                  <template #label><label-tip message="site.ai.autoBlockAbove" help /></template>
                  <el-slider v-model="values.autoBlockAbove" :min="0" :max="100" show-input></el-slider>
                </el-form-item>
              </el-col>
            </template>
          </el-row>
        </template>
        <template v-else-if="type === 'geo'">
          <el-row>
            <el-divider content-position="left">{{ $t('site.geo.jsonLd') }}</el-divider>
            <el-col :span="24">
              <el-form-item prop="jsonLdEnabled">
                <template #label><label-tip message="site.geo.jsonLdEnabled" help /></template>
                <el-switch v-model="values.jsonLdEnabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-divider content-position="left">{{ $t('site.geo.llmsTxt') }}</el-divider>
            <el-col :span="12">
              <el-form-item prop="llmsTxtEnabled">
                <template #label><label-tip message="site.geo.llmsTxtEnabled" help /></template>
                <el-switch v-model="values.llmsTxtEnabled"></el-switch>
              </el-form-item>
            </el-col>
            <template v-if="values.llmsTxtEnabled">
              <el-col :span="12">
                <el-form-item prop="llmsTxtArticleCount" :rules="{ required: true, message: () => $t('v.required') }">
                  <template #label><label-tip message="site.geo.llmsTxtArticleCount" help /></template>
                  <el-input-number v-model="values.llmsTxtArticleCount" :min="0" :max="100"></el-input-number>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item prop="llmsTxtChannelIds">
                  <template #label><label-tip message="site.geo.llmsTxtChannelIds" help /></template>
                  <el-tree-select
                    v-model="values.llmsTxtChannelIds"
                    :data="channelList"
                    node-key="id"
                    :props="{ label: 'name' }"
                    multiple
                    show-checkbox
                    check-strictly
                    :render-after-expand="false"
                    class="w-full"
                  />
                </el-form-item>
              </el-col>
            </template>
            <el-divider content-position="left">{{ $t('site.geo.robots') }}</el-divider>
            <!-- 子目录站点：爬虫只读主机根的 robots.txt，本站的设置不会被读取，由根站点声明本站站点地图 -->
            <el-col v-if="site?.subDir" :span="24">
              <el-form-item>
                <el-alert :title="$t('site.geo.robots.subDirNotice')" type="info" :closable="false" show-icon />
              </el-form-item>
            </el-col>
            <template v-else>
              <el-col :span="24">
                <el-form-item prop="robotsEnabled">
                  <template #label><label-tip message="site.geo.robotsEnabled" help /></template>
                  <el-switch v-model="values.robotsEnabled"></el-switch>
                </el-form-item>
              </el-col>
              <template v-if="values.robotsEnabled">
                <el-col :span="24">
                  <el-form-item prop="disallowedAiAgents">
                    <template #label><label-tip message="site.geo.disallowedAiAgents" help /></template>
                    <div class="w-full">
                      <div v-if="robotsCustomActive" class="text-xs text-gray-500 leading-5">{{ $t('site.geo.disallowedAiAgents.overridden') }}</div>
                      <div v-for="group in aiCrawlerGroups" :key="group.key" class="mb-1">
                        <div class="text-xs text-gray-500 leading-5">
                          {{ $t(`site.geo.disallowedAiAgents.${group.key}`) }}{{ $t('punct.colon') }}{{ $t(`site.geo.disallowedAiAgents.${group.key}.tip`) }}
                        </div>
                        <el-checkbox-group v-model="values.disallowedAiAgents" :disabled="robotsCustomActive">
                          <el-checkbox v-for="agent in group.agents" :key="agent" :value="agent">{{ agent }}</el-checkbox>
                        </el-checkbox-group>
                      </div>
                      <div v-if="extraAiAgents.length > 0" class="mb-1">
                        <div class="text-xs text-gray-500 leading-5">
                          {{ $t('site.geo.disallowedAiAgents.other') }}{{ $t('punct.colon') }}{{ $t('site.geo.disallowedAiAgents.other.tip') }}
                        </div>
                        <el-checkbox-group v-model="values.disallowedAiAgents" :disabled="robotsCustomActive">
                          <el-checkbox v-for="agent in extraAiAgents" :key="agent" :value="agent">{{ agent }}</el-checkbox>
                        </el-checkbox-group>
                      </div>
                    </div>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item prop="robotsCustom">
                    <template #label><label-tip message="site.geo.robotsCustom" help /></template>
                    <el-input v-model="values.robotsCustom" type="textarea" :rows="6" maxlength="30000"></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item :label="$t('site.geo.preview')">
                    <pre class="robots-preview">{{ robotsPreview }}</pre>
                  </el-form-item>
                </el-col>
              </template>
            </template>
            <el-divider content-position="left">{{ $t('site.geo.searchPush') }}</el-divider>
            <el-col :span="24">
              <el-form-item prop="baiduPushEnabled">
                <template #label><label-tip message="site.geo.baiduPushEnabled" help /></template>
                <el-switch v-model="values.baiduPushEnabled"></el-switch>
              </el-form-item>
            </el-col>
            <template v-if="values.baiduPushEnabled">
              <el-col :span="24">
                <el-form-item
                  prop="baiduPushUrl"
                  :rules="[
                    { required: true, message: () => $t('v.required') },
                    { pattern: /^https?:\/\/data\.zz\.baidu\.com\/urls\?.+$/, message: () => $t('site.geo.baiduPushUrl.invalid') },
                  ]"
                >
                  <template #label><label-tip message="site.geo.baiduPushUrl" help /></template>
                  <el-input v-model="values.baiduPushUrl" maxlength="300" placeholder="http://data.zz.baidu.com/urls?site=https://www.example.com&token=…"></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item :label="$t('site.geo.push.status')">
                  <div class="w-full flex items-center gap-2">
                    <span class="text-xs text-gray-500">{{ pushResultText(pushStatus.baidu) }}</span>
                    <el-tooltip :content="$t('site.geo.push.test.tooltip')" placement="top">
                      <el-button size="small" :loading="pushTesting === 'baidu'" @click="handlePushTest('baidu')">{{ $t('site.geo.push.test') }}</el-button>
                    </el-tooltip>
                  </div>
                </el-form-item>
              </el-col>
            </template>
            <el-col :span="24">
              <el-form-item prop="indexNowEnabled">
                <template #label><label-tip message="site.geo.indexNowEnabled" help /></template>
                <el-switch v-model="values.indexNowEnabled"></el-switch>
              </el-form-item>
            </el-col>
            <template v-if="values.indexNowEnabled">
              <el-col :span="24">
                <el-form-item
                  prop="indexNowKey"
                  :rules="[
                    { required: true, message: () => $t('v.required') },
                    { pattern: /^[A-Za-z0-9-]{8,128}$/, message: () => $t('site.geo.indexNowKey.invalid') },
                  ]"
                >
                  <template #label><label-tip message="site.geo.indexNowKey" help /></template>
                  <el-input v-model="values.indexNowKey" maxlength="128">
                    <template #append><el-button @click="generateIndexNowKey">{{ $t('site.geo.indexNowKey.generate') }}</el-button></template>
                  </el-input>
                  <div v-if="values.indexNowKey" class="text-xs text-gray-500">{{ $t('site.geo.indexNowKey.location', { url: indexNowKeyLocation }) }}</div>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item :label="$t('site.geo.push.status')">
                  <div class="w-full flex items-center gap-2">
                    <span class="text-xs text-gray-500">{{ pushResultText(pushStatus.indexNow) }}</span>
                    <el-tooltip :content="$t('site.geo.push.test.tooltip')" placement="top">
                      <el-button size="small" :loading="pushTesting === 'indexNow'" @click="handlePushTest('indexNow')">{{ $t('site.geo.push.test') }}</el-button>
                    </el-tooltip>
                  </div>
                </el-form-item>
              </el-col>
            </template>
          </el-row>
        </template>
        <template v-else-if="type === 'customs'">
          <!-- 站点设置模型没有自定义字段：指路到模型管理（站点设置模型页签），不留一个孤零零的保存按钮 -->
          <el-empty v-if="!loading && fields.length === 0" :description="$t('config.customs.empty')">
            <el-button v-if="hasPermission('model:page')" type="primary" @click="() => router.push({ name: 'ModelList', query: { type: 'site' } })">
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
              <el-form-item prop="name" :label="$t('site.name')" :rules="{ required: true, message: () => $t('v.required') }">
                <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="protocol" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.protocol" help /></template>
                <el-select v-model="values.protocol" class="w-full">
                  <el-option v-for="item in ['http', 'https']" :key="item" :label="item" :value="item"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="domain"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  { pattern: /^[a-z0-9-.]*$/, message: () => $t('site.error.domainPattern') },
                  { pattern: /^(?!(uploads|themes|WEB-INF|cp)$).*/i, message: () => $t('site.error.excludesPattern') },
                ]"
              >
                <template #label><label-tip message="site.domain" help /></template>
                <el-input v-model="values.domain" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="subDir"
                :rules="[
                  { pattern: /^[\w-]*$/, message: () => $t('site.error.subDirPattern') },
                  { pattern: /^(?!(uploads|themes|WEB-INF|cp|api|frontend|m)$).*/i, message: () => $t('site.error.excludesPattern') },
                ]"
              >
                <template #label><label-tip message="site.subDir" help /></template>
                <el-input v-model="values.subDir" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="lang"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  { pattern: /^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$/, message: () => $t('site.error.langPattern') },
                ]"
              >
                <template #label><label-tip message="site.lang" help /></template>
                <el-select v-model="values.lang" filterable allow-create clearable class="w-full">
                  <el-option v-for="item in langOptions" :key="item.value" :label="item.label" :value="item.value"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="modelId" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.model" help /></template>
                <el-select v-model="values.modelId" class="w-full">
                  <el-option v-for="item in modelList" :key="item.id" :label="item.name" :value="item.id"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="theme" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.theme" help /></template>
                <el-select v-model="values.theme" class="w-full">
                  <el-option v-for="item in themeList" :key="item" :label="item" :value="item"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="mobileTheme" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.mobileTheme" help /></template>
                <el-select v-model="values.mobileTheme" class="w-full">
                  <el-option v-for="item in themeList" :key="item" :label="item" :value="item"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="logo">
                <template #label><label-tip message="site.logo" help /></template>
                <image-upload v-model="values.logo"></image-upload>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="seoTitle">
                <template #label><label-tip message="site.seoTitle" help /></template>
                <el-input v-model="values.seoTitle" maxlength="150"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="seoKeywords">
                <template #label><label-tip message="site.seoKeywords" help /></template>
                <el-input v-model="values.seoKeywords" maxlength="150"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="seoDescription">
                <template #label><label-tip message="site.seoDescription" help /></template>
                <el-input v-model="values.seoDescription" type="textarea" :rows="3" maxlength="1000"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="pageSize" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.pageSize" help /></template>
                <el-input-number v-model="values.pageSize" :min="1" :max="200"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="status">
                <template #label><label-tip message="site.status" help /></template>
                <el-radio-group v-model="values.status">
                  <el-radio v-for="n in [0, 1]" :key="n" :value="n">{{ $t(`site.status.${n}`) }}</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <div
          v-if="
            (type !== 'editor' || currentUser.commercialTier >= 1) &&
            (!['disclosure', 'mailbox', 'interview', 'census', 'accessibility'].includes(type) || currentUser.commercialTier >= 4) &&
            (type !== 'ai' || currentUser.commercialTier >= 2) &&
            (type !== 'customs' || fields.length > 0)
          "
        >
          <el-button :disabled="perm(`siteSettings:${type}:update`)" :loading="buttonLoading" type="primary" native-type="submit" @click.prevent="handleSubmit">
            {{ $t('save') }}
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
.permalink-options {
  @apply flex flex-col items-start;
  :deep(.el-radio) {
    @apply h-auto mr-0 py-1;
  }
}
.permalink-shape {
  @apply mx-2 px-1 rounded bg-gray-100 text-xs text-gray-regular;
}
.robots-preview {
  @apply w-full bg-gray-100 rounded border border-dashed p-3 my-0 text-xs leading-5 overflow-auto;
}
.watermark-position {
  @apply bg-gray-100 w-36 h-36 flex flex-wrap rounded border border-dashed;
  :deep(.el-radio__label) {
    padding-left: 0;
  }
  :deep(.el-radio) {
    margin-right: 0;
    @apply w-1/3 h-1/3 flex justify-center items-center;
  }
}
</style>
