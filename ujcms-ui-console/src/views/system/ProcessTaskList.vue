<script setup lang="ts">
import { toRefs, watch, ref, markRaw } from 'vue';
import dayjs from 'dayjs';
import duration from 'dayjs/plugin/duration';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import BpmnViewer from 'bpmn-js/lib/Viewer';
import { queryProcessTaskList } from '@/api/system';
import defaultBpmnXml from '@/components/bpmnjs/custom-translate/defaultBpmnXml';

const visible = defineModel<boolean>();
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  instanceId: { type: String, default: null },
});
const { instanceId } = toRefs(props);

dayjs.extend(duration);
dayjs.extend(relativeTime);

const taskData = ref<any[]>([]);
const processData = ref<any[]>([]);
const loading = ref<boolean>(false);

const bpmnContainer = ref<any>();
let bpmnViewer: any;

const fetchData = async () => {
  loading.value = true;
  try {
    if (!instanceId?.value) {
      return;
    }
    const { xml, activities, tasks, processes } = await queryProcessTaskList(instanceId.value);
    taskData.value = tasks ?? [];
    processData.value = processes ?? [];
    if (!bpmnViewer) {
      bpmnViewer = markRaw(new BpmnViewer({ container: bpmnContainer.value, height: 200 }));
    }
    await bpmnViewer.importXML(xml ?? defaultBpmnXml('none', 'none', 'none'));
    const canvas = bpmnViewer.get('canvas');
    canvas.zoom('fit-viewport');
    activities?.forEach(({ activityId, endTime, deleteReason }: any) => {
      let color = 'highlight-curr';
      if (deleteReason && (deleteReason.includes(':reject') || deleteReason.includes(':cancel'))) {
        color = 'highlight-reject';
      } else if (endTime) {
        color = 'highlight';
      }
      canvas.addMarker(activityId, color);
    });
  } finally {
    loading.value = false;
  }
};
watch(visible, () => {
  if (visible.value) {
    fetchData();
  } else {
    bpmnViewer.clear();
  }
});
</script>

