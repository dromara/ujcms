<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue';
import { CircleCheck, CircleClose } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm, currentUser } from '@/stores/useCurrentUser';
import { promptRejectReason } from '@/utils/review';
import { pageSizes, pageLayout } from '@/utils/common';
import { passArticle, passArticles, delegateArticle, transferArticle, rejectArticle, backArticle, queryArticleReviewPage } from '@/api/content';
import { queryTaskFormProperties } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import ArticleForm from './ArticleForm.vue';
import { openArticleLink, articleStatusTagType } from './components/articleUtils';
import ReviewFormProperties from './components/ReviewFormProperties.vue';
import ReviewDelegate from './components/ReviewDelegate.vue';
import ReviewTransfer from './components/ReviewTransfer.vue';
import ReviewBack from './components/ReviewBack.vue';

// 流程任务对话框依赖 bpmn-js Viewer（约 200 kB）；异步加载，以免随列表页一并下载。对话框本身仍常驻挂载，行为不变。
const ProcessTaskList = defineAsyncComponent(() => import('@/views/system/ProcessTaskList.vue'));

defineOptions({
  name: 'ArticleReviewList',
});
const { t } = useI18n();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<Array<any>>([]);
const selection = ref<Array<any>>([]);
const tableLoading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));

const instanceId = ref<string>();
const taskListVisible = ref<boolean>(false);

const fetchData = async () => {
  tableLoading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryArticleReviewPage({
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    tableLoading.value = false;
  }
};
onMounted(() => {
  fetchData();
});

const taskId = ref<string>('');
const formProperties = ref<any[]>([]);
const formPropertiesVisible = ref<boolean>(false);
const backVisible = ref<boolean>(false);
const delegateVisible = ref<boolean>(false);
const transferVisible = ref<boolean>(false);

// 没有“文章修改”权限的审核人也能打开：对话框只读（ArticleForm 的 disable-edit），看过正文再决定通过还是退回
const handleEdit = (bean: any) => {
  beanId.value = bean.id;
  taskId.value = bean.taskId;
  formVisible.value = true;
};
const handlePassBatch = async (beans: any[]) => {
  await passArticles(beans.map((item) => item.taskId));
  fetchData();
  ElMessage.success(t('success'));
};

const handlePass = async (bean: any) => {
  taskId.value = bean.taskId;
  formProperties.value = await queryTaskFormProperties(bean.taskId);
  formPropertiesVisible.value = true;
};
const submitPass = async (params: any) => {
  await passArticle(taskId.value, params.properties, params.comment);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDelegate = async (bean: any) => {
  taskId.value = bean.taskId;
  delegateVisible.value = true;
};
const submitDelegate = async (params: any) => {
  await delegateArticle(taskId.value, params.toUserId, params.comment);
  fetchData();
  ElMessage.success(t('success'));
};
const handleTransfer = async (bean: any) => {
  taskId.value = bean.taskId;
  transferVisible.value = true;
};
const submitTransfer = async (params: any) => {
  await transferArticle(taskId.value, params.toUserId, params.comment);
  fetchData();
  ElMessage.success(t('success'));
};
const handleBack = (bean: any) => {
  taskId.value = bean.taskId;
  backVisible.value = true;
};
const submitBack = async (params: any) => {
  await backArticle(taskId.value, params.activityId, params.comment);
  fetchData();
  ElMessage.success(t('success'));
};
const handleReject = async (ids: string[]) => {
  const reason = await promptRejectReason(t);
  if (reason == null) {
    return;
  }
  await rejectArticle(ids, reason);
  fetchData();
  ElMessage.success(t('success'));
};
const handleTask = (id: string) => {
  instanceId.value = id;
  taskListVisible.value = true;
};
</script>

<template>
  <div>
    <div>
      <el-popconfirm :title="$t('review.confirmPass')" @confirm="() => handlePassBatch(selection)">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('articleReview:pass')" :icon="CircleCheck" type="primary">{{ $t('review.pass') }}</el-button>
        </template>
      </el-popconfirm>
      <el-button :disabled="selection.length <= 0 || perm('articleReview:reject')" :icon="CircleClose" @click="() => handleReject(selection.map((row) => row.taskId))">
        {{ $t('review.reject') }}
      </el-button>
      <column-setting name="articleReview" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="tableLoading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row)">
        <column-list name="articleReview">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" display="none"></el-table-column>
          <el-table-column property="title" :label="$t('article.title')" min-width="280">
            <template #default="{ row }">
              <el-link href="javascript:" type="primary" underline="never" @click="() => openArticleLink(row.status, row.fullUrl, row.dynamicFullUrl)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column property="channel.name" :label="$t('article.channel')" show-overflow-tooltip></el-table-column>
          <el-table-column property="org.name" :label="$t('article.org')" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="author" :label="$t('article.author')" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="editor" :label="$t('article.editor')" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="source" :label="$t('article.source')" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="user.username" :label="$t('article.user')" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('article.created')" min-width="120" display="none" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="modifiedUser.username" :label="$t('article.modifiedUser')" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="modified" :label="$t('article.modified')" min-width="120" display="none" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.modified).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="publishDate" :label="$t('article.publishDate')" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.publishDate).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="status" :label="$t('article.status')" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag :type="articleStatusTagType(row.status)" size="small">{{ $t(`article.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="330">
            <template #default="{ row }">
              <el-button type="primary" size="small" link @click="() => handleEdit(row)">{{ $t(perm('article:update') ? 'detail' : 'edit') }}</el-button>
              <el-button type="primary" :disabled="!row.processInstanceId || perm('processInstance:task')" size="small" link @click="() => handleTask(row.processInstanceId)">
                {{ $t('processInstance.op.task') }}
              </el-button>
              <el-button type="primary" :disabled="perm('articleReview:pass')" size="small" link @click="() => handlePass(row)">{{ $t('review.pass') }}</el-button>
              <el-button type="primary" :disabled="perm('articleReview:reject')" size="small" link @click="() => handleReject([row.taskId])">{{ $t('review.reject') }}</el-button>
              <el-button v-if="currentUser.commercialTier >= 3" type="primary" :disabled="perm('articleReview:back')" size="small" link @click="() => handleBack(row)">
                {{ $t('review.back') }}
              </el-button>
              <el-button v-if="currentUser.commercialTier >= 3" type="primary" :disabled="perm('articleReview:delegate')" size="small" link @click="() => handleDelegate(row)">
                {{ $t('review.delegate') }}
              </el-button>
              <el-button v-if="currentUser.commercialTier >= 3" type="primary" :disabled="perm('articleReview:transfer')" size="small" link @click="() => handleTransfer(row)">
                {{ $t('review.transfer') }}
              </el-button>
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
        size="small"
        background
        class="justify-end px-3 py-2"
        @size-change="() => fetchData()"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
    <article-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :is-review="true" :task-id @finished="fetchData" />
    <process-task-list v-model="taskListVisible" :instance-id="instanceId" />
    <review-form-properties v-model="formPropertiesVisible" v-model:form-properties="formProperties" @finished="submitPass" />
    <review-delegate v-model="delegateVisible" @finished="submitDelegate" />
    <review-transfer v-model="transferVisible" @finished="submitTransfer" />
    <review-back v-model="backVisible" :task-id @finished="submitBack" />
  </div>
</template>
