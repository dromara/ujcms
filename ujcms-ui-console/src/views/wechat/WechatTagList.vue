<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Plus } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { createWechatTag, deleteWechatTag, queryWechatAccountList, queryWechatTagList, queryWechatTagUserList, updateWechatTag } from '@/api/wechat';
import { stringParam, useRouteState } from '@/composables/useRouteState';

defineOptions({
  name: 'WechatTagList',
});
const { t } = useI18n();
const accountList = ref<any[]>([]);
// 当前公众号存于 query.accountId（docs/spec/console-view-state），默认首个公众号：视图在刷新、页签往返时都会重建，
// 不记住就会悄悄回到首个公众号，运营者可能在错误的公众号上继续操作
const { accountId } = useRouteState({ accountId: stringParam(() => accountList.value[0]?.id) });
const data = ref<any[]>([]);
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);

const formVisible = ref<boolean>(false);
const formValues = ref<any>({});

const userVisible = ref<boolean>(false);
const userTag = ref<any>({});
const userPage = ref<number>(1);
const userData = ref<any[]>([]);
const userLoading = ref<boolean>(false);

const fetchData = async () => {
  if (accountId.value == null) {
    data.value = [];
    return;
  }
  loading.value = true;
  try {
    data.value = await queryWechatTagList(accountId.value);
  } finally {
    loading.value = false;
  }
};
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
  if (accountList.value.length > 0) {
    // 恢复出的公众号须仍在列表中（切换站点后整页重载而地址不变、公众号已删除），否则回落到首个
    if (!accountList.value.some((item) => item.id === accountId.value)) {
      accountId.value = accountList.value[0].id;
    }
    fetchData();
  }
};
onMounted(fetchAccountList);

const handleAdd = () => {
  formValues.value = {};
  formVisible.value = true;
};
const handleEdit = (row: any) => {
  formValues.value = { id: row.id, name: row.name };
  formVisible.value = true;
};
const handleFormSubmit = async () => {
  if (accountId.value == null || !formValues.value.name) return;
  buttonLoading.value = true;
  try {
    if (formValues.value.id != null) {
      await updateWechatTag({ accountId: accountId.value, ...formValues.value });
    } else {
      await createWechatTag({ accountId: accountId.value, ...formValues.value });
    }
    formVisible.value = false;
    fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleDelete = async (id: string) => {
  if (accountId.value == null) return;
  await deleteWechatTag({ accountId: accountId.value, id });
  fetchData();
  ElMessage.success(t('success'));
};

const fetchUserData = async () => {
  if (accountId.value == null) return;
  userLoading.value = true;
  try {
    const result = await queryWechatTagUserList({ accountId: accountId.value, tagId: userTag.value.id, page: userPage.value });
    userData.value = result.items ?? [];
  } finally {
    userLoading.value = false;
  }
};
const handleViewUsers = (row: any) => {
  userTag.value = row;
  userPage.value = 1;
  userData.value = [];
  userVisible.value = true;
  fetchUserData();
};
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-select v-model="accountId" :placeholder="$t('wechat.selectAccount')" class="w-48" @change="() => fetchData()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-button type="primary" :disabled="accountId == null || perm('wechatTag:create')" :icon="Plus" @click="() => handleAdd()">
        {{ $t('add') }}
      </el-button>
    </div>
    <el-alert v-if="accountList.length <= 0" :title="$t('wechat.noAccount')" type="info" :closable="false" class="mt-3" />
    <div class="mt-3 app-block">
      <el-table v-loading="loading" row-key="id" :data="data">
        <el-table-column property="id" label="ID" width="100"></el-table-column>
        <el-table-column property="name" :label="$t('wechatTag.name')" :min-width="200" show-overflow-tooltip></el-table-column>
        <el-table-column property="count" :label="$t('wechatTag.count')" width="120"></el-table-column>
        <el-table-column :label="$t('table.action')" width="180">
          <template #default="{ row }">
            <el-button type="primary" :disabled="perm('wechatTag:list')" size="small" link @click="() => handleViewUsers(row)">
              {{ $t('wechatTag.op.viewFans') }}
            </el-button>
            <el-button type="primary" :disabled="perm('wechatTag:update')" size="small" link @click="() => handleEdit(row)">
              {{ $t('edit') }}
            </el-button>
            <el-popconfirm :title="$t('wechatTag.deleteConfirm')" @confirm="() => handleDelete(row.id)">
              <template #reference>
                <el-button type="primary" :disabled="perm('wechatTag:delete')" size="small" link>{{ $t('delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="formVisible" :title="formValues.id != null ? $t('edit') : $t('add')" :width="512">
      <el-form label-position="top">
        <el-form-item :label="$t('wechatTag.name')">
          <el-input v-model="formValues.name" maxlength="30"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="buttonLoading" :disabled="!formValues.name" @click="() => handleFormSubmit()">{{ $t('save') }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="userVisible" :title="`${$t('wechatTag.fans')} - ${userTag.name}`" :width="680">
      <el-table v-loading="userLoading" :row-key="(row: any) => row.openid" :data="userData">
        <el-table-column property="remark" :label="$t('wechatFan.remark')" width="160" show-overflow-tooltip></el-table-column>
        <el-table-column property="openid" label="OpenID" :min-width="220" show-overflow-tooltip></el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="userPage"
        :total="Number(userTag.count ?? 0)"
        :page-size="20"
        layout="total, prev, pager, next"
        size="small"
        background
        class="justify-end pt-2"
        @current-change="() => fetchUserData()"
      ></el-pagination>
    </el-dialog>
  </div>
</template>
