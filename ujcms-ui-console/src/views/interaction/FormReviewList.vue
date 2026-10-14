<script setup lang="ts">
import { computed, defineAsyncComponent, watch, onMounted, ref } from 'vue';
import { CircleCheck, CircleClose } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { promptRejectReason } from '@/utils/review';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { useRouteState, stringParam } from '@/composables/useRouteState';
import { queryModelList, queryDictList } from '@/api/config';
import { queryFormReviewTypeList, queryFormReviewPage, passForms, passForm, rejectForm, delegateForm, transferForm, backForm } from '@/api/content';
import { queryTaskFormProperties } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import FormForm from './FormForm.vue';
import ReviewFormProperties from '@/views/content/components/ReviewFormProperties.vue';
import ReviewDelegate from '@/views/content/components/ReviewDelegate.vue';
import ReviewTransfer from '@/views/content/components/ReviewTransfer.vue';
import ReviewBack from '@/views/content/components/ReviewBack.vue';
import AsideEntityList from '@/components/AsideEntityList.vue';

// 流程任务对话框依赖 bpmn-js Viewer（约 200 kB）；异步加载，以免随列表页一并下载。对话框本身仍常驻挂载，行为不变。
const ProcessTaskList = defineAsyncComponent(() => import('@/views/system/ProcessTaskList.vue'));

defineOptions({
  name: 'FormReviewList',
});
const { t } = useI18n();
const params = ref<any>({});
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));

const modelList = ref<any[]>([]);
const formTypeList = ref<any[]>([]);
const formTypesLoading = ref<boolean>(true);
// 左侧列表只作导航（不新增、不排序）：接口只返回有待审表单的类型，列表为空是常态
const formTypeGroups = computed(() => [{ key: 'all', items: formTypeList.value }]);
// 导航上下文（表单类型）存于路由 query：刷新、页签往返后恢复。默认值为列表首项（列表就绪后地址省略该键）
const { typeId: formTypeId } = useRouteState({
  typeId: stringParam(() => (formTypeList.value[0] != null ? String(formTypeList.value[0].id) : undefined)),
});
// 类型与模型由 ID 派生（而非在侦听里赋值）：恢复出的类型不触发侦听，列表就绪后照样得到类型与模型
const formType = computed<any>(() => formTypeList.value.find((item) => item.id == formTypeId.value) ?? {});
const model = computed<any>(() => modelList.value.find((item) => item.id == formType.value.modelId) ?? {});
const fields = computed(() => JSON.parse(model.value.customs || '[]'));
const dictListMap = ref<Record<string, any[]>>({});
const asideCollapsed = ref<boolean>(false);

const instanceId = ref<string>();
const taskListVisible = ref<boolean>(false);

const fetchModelList = async () => {
  modelList.value = await queryModelList({ type: 'form' });
};
const fetchFormReviewTypeList = async () => {
  formTypesLoading.value = true;
  try {
    formTypeList.value = await queryFormReviewTypeList();
  } finally {
    formTypesLoading.value = false;
  }
};

