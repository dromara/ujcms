<script setup lang="ts">
import { computed, ref, watch, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import type { UploadFile } from 'element-plus';
import { batchCreateChannel, CHANNEL_BATCH_MAX_SIZE } from '@/api/content';
import { queryModelList } from '@/api/config';
import { currentUser } from '@/stores/useCurrentUser';
import { flatTree, findTreeItem } from '@/utils/tree';
import { decodeText } from '@/utils/textFile';
import { parseChannelOutline, outlineToTree, type OutlineErrorType } from './channelOutline';

// 批量新增栏目（docs/spec/channel-management/plan.md D3、D4）：粘贴为主，选择文件只是把内容读进同一个文本框。
// 全有或全无——跳过任何一行都会让它的下级失去上级，所以文本有问题时不提交，逐行指出
defineOptions({
  name: 'ChannelBatchForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  // 左侧导航当前选中的栏目：作为默认的上级栏目
  parent: { type: Object, default: null },
  // 本站栏目树（左侧导航已加载的那一份）
  channelTree: { type: Array as PropType<any[]>, required: true },
  channelPermissions: { type: Array as PropType<string[]>, required: true },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

/** 可读入的文件大小上限：远大于任何实际的栏目清单，只为挡住误选的大文件 */
const FILE_MAX_SIZE = 2 * 1024 * 1024;
const ERROR_TYPES: OutlineErrorType[] = ['indent', 'name', 'slug', 'slugDuplicated', 'slugExist'];
const { t } = useI18n();
const parentId = ref<string>();
const channelModelId = ref<string>();
const articleModelId = ref<string>();
const channelModelList = ref<any[]>([]);
const articleModelList = ref<any[]>([]);
const text = ref<string>('');
const loading = ref<boolean>(false);

// 上级栏目选择器用独立的一份树：不改动左侧导航的节点对象（它们带 parent 回指，且 disabled 会影响导航）
const toPickerTree = (nodes: any[]): any[] =>
  nodes.map((node) => ({
    id: node.id,
    name: node.name,
    disabled: !currentUser.allChannelPermission && !props.channelPermissions.includes(node.id),
    children: toPickerTree(node.children ?? []),
  }));
const parentTree = computed(() => toPickerTree(props.channelTree));
const existingSlugs = computed(() => new Set<string>(flatTree(props.channelTree).map((item: any) => String(item.slug ?? '').toLowerCase())));
const parsed = computed(() => parseChannelOutline(text.value, existingSlugs.value));
const previewTree = computed(() => outlineToTree(parsed.value.items));
const tooMany = computed(() => parsed.value.items.length > CHANNEL_BATCH_MAX_SIZE);
// 各类问题及其行号，按最先出现的行排列；行号只列前 10 个
const errorGroups = computed(() =>
  ERROR_TYPES.map((type) => ({ type, lines: parsed.value.errors.filter((error) => error.type === type).map((error) => error.line) }))
    .filter((group) => group.lines.length > 0)
    .sort((a, b) => a.lines[0] - b.lines[0])
    .map((group) => ({ type: group.type, lines: group.lines.slice(0, 10).join(t('punct.enum')) + (group.lines.length > 10 ? '…' : '') })),
);
const submittable = computed(
  () => parsed.value.items.length > 0 && parsed.value.errors.length === 0 && !tooMany.value && channelModelId.value != null && articleModelId.value != null,
);

// 模型默认沿用上级栏目的（与新增表单一致），根栏目取第一个；操作员改选上级时随之更新，之后仍可另选
const applyModelDefaults = () => {
  const parent = parentId.value != null ? findTreeItem(props.channelTree, (item: any) => item.id === parentId.value) : undefined;
  channelModelId.value = parent?.channelModelId ?? channelModelList.value[0]?.id;
  articleModelId.value = parent?.articleModelId ?? articleModelList.value[0]?.id;
};
const fetchModels = async () => {
  [channelModelList.value, articleModelList.value] = await Promise.all([queryModelList({ type: 'channel' }), queryModelList({ type: 'article' })]);
  applyModelDefaults();
};
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      text.value = '';
      parentId.value = props.parent?.id;
      fetchModels();
    }
  },
);

