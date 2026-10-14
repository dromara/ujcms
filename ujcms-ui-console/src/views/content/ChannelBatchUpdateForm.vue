<script setup lang="ts">
import { computed, reactive, ref, watch, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { batchUpdateChannel, queryChannelTemplates, queryArticleTemplates, type ChannelBatchField } from '@/api/content';
import { queryModelList } from '@/api/config';
import { queryProcessModelList } from '@/api/system';
import { getModelData, mergeModelFields, arr2obj } from '@/data';
import { currentUser } from '@/stores/useCurrentUser';
import { findTreeItem } from '@/utils/tree';
import LabelTip from '@/components/LabelTip.vue';
import TemplateSelect from './TemplateSelect.vue';

// 批量设置（docs/spec/channel-management/plan.md D14–D21）：把勾选的设置应用到列表中选中的栏目，可连同它们的全部下级。
// 只提交勾选的属性；模板选“主题默认”、审核流程选“无”即清空。全有或全无，由服务端在写入前整体校验
defineOptions({
  name: 'ChannelBatchUpdateForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  // 列表中选中的栏目
  channels: { type: Array as PropType<any[]>, required: true },
  // 本站栏目树（左侧导航已加载的那一份）：据它算出下级栏目
  channelTree: { type: Array as PropType<any[]>, required: true },
  channelPermissions: { type: Array as PropType<string[]>, required: true },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

/** 审核流程下拉里“无”的取值；提交时换成 null */
const NO_PROCESS = '';
/** 超出栏目权限的下级栏目，最多列出的个数 */
const NAMES_MAX = 10;
// 各属性在栏目模型“系统字段”中对应的字段：所有栏目模型都隐藏的设置，这里也不提供
const FIELDS: { field: ChannelBatchField; code: string; aside?: boolean }[] = [
  { field: 'articleModelId', code: 'articleModel' },
  { field: 'channelTemplate', code: 'channelTemplate' },
  { field: 'articleTemplate', code: 'articleTemplate' },
  { field: 'processKey', code: 'processKey', aside: true },
  { field: 'nav', code: 'nav' },
  { field: 'real', code: 'real' },
  { field: 'allowSearch', code: 'allowSearch' },
  { field: 'pageSize', code: 'pageSize', aside: true },
];
const LABELS: Record<ChannelBatchField, string> = {
  articleModelId: 'channel.articleModel',
  channelTemplate: 'channel.channelTemplate',
  articleTemplate: 'channel.articleTemplate',
  processKey: 'channel.processKey',
  nav: 'channel.nav',
  real: 'channel.real',
  allowSearch: 'channel.allowSearch',
  pageSize: 'channel.pageSize',
};

const { t } = useI18n();
const loading = ref<boolean>(false);
const error = ref<string>();
const includeChildren = ref<boolean>(false);
const ticked = reactive<Record<ChannelBatchField, boolean>>({
  articleModelId: false,
  channelTemplate: false,
  articleTemplate: false,
  processKey: false,
  nav: false,
  real: false,
  allowSearch: false,
  pageSize: false,
});
const values = reactive<{
  articleModelId?: string;
  channelTemplate: string | null;
  articleTemplate: string | null;
  processKey: string;
  nav: boolean;
  real: boolean;
  allowSearch: boolean;
  pageSize?: number;
}>({ channelTemplate: null, articleTemplate: null, processKey: NO_PROCESS, nav: true, real: true, allowSearch: true, pageSize: 20 });
const channelModelList = ref<any[]>([]);
const articleModelList = ref<any[]>([]);
const channelTemplates = ref<string[]>([]);
const articleTemplates = ref<string[]>([]);
const processModelList = ref<any[]>([]);

const fields = computed(() =>
  FIELDS.filter(({ code, aside }) => {
    // 模型列表未取到时不隐藏任何设置
    if (channelModelList.value.length <= 0) {
      return true;
    }
    const defaults = aside ? getModelData().channel.asides : getModelData().channel.mains;
    return channelModelList.value.some((model) => arr2obj(mergeModelFields(defaults, aside ? model.asides : model.mains, 'channel'))[code]?.show);
  }).map(({ field }) => field),
);
const shown = (field: ChannelBatchField) => fields.value.includes(field);
const tickedFields = computed(() => fields.value.filter((field) => ticked[field]));

// 目标栏目：选中的栏目，勾选“包含下级”时连同各自的整棵子树（每个栏目只算一次）
const targets = computed(() => {
  const map = new Map<string, any>();
  const collect = (node: any) => {
    if (map.has(node.id)) {
      return;
    }
    map.set(node.id, node);
    (node.children ?? []).forEach(collect);
  };
  props.channels.forEach((row: any) => {
    if (!includeChildren.value) {
      map.set(row.id, row);
      return;
    }
    // 列表行不带完整的下级：到栏目树里取同一个栏目
    collect(findTreeItem(props.channelTree, (item: any) => item.id === row.id) ?? row);
  });
  return [...map.values()];
});
// 栏目权限之外的目标：服务端会整批拒绝，这里先指出来
const outOfScope = computed(() => (currentUser.allChannelPermission ? [] : targets.value.filter((node) => !props.channelPermissions.includes(node.id))));
const outOfScopeNames = computed(
  () =>
    outOfScope.value
      .slice(0, NAMES_MAX)
      .map((node) => node.name)
      .join(t('punct.enum')) + (outOfScope.value.length > NAMES_MAX ? '…' : ''),
);

const fetchLists = async () => {
  const [channelModels, articleModels, channelFiles, articleFiles, processModels] = await Promise.all([
    queryModelList({ type: 'channel' }),
    queryModelList({ type: 'article' }),
    queryChannelTemplates(),
    queryArticleTemplates(),
    // 审核流程是商业版基础版的能力：以下的版本没有流程可选，只能清除已有的设置（与编辑表单一致）
    currentUser.commercialTier > 0 ? queryProcessModelList({ category: 'sys_article', deployed: true }) : [],
  ]);
  channelModelList.value = channelModels;
  articleModelList.value = articleModels;
  channelTemplates.value = channelFiles;
  articleTemplates.value = articleFiles;
  processModelList.value = processModels;
};
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      return;
    }
    error.value = undefined;
    includeChildren.value = false;
    FIELDS.forEach(({ field }) => (ticked[field] = false));
    Object.assign(values, { articleModelId: undefined, channelTemplate: null, articleTemplate: null, processKey: NO_PROCESS, nav: true, real: true, allowSearch: true, pageSize: 20 });
    fetchLists();
  },
);

