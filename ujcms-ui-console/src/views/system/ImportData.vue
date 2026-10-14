<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { storeToRefs } from 'pinia';
import { saveAs } from 'file-saver';
import type { UploadFile } from 'element-plus';
import { Delete, Download, QuestionFilled, Upload } from '@element-plus/icons-vue';
import LabelTip from '@/components/LabelTip.vue';
import { toTree, disableParentTree, filterTreeNodeByName } from '@/utils/tree';
import { handleBlobError } from '@/utils/request';
import { queryChannelList } from '@/api/content';
import {
  queryImportDataMeta,
  queryImportDataSummary,
  importDataTestConnection,
  previewImportData,
  importDataChannel,
  importDataArticle,
  queryImportDataTask,
  stopImportDataTask,
  importDataDeleteCorrespond,
  exportImportDataCorrespond,
} from '@/api/system';
import { useImportDataStore, useImportDataPasswordStore } from '@/stores/importDataStore';
import ImportDataColumnReference from './components/ImportDataColumnReference.vue';
import ImportDataRunPanel from './components/ImportDataRunPanel.vue';

// 数据迁移（docs/spec/data-migration/plan.md，商业版 T1）：源数据库 → 迁移栏目 → 迁移文章。连接测试、预览是同步的只读请求；
// 迁入是后台任务，这里轮询进度。一次只跑一个任务；刷新页面后从 summary 接着看正在跑的那个
defineOptions({
  name: 'ImportData',
});

type Kind = 'channel' | 'article';
// 任务状态：0 等待，1 运行中，2 出错，3 已停止，4 完成
const TASK_WAITING = 0;
const TASK_RUNNING = 1;
const TASK_STOP = 3;
const POLL_MILLIS = 1500;

const { t } = useI18n();
const store = useImportDataStore();
const { datasource, channel, article } = storeToRefs(store);
const passwordStore = useImportDataPasswordStore();

const datasourceForm = ref<any>();
const channelForm = ref<any>();
const articleForm = ref<any>();
const meta = ref<any>();
const summary = ref<any>();
const channelData = ref<any[]>([]);
const probe = ref<any>();
const testing = ref<boolean>(false);
const loading = reactive<Record<Kind, boolean>>({ channel: false, article: false });
const preview = reactive<Record<Kind, any>>({ channel: undefined, article: undefined });
// 本次会话里启动的任务，显示在各自的卡片里；刷新页面后从 summary 接着看的那个显示在顶部
const task = reactive<Record<Kind, any>>({ channel: undefined, article: undefined });
const resumed = ref<any>();
const stopping = ref<boolean>(false);
let timer: ReturnType<typeof setTimeout> | undefined;
// 轮询的批次：重新安排、停止轮询都换一个号，途中的那次查询回来时发现号变了，结果就作废
let generation = 0;

const isRunning = (item: any): boolean => item != null && [TASK_WAITING, TASK_RUNNING].includes(item.status);
const isStopping = (item: any): boolean => item != null && item.status === TASK_STOP && item.report == null && item.error == null;
const runningTask = computed<any>(() => [task.channel, task.article, resumed.value].find((item) => isRunning(item) || isStopping(item)));
const running = computed<boolean>(() => runningTask.value != null);
const currentType = computed<any>(() => meta.value?.types?.find((item: any) => item.code === datasource.value.type));
const columnsOf = (kind: Kind): { name: string; required: boolean }[] => meta.value?.columns?.[kind] ?? [];

const source = () => ({
  type: datasource.value.type,
  host: datasource.value.host,
  port: datasource.value.port || undefined,
  database: datasource.value.database,
  options: datasource.value.options,
  username: datasource.value.username,
  password: passwordStore.password,
});
const validate = (form: any): Promise<boolean> => new Promise((resolve) => form.validate((valid: boolean) => resolve(valid)));
const formOf = (kind: Kind) => (kind === 'channel' ? channelForm.value : articleForm.value);
const sqlOf = (kind: Kind): string => (kind === 'channel' ? channel.value.sql : article.value.sql);