<template>
  <el-dialog v-model="visible" :title="$t('processInstance.op.task')" width="98%" top="5vh">
    <div ref="bpmnContainer" class="w-full"></div>
    <table v-loading="loading" class="w-full m-1 border-collapse table-auto">
      <thead>
        <tr>
          <td class="p-2 font-bold border-b text-secondary">{{ $t('processModel.name') }}</td>
          <td class="p-2 font-bold border-b text-secondary">{{ $t('processTask.assignee') }}</td>
          <td class="p-2 font-bold border-b text-secondary">{{ $t('processTask.created') }}</td>
          <!-- <td class="p-2 font-bold border-b text-secondary">{{ $t('processTask.endDate') }}</td> -->
          <td class="p-2 font-bold border-b text-secondary">{{ $t('processTask.duration') }}</td>
          <td class="p-2 font-bold border-b text-secondary">{{ $t('processTask.time') }}</td>
          <td class="p-2 font-bold border-b text-secondary">{{ $t('processTask.user') }}</td>
          <td class="p-2 font-bold border-b text-secondary">{{ $t('processTask.type') }}</td>
          <td class="p-2 font-bold border-b text-secondary">{{ $t('processTask.opinion') }}</td>
        </tr>
      </thead>
      <tbody>
        <template v-for="task in taskData" :key="task.id">
          <tr class="hover:bg-gray-50">
            <td class="p-2 border-b" :rowspan="task.commentList.length > 0 ? task.commentList.length : 1">{{ task.name }}</td>
            <td class="p-2 border-b" :rowspan="task.commentList.length > 0 ? task.commentList.length : 1">{{ task.assigneeName }}</td>
            <td class="p-2 border-b" :rowspan="task.commentList.length > 0 ? task.commentList.length : 1">
              {{ task.created ? dayjs(task.created).format('YYYY-MM-DD HH:mm') : undefined }}
            </td>
            <!--
            <td class="p-2 border-b" :rowspan="task.commentList.length > 0 ? task.commentList.length : 1">
              {{ task.endDate ? dayjs(task.endDate).format('YYYY-MM-DD HH:mm') : undefined }}
            </td>
             -->
            <td class="p-2 border-b" :rowspan="task.commentList.length > 0 ? task.commentList.length : 1">
              {{ task.duration ? dayjs.duration(Number(task.duration)).locale('zh-cn').humanize() : undefined }}
            </td>
            <td class="p-2 border-b">{{ task.commentList[0] ? dayjs(task.commentList[0].time).format('YYYY-MM-DD HH:mm') : undefined }}</td>
            <td class="p-2 border-b">{{ task.commentList[0]?.userName }}</td>
            <td class="p-2 border-b">
              <el-tag v-if="task.commentList[0]?.type === 'pass'" type="success" size="small">{{ $t(`processTask.type.${task.commentList[0].type}`) }}</el-tag>
              <el-tag v-else-if="task.commentList[0]?.type === 'reject'" type="danger" size="small">{{ $t(`processTask.type.${task.commentList[0].type}`) }}</el-tag>
              <el-tag v-else-if="task.commentList[0]" size="small">{{ $t(`processTask.type.${task.commentList[0].type}`) }}</el-tag>
            </td>
            <td class="p-2 border-b">{{ task.commentList[0]?.fullMessage }}</td>
          </tr>
          <template v-for="(comment, index) in task.commentList" :key="comment.id">
            <tr v-if="index > 0" class="hover:bg-gray-50">
              <td class="p-2 border-b">{{ dayjs(comment.time).format('YYYY-MM-DD HH:mm') }}</td>
              <td class="p-2 border-b">{{ comment.userName }}</td>
              <td class="p-2 border-b">
                <el-tag v-if="comment.type === 'pass'" type="success" size="small">{{ $t(`processTask.type.${comment.type}`) }}</el-tag>
                <el-tag v-else-if="comment.type === 'reject'" type="danger" size="small">{{ $t(`processTask.type.${comment.type}`) }}</el-tag>
                <el-tag v-else size="small">{{ $t(`processTask.type.${comment.type}`) }}</el-tag>
              </td>
              <td class="p-2 border-b">{{ comment.fullMessage }}</td>
            </tr>
          </template>
        </template>
      </tbody>
    </table>
    <p class="mt-4 text-lg">{{ $t('processInstance.history') }}</p>
    <el-table v-loading="loading" :data="processData" class="mt-1">
      <!-- <el-table-column prop="id" label="ID" show-overflow-tooltip /> -->
      <el-table-column prop="assignee" :label="$t('processInstance.assignee')" />
      <el-table-column prop="status" :label="$t('processInstance.status')">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'complete'" type="success" size="small">{{ $t(`processInstance.status.${row.status}`) }}</el-tag>
          <el-tag v-else-if="row.status === 'reject'" type="danger" size="small">{{ $t(`processInstance.status.${row.status}`) }}</el-tag>
          <el-tag v-else-if="row.status === 'cancel'" type="info" size="small">{{ $t(`processInstance.status.${row.status}`) }}</el-tag>
          <el-tag v-else type="warning" size="small">{{ $t(`processInstance.status.${row.status}`) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="opinion" :label="$t('processInstance.opinion')" />
      <el-table-column property="started" :label="$t('processInstance.started')">
        <template #default="{ row }">{{ dayjs(row.started).format('YYYY-MM-DD HH:mm') }}</template>
      </el-table-column>
      <el-table-column property="ended" :label="$t('processInstance.ended')">
        <template #default="{ row }">{{ row.ended ? dayjs(row.ended).format('YYYY-MM-DD HH:mm') : undefined }}</template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<style lang="scss" scoped>
:deep(.highlight:not(.djs-connection) .djs-visual > :nth-child(1)) {
  fill: #b3e19d !important;
}
:deep(.highlight-curr:not(.djs-connection) .djs-visual > :nth-child(1)) {
  fill: #a0cfff !important;
}
:deep(.highlight-reject:not(.djs-connection) .djs-visual > :nth-child(1)) {
  fill: #fab6b6 !important;
}
</style>