// 改动勾选或取值后，上一次提交留下的提示不再适用
watch([ticked, values], () => {
  error.value = undefined;
});

const handleSubmit = async () => {
  error.value = undefined;
  const submitFields = tickedFields.value;
  if (submitFields.length <= 0) {
    error.value = t('channel.batchUpdate.error.fieldRequired');
    return;
  }
  if (ticked.articleModelId && values.articleModelId == null) {
    error.value = t('channel.batchUpdate.error.valueRequired', { field: t(LABELS.articleModelId) });
    return;
  }
  if (ticked.pageSize && values.pageSize == null) {
    error.value = t('channel.batchUpdate.error.valueRequired', { field: t(LABELS.pageSize) });
    return;
  }
  const labels = submitFields.map((field) => t(LABELS[field])).join(t('punct.enum'));
  try {
    await ElMessageBox.confirm(t('channel.batchUpdate.confirm', { count: targets.value.length, fields: labels }), t('channel.op.batchUpdate'), { type: 'warning' });
  } catch {
    return;
  }
  loading.value = true;
  try {
    const result = await batchUpdateChannel({
      ids: props.channels.map((row: any) => row.id),
      includeChildren: includeChildren.value,
      fields: submitFields,
      articleModelId: values.articleModelId,
      channelTemplate: values.channelTemplate,
      articleTemplate: values.articleTemplate,
      processKey: values.processKey === NO_PROCESS ? null : values.processKey,
      nav: values.nav,
      real: values.real,
      allowSearch: values.allowSearch,
      pageSize: values.pageSize,
    });
    ElMessage.success(t('channel.batchUpdate.result', { count: result.updated }));
    emit('finished');
    emit('update:modelValue', false);
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('channel.op.batchUpdate')"
    width="640"
    top="8vh"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="(event: boolean) => $emit('update:modelValue', event)"
  >
    <el-alert :title="$t('channel.batchUpdate.tip')" type="info" :closable="false" class="mb-3" />
    <div class="flex items-center mb-1">
      <span>{{ $t('channel.batchUpdate.selected', { count: channels.length }) }}</span>
      <el-checkbox v-model="includeChildren" class="ml-6">{{ $t('channel.batchUpdate.includeChildren') }}</el-checkbox>
    </div>
    <div class="mb-3 text-sm text-gray-secondary">{{ $t('channel.batchUpdate.total', { count: targets.length }) }}</div>
    <div v-if="outOfScope.length > 0" class="mb-3 text-sm text-danger">{{ $t('channel.batchUpdate.outOfScope', { names: outOfScopeNames }) }}</div>

    <div v-if="shown('articleModelId')" class="setting-row">
      <el-checkbox v-model="ticked.articleModelId" class="setting-label">{{ $t('channel.articleModel') }}</el-checkbox>
      <div class="setting-control">
        <el-select v-model="values.articleModelId" :disabled="!ticked.articleModelId" class="w-full">
          <el-option v-for="item in articleModelList" :key="item.id" :label="item.name" :value="item.id"></el-option>
        </el-select>
        <div v-if="ticked.articleModelId" class="mt-1 text-xs leading-4 text-gray-secondary">{{ $t('channel.batchUpdate.articleModel.tip') }}</div>
      </div>
    </div>
    <div v-if="shown('channelTemplate')" class="setting-row">
      <el-checkbox v-model="ticked.channelTemplate" class="setting-label">{{ $t('channel.channelTemplate') }}</el-checkbox>
      <div class="setting-control">
        <template-select v-model="values.channelTemplate" surface="channel" :templates="channelTemplates" default-option :disabled="!ticked.channelTemplate" />
      </div>
    </div>
    <div v-if="shown('articleTemplate')" class="setting-row">
      <el-checkbox v-model="ticked.articleTemplate" class="setting-label">{{ $t('channel.articleTemplate') }}</el-checkbox>
      <div class="setting-control">
        <template-select v-model="values.articleTemplate" surface="article" :templates="articleTemplates" default-option :disabled="!ticked.articleTemplate" />
      </div>
    </div>
    <div v-if="shown('processKey')" class="setting-row">
      <el-checkbox v-model="ticked.processKey" class="setting-label">{{ $t('channel.processKey') }}</el-checkbox>
      <div class="setting-control">
        <!-- 空字符串是“无”这一选项的取值：不让 ElementPlus 把它当作未选择 -->
        <el-select v-model="values.processKey" :disabled="!ticked.processKey" :empty-values="[undefined, null]" class="w-full">
          <el-option :label="$t('channel.batchUpdate.processKey.none')" :value="NO_PROCESS"></el-option>
          <el-option v-for="item in processModelList" :key="item.key" :label="item.name" :value="item.key"></el-option>
        </el-select>
      </div>
    </div>
    <div v-if="shown('nav')" class="setting-row">
      <el-checkbox v-model="ticked.nav" class="setting-label"><label-tip message="channel.nav" help /></el-checkbox>
      <div class="setting-control">
        <el-radio-group v-model="values.nav" :disabled="!ticked.nav">
          <el-radio :value="true">{{ $t('yes') }}</el-radio>
          <el-radio :value="false">{{ $t('no') }}</el-radio>
        </el-radio-group>
      </div>
    </div>
    <div v-if="shown('real')" class="setting-row">
      <el-checkbox v-model="ticked.real" class="setting-label"><label-tip message="channel.real" help /></el-checkbox>
      <div class="setting-control">
        <el-radio-group v-model="values.real" :disabled="!ticked.real">
          <el-radio :value="true">{{ $t('yes') }}</el-radio>
          <el-radio :value="false">{{ $t('no') }}</el-radio>
        </el-radio-group>
      </div>
    </div>
    <div v-if="shown('allowSearch')" class="setting-row">
      <el-checkbox v-model="ticked.allowSearch" class="setting-label"><label-tip message="channel.allowSearch" help /></el-checkbox>
      <div class="setting-control">
        <el-radio-group v-model="values.allowSearch" :disabled="!ticked.allowSearch">
          <el-radio :value="true">{{ $t('yes') }}</el-radio>
          <el-radio :value="false">{{ $t('no') }}</el-radio>
        </el-radio-group>
      </div>
    </div>
    <div v-if="shown('pageSize')" class="setting-row">
      <el-checkbox v-model="ticked.pageSize" class="setting-label">{{ $t('channel.pageSize') }}</el-checkbox>
      <div class="setting-control">
        <el-input-number v-model="values.pageSize" :min="1" :max="200" :disabled="!ticked.pageSize"></el-input-number>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mt-3" />
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="loading" :disabled="channels.length <= 0 || outOfScope.length > 0" @click="() => handleSubmit()">{{ $t('submit') }}</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
// 每行：左侧勾选框（即该设置的名称），右侧是取值控件；未勾选的行不会提交
.setting-row {
  display: flex;
  align-items: flex-start;
  min-height: 32px;
  margin-bottom: 12px;
}
.setting-label {
  flex: none;
  width: 150px;
}
.setting-control {
  flex: 1;
  min-width: 0;
}
</style>
