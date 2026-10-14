<script setup lang="ts">
import { computed, defineAsyncComponent, watch, onMounted, onBeforeUnmount, ref } from 'vue';
import { Plus, Delete, Grid, Finished, DocumentRemove } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { useRouteState, stringParam, numberParam } from '@/composables/useRouteState';
import { queryFormTypeList, updateFormTypeOrder, deleteFormType, queryModelList, queryDictList } from '@/api/config';
import { queryProcessDefinitionList } from '@/api/system';
import { deleteForm, submitForm, completelyDeleteForm, updateFormOrder, queryFormPage } from '@/api/content';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import AsideEntityList from '@/components/AsideEntityList.vue';
import type { AsideEntityMoved } from '@/components/AsideEntityList.vue';
import EntityHeaderCard from '@/components/EntityHeaderCard.vue';
import FormForm from './FormForm.vue';
import FormTypeForm from './FormTypeForm.vue';

// 流程任务对话框依赖 bpmn-js Viewer（约 200 kB）；异步加载，以免随列表页一并下载。对话框本身仍常驻挂载，行为不变。
const ProcessTaskList = defineAsyncComponent(() => import('@/views/system/ProcessTaskList.vue'));

// 表单（docs/spec/console-master-detail）：左侧既是表单类型导航也是类型管理——搜索、拖拽排序、新增，“全部”为跨类型视图；
// 选中某个类型时，其元数据与类型级操作（编辑/删除）在主区顶栏。表单审核页的左侧仍是纯导航。
defineOptions({
  name: 'FormList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
const isSorted = ref<boolean>(false);

const modelList = ref<any[]>([]);
// ref 而非 computed：vuedraggable 就地改写 :list
const formTypeList = ref<any[]>([]);
const typesLoading = ref<boolean>(true);
// 导航上下文（表单类型、状态行）存于路由 query：刷新、页签往返后恢复；status 亦供首页待办等深链（?status=22）。
// typeId 为 '-1' 即全部类型
const { typeId: formTypeId, status } = useRouteState({
  typeId: stringParam('-1'),
  status: numberParam(-1, [0, 10, 11, 12, 20, 22]),
});
// 类型与模型由 ID 派生（而非在侦听里赋值）：恢复出的类型不触发侦听，列表就绪后照样得到类型与模型
const formType = computed<any>(() => formTypeList.value.find((item) => item.id == formTypeId.value) ?? {});
const model = computed<any>(() => modelList.value.find((item) => item.id == formType.value.modelId) ?? {});
const fields = computed(() => JSON.parse(model.value.customs || '[]'));
const dictListMap = ref<Record<string, any[]>>({});
const asideCollapsed = ref<boolean>(false);
const formTypeGroups = computed(() => [{ key: 'all', items: formTypeList.value, sortable: !perm('formType:update') }]);
const formTypeIds = computed(() => formTypeList.value.map((item) => String(item.id)));
const typeFormVisible = ref<boolean>(false);
const typeBeanId = ref<string>();
// 审核流程的名称：顶栏据此把流程标识显示为流程名。无权取流程定义时退回显示标识（视图不得调用其路由权限包未保证的接口）
const processList = ref<any[]>([]);
const processName = computed(() => {
  const key = formType.value.processKey;
  return key ? (processList.value.find((item) => item.key === key)?.name ?? key) : undefined;
});

const fetchModelList = async () => {
  modelList.value = await queryModelList({ type: 'form' });
};
const fetchFormTypeList = async () => {
  try {
    formTypeList.value = await queryFormTypeList();
  } finally {
    typesLoading.value = false;
  }
};
const fetchProcessList = async () => {
  if (perm('processDefinition:list')) {
    return;
  }
  processList.value = await queryProcessDefinitionList({ category: 'sys_form', latestVersion: true }).catch(() => []);
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
    onEnd: async function (event: any) {
      const { oldIndex, newIndex } = event;
      if (oldIndex !== newIndex) {
        await updateFormOrder(data.value[oldIndex].id, data.value[newIndex].id);
        data.value.splice(newIndex, 0, data.value.splice(oldIndex, 1)[0]);
        ElMessage.success(t('success'));
      }
    },
  });
};

