<script setup lang="ts">
import { onMounted, ref } from 'vue';
import dayjs from 'dayjs';
import { pageSizes, pageLayout } from '@/utils/common';
import { querySourceStat } from '@/api/stat';

const dateRange = ref<string>('last30day');
const getBeginByDateRange = (range: string): string | undefined => {
  switch (range) {
    case 'today':
      return dayjs().format('YYYY-MM-DD');
    case 'yesterday':
      return dayjs().subtract(1, 'day').format('YYYY-MM-DD');
    case 'last7day':
      return dayjs().subtract(6, 'day').format('YYYY-MM-DD');
    case 'last30day':
      return dayjs().subtract(29, 'day').format('YYYY-MM-DD');
    case 'last3month':
      return dayjs().subtract(3, 'month').format('YYYY-MM-DD');
    case 'last6month':
      return dayjs().subtract(6, 'month').format('YYYY-MM-DD');
    case 'lastYear':
      return dayjs().subtract(1, 'year').format('YYYY-MM-DD');
    default:
      return undefined;
  }
};

const getEndByDateRange = (range: string): string => {
  if (range === 'yesterday') {
    return dayjs().subtract(1, 'day').format('YYYY-MM-DD');
  }
  return dayjs().format('YYYY-MM-DD');
};

const loading = ref<boolean>(false);
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<any[]>([]);
const fetchData = async (range: string) => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await querySourceStat({
      begin: getBeginByDateRange(range),
      end: getEndByDateRange(range),
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
  fetchData(dateRange.value);
});
</script>

<template>
  <div>
    <div class="p-3 mt-3 app-block">
      <div>
        <el-radio-group v-model="dateRange" @change="(value: any) => fetchData(value)">
          <el-radio-button v-for="item in ['today', 'yesterday', 'last7day', 'last30day', 'last3month', 'last6month', 'lastYear', 'all']" :key="item" :value="item">
            {{ $t(`visit.${item}`) }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="mt-2">
        <el-table ref="table" v-loading="loading" :data="data">
          <el-table-column property="name" :label="$t('visitSource.name')" min-width="260"></el-table-column>
          <el-table-column property="pvCount" :label="$t('visit.pv')" min-width="80"></el-table-column>
          <el-table-column property="uvCount" :label="$t('visit.uv')" min-width="80"></el-table-column>
          <el-table-column property="ipCount" :label="$t('visit.ip')" min-width="80"></el-table-column>
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
          @size-change="() => fetchData(dateRange)"
          @current-change="() => fetchData(dateRange)"
        ></el-pagination>
      </div>
    </div>
  </div>
</template>
