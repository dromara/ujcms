<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRefs, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { saveAs } from 'file-saver';
import type { UploadFile } from 'element-plus';
import { handleBlobError } from '@/utils/request';
import { toTree, disableParentTree, filterTreeNodeByName } from '@/utils/tree';
import { perm } from '@/stores/useCurrentUser';
import {
  queryChannelList,
  downloadArticleImportTemplate,
  previewArticleImport,
  startArticleImport,
  queryArticleImportTask,
  stopArticleImportTask,
} from '@/api/content';

// 文章批量导入（docs/spec/article-management/plan.md D7，商业版 T1）。三步：选文件与选项 → 预检（只读）→ 执行（后台任务，
// 轮询进度）。服务端不在预检与执行之间保存文件：执行时把同一份文件再传一次，重新核对。
// 模板按所选的默认栏目下载：系统列之后是该栏目文章模型的自定义字段列；表格里的自定义字段按每一行所在栏目的模型识别。
// 导入默认是草稿；有“提交”权限的可以选导入后提交（规则同“保存并提交”）。
// 更新模式（有“编辑”权限）：上传导出的表格（带 ID 列），按 ID 更新已有文章，只写表格里有的列；空单元格按选项保留原值或清空；
// 与库中一样的行记为“无变化”，不写
defineOptions({
  name: 'ArticleImportDialog',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  // 列表里选中的栏目：作为默认栏目的初值
  channelId: { type: String, default: undefined },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

// 任务状态：0 等待，1 运行中，2 出错，3 已停止，4 完成
const TASK_WAITING = 0;
const TASK_RUNNING = 1;
const TASK_ERROR = 2;
const TASK_STOP = 3;
const POLL_MILLIS = 1500;
// 服务端列出的问题行上限（= 一次最多导入的行数）：每一行的原因都列得出来
const MAX_ISSUES = 2000;

const { modelValue: visible } = toRefs(props);
const { t } = useI18n();
const step = ref<'select' | 'preview' | 'run'>('select');
const file = ref<File>();
const upload = ref<any>();
const defaultChannelId = ref<string>();
const skipDuplicates = ref<boolean>(true);
// 导入后提交（有审批流程的栏目进入审核，其余发布）；没有“提交”权限的只能导入草稿
const submit = ref<boolean>(false);
// 新建文章，或按 ID 更新已有文章（须另有“编辑”权限）
const mode = ref<'create' | 'update'>('create');
const isUpdate = computed<boolean>(() => mode.value === 'update');
// 更新模式里，表格有这一列、单元格却空着的：保留原值，还是清空
const blankCells = ref<'keep' | 'clear'>('keep');
const options = computed(() => ({
  channelId: defaultChannelId.value,
  skipDuplicates: skipDuplicates.value,
  submit: submit.value,
  mode: mode.value,
  blankCells: blankCells.value,
}));
const channelData = ref<any[]>([]);
const loading = ref<boolean>(false);
const preview = ref<any>();
const task = ref<any>();
let timer: ReturnType<typeof setTimeout> | undefined;
// 轮询的批次：重新安排、停止轮询都换一个号，途中的那次查询回来时发现号变了，结果就作废
let generation = 0;

const running = computed<boolean>(() => task.value != null && [TASK_WAITING, TASK_RUNNING].includes(task.value.status));
// 已要求停止、结果还没写出来：执行器要到下一个检查点才停下，再写结果。这段时间接着轮询，不当作已经结束
const stopping = computed<boolean>(() => task.value != null && task.value.status === TASK_STOP && task.value.report == null && task.value.error == null);
const percentage = computed<number>(() => {
  if (task.value == null || !task.value.total) {
    // 没有总数：排队中、或排队时就被停止的是 0，其余（出错、没有可导入的行）算走完了
    return running.value || task.value?.status === TASK_STOP ? 0 : 100;
  }
  return Math.min(100, Math.round((task.value.current / task.value.total) * 100));
});
const issues = computed<any[]>(() => (step.value === 'run' ? (task.value?.report?.issues ?? []) : (preview.value?.issues ?? [])));
const issueCount = computed<number>(() => {
  if (step.value === 'run') {
    return (task.value?.report?.skipped ?? 0) + (task.value?.report?.failed ?? 0);
  }
  return (preview.value?.skipped ?? 0) + (preview.value?.invalid ?? 0);
});
const doneMessage = computed<string>(() => {
  const report = task.value?.report;
  if (report == null) {
    return '';
  }
  if (isUpdate.value) {
    return t('article.import.done.update', { updated: report.updated, unchanged: report.unchanged, failed: report.failed });
  }
  const done = t('article.import.done', { created: report.created, skipped: report.skipped, failed: report.failed });
  return submit.value && report.created > 0 ? `${done}${t('punct.period')}${t('article.import.done.submitted')}` : done;
});
const stoppedMessage = computed<string>(() => {
  const report = task.value?.report;
  if (report == null) {
    return '';
  }
  return isUpdate.value ? t('article.import.stopped.update', { updated: report.updated }) : t('article.import.stopped', { created: report.created });
});
// 问题清单存成 CSV（带 BOM，Excel 直接打开不乱码）：行号对照表格改好再导一次
const handleDownloadIssues = () => {
  const quote = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`;
  const lines = [[t('article.import.row'), t('article.title'), t('article.import.reason')], ...issues.value.map((issue: any) => [issue.row, issue.title, issue.reason])];
  const csv = `﻿${lines.map((line) => line.map(quote).join(',')).join('\r\n')}`;
  saveAs(new Blob([csv], { type: 'text/csv;charset=utf-8' }), 'article-import-issues.csv');
};

const stopPolling = () => {
  generation += 1;
  if (timer != null) {
    clearTimeout(timer);
    timer = undefined;
  }
};
const reset = () => {
  stopPolling();
  step.value = 'select';
  file.value = undefined;
  preview.value = undefined;
  task.value = undefined;
  upload.value?.clearFiles();
};

watch(visible, async () => {
  if (!visible.value) {
    // 关掉对话框不停止任务：它在后台跑完，结果在任务管理里也看得到
    stopPolling();
    return;
  }
  reset();
  skipDuplicates.value = true;
  submit.value = false;
  mode.value = 'create';
  blankCells.value = 'keep';
  channelData.value = disableParentTree(toTree(await queryChannelList({ isArticlePermission: true, isReal: true })));
  // 列表里选中的若是末级栏目，就作为默认栏目的初值
  const selectable = (nodes: any[]): string[] => nodes.flatMap((node) => (node.children?.length > 0 ? selectable(node.children) : [node.id]));
  defaultChannelId.value = props.channelId != null && selectable(channelData.value).includes(props.channelId) ? props.channelId : undefined;
});
onBeforeUnmount(stopPolling);

const handleFileChange = (uploadFile: UploadFile) => {
  file.value = uploadFile.raw;
};
const handleFileRemove = () => {
  file.value = undefined;
};
const handleTemplate = async () => {
  try {
    const { blob, filename } = await downloadArticleImportTemplate(defaultChannelId.value);
    saveAs(blob, filename);
  } catch (e) {
    await handleBlobError(e);
  }
};
const handlePreview = async () => {
  if (file.value == null) {
    ElMessage.warning(t('article.import.fileRequired'));
    return;
  }
  loading.value = true;
  try {
    preview.value = await previewArticleImport(file.value, options.value);
    step.value = 'preview';
  } finally {
    loading.value = false;
  }
};
const schedule = () => {
  stopPolling();
  const mine = generation;
  timer = setTimeout(() => poll(mine), POLL_MILLIS);
};
const poll = async (mine: number) => {
  timer = undefined;
  if (task.value == null) {
    return;
  }
  let next: any;
  try {
    next = await queryArticleImportTask(task.value.id);
  } catch {
    // 拦截器已提示；不再轮询
    return;
  }
  if (mine !== generation) {
    return;
  }
  task.value = next;
  if (running.value || stopping.value) {
    schedule();
  } else {
    emit('finished');
  }
};
const handleStart = async () => {
  if (file.value == null) {
    return;
  }
  loading.value = true;
  try {
    const { taskId } = await startArticleImport(file.value, options.value);
    task.value = { id: taskId, status: TASK_WAITING, current: 0, total: preview.value?.valid ?? 0 };
    step.value = 'run';
    schedule();
  } finally {
    loading.value = false;
  }
};
const handleStop = async () => {
  if (task.value == null) {
    return;
  }
  // 途中的那次查询作废：它可能是停止之前的状态
  stopPolling();
  try {
    task.value = await stopArticleImportTask(task.value.id);
  } catch {
    // 拦截器已提示。任务还在跑：接着查
    if (visible.value) {
      schedule();
    }
    return;
  }
  if (!visible.value) {
    return;
  }
  if (running.value || stopping.value) {
    schedule();
  } else {
    emit('finished');
  }
};
// 回到第一步重新选文件：上传组件随这一步重建，列表是空的——原来那份文件也一并放掉，不留一个看不见却还能预检的文件
const handleBack = () => {
  file.value = undefined;
  preview.value = undefined;
  step.value = 'select';
};
</script>

<template>
  <el-dialog :title="$t('article.op.import')" :model-value="modelValue" :width="760" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <div v-loading="loading">
      <!-- 第一步：文件与选项 -->
      <template v-if="step === 'select'">
        <div v-if="isUpdate" class="mb-4 text-xs leading-5 text-gray-secondary">{{ $t('article.import.update.tip') }}</div>
        <div v-else class="mb-4 text-xs leading-5 text-gray-secondary">
          {{ $t('article.import.tip') }}
          <el-button type="primary" link size="small" @click="() => handleTemplate()">{{ $t('article.import.template') }}</el-button>
        </div>
        <el-form label-position="top" @submit.prevent>
          <el-form-item :label="$t('article.import.mode')">
            <el-radio-group v-model="mode">
              <el-radio value="create">{{ $t('article.import.mode.create') }}</el-radio>
              <el-radio value="update" :disabled="perm('article:update')">{{ $t('article.import.mode.update') }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="$t('article.import.file')" required>
            <el-upload ref="upload" class="w-full" :auto-upload="false" :limit="1" accept=".xlsx" :on-change="handleFileChange" :on-remove="handleFileRemove">
              <el-button>{{ $t('article.import.selectFile') }}</el-button>
            </el-upload>
          </el-form-item>
          <el-form-item v-if="isUpdate" :label="$t('article.import.blankCells')">
            <el-radio-group v-model="blankCells">
              <el-radio value="keep">{{ $t('article.import.blankCells.keep') }}</el-radio>
              <el-radio value="clear">{{ $t('article.import.blankCells.clear') }}</el-radio>
            </el-radio-group>
            <div class="text-xs leading-5 text-gray-secondary">{{ $t('article.import.blankCells.tip') }}</div>
          </el-form-item>
          <el-form-item v-if="!isUpdate" :label="$t('article.import.channel')">
            <el-tree-select
              v-model="defaultChannelId"
              :data="channelData"
              node-key="id"
              :props="{ label: 'name', disabled: 'disabled' }"
              :render-after-expand="false"
              :filter-node-method="filterTreeNodeByName"
              filterable
              clearable
              default-expand-all
              class="w-full"
            />
            <div class="text-xs leading-5 text-gray-secondary">{{ $t('article.import.channel.tip') }}</div>
          </el-form-item>
          <el-form-item v-if="!isUpdate" :label="$t('article.import.duplicates')">
            <el-radio-group v-model="skipDuplicates">
              <el-radio :value="true">{{ $t('article.import.duplicates.skip') }}</el-radio>
              <el-radio :value="false">{{ $t('article.import.duplicates.import') }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="!isUpdate" :label="$t('article.import.status')">
            <el-radio-group v-model="submit">
              <el-radio :value="false">{{ $t('article.import.status.draft') }}</el-radio>
              <el-radio :value="true" :disabled="perm('article:submit')">{{ $t('article.import.status.submit') }}</el-radio>
            </el-radio-group>
            <div class="text-xs leading-5 text-gray-secondary">{{ $t('article.import.status.tip') }}</div>
          </el-form-item>
        </el-form>
      </template>

      <!-- 第二步：预检结果 -->
      <template v-else-if="step === 'preview' && preview != null">
        <div class="flex flex-wrap items-center gap-2 mb-3">
          <el-tag type="info" disable-transitions>{{ $t('article.import.result.total', { count: preview.total }) }}</el-tag>
          <el-tag type="success" disable-transitions>{{ $t(isUpdate ? 'article.import.result.updatable' : 'article.import.result.valid', { count: preview.valid }) }}</el-tag>
          <el-tag v-if="preview.unchanged > 0" type="info" disable-transitions>{{ $t('article.import.result.unchanged', { count: preview.unchanged }) }}</el-tag>
          <el-tag v-if="preview.skipped > 0" type="warning" disable-transitions>{{ $t('article.import.result.skipped', { count: preview.skipped }) }}</el-tag>
          <el-tag v-if="preview.invalid > 0" type="danger" disable-transitions>{{ $t('article.import.result.invalid', { count: preview.invalid }) }}</el-tag>
        </div>
        <div v-if="preview.customColumns?.length > 0" class="mb-3 text-xs leading-5 text-gray-secondary">
          {{ $t('article.import.customColumns', { headers: preview.customColumns.join($t('punct.enum')) }) }}
        </div>
        <el-alert v-if="preview.idColumn && !isUpdate" type="warning" :closable="false" show-icon class="mb-3" :title="$t('article.import.idColumnIgnored')" />
        <el-alert
          v-if="preview.unknownHeaders?.length > 0"
          type="warning"
          :closable="false"
          show-icon
          class="mb-3"
          :title="$t('article.import.unknownHeaders', { headers: preview.unknownHeaders.join($t('punct.enum')) })"
        />
        <el-alert
          v-if="preview.shadowedFields?.length > 0"
          type="info"
          :closable="false"
          show-icon
          class="mb-3"
          :title="$t('article.import.shadowedFields', { fields: preview.shadowedFields.join($t('punct.enum')) })"
        />
      </template>

      <!-- 第三步：执行 -->
      <template v-else-if="step === 'run' && task != null">
        <el-progress
          :percentage="percentage"
          :status="task.status === TASK_ERROR ? 'exception' : task.status === TASK_STOP ? 'warning' : running ? undefined : 'success'"
          class="mb-3"
        />
        <div v-if="task.status === TASK_WAITING" class="mb-3 text-sm">{{ $t('article.import.waiting') }}</div>
        <div v-else-if="stopping" class="mb-3 text-sm">{{ $t('article.import.stopping') }}</div>
        <div v-else-if="task.status === TASK_RUNNING" class="mb-3 text-sm">{{ $t(isUpdate ? 'article.import.running.update' : 'article.import.running', { current: task.current, total: task.total }) }}</div>
        <el-alert
          v-else-if="task.status === TASK_ERROR"
          type="error"
          :closable="false"
          show-icon
          class="mb-3"
          :title="$t('article.import.error', { message: task.error ?? '' })"
        />
        <el-alert
          v-else-if="task.report != null"
          :type="task.status === TASK_STOP ? 'warning' : task.report.failed > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
          class="mb-3"
          :title="task.status === TASK_STOP ? stoppedMessage : doneMessage"
        />
      </template>

      <!-- 被跳过、有问题的行：预检与执行结果共用 -->
      <template v-if="step !== 'select' && issues.length > 0">
        <div class="mb-1 text-sm">
          {{ $t('article.import.issues') }}
          <span v-if="issueCount > issues.length" class="ml-2 text-xs text-gray-secondary">{{ $t('article.import.issues.more', { max: MAX_ISSUES }) }}</span>
          <el-button type="primary" link size="small" class="ml-2" @click="() => handleDownloadIssues()">{{ $t('article.import.issues.download') }}</el-button>
        </div>
        <el-table :data="issues" size="small" max-height="320" border>
          <el-table-column property="row" :label="$t('article.import.row')" width="70" />
          <el-table-column property="title" :label="$t('article.title')" min-width="180" show-overflow-tooltip />
          <el-table-column property="reason" :label="$t('article.import.reason')" min-width="260" show-overflow-tooltip />
        </el-table>
      </template>
    </div>
    <template #footer>
      <template v-if="step === 'select'">
        <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="loading" :disabled="file == null" @click="() => handlePreview()">{{ $t('article.import.preview') }}</el-button>
      </template>
      <template v-else-if="step === 'preview'">
        <el-button @click="() => handleBack()">{{ $t('article.import.back') }}</el-button>
        <el-button type="primary" :loading="loading" :disabled="!preview || preview.valid <= 0" @click="() => handleStart()">
          {{ $t(isUpdate ? 'article.import.start.update' : 'article.import.start', { count: preview?.valid ?? 0 }) }}
        </el-button>
      </template>
      <template v-else>
        <el-button v-if="running || stopping" :loading="stopping" @click="() => handleStop()">{{ $t('article.import.stop') }}</el-button>
        <el-button v-else @click="() => reset()">{{ $t('article.import.again') }}</el-button>
        <el-button type="primary" @click="() => $emit('update:modelValue', false)">{{ $t('close') }}</el-button>
      </template>
    </template>
  </el-dialog>
</template>
