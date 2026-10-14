<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete, Search } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { moveListByScope } from '@/utils/common';
import { deleteModel, queryModelList, queryModelUsages, updateModelOrder } from '@/api/config';
import { useRouteState, enumParam } from '@/composables/useRouteState';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import ListMove from '@/components/ListMove.vue';
import ModelForm from './ModelForm.vue';
import ModelSystemFields from './ModelSystemFields.vue';
import ModelCustomFields from './ModelCustomFields.vue';
import { isReadonlyModel, isSingletonType, isSystemModel } from './components/modelRules';
import type { ModelUsage } from './components/modelRules';

// 模型管理（docs/spec/model-management/plan.md）：类型页签 + 一张表；每行三个编辑器（基本信息 / 系统字段 / 自定义字段）
// 各写各的一段。列表附带“使用情况”：被谁绑定决定了能否删除、能否改为本站私有。
defineOptions({
  name: 'ModelList',
});
const { t } = useI18n();
// topic（2026-09-15 起）与 form 为 T2 模型类型：低于 T2 不显示页签（地址里手写的也不接受）
const modelTypes = (['article', 'channel', 'topic', 'blockItem', 'form', 'user', 'site', 'global'] as const).filter((n) => currentUser.commercialTier >= 2 || !['topic', 'form'].includes(n));
// 导航上下文（模型类型页签）存于路由 query：刷新、页签往返后恢复
const { type: modelType } = useRouteState({
  type: enumParam('article', modelTypes),
});
const table = ref<any>();
const data = ref<any[]>([]);
// 使用统计按模型 ID 索引（字符串：ID 为雪花数，JSON 里是字符串）
const usages = ref<Record<string, ModelUsage>>({});
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const formAction = ref<'edit' | 'copy'>('edit');
const systemFieldsVisible = ref<boolean>(false);
const customFieldsVisible = ref<boolean>(false);
const beanId = ref<string>();
// 名称过滤（工作状态，不入路由）：每种类型只有几个模型，整表已在本地，就地过滤即可
const keyword = ref<string>('');
const filtering = computed(() => keyword.value.trim() !== '');
const rows = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return text === '' ? data.value : data.value.filter((row) => String(row.name).toLowerCase().includes(text));
});
const beanIds = computed(() => rows.value.map((row) => row.id));
const existingNames = computed(() => data.value.map((row) => String(row.name)));
const noMatch = computed(() => filtering.value && data.value.length > 0);
// 全局设置模型、用户模型各只有一个系统行，各处按固定 ID 取用：不提供新增与排序
const singleton = computed(() => isSingletonType(modelType.value));

const usageOf = (row: any): ModelUsage => usages.value[String(row.id)] ?? { modelId: row.id, count: 0, external: 0 };
const deleteTip = (row: any): string | undefined => {
  if (isSystemModel(row)) return t('model.sys.deleteTip');
  if (usageOf(row).count > 0) return t('model.usage.deleteTip');
  return undefined;
};
const deletable = (row: any) => !isReadonlyModel(row) && deleteTip(row) == null;
const selectable = (row: any) => !isReadonlyModel(row);
const batchDeletable = computed(() => selection.value.length > 0 && selection.value.every(deletable));
const fieldNames = (row: any): string => (row.fieldList ?? []).map((field: any) => field.name).join(t('punct.enum'));

