<script setup lang="ts">
import { ref, watch, toRefs, PropType } from 'vue';
import { ArrowLeft, ArrowRight, Search } from '@element-plus/icons-vue';
import { toTree } from '@/utils/tree';
import { queryUserPage, queryUserList, queryOrgList } from '@/api/user';

const props = defineProps({
  userIds: { type: Array as PropType<string[]>, required: true },
});
const emit = defineEmits({
  finished: null,
});

const { userIds } = toRefs(props);
const visible = ref<boolean>(false);
const users = ref<any[]>([]);

const orgTreeData = ref<any[]>([]);
const orgId = ref<string>();
const name = ref<string>();

const loading = ref<boolean>(false);
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const data = ref<any[]>([]);
const selection = ref<any[]>([]);

const userData = ref<any[]>([]);
const userSelection = ref<any[]>([]);

const fetchOrg = async () => {
  orgTreeData.value = toTree(await queryOrgList({ current: true }));
};
const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryUserPage({
      Q_Contains_1_username: name.value,
      Q_Contains_1_realName: name.value,
      Q_NotIn_id_Long: userData.value.map((item) => item.id).join(','),
      orgId: orgId.value,
      current: true,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
watch(
  userIds,
  async () => {
    if (userIds.value.length > 0) {
      users.value = await queryUserList({ Q_In_id_Long: userIds.value.join(',') });
      users.value.sort((o1, o2) => {
        return userIds.value.indexOf(o1.id) - userIds.value.indexOf(o2.id);
      });
      userData.value = [...users.value];
    } else {
      users.value = [];
      userData.value = [];
    }
  },
  { immediate: true },
);
watch(visible, () => {
  if (visible.value) {
    userData.value = [...users.value];
    fetchOrg();
    fetchData();
  }
});

const chooseData = async () => {
  userData.value.push(...selection.value);
  fetchData();
};

const unchooseData = async () => {
  userData.value = userData.value.filter((row) => userSelection.value.findIndex((item) => item.id === row.id) === -1);
  fetchData();
};

const closeTag = (user: any) => {
  const result = users.value.filter((row) => row.id !== user.id);
  emit(
    'finished',
    result.map((item) => item.id),
  );
};

const confirm = () => {
  emit(
    'finished',
    userData.value.map((item) => item.id),
  );
  visible.value = false;
};
</script>

<template>
  <div class="w-full">
    <el-button plain size="small" class="w-full" @click="visible = true">{{ $t('userSelect.choose') }}</el-button>
  </div>
  <div v-for="user in users" :key="user.id" class="w-full">
    <el-tag closable :title="`${user.username}${user.realName ? `(${user.realName})` : ''} [${user.org.name}]`" class="max-w-full mt-1 mr-1" @close="closeTag(user)">
      <span>{{ user.username }}</span>
      <span v-if="user.realName">({{ user.realName }})</span>
      <span class="ml-2">[{{ user.org.name }}]</span>
    </el-tag>
  </div>
  <el-dialog v-model="visible" :title="$t('userSelect.choose')" width="800" append-to-body>
    <div class="flex">
      <el-tree-select
        v-model="orgId"
        :data="orgTreeData"
        node-key="id"
        :default-expanded-keys="orgTreeData.map((item) => item.id)"
        :props="{ label: 'name' }"
        :render-after-expand="false"
        check-strictly
        clearable
        :placeholder="$t('userSelect.org')"
        class="w-64"
        @change="fetchData"
      />
      <el-input v-model="name" class="flex-1" :placeholder="$t('userSelect.usernameOrRealName')" @keyup.enter="fetchData">
        <template #append>
          <el-button :icon="Search" @click="fetchData" />
        </template>
      </el-input>
    </div>
    <div class="flex mt-1">
      <div class="flex-1">
        <el-table v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="username" :label="$t('user.username')"></el-table-column>
          <el-table-column property="realName" :label="$t('user.realName')" show-overflow-tooltip />
          <el-table-column property="org.name" :label="$t('user.org')" show-overflow-tooltip></el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          size="small"
          class="px-3 py-2"
          @size-change="() => fetchData()"
          @current-change="() => fetchData()"
        ></el-pagination>
      </div>
      <div class="px-2">
        <div class="mt-32"><el-button :icon="ArrowRight" @click="chooseData" /></div>
        <div class="mt-4"><el-button :icon="ArrowLeft" @click="unchooseData" /></div>
      </div>
      <div class="flex-1">
        <el-table :data="userData" @selection-change="(rows) => (userSelection = rows)">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="username" :label="$t('user.username')"></el-table-column>
          <el-table-column property="realName" :label="$t('user.realName')" show-overflow-tooltip />
          <el-table-column property="org.name" :label="$t('user.org')" show-overflow-tooltip></el-table-column>
        </el-table>
      </div>
    </div>
    <div class="flex justify-end mt-2">
      <el-button type="primary" @click="confirm">{{ $t('ok') }}</el-button>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
:deep(.el-tag__content) {
  @apply truncate;
}
</style>
