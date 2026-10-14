<script setup lang="ts">
import { markRaw, toRefs, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { queryProcessDefinitionList, queryProcessDefinitionXml, deleteProcessDefinition } from '@/api/system';
import BpmnViewer from 'bpmn-js/lib/Viewer';

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanKey: { type: String, default: null },
});
defineEmits({ 'update:modelValue': null });
const { beanKey } = toRefs(props);

const { t } = useI18n();
const table = ref<any>();
const data = ref<any[]>([]);
const loading = ref<boolean>(false);
const viewVisible = ref<boolean>(false);
const viewId = ref<string>();
const bpmnContainer = ref<any>();
let bpmnViewer: any;
const fetchData = async () => {
  loading.value = true;
  try {
    if (beanKey?.value) {
      data.value = await queryProcessDefinitionList({ key: beanKey.value });
    }
  } finally {
    loading.value = false;
  }
};
const handlerOpen = async () => {
  fetchData();
};

const handleView = (id: string) => {
  viewId.value = id;
  viewVisible.value = true;
};

const handleBpmnPreview = async () => {
  if (!viewId.value) {
    return;
  }
  const xml = await queryProcessDefinitionXml(viewId.value);
  if (!bpmnViewer) {
    bpmnViewer = markRaw(new BpmnViewer({ container: bpmnContainer.value, height: 350 }));
  }
  await bpmnViewer.importXML(xml);
  bpmnViewer.get('canvas').zoom('fit-viewport');
  viewVisible.value = true;
};

const handleDelete = async (ids: string[]) => {
  await deleteProcessDefinition(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <el-dialog
    :title="$t('processModel.op.definition')"
    width="98%"
    :model-value="modelValue"
    @opened="() => handlerOpen()"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <el-table ref="table" v-loading="loading" :data="data">
      <el-table-column prop="id" label="ID" show-overflow-tooltip />
      <el-table-column prop="key" :label="$t('processModel.key')" />
      <el-table-column prop="name" :label="$t('processModel.name')" />
      <el-table-column prop="version" :label="$t('processModel.version')" width="100" />
      <el-table-column :label="$t('table.action')" width="160">
        <template #default="{ row }">
          <el-button type="primary" :disabled="perm('processDefinition:xml')" size="small" link @click="() => handleView(row.id)">{{ $t('view') }}</el-button>
          <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.deploymentId])">
            <template #reference>
              <el-button type="primary" :disabled="perm('processDefinition:delete')" size="small" link>{{ $t('delete') }}</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="viewVisible" :title="$t('view')" width="98%" @opened="() => handleBpmnPreview()">
      <div ref="bpmnContainer" class="w-full h-full"></div>
    </el-dialog>
  </el-dialog>
</template>
