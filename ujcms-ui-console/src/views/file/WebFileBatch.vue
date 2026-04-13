<script setup lang="ts">
import { ref, watch, computed, PropType, toRefs } from 'vue';
import { Folder } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';

defineOptions({
  name: 'WebFileBatch',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  type: { type: String as PropType<'copy' | 'move'>, required: true },
  batchIds: { type: Array as PropType<string[]>, required: true },
  batchNames: { type: Array as PropType<string[]>, required: true },
  currentParentId: { type: String, required: true },
  baseId: { type: String, required: true },
  moveWebFile: { type: Function as PropType<(dir: string, names: string[], destDir: string) => Promise<any>>, required: true },
  copyWebFile: { type: Function as PropType<(dir: string, names: string[], destDir: string) => Promise<any>>, required: true },
  queryWebFileList: { type: Function as PropType<(params?: Record<string, any>) => Promise<any>>, required: true },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { modelValue: visible, type, batchNames, currentParentId, baseId } = toRefs(props);
const { t } = useI18n();
const loading = ref<boolean>(false);
const data = ref<any[]>([]);
const parentId = ref<string>('/');
const parents = computed(() =>
  parentId.value
    .substring(baseId.value.length)
    .split('/')
    .filter((it: string) => it !== ''),
);

const fetchWebFileList = async () => {
  loading.value = true;
  try {
    data.value = await props.queryWebFileList({ parentId: parentId.value, isDir: true });
  } finally {
    loading.value = false;
  }
};

const changeParentId = (id: string) => {
  parentId.value = id;
  fetchWebFileList();
};

watch(visible, () => {
  if (visible.value) {
    parentId.value = currentParentId.value;
    fetchWebFileList();
  }
});
const handleSubmit = async () => {
  if (currentParentId.value === parentId.value) {
    ElMessageBox.alert(t('webFile.error.sameDir'));
    return;
  }
  if (type.value === 'copy') {
    await props.copyWebFile(currentParentId.value, batchNames.value, parentId.value);
  } else if (type.value === 'move') {
    await props.moveWebFile(currentParentId.value, batchNames.value, parentId.value);
  } else {
    throw new Error('not support type: ' + type.value);
  }
  emit('finished');
  emit('update:modelValue', false);
  ElMessage.success(t('success'));
};
</script>

<template>
  <el-dialog :title="$t(`webFile.op.${type}`)" :model-value="modelValue" top="5vh" :width="768" append-to-body @update:model-value="(e) => $emit('update:modelValue', e)">
    <div class="mt-2">
      <el-button type="primary" size="small" plain @click="() => changeParentId(baseId)">{{ baseId }}</el-button>
      <el-button v-for="(item, index) in parents" :key="item" type="primary" size="small" plain @click="() => changeParentId('/' + parents.slice(0, index + 1).join('/'))">
        {{ item }}
      </el-button>
    </div>
    <el-table ref="table" :data="data" :show-header="false" class="mt-2 border-t">
      <el-table-column :label="$t('webFile.name')">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" :disabled="batchIds.findIndex((it) => row.id.startsWith(it)) >= 0" @click="() => changeParentId(row.id)">
            <el-icon class="mr-1"><Folder class="text-warning" /></el-icon>
            <span>{{ row.name }}</span>
          </el-link>
        </template>
      </el-table-column>
    </el-table>
    <el-button class="mt-2" @click="() => handleSubmit()">{{ $t('submit') }}</el-button>
  </el-dialog>
</template>