const fetchUsages = async () => {
  const type = modelType.value;
  const list: any[] = await queryModelUsages(type, true).catch(() => []);
  // 取数期间又切了页签：丢弃过期结果
  if (type === modelType.value) {
    usages.value = Object.fromEntries(list.map((item) => [String(item.modelId), item]));
  }
};
const fetchData = async () => {
  const type = modelType.value;
  loading.value = true;
  try {
    const [list] = await Promise.all([queryModelList({ type }), fetchUsages()]);
    if (type !== modelType.value) {
      return;
    }
    data.value = list;
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);
const handleTabChange = () => {
  keyword.value = '';
  fetchData();
};

const handleSystemFields = (id: string) => {
  beanId.value = id;
  systemFieldsVisible.value = true;
};
const handleCustomFields = (id: string) => {
  beanId.value = id;
  customFieldsVisible.value = true;
};
const handleAdd = () => {
  formAction.value = 'edit';
  beanId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  formAction.value = 'edit';
  beanId.value = id;
  formVisible.value = true;
};
// 复制：以源模型的字段集为初值新建（DialogForm 的 copy 模式）——系统字段开关与自定义字段不必从头再设
const handleCopy = (id: string) => {
  formAction.value = 'copy';
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteModel(ids);
  fetchData();
  ElMessage.success(t('success'));
};
// 全局模型的顺序各站共用：无全局数据权限不可调整（服务端同规），只移动选中行里可调整的那部分
const movable = computed(() => selection.value.filter((row) => !isReadonlyModel(row)));
// 全局模型与本站模型各自成组排序、逐组提交（整表提交时，无全局数据权限者连本站模型也排不了序）。
// 本地已先行重排，提交失败则重取，表格回到服务端的顺序
const move = async (selected: any[], type: 'top' | 'up' | 'down' | 'bottom') => {
  try {
    for (const group of moveListByScope(selected, data.value, type)) {
      await updateModelOrder(group.map((item) => item.id));
    }
  } catch (e) {
    await fetchData();
    throw e;
  }
};
</script>

<template>
  <div>
    <div class="flex items-center">
      <el-tooltip :content="$t('model.singletonTip')" :disabled="!singleton" placement="top">
        <span class="inline-flex">
          <el-button type="primary" :disabled="singleton || perm('model:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
        </span>
      </el-tooltip>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="!batchDeletable || perm('model:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <list-move v-if="!singleton" :disabled="movable.length <= 0 || filtering || perm('model:update')" class="ml-2" @move="(type) => move(movable, type)" />
      <el-input v-model="keyword" :placeholder="$t('model.filter')" :suffix-icon="Search" clearable class="ml-auto w-60" />
      <column-setting name="model" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-tabs v-model="modelType" class="block-tabs" @tab-change="handleTabChange">
        <el-tab-pane v-for="n in modelTypes" :key="n" :name="n" :label="$t(`model.type.${n}`)"></el-tab-pane>
      </el-tabs>
      <el-table
        ref="table"
        v-loading="loading"
        row-key="id"
        :data="rows"
        @selection-change="(list: any) => (selection = list)"
        @row-dblclick="(row: any) => !perm('model:update') && handleEdit(row.id)"
      >
        <column-list name="model">
          <el-table-column type="selection" :selectable="selectable" width="45"></el-table-column>
          <el-table-column property="id" label="ID" width="200" display="none"></el-table-column>
          <el-table-column property="name" :label="$t('model.name')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.name }}</span>
              <el-tag v-if="isSystemModel(row)" type="warning" size="small" class="ml-2" disable-transitions>{{ $t('model.sysTag') }}</el-tag>
              <el-tooltip v-if="isReadonlyModel(row)" :content="$t('model.readonly')" placement="top">
                <el-tag type="info" size="small" class="ml-2" disable-transitions>{{ $t('model.readonlyTag') }}</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column property="scope" :label="$t('model.scope')" width="130">
            <template #default="{ row }">
              <el-tag :type="row.scope === 2 ? 'success' : 'info'" size="small" disable-transitions>{{ $t(`model.scope.${row.scope}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('model.fun.customFields')" width="120">
            <template #default="{ row }">
              <el-tooltip v-if="(row.fieldList ?? []).length > 0" :content="fieldNames(row)" placement="top">
                <span class="cursor-default underline decoration-dotted underline-offset-2">{{ row.fieldList.length }}</span>
              </el-tooltip>
              <span v-else class="text-gray-placeholder">0</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('model.usage')" min-width="200">
            <template #default="{ row }">
              <template v-if="singleton"><span class="text-gray-placeholder">-</span></template>
              <template v-else-if="usageOf(row).count > 0">
                <span>{{ $t(`model.usage.${modelType}`, { count: usageOf(row).count }) }}</span>
                <span v-if="usageOf(row).external > 0" class="ml-2 text-xs text-gray-secondary">{{ $t('model.usage.external', { count: usageOf(row).external }) }}</span>
              </template>
              <span v-else class="text-gray-placeholder">{{ $t('model.usage.none') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="320">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('model:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t(isReadonlyModel(row) ? 'detail' : 'edit') }}</el-button>
              <el-button
                v-if="!['form', 'global', 'site'].includes(row.type)"
                type="primary"
                :disabled="perm('model:update')"
                size="small"
                link
                @click="() => handleSystemFields(row.id)"
              >
                {{ $t('model.fun.systemFields') }}
              </el-button>
              <el-button type="primary" :disabled="perm('model:update')" size="small" link @click="() => handleCustomFields(row.id)">
                {{ $t('model.fun.customFields') }}
              </el-button>
              <el-button v-if="!singleton" type="primary" :disabled="perm('model:create')" size="small" link @click="() => handleCopy(row.id)">{{ $t('copy') }}</el-button>
              <el-tooltip :content="deleteTip(row)" :disabled="deleteTip(row) == null" placement="top">
                <span class="inline-flex">
                  <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                    <template #reference>
                      <el-button type="primary" :disabled="!deletable(row) || perm('model:delete')" size="small" link>{{ $t('delete') }}</el-button>
                    </template>
                  </el-popconfirm>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
        </column-list>
        <!-- 空状态放在表格的 empty 插槽；只给说明文字，不重复工具栏近在眼前的“新增” -->
        <template #empty>
          <span v-if="!loading">{{ $t(noMatch ? 'model.noMatch' : 'model.empty') }}</span>
        </template>
      </el-table>
    </div>
    <model-form
      v-model="formVisible"
      :bean-id="beanId"
      :bean-ids="beanIds"
      :model-type="modelType"
      :action="formAction"
      :addable="!singleton"
      :existing-names="existingNames"
      :usages="usages"
      @finished="fetchData"
    />
    <model-system-fields v-model="systemFieldsVisible" :bean-id="beanId" @finished="fetchData" />
    <model-custom-fields v-model="customFieldsVisible" :bean-id="beanId" @finished="fetchData" />
  </div>
</template>