const refreshSummary = async () => {
  summary.value = await queryImportDataSummary();
};

const stopPolling = () => {
  generation += 1;
  if (timer != null) {
    clearTimeout(timer);
    timer = undefined;
  }
};
const schedule = () => {
  stopPolling();
  const mine = generation;
  timer = setTimeout(() => poll(mine), POLL_MILLIS);
};
const assign = (next: any) => {
  if (task.channel?.id === next.id) {
    task.channel = next;
  } else if (task.article?.id === next.id) {
    task.article = next;
  } else {
    resumed.value = next;
  }
};
const poll = async (mine: number) => {
  timer = undefined;
  const current = runningTask.value;
  if (current == null) {
    return;
  }
  let next: any;
  try {
    next = await queryImportDataTask(current.id);
  } catch {
    // 拦截器已提示；不再轮询
    return;
  }
  if (mine !== generation) {
    return;
  }
  assign(next);
  if (isRunning(next) || isStopping(next)) {
    schedule();
  } else {
    stopping.value = false;
    refreshSummary();
  }
};

onMounted(async () => {
  meta.value = await queryImportDataMeta();
  if (!datasource.value.type && meta.value.types.length > 0) {
    datasource.value.type = meta.value.types[0].code;
  }
  channelData.value = disableParentTree(toTree(await queryChannelList({ isArticlePermission: true, isReal: true })));
  await refreshSummary();
  if (summary.value?.running != null) {
    resumed.value = summary.value.running;
    schedule();
  }
});
onBeforeUnmount(stopPolling);
// 换了数据库类型：上次的连接结果不再算数
watch(() => datasource.value.type, () => (probe.value = undefined));

const handleTestConnection = async () => {
  if (!(await validate(datasourceForm.value))) {
    return;
  }
  testing.value = true;
  probe.value = undefined;
  try {
    probe.value = await importDataTestConnection(source());
  } finally {
    testing.value = false;
  }
};
const handlePreview = async (kind: Kind) => {
  if (!(await validate(datasourceForm.value)) || !(await validate(formOf(kind)))) {
    return;
  }
  loading[kind] = true;
  try {
    preview[kind] = await previewImportData({ ...source(), kind, sql: sqlOf(kind) });
    task[kind] = undefined;
  } finally {
    loading[kind] = false;
  }
};
const handleStart = async (kind: Kind) => {
  if (running.value || !(await validate(datasourceForm.value)) || !(await validate(formOf(kind)))) {
    return;
  }
  loading[kind] = true;
  try {
    const { taskId } =
      kind === 'channel'
        ? await importDataChannel({ ...source(), sql: channel.value.sql })
        : await importDataArticle({ ...source(), sql: article.value.sql, defaultChannelId: article.value.defaultChannelId });
    task[kind] = { id: taskId, status: TASK_WAITING, current: 0, total: preview[kind]?.total ?? 0 };
    resumed.value = undefined;
    schedule();
  } finally {
    loading[kind] = false;
  }
};
const handleStop = async () => {
  const current = runningTask.value;
  if (current == null) {
    return;
  }
  // 途中的那次查询作废：它可能是停止之前的状态
  stopPolling();
  let next: any;
  try {
    next = await stopImportDataTask(current.id);
  } catch {
    // 拦截器已提示。任务还在跑：接着查
    schedule();
    return;
  }
  assign(next);
  if (isRunning(next) || isStopping(next)) {
    stopping.value = true;
    schedule();
  } else {
    stopping.value = false;
    refreshSummary();
  }
};
const handleFill = async (kind: Kind, sql: string) => {
  if (sqlOf(kind).trim() !== '') {
    try {
      await ElMessageBox.confirm(t('importData.op.fillSample.confirm'), { type: 'warning' });
    } catch {
      return;
    }
  }
  if (kind === 'channel') {
    channel.value.sql = sql;
  } else {
    article.value.sql = sql;
  }
};
const handleDeleteCorrespond = async () => {
  await importDataDeleteCorrespond();
  await refreshSummary();
  ElMessage.success(t('success'));
};
const handleExportCorrespond = async () => {
  try {
    const { blob, filename } = await exportImportDataCorrespond();
    saveAs(blob, filename);
  } catch (e) {
    await handleBlobError(e);
  }
};
// 迁移方案：连接信息（不含口令）与两条 SQL，便于在测试环境调好后拿到正式环境用
const handleExportPlan = () => {
  const plan = {
    version: 1,
    datasource: { ...datasource.value },
    channel: { sql: channel.value.sql },
    article: { sql: article.value.sql, defaultChannelId: article.value.defaultChannelId },
  };
  saveAs(new Blob([JSON.stringify(plan, null, 2)], { type: 'application/json;charset=utf-8' }), 'ujcms-migration-plan.json');
};
const handleImportPlan = async (file: UploadFile) => {
  try {
    const plan = JSON.parse(await file.raw!.text());
    if (plan?.version !== 1 || typeof plan.datasource !== 'object') {
      throw new Error('invalid plan');
    }
    const { type, host, port, database, options, username } = plan.datasource;
    datasource.value = { type, host, port, database, options, username };
    channel.value = { sql: String(plan.channel?.sql ?? '') };
    article.value = { sql: String(plan.article?.sql ?? ''), defaultChannelId: plan.article?.defaultChannelId };
    preview.channel = undefined;
    preview.article = undefined;
    probe.value = undefined;
    ElMessage.success(t('importData.plan.imported'));
  } catch {
    ElMessage.error(t('importData.plan.invalid'));
  }
};
</script>