const fetchData = async () => {
  if (!formType.value.id) {
    return;
  }
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryFormReviewPage({
      ...toParams(params.value),
      typeId: formType.value.id,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
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
  await Promise.all([fetchFormReviewTypeList(), fetchModelList()]);
  if (formTypeList.value.some((item) => String(item.id) === formTypeId.value)) {
    // 恢复出的类型有效：侦听不会触发，显式加载
    fetchData();
  } else {
    // 无恢复值，或类型已删除/属于其它站点：回落首项（侦听随之重置查询并加载列表）
    formTypeId.value = formTypeList.value[0] != null ? String(formTypeList.value[0].id) : undefined;
  }
});

const handleReset = () => {
  resetParams(params.value);
  fetchData();
};

const handlePassBatch = async (beans: any[]) => {
  await passForms(beans.map((item) => item.taskId));
  fetchData();
  ElMessage.success(t('success'));
};

const taskId = ref<string>('');
const formProperties = ref<any[]>([]);
const formPropertiesVisible = ref<boolean>(false);
const backVisible = ref<boolean>(false);
const delegateVisible = ref<boolean>(false);
const transferVisible = ref<boolean>(false);

const handleEdit = (bean: any) => {
  beanId.value = bean.id;
  taskId.value = bean.taskId;
  formVisible.value = true;
};

const handlePass = async (bean: any) => {
  taskId.value = bean.taskId;
  formProperties.value = await queryTaskFormProperties(bean.taskId);
  formPropertiesVisible.value = true;
};
const submitPass = async (params: any) => {
  await passForm(taskId.value, params.properties, params.comment);
  fetchData();
  ElMessage.success(t('success'));
};

const handleDelegate = async (bean: any) => {
  taskId.value = bean.taskId;
  delegateVisible.value = true;
};
const submitDelegate = async (params: any) => {
  await delegateForm(taskId.value, params.toUserId, params.comment);
  fetchData();
  ElMessage.success(t('success'));
};

const handleTransfer = async (bean: any) => {
  taskId.value = bean.taskId;
  transferVisible.value = true;
};
const submitTransfer = async (params: any) => {
  await transferForm(taskId.value, params.toUserId, params.comment);
  fetchData();
  ElMessage.success(t('success'));
};

const handleBack = (bean: any) => {
  taskId.value = bean.taskId;
  backVisible.value = true;
};
const submitBack = async (params: any) => {
  await backForm(taskId.value, params.activityId, params.comment);
  fetchData();
  ElMessage.success(t('success'));
};

const handleReject = async (ids: string[]) => {
  const reason = await promptRejectReason(t);
  if (reason == null) {
    return;
  }
  await rejectForm(ids, reason);
  fetchData();
  ElMessage.success(t('success'));
};
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
      :search-placeholder="$t('formType.filter')"
      :no-match-text="$t('formType.noMatch')"
      :empty-text="$t('formReview.none')"
      :loading="formTypesLoading"
    />
    <el-main class="p-0">
      <div>
        <div>
          <el-popconfirm :title="$t('review.confirmPass')" @confirm="() => handlePassBatch(selection)">
            <template #reference>
              <el-button :disabled="selection.length <= 0 || perm('formReview:pass')" :icon="CircleCheck" type="primary">{{ $t('review.pass') }}</el-button>
            </template>
          </el-popconfirm>
          <el-button :disabled="selection.length <= 0 || perm('formReview:reject')" :icon="CircleClose" @click="() => handleReject(selection.map((row) => row.taskId))">
            {{ $t('review.reject') }}
          </el-button>
          <column-setting :name="`form_${model.id}`" class="ml-2" />
        </div>
        <div class="mt-3 app-block">
          <el-table
            v-loading="loading"
            :data="data"
            @selection-change="(rows) => (selection = rows)"
            @row-dblclick="(row) => handleEdit(row)"
          >
            <column-list :name="`form_${model.id}`">
              <el-table-column type="selection" width="45"></el-table-column>
              <!-- 审核列表按审核任务的创建时间倒序（服务端的流程任务查询），各列不提供排序 -->
              <el-table-column property="id" label="ID" width="200" display="none"></el-table-column>
              <el-table-column
                v-for="field in fields.filter((item) => item.showInList)"
                :key="field.code"
                :property="`customs.${field.code}`"
                :label="field.name"
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
              <el-table-column property="org.name" :label="$t('form.org')" display="none" show-overflow-tooltip></el-table-column>
              <el-table-column property="user.username" :label="$t('form.user')" show-overflow-tooltip></el-table-column>
              <el-table-column property="created" :label="$t('form.created')" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
              </el-table-column>
              <el-table-column property="modifiedUser.username" :label="$t('form.modifiedUser')" display="none" show-overflow-tooltip></el-table-column>
              <el-table-column property="modified" :label="$t('form.modified')" min-width="120" display="none" show-overflow-tooltip>
                <template #default="{ row }">{{ dayjs(row.modified).format('YYYY-MM-DD HH:mm') }}</template>
              </el-table-column>
              <el-table-column property="ip" :label="$t('form.ip')" display="none" show-overflow-tooltip></el-table-column>
              <el-table-column property="status" :label="$t('form.status')" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tag v-if="row.status === 0" type="success" size="small">{{ $t(`form.status.${row.status}`) }}</el-tag>
                  <el-tag v-else-if="row.status === 20" type="warning" size="small">{{ $t(`form.status.${row.status}`) }}</el-tag>
                  <el-tooltip v-else-if="row.status === 22" :content="row.rejectReason" placement="top">
                    <el-tag type="danger" size="small">{{ $t(`form.status.${row.status}`) }}</el-tag>
                  </el-tooltip>
                  <el-tag v-else type="info" size="small">{{ $t(`form.status.${row.status}`) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="$t('table.action')" width="330">
                <template #default="{ row }">
                  <el-button type="primary" :disabled="perm('form:update')" size="small" link @click="() => handleEdit(row)">{{ $t('edit') }}</el-button>
                  <el-button type="primary" :disabled="!row.processInstanceId || perm('processInstance:task')" size="small" link @click="() => handleTask(row.processInstanceId)">
                    {{ $t('processInstance.op.task') }}
                  </el-button>
                  <el-popconfirm :title="$t('review.confirmPass')" @confirm="() => handlePass(row)">
                    <template #reference>
                      <el-button type="primary" :disabled="perm('formReview:pass')" size="small" link>{{ $t('review.pass') }}</el-button>
                    </template>
                  </el-popconfirm>
                  <el-button type="primary" :disabled="perm('formReview:reject')" size="small" link @click="() => handleReject([row.taskId])">{{ $t('review.reject') }}</el-button>
                  <el-button type="primary" :disabled="perm('formReview:back')" size="small" link @click="() => handleBack(row)">{{ $t('review.back') }}</el-button>
                  <el-button type="primary" :disabled="perm('formReview:delegate')" size="small" link @click="() => handleDelegate(row)">{{ $t('review.delegate') }}</el-button>
                  <el-button type="primary" :disabled="perm('formReview:transfer')" size="small" link @click="() => handleTransfer(row)">{{ $t('review.transfer') }}</el-button>
                </template>
              </el-table-column>
            </column-list>
          </el-table>
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="pageSizes"
            :layout="pageLayout"
            class="justify-end px-3 py-2"
            size="small"
            background
            @size-change="() => fetchData()"
            @current-change="() => fetchData()"
          ></el-pagination>
        </div>
        <form-form
          v-model="formVisible"
          :bean-id="beanId"
          :bean-ids="beanIds"
          :model="model"
          :form-type="formType"
          :model-list="modelList"
          :is-review="true"
          @finished="fetchData"
        />
        <process-task-list v-model="taskListVisible" :instance-id="instanceId" />
        <review-form-properties v-model="formPropertiesVisible" v-model:form-properties="formProperties" @finished="submitPass" />
        <review-delegate v-model="delegateVisible" @finished="submitDelegate" />
        <review-transfer v-model="transferVisible" @finished="submitTransfer" />
        <review-back v-model="backVisible" :task-id @finished="submitBack" />
      </div>
    </el-main>
  </el-container>
</template>
