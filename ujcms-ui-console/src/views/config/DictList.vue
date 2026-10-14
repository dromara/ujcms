<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Plus, Delete, Grid, Lock, Search } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { useRouteState, stringParam } from '@/composables/useRouteState';
import { queryDictTypeList, updateDictTypeOrder, deleteDictType, queryDictTypeUsages, queryDictList, updateDict, deleteDict, updateDictOrder } from '@/api/config';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import AsideEntityList from '@/components/AsideEntityList.vue';
import type { AsideEntityMoved } from '@/components/AsideEntityList.vue';
import EntityHeaderCard from '@/components/EntityHeaderCard.vue';
import DictForm from './DictForm.vue';
import DictTypeForm from './DictTypeForm.vue';

// 字典（docs/spec/dictionary）：左侧既是类型导航也是类型管理——搜索、按共享范围分组、组内拖拽排序；
// 当前类型的元数据与类型级操作（编辑/删除）在主区顶栏；条目在主区表格，拖拽排序、行内启用开关。
defineOptions({
  name: 'DictList',
});
const { t } = useI18n();
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
// 条目过滤（工作状态，不入路由）：条目不分页、整表已在本地，就地过滤即可
const keyword = ref<string>('');
// 类型按共享范围分两组，各自独立排序：全局类型的顺序为各站共用、改动需全局数据权限，本站类型的顺序只属本站。
// 拖拽只提交所在组的 ID（混在一起提交，无全局数据权限者连本站类型也排不了序）。两个 ref 而非 computed：vuedraggable 就地改写 :list
const globalTypes = ref<any[]>([]);
const siteTypes = ref<any[]>([]);
const typeList = computed(() => [...globalTypes.value, ...siteTypes.value]);
// 导航上下文（选中的字典类型）存于路由 query：刷新、页签往返后恢复。默认值为列表首项（列表就绪后地址省略该键）；
// 恢复出的类型由 fetchTypeList 校验，不存在即回落首项
const { typeId } = useRouteState({
  typeId: stringParam(() => (typeList.value[0] != null ? String(typeList.value[0].id) : undefined)),
});
const typeIds = computed(() => typeList.value.map((item) => String(item.id)));
const typeFormVisible = ref<boolean>(false);
const typeBeanId = ref<string>();
const asideCollapsed = ref<boolean>(false);
const dictType = computed(() => typeList.value.find((item) => String(item.id) === typeId.value));
// 绑定当前类型的模型自定义字段：说明类型“被谁使用”，有使用即不可删除（服务端同规）
const usages = ref<any[]>([]);
// 全局类型及其条目属全局数据：无全局数据权限时只读（服务端同规），不给出必然 403 的操作
const readonly = computed(() => dictType.value?.scope === 2 && !currentUser.globalPermission);
// 系统条目（sys）不可删（服务端同规）
const deletable = (bean: any) => !bean.sys && !readonly.value;

const typeGroups = computed(() => [
  { key: 2, caption: t('dictType.scope.2'), items: globalTypes.value, sortable: currentUser.globalPermission && !perm('dictType:update') },
  { key: 0, caption: t('dictType.scope.0'), items: siteTypes.value, sortable: !perm('dictType:update') },
]);

const filtering = computed(() => keyword.value.trim() !== '');
// 未过滤时须返回 data 本身：拖拽以 DOM 行号定位，行号与 data 下标一致才成立
const rows = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (text === '') {
    return data.value;
  }
  return data.value.filter((row) => String(row.name).toLowerCase().includes(text) || String(row.value).toLowerCase().includes(text));
});
const beanIds = computed(() => rows.value.map((row) => row.id));
const existingValues = computed(() => data.value.map((row) => String(row.value)));
const rowSortable = computed(() => !filtering.value && !readonly.value && !perm('dict:update'));
// 表格为空的两种原因：过滤词没有匹配（有条目），或该类型尚无条目
const noMatch = computed(() => filtering.value && data.value.length > 0);

