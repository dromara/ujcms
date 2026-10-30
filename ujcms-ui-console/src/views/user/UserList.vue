<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { Plus, Delete, ArrowDown, Grid, Upload } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { currentUser, perm, isShowTier } from '@/stores/useCurrentUser';
import { showCommercialNotice } from '@/utils/commercial';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { toTree, findTreeItem, filterTreeNodeByName } from '@/utils/tree';
import { useRouteState, stringParam, flagParam } from '@/composables/useRouteState';
import { deleteUser, updateUserStatus, queryUserPage, queryOrgList } from '@/api/user';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import CollapsibleAside from '@/components/CollapsibleAside.vue';
import UserForm from './UserForm.vue';
import UserBatchForm from './UserBatchForm.vue';
import UserImportDialog from './UserImportDialog.vue';
import UserPasswordForm from './UserPasswordForm.vue';
import UserPermissionForm from './UserPermissionForm.vue';
import UserRolesForm from './UserRolesForm.vue';

defineOptions({
  name: 'UserList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<Array<any>>([]);
const selection = ref<Array<any>>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const batchFormVisible = ref<boolean>(false);
const importVisible = ref<boolean>(false);
const passwordFormVisible = ref<boolean>(false);
const permissionFormVisible = ref<boolean>(false);
const rolesFormVisible = ref<boolean>(false);
const rolesFormMode = ref<'add' | 'remove'>('add');
const passwordFormUsername = ref<string>('');
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));

// 导航上下文（选中组织、全局数据）存于路由 query：刷新、页签往返后恢复
const { orgId, global: showGlobalData } = useRouteState({
  orgId: stringParam(),
  global: flagParam(() => currentUser.globalPermission),
});
const orgTreeLoading = ref<boolean>(false);
const orgTree = ref<any>();
const orgTreeData = ref<any[]>([]);
const orgTreeFilter = ref<string>();
// 以 ID 为准、节点由组织树派生；树加载前为 undefined
const org = computed<any>(() => (orgId.value != null ? findTreeItem(orgTreeData.value, (item: any) => item.id === orgId.value) : undefined));
const asideCollapsed = ref<boolean>(false);
watch(orgTreeFilter, (val) => {
  orgTree.value?.filter(val);
});

const deletable = (bean: any) => bean.id > 1 && currentUser.rank <= bean.rank;

