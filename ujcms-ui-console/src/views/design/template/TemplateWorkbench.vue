<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type PropType } from 'vue';
import { Back, Clock, Collection, Document, Folder, Loading, Picture } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { Codemirror } from 'vue-codemirror';
import { basicSetup } from 'codemirror';
import { keymap } from '@codemirror/view';
import type { Extension } from '@codemirror/state';
import { html } from '@codemirror/lang-html';
import { css } from '@codemirror/lang-css';
import { javascript } from '@codemirror/lang-javascript';
import { json } from '@codemirror/lang-json';
import { perm } from '@/stores/useCurrentUser';
import { queryWebFileTemplate, queryWebFileTemplateList, updateWebFileTemplate, queryWebFileTemplateHistoryDetail, restoreWebFileTemplateHistory } from '@/api/file';
import WebFileHistoryDrawer from '@/views/file/WebFileHistoryDrawer.vue';
import MergeDiff from './MergeDiff.vue';
import { freemarker } from './freemarker';
import { editorTheme } from './editorTheme';
import { rootBadge } from './dirRoles';

// 设计›模板 编辑工作台（template-manager P3）：文件树 + 全高编辑器的编辑模式。
// 定位是「编辑器升级而非 IDE」：只承担 编辑/切换/预览/历史/对比；文件管理操作
// （新建/改名/复制移动/删除/上传/ZIP）留在浏览模式的 WebFileList。仅模板面使用，
// API 直接引入而非属性下钻（同 WebFileHistoryDrawer 先例）。
defineOptions({
  name: 'TemplateWorkbench',
});
const props = defineProps({
  // 浏览根：/{siteId}/{theme}，原始目录模式为 /
  root: { type: String, required: true },
  // 根显示名（主题名；原始目录模式为 /）
  rootLabel: { type: String, required: true },
  // 是否标注主题根的结构化/开发目录角色（原始目录模式关闭）
  structured: { type: Boolean, default: true },
  // 进入工作台时打开的文件（浏览列表行）
  initialFile: { type: Object as PropType<any>, required: true },
  // 预览语义由外壳（TemplateManager）透传，避免复制 templates/ 直下判定与令牌逻辑
  templateNameOf: { type: Function as PropType<(row: any) => string | undefined>, required: true },
  preview: { type: Function as PropType<(row: any) => Promise<void>>, required: true },
  previewDisabled: { type: Boolean, default: false },
  previewingId: { type: String, default: null },
});
const emit = defineEmits({ close: null });
const { t } = useI18n();

const currentRow = ref<any>(props.initialFile);
const bean = ref<any>();
const text = ref<string>('');
const origText = ref<string>('');
const loading = ref<boolean>(false);
const saving = ref<boolean>(false);
const historyVisible = ref<boolean>(false);
// 对比模式：历史版本快照 vs 当前缓冲；null 为普通编辑
const diff = ref<{ row: any; content: string } | null>(null);

const dirty = computed<boolean>(() => bean.value?.editable === true && text.value !== origText.value);
// 根内相对路径展示（操作员不接触站点 ID 前缀）
const relPath = computed<string>(() => {
  const id = String(currentRow.value?.id ?? '');
  return props.root === '/' ? id : id.slice(props.root.length);
});
const previewable = computed<boolean>(() => currentRow.value != null && props.templateNameOf(currentRow.value) != null);

// ----- 全高布局：以挂载位置实测顶端偏移，不依赖 Layout 高度常量（AppMain p-4 底边距 16px）-----
const rootEl = ref<HTMLElement>();
const paneHeight = ref<string>('auto');
const updateHeight = () => {
  if (rootEl.value != null) {
    paneHeight.value = `calc(100vh - ${Math.max(rootEl.value.getBoundingClientRect().top, 0)}px - 16px)`;
  }
};

