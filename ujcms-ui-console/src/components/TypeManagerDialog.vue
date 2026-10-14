<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { Plus, Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { perm } from '@/stores/useCurrentUser';

// 类型管理对话框（docs/spec/console-master-detail R3）：只有“名称 + 描述”的分类词表（留言类型、信箱类型）就地维护——
// 数据屏工具栏上的“类型管理”按钮打开本对话框，不另占一个菜单项，也不占用左侧栏：这类数据按状态跨类型处理，类型只是一个属性。
// 手动排序即数据，故无查询、无列排序；类型表单经 form 插槽由宿主给出，渲染为本对话框的同级节点（两个对话框各自叠放）。
defineOptions({ name: 'TypeManagerDialog' });

const props = defineProps<{
  title: string;
  // 权限前缀，如 'mailboxType' → mailboxType:create / :update / :delete
  perms: string;
  nameLabel: string;
  descriptionLabel: string;
  queryList: () => Promise<any[]>;
  moveOrder: (fromId: string, toId: string) => Promise<any>;
  deleteBeans: (ids: string[]) => Promise<any>;
}>();
const visible = defineModel<boolean>({ required: true });
// 关闭后通知宿主：类型可能已增删改、换序，宿主据此重取自己的类型列表与数据
const emit = defineEmits<{ closed: [] }>();
const { t } = useI18n();

const table = ref<any>();
const data = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
const sortDisabled = computed(() => perm(`${props.perms}:update`));

const fetchData = async () => {
  loading.value = true;
  try {
    data.value = await props.queryList();
  } finally {
    loading.value = false;
  }
};

let sortable: any;
// el-dialog 首次打开才渲染内容，挂载时表格尚不存在：拖拽在 opened 里初始化一次（不可配 destroy-on-close，否则 tbody 每次重建）
const initDragTable = () => {
  if (sortable != null) {
    return;
  }
  const tbody = table.value?.$el.querySelector('.el-table__body-wrapper tbody');
  if (tbody == null) {
    return;
  }
  sortable = Sortable.create(tbody, {
    handle: '.drag-handle',
    animation: 200,
    chosenClass: 'sortable-chosen',
    onEnd: async (event: any) => {
      const { oldIndex, newIndex } = event;
      if (oldIndex === newIndex) {
        return;
      }
      const fromId = data.value[oldIndex].id;
      const toId = data.value[newIndex].id;
      // 先改本地顺序再提交：提交失败时重新取数，Vue 才会把已被拖走的行搬回原位
      data.value.splice(newIndex, 0, data.value.splice(oldIndex, 1)[0]);
      try {
        await props.moveOrder(fromId, toId);
        ElMessage.success(t('success'));
      } catch {
        await fetchData();
      }
    },
  });
};
onBeforeUnmount(() => {
  sortable?.destroy();
});

const handleOpened = () => {
  initDragTable();
  fetchData();
};
const handleAdd = () => {
  beanId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await props.deleteBeans(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <el-dialog v-model="visible" :title="title" width="720px" append-to-body @opened="handleOpened" @closed="() => emit('closed')">
      <div>
        <el-button type="primary" :disabled="perm(`${perms}:create`)" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      </div>
      <el-table ref="table" v-loading="loading" row-key="id" :data="data" class="mt-3" @row-dblclick="(row: any) => !perm(`${perms}:update`) && handleEdit(row.id)">
        <el-table-column width="42">
          <el-icon class="text-lg align-middle" :class="sortDisabled ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']">
            <Grid />
          </el-icon>
        </el-table-column>
        <el-table-column property="name" :label="nameLabel" :min-width="140" show-overflow-tooltip></el-table-column>
        <el-table-column property="description" :label="descriptionLabel" :min-width="240" show-overflow-tooltip></el-table-column>
        <el-table-column :label="$t('table.action')" width="120">
          <template #default="{ row }">
            <el-button type="primary" :disabled="perm(`${perms}:update`)" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
            <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
              <template #reference>
                <el-button type="primary" :disabled="perm(`${perms}:delete`)" size="small" link>{{ $t('delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <slot name="form" :visible="formVisible" :set-visible="(value: boolean) => (formVisible = value)" :bean-id="beanId" :bean-ids="beanIds" :finished="fetchData"></slot>
  </div>
</template>

<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>