<template>
  <div>
    <div class="flex flex-wrap items-center gap-2">
      <el-tag v-if="summary != null" type="info" disable-transitions>{{ $t('importData.summary', { channels: summary.channels, articles: summary.articles }) }}</el-tag>
      <el-button :icon="Download" :disabled="summary == null || summary.channels + summary.articles === 0" @click="() => handleExportCorrespond()">
        {{ $t('importData.op.exportCorrespond') }}
      </el-button>
      <el-popconfirm :title="$t('importData.op.deleteCorrespond.confirm')" width="360" @confirm="() => handleDeleteCorrespond()">
        <template #reference>
          <el-button :icon="Delete" :disabled="running || summary == null || summary.channels + summary.articles === 0">{{ $t('importData.op.deleteCorrespond') }}</el-button>
        </template>
      </el-popconfirm>
      <el-tooltip :content="$t('importData.op.deleteCorrespond.tooltip')" placement="top">
        <el-icon class="text-base align-middle"><QuestionFilled /></el-icon>
      </el-tooltip>
      <span class="flex-1"></span>
      <el-button :icon="Download" link @click="() => handleExportPlan()">{{ $t('importData.op.exportPlan') }}</el-button>
      <el-upload :auto-upload="false" :show-file-list="false" accept=".json" :on-change="handleImportPlan">
        <el-button :icon="Upload" link>{{ $t('importData.op.importPlan') }}</el-button>
      </el-upload>
    </div>
    <el-alert type="info" :closable="false" show-icon class="mt-3" :title="$t('importData.switchReminder')" />
    <!-- 刷新页面后接着看的任务 -->
    <div v-if="resumed != null" class="p-3 mt-3 app-block">
      <div class="text-gray-primary">{{ $t('importData.run.inProgress') }}</div>
      <import-data-run-panel :kind="resumed.report?.kind ?? ''" :task="resumed" :stopping="stopping" @stop="() => handleStop()" />
    </div>

    <div class="p-3 mt-3 app-block">
      <div class="mb-3 text-gray-primary">{{ $t('importData.datasource') }}</div>
      <el-form ref="datasourceForm" :model="datasource" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item prop="type" :label="$t('importData.datasource.type')" :rules="{ required: true, message: () => $t('v.required') }">
              <el-select v-model="datasource.type" class="w-full">
                <el-option v-for="item in meta?.types ?? []" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item prop="host" :label="$t('importData.datasource.host')" :rules="{ required: true, message: () => $t('v.required') }">
              <el-input v-model="datasource.host" placeholder="127.0.0.1" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="port" :label="$t('importData.datasource.port')">
              <el-input-number v-model="datasource.port" :min="1" :max="65535" :controls="false" :placeholder="String(currentType?.defaultPort ?? '')" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item
              prop="database"
              :label="currentType?.databaseKind === 'schema' ? $t('importData.datasource.schema') : $t('importData.datasource.database')"
              :rules="{ required: currentType?.databaseKind !== 'schema', message: () => $t('v.required') }"
            >
              <el-input v-model="datasource.database" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item prop="options">
              <template #label><label-tip message="importData.datasource.options" help /></template>
              <el-input v-model="datasource.options" :placeholder="currentType?.optionsExample ?? ''" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item prop="username" :label="$t('importData.datasource.username')" :rules="{ required: true, message: () => $t('v.required') }">
              <el-input v-model="datasource.username" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item prop="password" :label="$t('importData.datasource.password')">
              <el-input v-model="passwordStore.password" show-password />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item>
              <el-button type="primary" :loading="testing" @click.prevent="() => handleTestConnection()">{{ $t('importData.op.connectionTest') }}</el-button>
              <span v-if="probe != null" class="ml-3 text-sm text-success">{{ $t('importData.connected', { product: probe.product, version: probe.version }) }}</span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <div class="p-3 mt-3 app-block">
      <div class="mb-3 text-gray-primary">{{ $t('importData.channel') }}</div>
      <import-data-column-reference kind="channel" :columns="columnsOf('channel')" class="mb-3" @fill="(sql: string) => handleFill('channel', sql)" />
      <el-form ref="channelForm" :model="channel" label-position="top">
        <el-form-item prop="sql" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="importData.channel.sql" help /></template>
          <el-input v-model="channel.sql" :autosize="{ minRows: 5, maxRows: 20 }" type="textarea" class="font-mono" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading.channel" @click.prevent="() => handlePreview('channel')">{{ $t('importData.op.preview') }}</el-button>
          <el-button type="primary" :loading="loading.channel" :disabled="running" @click.prevent="() => handleStart('channel')">{{ $t('importData.op.start') }}</el-button>
        </el-form-item>
      </el-form>
      <import-data-run-panel kind="channel" :preview="preview.channel" :task="task.channel" :stopping="stopping && task.channel?.id === runningTask?.id" @stop="() => handleStop()" />
    </div>

    <div class="p-3 mt-3 app-block">
      <div class="mb-3 text-gray-primary">{{ $t('importData.article') }}</div>
      <import-data-column-reference kind="article" :columns="columnsOf('article')" class="mb-3" @fill="(sql: string) => handleFill('article', sql)" />
      <el-form ref="articleForm" :model="article" label-position="top">
        <el-form-item prop="sql" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="importData.article.sql" help /></template>
          <el-input v-model="article.sql" :autosize="{ minRows: 5, maxRows: 20 }" type="textarea" class="font-mono" />
        </el-form-item>
        <el-form-item prop="defaultChannelId">
          <template #label><label-tip message="importData.article.defaultChannel" help /></template>
          <el-tree-select
            v-model="article.defaultChannelId"
            :data="channelData"
            node-key="id"
            :props="{ label: 'name', disabled: 'disabled' }"
            :render-after-expand="false"
            :filter-node-method="filterTreeNodeByName"
            filterable
            clearable
            default-expand-all
            class="w-full md:w-1/2"
          />
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading.article" @click.prevent="() => handlePreview('article')">{{ $t('importData.op.preview') }}</el-button>
          <el-button type="primary" :loading="loading.article" :disabled="running" @click.prevent="() => handleStart('article')">{{ $t('importData.op.start') }}</el-button>
        </el-form-item>
      </el-form>
      <import-data-run-panel kind="article" :preview="preview.article" :task="task.article" :stopping="stopping && task.article?.id === runningTask?.id" @stop="() => handleStop()" />
    </div>
  </div>
</template>