// auto-upload=false 时 ElementPlus 只触发 on-change；文件在浏览器里读成文本，不上传
const handleFileSelect = async (file: UploadFile) => {
  const raw = file.raw;
  if (raw == null) {
    return;
  }
  if (raw.size > FILE_MAX_SIZE) {
    ElMessage.error(t('channel.batch.fileError'));
    return;
  }
  try {
    text.value = decodeText(await raw.arrayBuffer());
  } catch {
    ElMessage.error(t('channel.batch.fileError'));
  }
};

const handleSubmit = async () => {
  if (!submittable.value || channelModelId.value == null || articleModelId.value == null) {
    return;
  }
  loading.value = true;
  try {
    const result = await batchCreateChannel({
      parentId: parentId.value ?? undefined,
      channelModelId: channelModelId.value,
      articleModelId: articleModelId.value,
      items: parsed.value.items.map((item) => ({ name: item.name, slug: item.slug, parent: item.parent })),
    });
    ElMessage.success(t('channel.batch.result', { count: result.created }));
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
    :title="$t('channel.op.batchCreate')"
    width="860"
    top="6vh"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="(event: boolean) => $emit('update:modelValue', event)"
  >
    <el-form label-width="90px">
      <el-form-item :label="$t('channel.parent')">
        <el-tree-select
          v-model="parentId"
          :data="parentTree"
          node-key="id"
          :props="{ label: 'name', disabled: 'disabled' }"
          :render-after-expand="false"
          :placeholder="$t('channel.root')"
          check-strictly
          clearable
          class="w-full"
          @change="() => applyModelDefaults()"
        />
      </el-form-item>
      <el-row>
        <el-col :span="12">
          <el-form-item :label="$t('channel.channelModel')" required>
            <el-select v-model="channelModelId" class="w-full">
              <el-option v-for="item in channelModelList" :key="item.id" :label="item.name" :value="item.id"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$t('channel.articleModel')" required>
            <el-select v-model="articleModelId" class="w-full">
              <el-option v-for="item in articleModelList" :key="item.id" :label="item.name" :value="item.id"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <el-alert :title="$t('channel.batch.tip', { sep: '|' })" type="info" :closable="false" class="mb-3" />
    <el-row :gutter="12">
      <el-col :span="14">
        <el-input v-model="text" type="textarea" :rows="14" resize="none" :placeholder="$t('channel.batch.placeholder', { sep: '|' })" spellcheck="false"></el-input>
      </el-col>
      <el-col :span="10">
        <div class="px-2 py-1 overflow-auto border border-gray-200 rounded batch-preview">
          <div v-if="previewTree.length === 0" class="mt-2 text-sm text-gray-secondary">{{ $t('channel.batch.empty') }}</div>
          <el-tree v-else :data="previewTree" :props="{ label: 'name' }" node-key="id" default-expand-all :expand-on-click-node="false">
            <template #default="{ data }">
              <span>{{ data.name }}</span>
              <span v-if="data.slug" class="ml-2 text-xs text-gray-secondary">{{ data.slug }}</span>
            </template>
          </el-tree>
        </div>
      </el-col>
    </el-row>
    <div class="flex items-center mt-3">
      <el-upload :on-change="handleFileSelect" :auto-upload="false" :show-file-list="false" accept=".txt,text/plain">
        <el-button>{{ $t('channel.batch.chooseFile') }}</el-button>
      </el-upload>
      <span class="ml-3 text-sm text-gray-secondary">{{ $t('channel.batch.recognized', { count: parsed.items.length, depth: parsed.depth }) }}</span>
    </div>
    <div v-if="errorGroups.length > 0 || tooMany" class="mt-2 text-sm text-danger">
      <div v-for="group in errorGroups" :key="group.type">{{ $t('channel.batch.error.lines', { lines: group.lines, message: $t(`channel.batch.error.${group.type}`) }) }}</div>
      <div v-if="tooMany">{{ $t('channel.batch.tooMany', { max: CHANNEL_BATCH_MAX_SIZE }) }}</div>
    </div>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="loading" :disabled="!submittable" @click="() => handleSubmit()">{{ $t('add') }}</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
// 与左侧 14 行文本框等高
.batch-preview {
  height: 306px;
}
</style>
