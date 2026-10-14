<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRefs, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { saveAs } from 'file-saver';
import type { UploadFile } from 'element-plus';
import { handleBlobError } from '@/utils/request';
import { toTree, disableParentTree, filterTreeNodeByName } from '@/utils/tree';
import {
  queryChannelList,
  downloadArticleImportTemplate,
  previewArticleImport,
  startArticleImport,
  queryArticleImportTask,
  stopArticleImportTask,
} from '@/api/content';

// 文章批量导入（docs/spec/article-management/plan.md D7，商业版 T1）。三步：选文件与选项 → 预检（只读）→ 执行（后台任务，
// 轮询进度）。服务端不在预检与执行之间保存文件：执行时把同一份文件再传一次，重新核对
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
const MAX_ISSUES = 200;

const { modelValue: visible } = toRefs(props);
const { t } = useI18n();
const step = ref<'select' | 'preview' | 'run'>('select');
const file = ref<File>();
const upload = ref<any>();
const defaultChannelId = ref<string>();
const skipDuplicates = ref<boolean>(true);
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
    const { blob, filename } = await downloadArticleImportTemplate();
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
    preview.value = await previewArticleImport(file.value, defaultChannelId.value, skipDuplicates.value);
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
    const { taskId } = await startArticleImport(file.value, defaultChannelId.value, skipDuplicates.value);
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
        <div class="mb-4 text-xs leading-5 text-gray-secondary">
          {{ $t('article.import.tip') }}
          <el-button type="primary" link size="small" @click="() => handleTemplate()">{{ $t('article.import.template') }}</el-button>
        </div>
        <el-form label-position="top" @submit.prevent>
          <el-form-item :label="$t('article.import.file')" required>
            <el-upload ref="upload" class="w-full" :auto-upload="false" :limit="1" accept=".xlsx" :on-change="handleFileChange" :on-remove="handleFileRemove">
              <el-button>{{ $t('article.import.selectFile') }}</el-button>
            </el-upload>
          </el-form-item>
          <el-form-item :label="$t('article.import.channel')">
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
          <el-form-item :label="$t('article.import.duplicates')">
            <el-radio-group v-model="skipDuplicates">
              <el-radio :value="true">{{ $t('article.import.duplicates.skip') }}</el-radio>
              <el-radio :value="false">{{ $t('article.import.duplicates.import') }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </template>

      <!-- 第二步：预检结果 -->
      <template v-else-if="step === 'preview' && preview != null">
        <div class="flex flex-wrap items-center gap-2 mb-3">
          <el-tag type="info" disable-transitions>{{ $t('article.import.result.total', { count: preview.total }) }}</el-tag>
          <el-tag type="success" disable-transitions>{{ $t('article.import.result.valid', { count: preview.valid }) }}</el-tag>
          <el-tag v-if="preview.skipped > 0" type="warning" disable-transitions>{{ $t('article.import.result.skipped', { count: preview.skipped }) }}</el-tag>
          <el-tag v-if="preview.invalid > 0" type="danger" disable-transitions>{{ $t('article.import.result.invalid', { count: preview.invalid }) }}</el-tag>
        </div>
        <el-alert
          v-if="preview.unknownHeaders?.length > 0"
          type="warning"
          :closable="false"
          show-icon
          class="mb-3"
          :title="$t('article.import.unknownHeaders', { headers: preview.unknownHeaders.join($t('punct.enum')) })"
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
        <div v-else-if="task.status === TASK_RUNNING" class="mb-3 text-sm">{{ $t('article.import.running', { current: task.current, total: task.total }) }}</div>
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
          :title="
            task.status === TASK_STOP
              ? $t('article.import.stopped', { created: task.report.created })
              : $t('article.import.done', { created: task.report.created, skipped: task.report.skipped, failed: task.report.failed })
          "
        />
      </template>

      <!-- 被跳过、有问题的行：预检与执行结果共用 -->
      <template v-if="step !== 'select' && issues.length > 0">
        <div class="mb-1 text-sm">
          {{ $t('article.import.issues') }}
          <span v-if="issueCount > issues.length" class="ml-2 text-xs text-gray-secondary">{{ $t('article.import.issues.more', { max: MAX_ISSUES }) }}</span>
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
          {{ $t('article.import.start', { count: preview?.valid ?? 0 }) }}
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
