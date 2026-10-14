<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Search } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import { pageSizes, pageLayout } from '@/utils/common';
import { queryPerformanceStatByOrg } from '@/api/stat';
import { queryPerformanceTypeList } from '@/api/config';

const loading = ref<boolean>(false);
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<any[]>([]);
const performanceTypeList = ref<any[]>([]);
const range = ref<any>([]);

const fetchPerformanceTypeList = async () => {
  performanceTypeList.value = await queryPerformanceTypeList();
};

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryPerformanceStatByOrg({ begin: range.value?.[0], end: range.value?.[1] });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};

onMounted(async () => {
  fetchPerformanceTypeList();
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
          <el-table-column :label="$t('performanceStat.org')" min-width="80">
            <template #default="{ row }">
              <span>{{ row.org.names?.join(' / ') }}</span>
            </template>
          </el-table-column>

          <el-table-column v-for="item in performanceTypeList" :key="item.id" :label="`${item.name} (${item.score}${$t('performanceStat.score')})`" min-width="80">
            <template #default="{ row }">
              <span>{{ row.performanceMap[item.id] ?? 0 }}</span>
            </template>
          </el-table-column>

          <el-table-column property="totalCount" :label="$t('performanceStat.totalCount')" min-width="80"></el-table-column>
          <el-table-column property="totalScore" :label="$t('performanceStat.totalScore')" min-width="80"></el-table-column>
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
