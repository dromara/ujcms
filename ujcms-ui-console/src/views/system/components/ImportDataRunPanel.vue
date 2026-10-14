<script setup lang="ts">
import { computed } from 'vue';

// 数据迁移的预览与执行结果（docs/spec/data-migration/plan.md）：预览列出认出的列、缺的列、前几行；
// 执行时显示进度与结果，有问题的行列成表。按钮（预览、开始）在页面上，这里只有“停止”
defineOptions({
  name: 'ImportDataRunPanel',
});
const props = defineProps({
  kind: { type: String, required: true },
  preview: { type: Object, default: undefined },
  task: { type: Object, default: undefined },
  // 已要求停止、结果还没写出来
  stopping: { type: Boolean, default: false },
});
const emit = defineEmits({ stop: null });

// 任务状态：0 等待，1 运行中，2 出错，3 已停止，4 完成
const TASK_WAITING = 0;
const TASK_RUNNING = 1;
const TASK_ERROR = 2;
const TASK_STOP = 3;
const MAX_ISSUES = 200;

const running = computed<boolean>(() => props.task != null && [TASK_WAITING, TASK_RUNNING].includes(props.task.status));
const percentage = computed<number>(() => {
  if (props.task == null) {
    return 0;
  }
  if (!props.task.total) {
    // 没有总数：进行中的显示 0，结束的算走完了
    return running.value || props.stopping ? 0 : 100;
  }
  return Math.min(100, Math.round((props.task.current / props.task.total) * 100));
});
const issues = computed<any[]>(() => props.task?.report?.issues ?? []);
const previewRecognised = computed<number>(() => (props.preview?.columns ?? []).filter((column: any) => column.mapped).length);
const previewLabels = computed<string[]>(() => (props.preview?.columns ?? []).map((column: any) => column.label));
</script>

<template>
  <div class="mt-3">
    <!-- 执行 -->
    <template v-if="task != null">
      <el-progress
        :percentage="percentage"
        :status="task.status === TASK_ERROR ? 'exception' : task.status === TASK_STOP ? 'warning' : running ? undefined : 'success'"
        class="mb-2"
      />
      <div class="flex items-center gap-3 mb-2 text-sm">
        <span v-if="task.status === TASK_WAITING">{{ $t('importData.run.waiting') }}</span>
        <span v-else-if="stopping">{{ $t('importData.run.stopping') }}</span>
        <span v-else-if="task.status === TASK_RUNNING">
          {{ task.total ? $t('importData.run.running', { current: task.current, total: task.total }) : $t('importData.run.runningNoTotal', { current: task.current }) }}
        </span>
        <el-button v-if="running || stopping" size="small" :loading="stopping" @click="() => emit('stop')">{{ $t('importData.op.stop') }}</el-button>
      </div>
      <el-alert v-if="task.status === TASK_ERROR" type="error" :closable="false" show-icon class="mb-2" :title="$t('importData.run.error', { message: task.error ?? '' })" />
      <el-alert
        v-else-if="task.report != null"
        :type="task.status === TASK_STOP ? 'warning' : task.report.failed > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
        class="mb-2"
        :title="
          task.status === TASK_STOP
            ? $t('importData.run.stopped', { created: task.report.created })
            : $t('importData.run.done', { created: task.report.created, skipped: task.report.skipped, failed: task.report.failed })
        "
      />
      <el-alert v-if="task.report != null && kind === 'article' && !task.report.deduplicated" type="warning" :closable="false" show-icon class="mb-2" :title="$t('importData.run.noId')" />
      <template v-if="issues.length > 0">
        <div class="mb-1 text-sm">
          {{ $t('importData.run.issues') }}
          <span v-if="task.report.failed > issues.length" class="ml-2 text-xs text-gray-secondary">{{ $t('importData.run.issues.more', { max: MAX_ISSUES }) }}</span>
        </div>
        <el-table :data="issues" size="small" max-height="320" border>
          <el-table-column property="row" :label="$t('importData.run.row')" width="70" />
          <el-table-column property="title" :label="$t('importData.run.key')" min-width="180" show-overflow-tooltip />
          <el-table-column property="reason" :label="$t('importData.run.reason')" min-width="260" show-overflow-tooltip />
        </el-table>
      </template>
    </template>

    <!-- 预览 -->
    <template v-else-if="preview != null">
      <div class="flex flex-wrap items-center gap-2 mb-2">
        <el-tag type="success" disable-transitions>{{ $t('importData.preview.recognised', { count: previewRecognised }) }}</el-tag>
        <el-tag v-if="preview.total != null" type="info" disable-transitions>{{ $t('importData.preview.total', { total: preview.total }) }}</el-tag>
        <el-tag v-else type="info" disable-transitions>{{ $t('importData.preview.totalUnknown') }}</el-tag>
      </div>
      <el-alert v-if="preview.missingRequired?.length > 0" type="error" :closable="false" show-icon class="mb-2" :title="$t('importData.preview.missing', { columns: preview.missingRequired.join($t('punct.enum')) })" />
      <el-alert v-if="preview.unknown?.length > 0" type="warning" :closable="false" show-icon class="mb-2" :title="$t('importData.preview.unknown', { columns: preview.unknown.join($t('punct.enum')) })" />
      <div class="mb-1 text-sm">{{ preview.rows.length > 0 ? $t('importData.preview.rows', { count: preview.rows.length }) : $t('importData.preview.empty') }}</div>
      <el-table v-if="preview.rows.length > 0" :data="preview.rows" size="small" max-height="360" border>
        <el-table-column v-for="(label, index) in previewLabels" :key="label" :property="label" min-width="140" show-overflow-tooltip>
          <template #header>
            <span :class="preview.columns[index].mapped ? '' : 'text-gray-secondary line-through'">{{ label }}</span>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>