const fetchData = async () => {
  const id = typeId.value;
  if (id == null) {
    data.value = [];
    return;
  }
  loading.value = true;
  try {
    const list = await queryDictList({ typeId: id });
    // 取数期间又切了类型：丢弃过期结果
    if (id !== typeId.value) {
      return;
    }
    data.value = list;
    // 左侧条目数随之更新（新增/删除条目后无需重取类型列表）
    if (dictType.value != null) {
      dictType.value.entryCount = list.length;
    }
  } finally {
    loading.value = false;
  }
};
const fetchUsages = async () => {
  const id = typeId.value;
  usages.value = [];
  if (id == null) {
    return;
  }
  const list = await queryDictTypeUsages(id, true).catch(() => []);
  if (id === typeId.value) {
    usages.value = list;
  }
};
const fetchTypeList = async () => {
  const list: any[] = await queryDictTypeList();
  globalTypes.value = list.filter((item) => item.scope === 2);
  siteTypes.value = list.filter((item) => item.scope !== 2);
  if (!typeList.value.some((item) => String(item.id) === typeId.value)) {
    typeId.value = typeList.value.length > 0 ? String(typeList.value[0].id) : undefined;
  }
};

let sortable: any;
const initDragTable = () => {
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
      // 先改本地顺序再提交：提交失败时重新取数，Vue 才会把已被拖走的行搬回原位。
      // order 一并重排——行内启用开关回传整行，不得带着旧 order
      data.value.splice(newIndex, 0, data.value.splice(oldIndex, 1)[0]);
      data.value.forEach((row, index) => (row.order = index + 1));
      try {
        await updateDictOrder(data.value.map((row) => row.id));
        ElMessage.success(t('success'));
      } catch {
        await fetchData();
      }
    },
  });
};
onMounted(async () => {
  // 拖拽初始化须在任何 await 之前：它查询本视图的 DOM
  initDragTable();
  // 首次加载显式触发（不依赖对 typeId 的侦听：恢复出的类型恰为校验后的取值时，侦听不会触发）
  await fetchTypeList();
  fetchData();
  fetchUsages();
});
onBeforeUnmount(() => {
  sortable?.destroy();
});

