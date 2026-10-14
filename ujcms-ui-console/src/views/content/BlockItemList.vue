<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import { Plus, Delete, Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { toParams, resetParams } from '@/utils/common';
import { useRouteState, stringParam } from '@/composables/useRouteState';
import { queryBlockList, updateBlockOrder, deleteBlock, blockScopeNotAllowed } from '@/api/config';
import { deleteBlockItem, queryBlockItemList, updateBlockItem, updateBlockItemOrder } from '@/api/content';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import AsideEntityList from '@/components/AsideEntityList.vue';
import type { AsideEntityMoved } from '@/components/AsideEntityList.vue';
import EntityHeaderCard from '@/components/EntityHeaderCard.vue';
import BlockItemForm from './BlockItemForm.vue';
import BlockForm from './BlockForm.vue';

// 区块（docs/spec/console-master-detail）：左侧既是区块导航也是区块管理——搜索、按共享范围分组、组内拖拽排序、新增；
// 当前区块的元数据与区块级操作（编辑/删除）在主区顶栏；区块项在主区表格。
defineOptions({
  name: 'BlockItemList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
// 区块按共享范围分两组，各自独立排序：全局区块的顺序为各站共用、改动需全局数据权限，本站区块的顺序只属本站。
// 拖拽只提交所在组的 ID（混在一起提交，无全局数据权限者连本站区块也排不了序）。两个 ref 而非 computed：vuedraggable 就地改写 :list
const globalBlocks = ref<any[]>([]);
const siteBlocks = ref<any[]>([]);
const blocksLoading = ref<boolean>(true);
const blockList = computed(() => [...globalBlocks.value, ...siteBlocks.value]);
// 导航上下文（选中的区块）存于路由 query：刷新、页签往返后恢复。默认值为列表首项（列表就绪后地址省略该键）；
// 恢复出的区块由 fetchBlockList 校验，不存在即回落首项
const { blockId } = useRouteState({
  blockId: stringParam(() => (blockList.value[0] != null ? String(blockList.value[0].id) : undefined)),
});
const blockIds = computed(() => blockList.value.map((item) => String(item.id)));
const blockFormVisible = ref<boolean>(false);
const blockBeanId = ref<string>();
const asideCollapsed = ref<boolean>(false);
const block = computed(() => blockList.value.find((item) => String(item.id) === blockId.value));
const blockGroups = computed(() => [
  { key: 2, caption: t('block.scope.2'), items: globalBlocks.value, sortable: currentUser.globalPermission && !perm('block:update') },
  { key: 0, caption: t('block.scope.0'), items: siteBlocks.value, sortable: !perm('block:update') },
]);
// 全局区块属全局数据：无全局数据权限时区块本身只读（服务端同规），不给出必然 403 的操作。
// 只读只及于区块：区块项是各站自己的数据，全局区块下的本站区块项照常增删改
const readonly = computed(() => block.value?.scope === 2 && !currentUser.globalPermission);
// 删除区块会级联其区块项，而全局区块的区块项分属各站：其它站点仍有区块项时不可删除（服务端同规）
const usedByOtherSites = ref<boolean>(false);
const previewSrcList = computed(() => data.value.map((it) => it.image));
const mobilePreviewSrcList = computed(() => data.value.map((it) => it.mobileImage));
const isSorted = ref<boolean>(false);

const fetchData = async () => {
  const id = blockId.value;
  if (id == null) {
    data.value = [];
    return;
  }
  loading.value = true;
  try {
    const query = toParams(params.value);
    const list = await queryBlockItemList({ ...query, blockId: id, Q_OrderBy: sort.value });
    // 取数期间又切了区块：丢弃过期结果
    if (id !== blockId.value) {
      return;
    }
    data.value = list;
    isSorted.value = sort.value !== undefined;
    // 左侧区块项数随之更新（新增/删除区块项后无需重取区块列表）；带查询条件时列表只是子集，不可据此改数
    if (block.value != null && Object.values(query).every((value) => value === undefined || value === '')) {
      block.value.itemCount = list.length;
    }
  } finally {
    loading.value = false;
  }
};
const fetchUsedByOtherSites = async () => {
  const bean = block.value;
  usedByOtherSites.value = false;
  // 只有全局区块、且当前用户本可删除它时才值得一问
  if (bean == null || bean.scope !== 2 || readonly.value || perm('block:delete')) {
    return;
  }
  // “改为本站私有是否被其它站点的区块项阻止”与“能否删除”是同一个条件
  const used = await blockScopeNotAllowed(0, bean.id).catch(() => false);
  if (String(bean.id) === blockId.value) {
    usedByOtherSites.value = used === true;
  }
};
const fetchBlockList = async () => {
  try {
    const list: any[] = await queryBlockList();
    globalBlocks.value = list.filter((item) => item.scope === 2);
    siteBlocks.value = list.filter((item) => item.scope !== 2);
  } finally {
    blocksLoading.value = false;
  }
  // 恢复出的区块可能已删除或属于其它站点：不存在即回落首项
  if (!blockList.value.some((item) => String(item.id) === blockId.value)) {
    blockId.value = blockList.value.length > 0 ? String(blockList.value[0].id) : undefined;
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
      const fromId = data.value[oldIndex].id;
      const toId = data.value[newIndex].id;
      // 先改本地顺序再提交：提交失败时重新取数，Vue 才会把已被拖走的行搬回原位
      data.value.splice(newIndex, 0, data.value.splice(oldIndex, 1)[0]);
      try {
        await updateBlockItemOrder(fromId, toId);
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
  // 首次加载显式触发（不依赖对 blockId 的侦听：恢复出的区块恰为校验后的取值时，侦听不会触发）
  await fetchBlockList();
  fetchData();
  fetchUsedByOtherSites();
});
onBeforeUnmount(() => {
  sortable?.destroy();
});

const handleSort = ({ column, prop, order }: { column: any; prop: string | null; order: string | null }) => {
  if (prop && order) {
    sort.value = (column.sortBy ?? prop) + (order === 'descending' ? '_desc' : '');
  } else {
    sort.value = undefined;
  }
  fetchData();
};
const handleSearch = () => fetchData();
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  sort.value = undefined;
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
const handleUpdate = async (bean: any) => {
  await updateBlockItem(bean);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDelete = async (ids: string[]) => {
  await deleteBlockItem(ids);
  fetchData();
  ElMessage.success(t('success'));
};

// ----- 区块（左侧）-----
// 选中项由左侧列表经 v-model 写入 blockId，此处只处理切换之后的事：查询条件与排序属于上一个区块
const handleBlockSelected = () => {
  handleReset();
  fetchUsedByOtherSites();
};
const handleAddBlock = () => {
  blockBeanId.value = undefined;
  blockFormVisible.value = true;
};
const handleEditBlock = (id: string) => {
  blockBeanId.value = id;
  blockFormVisible.value = true;
};
const handleBlockFinished = async () => {
  await fetchBlockList();
  fetchData();
  fetchUsedByOtherSites();
};
// 只提交所拖动的那一组的 ID（见上：两组各自排序）；失败时重新取数，把已被拖走的行搬回原位
const handleBlockMoved = async (event: AsideEntityMoved) => {
  try {
    await updateBlockOrder(event.ids);
    ElMessage.success(t('success'));
  } catch {
    await fetchBlockList();
  }
};
const handleDeleteBlock = async () => {
  if (block.value == null) {
    return;
  }
  await deleteBlock([block.value.id]);
  ElMessage.success(t('success'));
  // 选中项已不存在：fetchBlockList 回落首项
  await handleBlockFinished();
};
</script>

<template>
  <el-container>
    <aside-entity-list
      v-model="blockId"
      v-model:collapsed="asideCollapsed"
      :groups="blockGroups"
      :search-keys="['name', 'alias', 'category']"
      count-key="itemCount"
      :search-placeholder="$t('block.filter')"
      :no-match-text="$t('block.noMatch')"
      :add-label="$t('block.op.create')"
      :add-disabled="perm('block:create')"
      :edit-label="$t('block.op.update')"
      :edit-disabled="perm('block:update')"
      :empty-text="$t('block.none')"
      :loading="blocksLoading"
      @select="handleBlockSelected"
      @add="handleAddBlock"
      @edit="(item: any) => handleEditBlock(String(item.id))"
      @moved="handleBlockMoved"
    >
      <template #suffix="{ item }">
        <span v-if="!item.enabled" class="ml-1 shrink-0 text-xs text-danger">{{ $t('block.disabled') }}</span>
      </template>
    </aside-entity-list>
    <el-main class="p-0">
      <!-- 当前区块顶栏：元数据（别名供模板 [@BlockItemList block=…] 取用；停用标记解释新增按钮为何禁用）与区块级操作就地可见 -->
      <entity-header-card
        v-if="block"
        :name="block.name"
        :alias="block.alias"
        :edit-label="$t('block.op.update')"
        :delete-label="$t('block.op.delete')"
        :edit-disabled="perm('block:update')"
        :delete-disabled="readonly || usedByOtherSites || perm('block:delete')"
        :delete-tip="usedByOtherSites ? $t('block.otherSites.deleteTip') : undefined"
        :confirm-text="block.itemCount > 0 ? $t('block.confirmDelete', { count: block.itemCount }) : $t('confirmDelete')"
        @edit="() => handleEditBlock(String(block.id))"
        @delete="() => handleDeleteBlock()"
      >
        <template #tags>
          <el-tag :type="block.scope === 2 ? 'success' : 'info'" size="small" disable-transitions>{{ $t(`block.scope.${block.scope}`) }}</el-tag>
          <el-tag v-if="block.category" type="info" size="small" disable-transitions>{{ block.category }}</el-tag>
          <el-tag v-if="!block.enabled" type="danger" size="small" disable-transitions>{{ $t('block.disabled') }}</el-tag>
          <el-tag v-if="readonly" type="info" size="small" disable-transitions>{{ $t('block.readonly') }}</el-tag>
        </template>
        <template #meta>
          <span v-if="block.blockItemModel">{{ $t('block.blockItemModel') }}{{ $t('punct.colon') }}{{ block.blockItemModel.name }}</span>
          <span v-if="block.title" class="truncate max-w-md" :title="block.title">{{ $t('block.title') }}{{ $t('punct.colon') }}{{ block.title }}</span>
        </template>
      </entity-header-card>
      <div class="mb-3">
        <query-form :params="params" @search="handleSearch" @reset="handleReset">
          <query-item :label="$t('blockItem.title')" name="Q_Contains_title"></query-item>
          <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
        </query-form>
      </div>
      <div>
        <el-button type="primary" :disabled="!block?.enabled || perm('blockItem:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm('blockItem:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <column-setting name="blockItem" class="ml-2" />
      </div>
      <div class="mt-3 app-block">
        <el-table
          ref="table"
          v-loading="loading"
          row-key="id"
          :data="data"
          @selection-change="(rows: any) => (selection = rows)"
          @row-dblclick="(row: any) => !perm('blockItem:update') && handleEdit(row.id)"
          @sort-change="handleSort"
        >
          <column-list name="blockItem">
            <el-table-column type="selection" width="38"></el-table-column>
            <el-table-column width="42">
              <el-icon
                class="text-lg align-middle"
                :class="isSorted || perm('blockItem:update') ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']"
              >
                <Grid />
              </el-icon>
            </el-table-column>
            <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
            <el-table-column property="title" :label="$t('blockItem.title')" sortable="custom" min-width="200" show-overflow-tooltip></el-table-column>
            <el-table-column property="subtitle" :label="$t('blockItem.subtitle')" sortable="custom" display="none" min-width="200" show-overflow-tooltip></el-table-column>
            <el-table-column property="image" :label="$t('blockItem.image')">
              <template #default="{ row, $index }">
                <el-image
                  v-if="!!row.image"
                  :src="row.image"
                  fit="contain"
                  :preview-src-list="previewSrcList"
                  :initial-index="$index"
                  preview-teleported
                  class="w-32 h-32"
                ></el-image>
              </template>
            </el-table-column>
            <el-table-column property="mobileImage" :label="$t('blockItem.mobileImage')" display="none">
              <template #default="{ row, $index }">
                <el-image
                  v-if="!!row.mobileImage"
                  :src="row.mobileImage"
                  fit="contain"
                  :preview-src-list="mobilePreviewSrcList"
                  :initial-index="$index"
                  preview-teleported
                  class="w-32 h-32"
                ></el-image>
              </template>
            </el-table-column>
            <el-table-column property="targetBlank" :label="$t('blockItem.targetBlank')" sortable="custom" width="120">
              <template #default="{ row }">
                <el-switch v-model="row.targetBlank" :disabled="perm('blockItem:update')" @change="(targetBlank: any) => handleUpdate({ ...row, targetBlank })" />
              </template>
            </el-table-column>
            <el-table-column property="enabled" :label="$t('enable')" sortable="custom" width="100">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" :disabled="perm('blockItem:update')" @change="(enabled: any) => handleUpdate({ ...row, enabled })" />
              </template>
            </el-table-column>
            <el-table-column :label="$t('table.action')">
              <template #default="{ row }">
                <el-button type="primary" :disabled="perm('blockItem:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="perm('blockItem:delete')" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </column-list>
        </el-table>
      </div>
      <block-item-form v-model="formVisible" :bean-id :bean-ids :block-id @finished="fetchData" />
      <block-form v-model="blockFormVisible" :bean-id="blockBeanId" :bean-ids="blockIds" @finished="handleBlockFinished" />
    </el-main>
  </el-container>
</template>

<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>