// ----- 文件加载 / 保存 -----
const loadBean = async () => {
  loading.value = true;
  try {
    const result = await queryWebFileTemplate(currentRow.value.id);
    bean.value = result;
    text.value = result.text ?? '';
    origText.value = text.value;
  } finally {
    loading.value = false;
  }
};
const handleSave = async () => {
  if (!dirty.value || saving.value || bean.value == null || perm('webFileTemplate:update')) {
    return;
  }
  saving.value = true;
  try {
    await updateWebFileTemplate({ id: bean.value.id, name: bean.value.name, text: text.value });
    origText.value = text.value;
    ElMessage.success(t('success'));
  } finally {
    saving.value = false;
  }
};

// ----- 未保存守卫：树内切换文件、返回列表；路由离开由外壳统一守卫（Builder 先例）-----
const confirmLeave = async (): Promise<boolean> => {
  if (!dirty.value) {
    return true;
  }
  try {
    await ElMessageBox.confirm(t('webFile.template.leaveConfirm'), { type: 'warning' });
    return true;
  } catch {
    return false;
  }
};
const handleBack = async () => {
  if (await confirmLeave()) {
    emit('close');
  }
};
// 浏览器关闭/刷新守卫：仅脏状态时注册
const onBeforeUnload = (e: BeforeUnloadEvent) => {
  e.preventDefault();
};
watch(dirty, (value) => {
  if (value) {
    window.addEventListener('beforeunload', onBeforeUnload);
  } else {
    window.removeEventListener('beforeunload', onBeforeUnload);
  }
});
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload));

// ----- 文件树：懒加载，一次一层（既有 list 端点即够，无需后端递归接口）-----
const tree = ref<any>();
// 初始文件的祖先目录链（id 即完整路径）：懒加载节点创建时命中 default-expanded-keys 即自动展开，
// 逐层触发加载，进入工作台时树自动定位到当前文件
const initialExpandedKeys = (() => {
  const id = String(props.initialFile?.id ?? '');
  const base = props.root === '/' ? '' : props.root;
  const segments = (id.startsWith(base) ? id.slice(base.length) : id).split('/').filter(Boolean);
  segments.pop();
  const keys: string[] = [];
  let acc = base;
  for (const segment of segments) {
    acc = `${acc}/${segment}`;
    keys.push(acc);
  }
  return keys;
})();
const loadNode = async (node: any, resolve: (rows: any[]) => void) => {
  const parentId = node.level === 0 ? props.root : node.data.id;
  const rows = await queryWebFileTemplateList({ parentId });
  resolve([...rows.filter((row: any) => row.directory), ...rows.filter((row: any) => !row.directory)]);
  // 当前文件所在层加载完成后补一次高亮：current-node-key 在异步分层加载下不保证回填
  if (rows.some((row: any) => String(row.id) === String(currentRow.value?.id))) {
    await nextTick();
    tree.value?.setCurrentKey?.(currentRow.value?.id);
  }
};
const openRow = async (row: any) => {
  if (row.directory || String(row.id) === String(currentRow.value?.id)) {
    return;
  }
  if (!(await confirmLeave())) {
    // 还原树高亮到当前文件
    tree.value?.setCurrentKey?.(currentRow.value?.id);
    return;
  }
  currentRow.value = row;
  diff.value = null;
  await loadBean();
};
watch(currentRow, (row) => {
  if (row != null && tree.value?.getNode?.(row.id) != null) {
    tree.value.setCurrentKey(row.id);
  }
});

// ----- 编辑器：按扩展名选择语言；html/ftl 叠加 FreeMarker 高亮；Mod-s 保存 -----
const languageOf = (name: string): Extension[] => {
  const ext = String(name ?? '')
    .slice(String(name ?? '').lastIndexOf('.') + 1)
    .toLowerCase();
  switch (ext) {
    case 'html':
    case 'htm':
    case 'ftl':
      return [html(), freemarker()];
    case 'css':
      return [css()];
    case 'js':
    case 'mjs':
      return [javascript()];
    case 'json':
      return [json()];
    default:
      return [];
  }
};
// 非脏状态也吞掉按键，避免触发浏览器“保存网页”；macOS 上 Mod=Cmd，另绑 Ctrl-s 以兼容旧对话框编辑器的按键习惯
const saveKey = {
  run: () => {
    handleSave();
    return true;
  },
};
const editorExtensions = computed<Extension[]>(() => [
  keymap.of([
    { key: 'Mod-s', ...saveKey },
    { key: 'Ctrl-s', ...saveKey },
  ]),
  basicSetup,
  ...editorTheme,
  ...languageOf(currentRow.value?.name),
]);
const diffExtensions = computed<Extension[]>(() => [...editorTheme, ...languageOf(currentRow.value?.name)]);