const fetchOrg = async () => {
  orgTreeLoading.value = true;
  try {
    orgTreeData.value = toTree(await queryOrgList({ current: !showGlobalData.value }));
    // 恢复出的组织可能已删除或不在当前范围（其它站点、已取消全局数据）：回落到全部组织
    if (orgId.value != null && org.value == null) {
      orgId.value = undefined;
    }
    nextTick(() => {
      // 切换全局数据会重载组织树，新树的节点全部可见：须重新套用过滤词
      if (orgTreeFilter.value != null) {
        orgTree.value?.filter(orgTreeFilter.value);
      }
      // setCurrentKey 会展开选中节点的各级父节点
      orgTree.value?.setCurrentKey(orgId.value ?? null);
    });
  } finally {
    orgTreeLoading.value = false;
  }
};

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryUserPage({
      ...toParams(params.value),
      orgId: orgId.value,
      current: !showGlobalData.value,
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

onMounted(async () => {
  if (orgId.value != null) {
    // 有恢复出的组织：先加载组织树完成校验，再查列表，避免带着失效的组织 ID 发请求
    try {
      await fetchOrg();
    } catch {
      // 请求拦截器已提示错误；列表照常加载
    }
  } else {
    fetchOrg();
  }
  fetchData();
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
const treeRootClick = () => {
  orgTree.value.setCurrentKey(null);
  orgId.value = undefined;
  handleSearch();
};
// 全局数据同时决定组织树与用户列表的范围：选中的组织可能不在新范围内，回到全部组织
const handleGlobalChange = () => {
  orgId.value = undefined;
  fetchOrg();
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
const handleBatchAdd = () => {
  batchFormVisible.value = true;
};
// 导入用户是商业版 T1：社区版的入口带徽标，点击说明所需版本
const handleImport = () => {
  if (currentUser.commercialTier < 1) {
    showCommercialNotice(1, t('commercial.feature.userImport'));
    return;
  }
  importVisible.value = true;
};
const handlePasswordEdit = (id: string, username: string) => {
  beanId.value = id;
  passwordFormUsername.value = username;
  passwordFormVisible.value = true;
};
const handlePermissionEdit = (id: string) => {
  beanId.value = id;
  permissionFormVisible.value = true;
};
// 批量添加 / 移除角色：对选中的行，只加减指定的角色（D9）
const handleRoles = (mode: 'add' | 'remove') => {
  rolesFormMode.value = mode;
  rolesFormVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteUser(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleStatus = async (ids: string[], status: number) => {
  await updateUserStatus(ids, status);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <el-container>
    <!-- 选中的组织会随地址恢复（刷新、页签/侧栏往返），须有回到全部组织的入口：根行（同文章管理的「全部栏目」） -->
    <collapsible-aside
      v-model:collapsed="asideCollapsed"
      v-model:keyword="orgTreeFilter"
      :search-placeholder="$t('org.filter')"
      :root-label="$t('user.allOrg')"
      :root-active="orgId == null"
      @root="treeRootClick"
    >
      <el-tree
        ref="orgTree"
        v-loading="orgTreeLoading"
        :data="orgTreeData"
        :props="{ label: 'name' }"
        :expand-on-click-node="false"
        :default-expanded-keys="orgTreeData.map((item) => item.id)"
        node-key="id"
        highlight-current
        :filter-node-method="filterTreeNodeByName"
        @node-click="
          (node) => {
            orgId = node.id;
            handleSearch();
          }
        "
      ></el-tree>
    </collapsible-aside>
    <el-main class="p-0">
      <div class="mb-3">
        <query-form :params="params" @search="handleSearch" @reset="handleReset">
          <query-item :label="$t('user.username')" name="Q_Contains_username"></query-item>
          <!-- 手机号、邮箱整列加密：只能按完整值精确查找（服务端改写到盲索引），也不能按它们排序 -->
          <query-item :label="$t('user.mobile')" name="Q_EQ_mobile"></query-item>
          <query-item :label="$t('user.email')" name="Q_EQ_email"></query-item>
          <query-item :label="$t('user.rank')" name="Q_GE_rank,Q_LE_rank" type="number"></query-item>
          <query-item :label="$t('user.created')" name="Q_GE_@userExt-created_DateTime,Q_LE_@userExt-created_DateTime" type="datetime"></query-item>
          <query-item :label="$t('user.status')" name="Q_In_status_Int" :options="[0, 1, 2, 3].map((item) => ({ label: $t(`user.status.${item}`), value: item }))"></query-item>
          <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
        </query-form>
      </div>
      <div>
        <el-button type="primary" :icon="Plus" @click="handleAdd">{{ $t('add') }}</el-button>
        <el-button :icon="Grid" :disabled="perm('user:create')" class="ml-2" @click="handleBatchAdd">{{ $t('user.op.batchCreate') }}</el-button>
        <el-button v-if="isShowTier(1)" :icon="Upload" :disabled="currentUser.commercialTier >= 1 && perm('user:import')" class="ml-2" @click="handleImport">
          {{ $t('user.op.import') }}<commercial-badge v-if="currentUser.commercialTier < 1" :tier="1" :size="12" class="ml-1" />
        </el-button>
        <el-dropdown :disabled="selection.length <= 0 || perm('user:updateStatus')" class="ml-2">
          <el-button :disabled="selection.length <= 0 || perm('user:updateStatus')">
            {{ $t('user.op.status') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="n in [0, 1, 2, 3]"
                :key="n"
                @click="
                  handleStatus(
                    selection.map((row) => row.id),
                    n,
                  )
                "
              >
                {{ $t(`user.status.${n}`) }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown :disabled="selection.length <= 0 || perm('user:updatePermission')" class="ml-2">
          <el-button :disabled="selection.length <= 0 || perm('user:updatePermission')">
            {{ $t('user.op.roles') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="() => handleRoles('add')">{{ $t('user.roles.add') }}</el-dropdown-item>
              <el-dropdown-item @click="() => handleRoles('remove')">{{ $t('user.roles.remove') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm('user:delete')" :icon="Delete" class="ml-2">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <el-checkbox v-if="currentUser.globalPermission" v-model="showGlobalData" class="ml-2 align-middle" :label="$t('globalData')" :border="true" @change="handleGlobalChange" />
        <column-setting name="user" class="ml-2" />
      </div>
      <div class="mt-3 app-block">
        <el-table
          ref="table"
          v-loading="loading"
          :data="data"
          @selection-change="(rows) => (selection = rows)"
          @row-dblclick="(row) => handleEdit(row.id)"
          @sort-change="handleSort"
        >
          <column-list name="user">
            <el-table-column type="selection" :selectable="deletable" width="38"></el-table-column>
            <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
            <el-table-column property="username" :label="$t('user.username')" sortable="custom" min-width="100"></el-table-column>
            <el-table-column property="mobile" :label="$t('user.mobile')" display="none" min-width="100" show-overflow-tooltip></el-table-column>
            <el-table-column property="email" :label="$t('user.email')" display="none" min-width="100" show-overflow-tooltip></el-table-column>
            <el-table-column property="realName" :label="$t('user.realName')" sortable="custom" min-width="100" show-overflow-tooltip />
            <el-table-column property="gender" :label="$t('user.gender')" sortable="custom" display="none">
              <template #default="{ row }">{{ $t(`gender.${row.gender}`) }}</template>
            </el-table-column>
            <el-table-column property="created" :label="$t('user.created')" sort-by="@userExt-created" sortable="custom" display="none" width="170">
              <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm:ss') }}</template>
            </el-table-column>
            <el-table-column property="birthday" :label="$t('user.birthday')" sort-by="@userExt-birthday" sortable="custom" display="none" width="110">
              <template #default="{ row }">{{ dayjs(row.birthday).format('YYYY-MM-DD') }}</template>
            </el-table-column>
            <el-table-column property="loginDate" :label="$t('user.loginDate')" sort-by="@userExt-loginDate" sortable="custom" display="none" width="170">
              <template #default="{ row }">{{ dayjs(row.loginDate).format('YYYY-MM-DD HH:mm:ss') }}</template>
            </el-table-column>
            <el-table-column property="loginIp" :label="$t('user.loginIp')" sort-by="@userExt-loginIp" sortable="custom" display="none" show-overflow-tooltip />
            <el-table-column property="loginCount" :label="$t('user.loginCount')" sort-by="@userExt-loginCount" sortable="custom" display="none" show-overflow-tooltip />
            <el-table-column property="org.name" :label="$t('user.org')" sort-by="org-name" sortable="custom" show-overflow-tooltip></el-table-column>
            <el-table-column property="orgs" :label="$t('user.orgs')" display="none" show-overflow-tooltip>
              <template #default="{ row }">
                <el-space>
                  <span v-for="item in row.orgList" :key="item.id">{{ item.name }}</span>
                </el-space>
              </template>
            </el-table-column>
            <el-table-column property="roles" :label="$t('user.role')" show-overflow-tooltip>
              <template #default="{ row }">
                <el-space>
                  <span v-for="item in row.roleList" :key="item.id">{{ item.name }}</span>
                </el-space>
              </template>
            </el-table-column>
            <el-table-column property="group.name" :label="$t('user.group')" sort-by="group-name" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="rank" :label="$t('user.rank')" sortable="custom" width="80" show-overflow-tooltip></el-table-column>
            <el-table-column property="status" :label="$t('user.status')" width="80" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.status === 0" type="success" size="small">{{ $t(`user.status.${row.status}`) }}</el-tag>
                <el-tag v-else-if="row.status === 1" type="info" size="small">{{ $t(`user.status.${row.status}`) }}</el-tag>
                <el-tag v-else-if="row.status === 2" type="warning" size="small">{{ $t(`user.status.${row.status}`) }}</el-tag>
                <el-tag v-else-if="row.status === 3" type="danger" size="small">{{ $t(`user.status.${row.status}`) }}</el-tag>
                <el-tag v-else type="danger">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('table.action')" width="330">
              <template #default="{ row }">
                <el-button type="primary" :disabled="perm('user:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                <el-button
                  type="primary"
                  :disabled="currentUser.rank > row.rank || perm('user:updatePassword')"
                  size="small"
                  link
                  @click="() => handlePasswordEdit(row.id, row.username)"
                >
                  {{ $t('changePassword') }}
                </el-button>
                <el-button type="primary" :disabled="perm('user:updatePermission')" size="small" link @click="() => handlePermissionEdit(row.id)">
                  {{ $t('permissionSettings') }}
                </el-button>
                <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="!deletable(row) || perm('user:delete')" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
                <el-dropdown :disabled="!deletable(row)" class="ml-2 align-middle">
                  <el-button :disabled="!deletable(row)" type="primary" size="small" link>
                    {{ $t('user.op.status') }}
                  </el-button>
                  <template #dropdown>
                    <div>
                      <el-dropdown-menu>
                        <el-dropdown-item v-for="n in [0, 1, 2, 3]" :key="n" :disabled="perm('user:updateStatus') || n === row.status" @click="() => handleStatus([row.id], n)">
                          <span class="text-xs">{{ $t(`user.status.${n}`) }}</span>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </div>
                  </template>
                </el-dropdown>
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
      <user-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :org="org" :show-global-data="showGlobalData" @finished="fetchData" />
      <user-batch-form v-model="batchFormVisible" :org="org" :show-global-data="showGlobalData" @finished="fetchData" />
      <user-import-dialog v-model="importVisible" :org="org" :show-global-data="showGlobalData" @finished="fetchData" />
      <user-permission-form v-model="permissionFormVisible" :bean-id="beanId" @finished="fetchData"></user-permission-form>
      <user-roles-form v-model="rolesFormVisible" :mode="rolesFormMode" :users="selection" @finished="fetchData" />
      <user-password-form v-model="passwordFormVisible" :bean-id="beanId" :username="passwordFormUsername"></user-password-form>
    </el-main>
  </el-container>
</template>