// 请求序号：快速连切类型 / 状态时，先发后至的响应不得覆盖后发先至的结果
let fetchSeq = 0;
const fetchData = async () => {
  const seq = ++fetchSeq;
  const query = toParams(params.value);
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryFormPage({
      ...query,
      Q_EQ_status_Short: status.value !== -1 ? status.value : undefined,
      Q_EQ_typeId_Long: formTypeId.value !== '-1' ? formTypeId.value : undefined,
      Q_OrderBy: sort.value,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    if (seq !== fetchSeq) {
      return;
    }
    isSorted.value = sort.value !== undefined;
    data.value = content;
    total.value = Number(totalElements);
    // 左侧表单数随之更新（新增/彻底删除后无需重取类型列表）。只在总数确为该类型的全部表单时才可据此改数：
    // 选中了具体类型、状态为“全部”、且不带查询条件
    if (formType.value.id != null && status.value === -1 && Object.values(query).every((value) => value === undefined || value === '')) {
      formType.value.formCount = total.value;
    }
  } finally {
    if (seq === fetchSeq) {
      loading.value = false;
    }
  }
};

// 切换类型：各类型的自定义字段不同，查询条件随之重置
watch(formTypeId, () => handleReset());
watch(fields, async () => {
  dictListMap.value = {};
  fields.value
    .filter((field: any) => ['radio', 'select', 'checkbox', 'multipleSelect'].indexOf(field.type) >= 0)
    .map((field: any) => field.dictTypeId)
    .forEach(async (typeId: string) => {
      dictListMap.value[typeId] = (await queryDictList({ typeId })).map((item: any) => ({ label: item.name, value: item.value }));
    });
});

onMounted(async () => {
  // 拖拽初始化须在任何 await 之前：它查询本视图的 DOM
  initDragTable();
  const restored = formTypeId.value !== '-1';
  // 有恢复出的类型：先加载类型列表完成校验，再查列表
  fetchProcessList();
  await Promise.all([fetchFormTypeList(), fetchModelList(), restored ? undefined : fetchData()]);
  if (restored) {
    if (formTypeList.value.some((item) => String(item.id) === formTypeId.value)) {
      fetchData();
    } else {
      // 类型已删除或属于其它站点：回落到全部类型（侦听随之重置查询并加载列表）
      formTypeId.value = '-1';
    }
  }
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
const handleSubmit = async (ids: string[]) => {
  await submitForm(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDelete = async (ids: string[]) => {
  await deleteForm(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleCompletelyDelete = async (ids: string[]) => {
  await completelyDeleteForm(ids);
  fetchData();
  ElMessage.success(t('success'));
};

// ----- 表单类型（左侧）-----
// 选中项由左侧列表经 v-model 写入 formTypeId；切换之后的事由上面对 formTypeId 的侦听处理（重置查询并取数）
const handleAddType = () => {
  typeBeanId.value = undefined;
  typeFormVisible.value = true;
};
const handleEditType = (id: string) => {
  typeBeanId.value = id;
  typeFormVisible.value = true;
};
// 类型改绑了模型，列与查询项随之变化：模型列表一并重取
const handleTypeFinished = async () => {
  await Promise.all([fetchFormTypeList(), fetchModelList()]);
  fetchData();
};
const handleTypeMoved = async (event: AsideEntityMoved) => {
  try {
    await updateFormTypeOrder(event.fromId, event.toId);
    ElMessage.success(t('success'));
  } catch {
    await fetchFormTypeList();
  }
};
const handleDeleteType = async () => {
  if (formType.value.id == null) {
    return;
  }
  await deleteFormType([formType.value.id]);
  ElMessage.success(t('success'));
  await fetchFormTypeList();
  // 选中项已不存在：回到“全部”（侦听随之重置查询并取数）
  formTypeId.value = '-1';
};

const instanceId = ref<string>();
const taskListVisible = ref<boolean>(false);
const handleTask = (id: string) => {
  instanceId.value = id;
  taskListVisible.value = true;
};
</script>

<template>
  <el-container>
    <aside-entity-list
      v-model="formTypeId"
      v-model:collapsed="asideCollapsed"
      :groups="formTypeGroups"
      :search-keys="['name']"
      count-key="formCount"
      :all-option="{ value: '-1', label: $t('all') }"
      :search-placeholder="$t('formType.filter')"
      :no-match-text="$t('formType.noMatch')"
      :add-label="$t('formType.op.create')"
      :add-disabled="perm('formType:create')"
      :edit-label="$t('formType.op.update')"
      :edit-disabled="perm('formType:update')"
      :empty-text="$t('formType.none')"
      :loading="typesLoading"
      @add="handleAddType"
      @edit="(item: any) => handleEditType(String(item.id))"
      @moved="handleTypeMoved"
    >
      <template #suffix="{ item }">
        <span v-if="!item.enabled" class="ml-1 shrink-0 text-xs text-danger">{{ $t('formType.disabled') }}</span>
      </template>
    </aside-entity-list>
    <el-main class="p-0">
      <div>
        <!-- 当前表单类型顶栏（选中“全部”时没有）：元数据与类型级操作就地可见。类型下还有表单即不可删除——表单是访客提交的数据，不随类型级联 -->
        <entity-header-card
          v-if="formType.id != null"
          :name="formType.name"
          :edit-label="$t('formType.op.update')"
          :delete-label="$t('formType.op.delete')"
          :edit-disabled="perm('formType:update')"
          :delete-disabled="formType.formCount > 0 || perm('formType:delete')"
          :delete-tip="formType.formCount > 0 ? $t('formType.hasForms.deleteTip') : undefined"
          :confirm-text="$t('confirmDelete')"
          @edit="() => handleEditType(String(formType.id))"
          @delete="() => handleDeleteType()"
        >
          <template #tags>
            <el-tag type="info" size="small" disable-transitions>{{ $t(`formType.mode.${formType.mode}`) }}</el-tag>
            <el-tag v-if="formType.viewable" type="success" size="small" disable-transitions>{{ $t('formType.viewable') }}</el-tag>
            <el-tag v-if="!formType.enabled" type="danger" size="small" disable-transitions>{{ $t('formType.disabled') }}</el-tag>
          </template>
          <template #meta>
            <span v-if="model.name">{{ $t('formType.model') }}{{ $t('punct.colon') }}{{ model.name }}</span>
            <span v-if="processName">{{ $t('formType.processKey') }}{{ $t('punct.colon') }}{{ processName }}</span>
          </template>
        </entity-header-card>
        <div class="mb-3">
          <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
            <template v-for="field in fields.filter((item) => ['imageUpload', 'videoUpload', 'audioUpload', 'fileUpload'].indexOf(item.type) === -1)" :key="field.code">
              <query-item
                v-if="['number', 'slider'].indexOf(field.type) >= 0"
                :label="field.name"
                :name="`Q_GE_mainsJson$${field.code}_Int,Q_LE_mainsJson$${field.code}_Int`"
                type="number"
              ></query-item>
              <query-item
                v-else-if="field.type === 'date' && field.dateType === 'datetime'"
                :label="field.name"
                :name="`Q_GE_mainsJson$${field.code}_DateTime,Q_LE_mainsJson$${field.code}_DateTime`"
                type="datetime"
              ></query-item>
              <query-item
                v-else-if="field.type === 'date'"
                :label="field.name"
                :name="`Q_GE_mainsJson$${field.code}_Date,Q_LE_mainsJson$${field.code}_Date`"
                type="date"
              ></query-item>
              <query-item
                v-else-if="field.type === 'switch'"
                :label="field.name"
                :name="`Q_EQ_mainsJson$${field.code}_Boolean`"
                :options="[
                  { label: $t('yes'), value: 'true' },
                  { label: $t('no'), value: 'false' },
                ]"
                :multiple="false"
              ></query-item>
              <query-item
                v-else-if="['radio', 'select'].indexOf(field.type) >= 0"
                :label="field.name"
                :name="`Q_In_mainsJson$${field.code}Key${field.dataType === 'number' ? '_Int' : ''}`"
                :options="dictListMap[field.dictTypeId]"
              ></query-item>
              <query-item
                v-else-if="['checkbox', 'multipleSelect'].indexOf(field.type) >= 0"
                :label="field.name"
                :name="`Q_ArrayIn_mainsJson$${field.code}Key${field.dataType === 'number' ? '_Int' : ''}`"
                :options="dictListMap[field.dictTypeId]"
              ></query-item>
              <query-item v-else-if="field.clob" :label="field.name" :name="`Q_Contains_@formExt-clobsJson$${field.code}`"></query-item>
              <query-item v-else :label="field.name" :name="`Q_Contains_mainsJson$${field.code}`"></query-item>
            </template>
            <query-item :label="$t('form.user')" name="Q_Like_user-username"></query-item>
            <query-item :label="$t('form.created')" name="Q_GE_created_DateTime,Q_LE_created_DateTime" type="datetime"></query-item>
            <query-item :label="$t('form.modifiedUser')" name="Q_Like_modifiedUser@user-username"></query-item>
            <query-item :label="$t('form.modified')" name="Q_GE_modified_DateTime,Q_LE_modified_DateTime" type="datetime"></query-item>
            <query-item :label="$t('form.org')" name="Q_Like_org-name"></query-item>
            <query-item :label="$t('form.ip')" name="Q_Contains_ip"></query-item>
            <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
          </query-form>
        </div>
        <div>
          <el-tooltip :disabled="model.id !== undefined" :content="$t('form.error.selectType')" placement="top">
            <el-button type="primary" :disabled="perm('form:create') || model.id === undefined" :icon="Plus" @click="() => handleAdd()">
              {{ $t('add') }}
            </el-button>
          </el-tooltip>
          <el-popconfirm :title="$t('confirmExecute')" @confirm="() => handleSubmit(selection.map((row) => row.id))">
            <template #reference>
              <el-button :disabled="selection.length <= 0 || selection.filter((item) => ![10, 20, 22].includes(item.status)).length > 0 || perm('form:submit')" :icon="Finished">
                {{ $t('form.op.submit') }}
              </el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
            <template #reference>
              <el-button :disabled="selection.length <= 0 || selection.filter((item) => [20].includes(item.status)).length > 0 || perm('form:delete')" :icon="DocumentRemove">
                {{ $t('delete') }}
              </el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm :title="$t('confirmCompletelyDelete')" @confirm="() => handleCompletelyDelete(selection.map((row) => row.id))">
            <template #reference>
              <el-button :disabled="selection.length <= 0 || perm('form:completelyDelete')" :icon="Delete">{{ $t('form.op.completelyDelete') }}</el-button>
            </template>
          </el-popconfirm>
          <column-setting :name="`form_${model.id}`" class="ml-2" />
        </div>
        <div class="mt-3 app-block">
          <el-tabs
            v-model="status"
            class="block-tabs"
            @tab-change="
              () => {
                currentPage = 1;
                fetchData();
              }
            "
          >
            <el-tab-pane :name="-1" :label="$t('all')"></el-tab-pane>
            <el-tab-pane v-for="s in [0, 10, 11, 12, 20, 22]" :key="s" :name="s" :label="$t(`form.status.${s}`)"></el-tab-pane>
          </el-tabs>
          <el-table
            ref="table"
            v-loading="loading"
            row-key="id"
            :data="data"
            @selection-change="(rows) => (selection = rows)"
            @row-dblclick="(row) => handleEdit(row.id)"
            @sort-change="handleSort"
          >
            <column-list :name="`form_${model.id}`">
              <el-table-column type="selection" width="45"></el-table-column>
              <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
              <el-table-column width="42">
                <el-icon
                  class="text-lg align-middle"
                  :class="isSorted || perm('form:updateOrder') ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']"
                >
                  <Grid />
                </el-icon>
              </el-table-column>
              <el-table-column
                v-for="field in fields.filter((item) =>
                  ['text', 'textarea', 'number', 'date', 'color', 'slider', 'switch', 'radio', 'checkbox', 'select', 'multipleSelect'].includes(item.type),
                )"
                :key="field.code"
                :property="`customs.${field.code}`"
                :label="field.name"
                :sort-by="`mainsJson$${field.code}${field.dataType === 'number' ? '_Int' : ''}`"
                :sortable="['checkbox', 'multipleSelect'].indexOf(field.type) === -1 ? 'custom' : undefined"
                :display="field.showInList ? undefined : 'none'"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <template v-if="field.type === 'date' && field.dateType === 'date'">
                    {{ dayjs(row.customs[field.code]).format('YYYY-MM-DD') }}
                  </template>
                  <template v-else-if="field.type === 'date' && field.dateType === 'datetime'">
                    {{ dayjs(row.customs[field.code]).format('YYYY-MM-DD HH:mm:ss') }}
                  </template>
                  <template v-else-if="field.type === 'switch'">
                    <el-tag :type="row.customs[field.code] ? 'success' : 'info'" size="small">{{ $t(row.customs[field.code] ? 'yes' : 'no') }}</el-tag>
                  </template>
                </template>
              </el-table-column>
              <el-table-column
                property="type.name"
                :label="$t('form.type')"
                sort-by="type@formType-name"
                sortable="custom"
                :display="formTypeId !== '-1' ? 'none' : undefined"
                show-overflow-tooltip
              ></el-table-column>
              <el-table-column property="org.name" :label="$t('form.org')" sort-by="org-name" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
              <el-table-column property="user.username" :label="$t('form.user')" sort-by="user-username" sortable="custom" show-overflow-tooltip></el-table-column>
              <el-table-column property="created" :label="$t('form.created')" min-width="120" sortable="custom" show-overflow-tooltip>
                <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
              </el-table-column>
              <el-table-column
                property="modifiedUser.username"
                :label="$t('form.modifiedUser')"
                sort-by="modifiedUser@user-username"
                sortable="custom"
                display="none"
                show-overflow-tooltip
              ></el-table-column>
              <el-table-column property="modified" :label="$t('form.modified')" min-width="120" sortable="custom" display="none" show-overflow-tooltip>
                <template #default="{ row }">{{ dayjs(row.modified).format('YYYY-MM-DD HH:mm') }}</template>
              </el-table-column>
              <el-table-column property="ip" :label="$t('form.ip')" sort-by="ip" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
              <el-table-column property="status" :label="$t('form.status')" sortable="custom" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tag v-if="row.status === 0" type="success" size="small">{{ $t(`form.status.${row.status}`) }}</el-tag>
                  <el-tag v-else-if="row.status === 20" type="warning" size="small">{{ $t(`form.status.${row.status}`) }}</el-tag>
                  <el-tooltip v-else-if="row.status === 22" :content="row.rejectReason" placement="top">
                    <el-tag type="danger" size="small">{{ $t(`form.status.${row.status}`) }}</el-tag>
                  </el-tooltip>
                  <el-tag v-else type="info" size="small">{{ $t(`form.status.${row.status}`) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="$t('table.action')" width="280">
                <template #default="{ row }">
                  <el-button type="primary" :disabled="perm('form:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                  <el-button type="primary" :disabled="!row.processInstanceId || perm('processInstance:task')" size="small" link @click="() => handleTask(row.processInstanceId)">
                    {{ $t('processInstance.op.task') }}
                  </el-button>
                  <el-popconfirm :title="$t('confirmExecute')" @confirm="() => handleSubmit([row.id])">
                    <template #reference>
                      <el-button type="primary" :disabled="perm('form:submit') || ![10, 20, 22].includes(row.status)" size="small" link>{{ $t('form.op.submit') }}</el-button>
                    </template>
                  </el-popconfirm>
                  <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                    <template #reference>
                      <el-button type="primary" :disabled="perm('form:delete') || row.status === 20" size="small" link>{{ $t('delete') }}</el-button>
                    </template>
                  </el-popconfirm>
                  <el-popconfirm :title="$t('confirmCompletelyDelete')" @confirm="() => handleCompletelyDelete([row.id])">
                    <template #reference>
                      <el-button type="primary" :disabled="perm('form:completelyDelete')" size="small" link>{{ $t('completelyDelete') }}</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </column-list>
          </el-table>
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total
            :page-sizes
            :layout="pageLayout"
            class="justify-end px-3 py-2"
            size="small"
            background
            @size-change="() => fetchData()"
            @current-change="() => fetchData()"
          ></el-pagination>
        </div>
        <form-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :model="model" :form-type="formType" :model-list="modelList" @finished="fetchData" />
        <form-type-form v-model="typeFormVisible" :bean-id="typeBeanId" :bean-ids="formTypeIds" @finished="handleTypeFinished" />
        <process-task-list v-model="taskListVisible" :instance-id="instanceId" />
      </div>
    </el-main>
  </el-container>
</template>

<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>