// ----- 历史与对比 -----
const handleRestored = async () => {
  // 恢复直接落盘：重载缓冲（与原对话框编辑器行为一致），退出对比
  diff.value = null;
  await loadBean();
};
const handleCompare = async (row: any) => {
  const detail = await queryWebFileTemplateHistoryDetail(row.id);
  diff.value = { row, content: detail.content ?? '' };
  historyVisible.value = false;
};
const handleRestoreVersion = async () => {
  if (diff.value == null) {
    return;
  }
  const row = diff.value.row;
  try {
    await ElMessageBox.confirm(t('webFile.history.confirmRestore', { name: row.path }), t('webFile.history.restore'), {
      confirmButtonText: t('submit'),
      cancelButtonText: t('cancel'),
      type: 'warning',
    });
  } catch {
    return;
  }
  await restoreWebFileTemplateHistory(row.id);
  ElMessage.success(t('webFile.history.restored'));
  await handleRestored();
};

onMounted(() => {
  updateHeight();
  window.addEventListener('resize', updateHeight);
  loadBean();
});
onBeforeUnmount(() => window.removeEventListener('resize', updateHeight));

defineExpose({
  isDirty: () => dirty.value,
});
</script>

<template>
  <div ref="rootEl" class="flex flex-col" :style="{ height: paneHeight }">
    <div class="mb-2 flex items-center gap-2">
      <el-button :icon="Back" @click="() => handleBack()">{{ $t('webFile.template.backToList') }}</el-button>
      <span class="text-sm text-gray-secondary">{{ rootLabel }}</span>
      <span class="text-sm font-medium">{{ relPath }}</span>
      <el-tag v-if="dirty" type="danger" size="small">{{ $t('form.unsaved') }}</el-tag>
      <div class="!ml-auto flex items-center">
        <el-button v-if="previewable" :disabled="previewDisabled" :loading="previewingId === currentRow?.id" @click="() => preview(currentRow)">
          {{ $t('webFile.op.preview') }}
        </el-button>
        <el-button :disabled="perm('webFileTemplate:show')" :icon="Clock" @click="() => (historyVisible = true)">{{ $t('webFile.op.history') }}</el-button>
        <el-button type="primary" :disabled="!dirty || perm('webFileTemplate:update')" :loading="saving" @click="() => handleSave()">
          {{ $t('save') }}
        </el-button>
      </div>
    </div>
    <div class="app-block min-h-0 flex-1">
      <el-splitter>
        <el-splitter-panel :size="280" :min="180" :max="560">
          <div class="h-full overflow-auto p-2">
            <el-tree
              ref="tree"
              lazy
              :load="loadNode"
              :props="{ label: 'name', isLeaf: (data: any) => !data.directory }"
              node-key="id"
              highlight-current
              :current-node-key="currentRow?.id"
              :default-expanded-keys="initialExpandedKeys"
              :expand-on-click-node="true"
              @node-click="(data: any) => openRow(data)"
            >
              <template #default="{ data }">
                <span class="flex min-w-0 items-center">
                  <el-icon class="mr-1 shrink-0">
                    <Folder v-if="data.fileType === 'DIRECTORY'" class="text-warning" />
                    <Collection v-else-if="data.fileType === 'ZIP'" />
                    <Picture v-else-if="data.fileType === 'IMAGE'" />
                    <Document v-else />
                  </el-icon>
                  <span class="truncate">{{ data.name }}</span>
                  <el-tag v-if="structured && rootBadge(data, root)" size="small" type="info" effect="plain" class="ml-2" :class="{ 'opacity-60': rootBadge(data, root)?.dev }">
                    {{ $t(rootBadge(data, root)!.key) }}
                  </el-tag>
                </span>
              </template>
            </el-tree>
          </div>
        </el-splitter-panel>
        <el-splitter-panel>
          <!-- 加载遮罩用显式 v-if 而非 v-loading 指令：分栏面板初始化期间指令的淡出过渡可能挂起，遗留孤儿遮罩 -->
          <div class="relative flex h-full min-h-0 flex-col p-2">
            <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-white/60">
              <el-icon class="is-loading text-primary" :size="24"><Loading /></el-icon>
            </div>
            <!-- 对比模式：历史版本（左，只读）vs 当前缓冲（右，含未保存改动） -->
            <template v-if="diff != null">
              <div class="mb-2 flex items-center gap-2">
                <el-tag type="info" size="small">{{ $t('webFile.template.diff.version') }}</el-tag>
                <span class="text-sm">
                  {{ dayjs(diff.row.created).format('YYYY-MM-DD HH:mm:ss') }}
                  · {{ $t(`webFile.history.source.${diff.row.source}`) }}
                  <template v-if="diff.row.actor"> · {{ diff.row.actor }}</template>
                </span>
                <span class="text-sm text-gray-secondary">⇄ {{ $t('webFile.template.diff.current') }}</span>
                <el-tag v-if="dirty" type="danger" size="small">{{ $t('form.unsaved') }}</el-tag>
                <div class="!ml-auto flex items-center">
                  <el-button :disabled="perm('webFileTemplate:update')" type="primary" plain @click="() => handleRestoreVersion()">
                    {{ $t('webFile.template.diff.restoreThis') }}
                  </el-button>
                  <el-button @click="() => (diff = null)">{{ $t('webFile.template.diff.exit') }}</el-button>
                </div>
              </div>
              <merge-diff class="min-h-0 flex-1" :a="diff.content" :b="text" :extensions="diffExtensions" />
            </template>
            <!-- 不可编辑文件：轻量占位（管理操作在浏览模式进行） -->
            <div v-else-if="bean != null && !bean.editable" class="flex h-full flex-col items-center justify-center gap-3">
              <el-icon :size="48" class="text-gray-400">
                <Collection v-if="bean.zip" />
                <Picture v-else-if="bean.image" />
                <Document v-else />
              </el-icon>
              <div class="font-medium">{{ bean.name }}</div>
              <div class="text-sm text-gray-secondary">{{ bean.size }}</div>
              <el-image v-if="bean.image" :src="bean.url" class="max-h-64 max-w-lg" fit="contain" />
              <el-link v-if="bean.url" type="primary" :href="bean.url" target="_blank">{{ $t('webFile.op.view') }}</el-link>
              <div class="text-sm text-gray-secondary">{{ $t('webFile.template.notEditable') }}</div>
            </div>
            <!-- 编辑模式：按文件重建实例（:key），避免撤销历史跨文件串联 -->
            <codemirror
              v-else-if="bean != null"
              :key="String(bean.id)"
              v-model="text"
              :extensions="editorExtensions"
              :indent-with-tab="true"
              :tab-size="2"
              class="editor-pane min-h-0 flex-1 border border-gray-300"
              :style="{ height: '100%' }"
            />
          </div>
        </el-splitter-panel>
      </el-splitter>
    </div>
    <web-file-history-drawer
      v-model="historyVisible"
      :file-id="String(currentRow?.id ?? '')"
      compare
      @restored="() => handleRestored()"
      @compare="(row: any) => handleCompare(row)"
    />
  </div>
</template>

<style scoped>
.editor-pane :deep(.cm-editor) {
  height: 100%;
}
.editor-pane :deep(.cm-scroller) {
  overflow: auto;
}
</style>
