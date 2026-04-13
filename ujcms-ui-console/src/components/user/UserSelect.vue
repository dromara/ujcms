<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { queryUser, queryUserPage } from '@/api/user';

const userId = defineModel<string>();
const user = ref<any>({});

const name = ref<string>();

const loading = ref<boolean>(false);
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const data = ref<any[]>([]);

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryUserPage({
      Q_Contains_1_username: name.value,
      Q_Contains_1_realName: name.value,
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

onMounted(async () => {
  if (userId.value != null) {
    user.value = await queryUser(userId.value);
  }
});

const userRemoteMethod = async (query: string) => {
  name.value = query;
  fetchData();
};

const userChange = (value: any) => {
  userId.value = value;
};
</script>

<template>
  <el-select v-model="user" filterable remote default-first-option :remote-method="userRemoteMethod" :loading="loading" @change="userChange">
    <el-option v-for="item in data" :key="item.id" :label="`${item.username}${item.realName ? `(${item.realName})` : ''} - [${item.org.name}]`" :value="item.id" />
    <template #footer>
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
    </template>
  </el-select>
</template>

<style lang="scss" scoped></style>
