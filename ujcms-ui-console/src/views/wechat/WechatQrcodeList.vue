<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Plus, Delete, CopyDocument } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteWechatQrcode, queryWechatAccountList, queryWechatQrcodePage } from '@/api/wechat';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import WechatQrcodeForm from './WechatQrcodeForm.vue';

defineOptions({
  name: 'WechatQrcodeList',
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
const accountList = ref<any[]>([]);
const accountId = ref<string>();
const type = ref<number>(-1);
const formVisible = ref<boolean>(false);
const formBean = ref<any>(null);
const imageVisible = ref<boolean>(false);
const imageRow = ref<any>({});

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryWechatQrcodePage({
      ...toParams(params.value),
      Q_EQ_accountId_Long: accountId.value,
      Q_EQ_type_Short: type.value !== -1 ? type.value : undefined,
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
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
};
onMounted(() => {
  fetchData();
  fetchAccountList();
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
  accountId.value = undefined;
  type.value = -1;
  sort.value = undefined;
  fetchData();
};
const handleAdd = () => {
  formBean.value = null;
  formVisible.value = true;
};
const handleEdit = (row: any) => {
  formBean.value = row;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteWechatQrcode(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleView = (row: any) => {
  imageRow.value = row;
  imageVisible.value = true;
};
const handleCopy = async (text: string) => {
  await navigator.clipboard.writeText(text);
  ElMessage.success(t('success'));
};
const handleDownload = () => {
  if (imageRow.value.ticket) {
    window.open(qrcodeImageUrl(imageRow.value.ticket), '_blank');
  }
};
// 二维码图片通过微信 showqrcode 接口渲染，无需登录态，有效期内均可访问
const qrcodeImageUrl = (ticket: string): string => `https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=${encodeURIComponent(ticket)}`;
const accountName = (id: string): string => accountList.value.find((item) => item.id === id)?.name ?? '';
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
        <query-item :label="$t('wechatQrcode.name')" name="Q_Contains_name"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-select v-model="accountId" clearable :placeholder="$t('wechatQrcode.account')" class="w-48" @change="() => fetchData()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-select v-model="type" class="w-36" @change="() => fetchData()">
        <el-option :value="-1" :label="$t('wechatQrcode.type.all')"></el-option>
        <el-option v-for="n in [0, 1]" :key="n" :value="n" :label="$t(`wechatQrcode.type.${n}`)"></el-option>
      </el-select>
      <el-button type="primary" :disabled="accountList.length <= 0 || perm('wechatQrcode:create')" :icon="Plus" @click="() => handleAdd()">
        {{ $t('add') }}
      </el-button>
      <el-popconfirm :title="$t('wechatQrcode.deleteConfirm')" :width="320" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('wechatQrcode:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="wechatQrcode" />
    </div>
    <div class="mt-3 app-block">
      <el-table
        id="dataTable"
        ref="table"
        v-loading="loading"
        row-key="id"
        :data="data"
        @selection-change="(rows) => (selection = rows)"
        @sort-change="handleSort"
        @row-dblclick="(row) => handleEdit(row)"
      >
        <column-list name="wechatQrcode">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="name" :label="$t('wechatQrcode.name')" sortable="custom" :min-width="140" show-overflow-tooltip></el-table-column>
          <el-table-column property="accountId" :label="$t('wechatQrcode.account')" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ accountName(row.accountId) }}</template>
          </el-table-column>
          <el-table-column property="type" :label="$t('wechatQrcode.type')" sortable="custom" width="90">
            <template #default="{ row }">
              <el-tag :type="row.type === 1 ? 'success' : 'primary'" size="small">{{ $t(`wechatQrcode.type.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('wechatQrcode.scene')" :min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.type === 0 ? row.sceneId : row.sceneStr }}</template>
          </el-table-column>
          <el-table-column property="scanCount" :label="$t('wechatQrcode.scanCount')" sortable="custom" width="110"></el-table-column>
          <el-table-column property="expiresAt" :label="$t('wechatQrcode.expiresAt')" sortable="custom" width="180">
            <template #default="{ row }">
              <template v-if="row.type === 1">{{ $t('wechatQrcode.permanent') }}</template>
              <template v-else>
                <el-tag v-if="row.expired" type="info" size="small" class="mr-1">{{ $t('wechatQrcode.expired') }}</el-tag>
                <span :class="row.expired ? 'text-gray-secondary' : ''">{{ dayjs(row.expiresAt).format('YYYY-MM-DD HH:mm') }}</span>
              </template>
            </template>
          </el-table-column>
          <el-table-column property="created" :label="$t('wechatQrcode.created')" sortable="custom" width="170">
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="160">
            <template #default="{ row }">
              <el-button type="primary" size="small" link @click="() => handleView(row)">{{ $t('wechatQrcode.op.view') }}</el-button>
              <el-button type="primary" :disabled="perm('wechatQrcode:update')" size="small" link @click="() => handleEdit(row)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('wechatQrcode.deleteConfirm')" :width="320" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('wechatQrcode:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <wechat-qrcode-form v-model="formVisible" :account-list="accountList" :account-id="accountId" :bean="formBean" @finished="() => fetchData()" />
    <el-dialog v-model="imageVisible" :title="$t('wechatQrcode.image')" :width="512">
      <div class="text-center">
        <img v-if="imageRow.ticket" :src="qrcodeImageUrl(imageRow.ticket)" :alt="imageRow.name" class="w-64 h-64 inline-block" />
      </div>
      <el-form label-position="top" class="mt-2">
        <el-form-item :label="$t('wechatQrcode.imageUrl')">
          <el-input :model-value="qrcodeImageUrl(imageRow.ticket ?? '')" readonly>
            <template #append>
              <el-button :icon="CopyDocument" @click="() => handleCopy(qrcodeImageUrl(imageRow.ticket ?? ''))">{{ $t('wechatQrcode.copyLink') }}</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="$t('wechatQrcode.url')">
          <el-input :model-value="imageRow.url" readonly>
            <template #append>
              <el-button :icon="CopyDocument" @click="() => handleCopy(imageRow.url)">{{ $t('wechatQrcode.copyLink') }}</el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="imageVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" @click="() => handleDownload()">{{ $t('wechatQrcode.download') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
