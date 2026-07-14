<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Delete, ArrowDown } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteComment, queryCommentPage, updateCommentStatus } from '@/api/interaction';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import CommentForm from './CommentForm.vue';

defineOptions({
  name: 'CommentList',
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

const route = useRoute();
const status = ref<number>(Number(route.query.status ?? '-1'));

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryCommentPage({
      ...toParams(params.value),
      Q_EQ_status_Short: status.value !== -1 ? status.value : undefined,
      Q_OrderBy: sort.value,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);

const handleSort = ({ column, prop, order }: { column: any; prop: string; order: string }) => {
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

const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteComment(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleStatus = async (ids: string[], status: number) => {
  await updateCommentStatus(ids, status);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('comment.text')" name="Q_Contains_text"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-dropdown :disabled="selection.length <= 0 || perm('comment:updateStatus')">
        <el-button :disabled="selection.length <= 0 || perm('comment:updateStatus')">
          {{ $t('comment.op.status') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="n in [0, 1, 2]"
              :key="n"
              @click="
                () =>
                  handleStatus(
                    selection.map((row) => row.id),
                    n,
                  )
              "
            >
              {{ $t(`comment.status.${n}`) }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('comment:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="comment" />
    </div>
    <el-radio-group v-model="status" class="mt-3" @change="() => fetchData()">
      <el-radio-button :value="-1">{{ $t('all') }}</el-radio-button>
      <el-radio-button :value="0">{{ $t('comment.status.0') }}</el-radio-button>
      <el-radio-button :value="1">{{ $t('comment.status.1') }}</el-radio-button>
      <el-radio-button :value="2">{{ $t('comment.status.2') }}</el-radio-button>
    </el-radio-group>
    <div class="app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="comment">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
          <el-table-column property="text" :label="$t('comment.text')" min-width="260" show-overflow-tooltip></el-table-column>
          <el-table-column property="images" :label="$t('comment.images')" min-width="120">
            <template #default="{ row }">
              <el-image
                v-for="(url, index) in row.images ?? []"
                :key="url"
                :src="url"
                :preview-src-list="row.images"
                :initial-index="index"
                preview-teleported
                fit="cover"
                class="mr-1 align-middle"
                style="width: 32px; height: 32px"
              ></el-image>
            </template>
          </el-table-column>
          <el-table-column property="article.title" :label="$t('comment.article')" min-width="160" show-overflow-tooltip></el-table-column>
          <el-table-column
            property="user.username"
            :label="$t('comment.user')"
            min-width="80"
            sort-by="user-username"
            sortable="custom"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column property="created" :label="$t('comment.created')" min-width="120" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="status" :label="$t('comment.status')" min-width="80" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="success" size="small" disable-transitions>{{ $t(`comment.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 1" type="info" size="small" disable-transitions>{{ $t(`comment.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 2" type="danger" size="small" disable-transitions>{{ $t(`comment.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('comment:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('comment:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
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
    <comment-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
  </div>
</template>
