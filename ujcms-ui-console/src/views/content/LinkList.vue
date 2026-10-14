<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Plus, Delete, Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { perm } from '@/stores/useCurrentUser';
import { localizedLabel } from '@/i18n';
import { toParams, resetParams } from '@/utils/common';
import { useRouteState, stringParam } from '@/composables/useRouteState';
import { deleteLink, queryLinkList, updateLinkOrder, queryLinkGroupList, updateLinkGroupOrder, deleteLinkGroup, queryLinkGroupSlots } from '@/api/content';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import AsideEntityList from '@/components/AsideEntityList.vue';
import type { AsideEntityMoved } from '@/components/AsideEntityList.vue';
import EntityHeaderCard from '@/components/EntityHeaderCard.vue';
import LinkForm from './LinkForm.vue';
import LinkGroupForm from './LinkGroupForm.vue';

// 链接（docs/spec/console-master-detail）：左侧既是链接组导航也是链接组管理——搜索、拖拽排序、新增；
// 当前链接组的元数据与组级操作（编辑/删除）在主区顶栏；链接在主区表格，只列当前组，拖拽排序即组内排序。
defineOptions({
  name: 'LinkList',
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
const isSorted = ref<boolean>(false);
// ref 而非 computed：vuedraggable 就地改写 :list
const groupList = ref<any[]>([]);
const groupsLoading = ref<boolean>(true);
// 导航上下文（选中的链接组）存于路由 query：刷新、页签往返后恢复。默认值为列表首项（列表就绪后地址省略该键）；
// 恢复出的链接组由 fetchGroupList 校验，不存在即回落首项
const { groupId } = useRouteState({
  groupId: stringParam(() => (groupList.value[0] != null ? String(groupList.value[0].id) : undefined)),
});
const groupIds = computed(() => groupList.value.map((item) => String(item.id)));
const groupFormVisible = ref<boolean>(false);
const groupBeanId = ref<string>();
const asideCollapsed = ref<boolean>(false);
const group = computed(() => groupList.value.find((item) => String(item.id) === groupId.value));
const groups = computed(() => [{ key: 'all', items: groupList.value, sortable: !perm('linkGroup:update') }]);
// 展示位词汇表（当前站点桌面∪手机主题的 linkSlots 声明）：顶栏据此把展示位标识显示为主题给出的名称
const slots = ref<any[]>([]);
const slotLabel = computed(() => {
  const name = group.value?.slot;
  if (!name) {
    return undefined;
  }
  const slot = slots.value.find((item) => item.name === name);
  return slot != null ? localizedLabel(slot.label, slot.name) : name;
});

const fetchData = async () => {
  const id = groupId.value;
  if (id == null) {
    data.value = [];
    return;
  }
  loading.value = true;
  try {
    const query = toParams(params.value);
    const list = await queryLinkList({ ...query, Q_EQ_groupId_Long: id, Q_OrderBy: sort.value });
    // 取数期间又切了链接组：丢弃过期结果
    if (id !== groupId.value) {
      return;
    }
    data.value = list;
    isSorted.value = sort.value !== undefined;
    // 左侧链接数随之更新（新增/删除链接后无需重取链接组列表）；带查询条件时列表只是子集，不可据此改数
    if (group.value != null && Object.values(query).every((value) => value === undefined || value === '')) {
      group.value.linkCount = list.length;
    }
  } finally {
    loading.value = false;
  }
};
const fetchGroupList = async () => {
  try {
    groupList.value = await queryLinkGroupList();
  } finally {
    groupsLoading.value = false;
  }
  if (!groupList.value.some((item) => String(item.id) === groupId.value)) {
    groupId.value = groupList.value.length > 0 ? String(groupList.value[0].id) : undefined;
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
        await updateLinkOrder(fromId, toId);
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
  queryLinkGroupSlots().then((list) => (slots.value = list));
  // 首次加载显式触发（不依赖对 groupId 的侦听：恢复出的链接组恰为校验后的取值时，侦听不会触发）
  await fetchGroupList();
  fetchData();
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
const handleDelete = async (ids: string[]) => {
  await deleteLink(ids);
  fetchData();
  ElMessage.success(t('success'));
};
// 链接可在表单里改投别的链接组：两边的链接数都会变，重取链接组列表最省事
const handleFinished = async () => {
  await fetchGroupList();
  fetchData();
};

// ----- 链接组（左侧）-----
// 选中项由左侧列表经 v-model 写入 groupId，此处只处理切换之后的事：查询条件与排序属于上一个链接组
const handleGroupSelected = () => handleReset();
const handleAddGroup = () => {
  groupBeanId.value = undefined;
  groupFormVisible.value = true;
};
const handleEditGroup = (id: string) => {
  groupBeanId.value = id;
  groupFormVisible.value = true;
};
const handleGroupFinished = async () => {
  await fetchGroupList();
  fetchData();
};
const handleGroupMoved = async (event: AsideEntityMoved) => {
  try {
    await updateLinkGroupOrder(event.fromId, event.toId);
    ElMessage.success(t('success'));
  } catch {
    await fetchGroupList();
  }
};
const handleDeleteGroup = async () => {
  if (group.value == null) {
    return;
  }
  await deleteLinkGroup([group.value.id]);
  ElMessage.success(t('success'));
  // 选中项已不存在：fetchGroupList 回落首项
  await handleGroupFinished();
};
</script>

<template>
  <el-container>
    <aside-entity-list
      v-model="groupId"
      v-model:collapsed="asideCollapsed"
      :groups="groups"
      count-key="linkCount"
      :search-placeholder="$t('linkGroup.filter')"
      :no-match-text="$t('linkGroup.noMatch')"
      :add-label="$t('linkGroup.op.create')"
      :add-disabled="perm('linkGroup:create')"
      :edit-label="$t('linkGroup.op.update')"
      :edit-disabled="perm('linkGroup:update')"
      :empty-text="$t('linkGroup.none')"
      :loading="groupsLoading"
      @select="handleGroupSelected"
      @add="handleAddGroup"
      @edit="(item: any) => handleEditGroup(String(item.id))"
      @moved="handleGroupMoved"
    />
    <el-main class="p-0">
      <!-- 当前链接组顶栏：元数据（别名供模板 [@LinkList groupAlias=…] 取用）与组级操作就地可见 -->
      <entity-header-card
        v-if="group"
        :name="group.name"
        :alias="group.alias"
        :edit-label="$t('linkGroup.op.update')"
        :delete-label="$t('linkGroup.op.delete')"
        :edit-disabled="perm('linkGroup:update')"
        :delete-disabled="perm('linkGroup:delete')"
        :confirm-text="group.linkCount > 0 ? $t('linkGroup.confirmDelete', { count: group.linkCount }) : $t('confirmDelete')"
        @edit="() => handleEditGroup(String(group.id))"
        @delete="() => handleDeleteGroup()"
      >
        <template #tags>
          <el-tag type="info" size="small" disable-transitions>
            {{ $t(group.type === 2 ? 'linkGroup.type.image' : 'linkGroup.type.text') }}<template v-if="group.type === 2"> {{ group.imageWidth }}×{{ group.imageHeight }}</template>
          </el-tag>
          <el-tag :type="slotLabel ? 'success' : 'info'" size="small" disable-transitions>{{ slotLabel ?? $t('linkGroup.slot.none') }}</el-tag>
        </template>
        <template #meta>
          <span v-if="group.description" class="truncate max-w-md" :title="group.description">{{ group.description }}</span>
        </template>
      </entity-header-card>
      <div class="mb-3">
        <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
          <query-item :label="$t('link.name')" name="Q_Contains_name"></query-item>
          <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
        </query-form>
      </div>
      <div class="space-x-2">
        <el-button type="primary" :disabled="group == null || perm('link:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm('link:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <column-setting name="link" />
      </div>
      <div class="mt-3 app-block">
        <el-table
          ref="table"
          v-loading="loading"
          row-key="id"
          :data="data"
          @selection-change="(rows: any) => (selection = rows)"
          @row-dblclick="(row: any) => !perm('link:update') && handleEdit(row.id)"
          @sort-change="handleSort"
        >
          <column-list name="link">
            <el-table-column type="selection" width="38"></el-table-column>
            <el-table-column width="42">
              <el-icon
                class="text-lg align-middle"
                :class="isSorted || perm('link:update') ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']"
              >
                <Grid />
              </el-icon>
            </el-table-column>
            <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
            <el-table-column property="name" :label="$t('link.name')" sortable="custom" :min-width="120" show-overflow-tooltip></el-table-column>
            <el-table-column property="url" :label="$t('link.url')" :min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.channel != null">{{ $t('link.refType.channel') }}: {{ row.channel.name }}</span>
                <span v-else-if="row.article != null">{{ $t('link.refType.article') }}: {{ row.article.title }}</span>
                <span v-else>{{ row.url }}</span>
              </template>
            </el-table-column>
            <el-table-column property="enabled" :label="$t('link.enabled')" sortable="custom" width="120">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ $t(row.enabled ? 'yes' : 'no') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('table.action')">
              <template #default="{ row }">
                <el-button type="primary" :disabled="perm('link:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="perm('link:delete')" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </column-list>
        </el-table>
      </div>
      <link-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :groups="groupList" :group-id="groupId" @finished="handleFinished" />
      <link-group-form v-model="groupFormVisible" :bean-id="groupBeanId" :bean-ids="groupIds" @finished="handleGroupFinished" />
    </el-main>
  </el-container>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>
