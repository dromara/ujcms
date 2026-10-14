<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Search } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import { pageSizes, pageLayout } from '@/utils/common';
import { queryArticleStatByOrg } from '@/api/stat';

const loading = ref<boolean>(false);
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<any[]>([]);
const range = ref<any>([]);

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryArticleStatByOrg({ begin: range.value?.[0], end: range.value?.[1] });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};

onMounted(async () => {
  range.value[0] = dayjs().subtract(1, 'month').add(1, 'day').startOf('day').toDate();
  range.value[1] = dayjs().startOf('day').toDate();
  fetchData();
});
</script>

<template>
  <div>
    <div class="p-3 mt-3 app-block">
      <form class="flex">
        <div class="space-y-1">
          <el-date-picker v-model="range" type="daterange" />
        </div>
        <div>
          <el-button-group class="ml-2">
            <el-button native-type="submit" :icon="Search" @click.prevent="() => fetchData()">{{ $t('search') }}</el-button>
          </el-button-group>
        </div>
      </form>
      <div class="mt-2">
        <el-table ref="table" v-loading="loading" :data="data">
          <el-table-column :label="$t('articleStat.org')" min-width="180">
            <template #default="{ row }">
              <span>{{ row.org.names?.join(' / ') }}</span>
            </template>
          </el-table-column>
          <el-table-column property="total" :label="$t('articleStat.total')" min-width="80"></el-table-column>
          <el-table-column property="published" :label="$t('articleStat.published')" min-width="80"></el-table-column>
          <el-table-column property="unpublished" :label="$t('articleStat.unpublished')" min-width="80"></el-table-column>
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
    </div>
  </div>
</template>