const handleAdd = () => {
  beanId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteDict(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleEnabled = async (row: any, enabled: boolean) => {
  try {
    await updateDict({ ...row, enabled });
    row.enabled = enabled;
    ElMessage.success(t('success'));
  } catch {
    await fetchData();
  }
};

// ----- 类型（左侧）-----
// 选中项由左侧列表经 v-model 写入 typeId，此处只处理切换之后的事
const handleTypeSelected = () => {
  // 过滤词属于上一个类型：带过去只会得到一张原因不明的空表
  keyword.value = '';
  fetchData();
  fetchUsages();
};
const handleAddType = () => {
  typeBeanId.value = undefined;
  typeFormVisible.value = true;
};
const handleEditType = (id: string) => {
  typeBeanId.value = id;
  typeFormVisible.value = true;
};
const handleTypeFinished = async () => {
  await fetchTypeList();
  fetchData();
  fetchUsages();
};
// 只提交所拖动的那一组的 ID（见上：两组各自排序）；失败时重新取数，把已被拖走的行搬回原位
const handleTypeMoved = async (event: AsideEntityMoved) => {
  try {
    await updateDictTypeOrder(event.ids);
    ElMessage.success(t('success'));
  } catch {
    await fetchTypeList();
  }
};
const handleDeleteType = async () => {
  if (dictType.value == null) {
    return;
  }
  await deleteDictType([dictType.value.id]);
  ElMessage.success(t('success'));
  // 选中项已不存在：fetchTypeList 回落首项
  await handleTypeFinished();
};
</script>

<template>
  <el-container>
    <aside-entity-list
      v-model="typeId"
      v-model:collapsed="asideCollapsed"
      :groups="typeGroups"
      count-key="entryCount"
      :search-placeholder="$t('dictType.filter')"
      :no-match-text="$t('dictType.noMatch')"
      :add-label="$t('dictType.op.create')"
      :add-disabled="perm('dictType:create')"
      :edit-label="$t('dictType.op.update')"
      :edit-disabled="perm('dictType:update')"
      @select="handleTypeSelected"
      @add="handleAddType"
      @edit="(item: any) => handleEditType(String(item.id))"
      @moved="handleTypeMoved"
    >
      <template #suffix="{ item }">
        <el-icon v-if="item.sys" class="ml-1 shrink-0 text-gray-placeholder"><Lock /></el-icon>
      </template>
    </aside-entity-list>
    <el-main class="p-0">
      <!-- 当前类型顶栏：元数据（别名供模板 [@DictList type=…] 取用）与类型级操作就地可见 -->
      <entity-header-card
        v-if="dictType"
        :name="dictType.name"
        :alias="dictType.alias"
        :edit-label="$t('dictType.op.update')"
        :delete-label="$t('dictType.op.delete')"
        :edit-disabled="perm('dictType:update')"
        :delete-disabled="dictType.sys || readonly || usages.length > 0 || perm('dictType:delete')"
        :delete-tip="usages.length > 0 ? $t('dictType.usages.deleteTip') : undefined"
        :confirm-text="data.length > 0 ? $t('dictType.confirmDelete', { count: data.length }) : $t('confirmDelete')"
        @edit="() => handleEditType(String(dictType.id))"
        @delete="() => handleDeleteType()"
      >
        <template #tags>
          <el-tag :type="dictType.scope === 2 ? 'success' : 'info'" size="small" disable-transitions>{{ $t(`dictType.scope.${dictType.scope}`) }}</el-tag>
          <el-tag v-if="dictType.sys" type="warning" size="small" disable-transitions>{{ $t('dictType.sys') }}</el-tag>
          <el-tag v-if="readonly" type="info" size="small" disable-transitions>{{ $t('dictType.readonly') }}</el-tag>
        </template>
        <template #meta>
          <span>{{ $t(`dictType.dataType.${dictType.dataType}`) }}</span>
          <el-tooltip v-if="usages.length > 0" placement="bottom">
            <template #content>
              <div v-for="(item, index) in usages" :key="index">{{ item.modelName }} - {{ item.fieldName }}</div>
            </template>
            <span class="cursor-default underline decoration-dotted underline-offset-2">{{ $t('dictType.usages', { count: usages.length }) }}</span>
          </el-tooltip>
          <span v-if="dictType.remark" class="truncate max-w-md" :title="dictType.remark">{{ dictType.remark }}</span>
        </template>
      </entity-header-card>
      <div class="flex items-center">
        <el-button type="primary" :disabled="dictType == null || readonly || perm('dict:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || readonly || perm('dict:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <el-input v-model="keyword" :placeholder="$t('dict.filter')" :suffix-icon="Search" clearable class="ml-auto w-60" />
        <column-setting name="dict" class="ml-2" />
      </div>
      <div class="mt-3 app-block">
        <el-table
          ref="table"
          v-loading="loading"
          row-key="id"
          :data="rows"
          @selection-change="(list: any) => (selection = list)"
          @row-dblclick="(row: any) => !perm('dict:update') && handleEdit(row.id)"
        >
          <column-list name="dict">
            <el-table-column type="selection" :selectable="deletable" width="38"></el-table-column>
            <el-table-column width="42">
              <el-icon class="text-lg align-middle" :class="rowSortable ? ['cursor-move', 'text-gray-secondary', 'drag-handle'] : ['cursor-not-allowed', 'text-gray-disabled']">
                <Grid />
              </el-icon>
            </el-table-column>
            <el-table-column property="id" label="ID" width="200" display="none"></el-table-column>
            <el-table-column property="name" :label="$t('dict.name')" :min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.name }}</span>
                <el-tag v-if="row.sys" type="warning" size="small" class="ml-2" disable-transitions>{{ $t('dict.sysTag') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column property="value" :label="$t('dict.value')" :min-width="140" show-overflow-tooltip></el-table-column>
            <el-table-column property="remark" :label="$t('dict.remark')" :min-width="200" show-overflow-tooltip></el-table-column>
            <el-table-column property="enabled" :label="$t('dict.enabled')" width="100">
              <template #default="{ row }">
                <el-switch :model-value="row.enabled" :disabled="row.sys || readonly || perm('dict:update')" @change="(value: any) => handleEnabled(row, value === true)" />
              </template>
            </el-table-column>
            <el-table-column :label="$t('table.action')" width="140">
              <template #default="{ row }">
                <el-button type="primary" :disabled="perm('dict:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t(readonly ? 'detail' : 'edit') }}</el-button>
                <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="!deletable(row) || perm('dict:delete')" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </column-list>
          <!-- 空状态放在表格的 empty 插槽：用 v-if 换掉整张表会销毁拖拽所绑定的 tbody。只给说明文字，不重复工具栏近在眼前的“新增” -->
          <template #empty>
            <span v-if="!loading && dictType">{{ $t(noMatch ? 'dict.noMatch' : 'dict.empty') }}</span>
          </template>
        </el-table>
      </div>
      <dict-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :type="dictType" :existing-values="existingValues" :readonly="readonly" @finished="fetchData" />
      <dict-type-form v-model="typeFormVisible" :bean-id="typeBeanId" :bean-ids="typeIds" @finished="handleTypeFinished" />
    </el-main>
  </el-container>
</template>

<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>
