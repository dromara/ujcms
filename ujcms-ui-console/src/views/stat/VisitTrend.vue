<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import echarts, { ECOption } from '@/utils/echarts';
import { queryTrendStat } from '@/api/stat';

const { t } = useI18n();

const dateRange = ref<string>('last30day');
const getBeginByDateRange = (range: string): string | undefined => {
  switch (range) {
    case 'now':
      return dayjs().subtract(2, 'hour').format();
    case 'today':
      return dayjs().startOf('day').format();
    case 'yesterday':
      return dayjs().startOf('day').subtract(1, 'day').format();
    case 'last7day':
      return dayjs().startOf('day').subtract(6, 'day').format();
    case 'last30day':
      return dayjs().startOf('day').subtract(29, 'day').format();
    case 'last3month':
      return dayjs().startOf('day').subtract(3, 'month').format();
    case 'last6month':
      return dayjs().startOf('day').subtract(6, 'month').format();
    case 'lastYear':
      return dayjs().startOf('day').subtract(1, 'year').format();
    default:
      return undefined;
  }
};

const getEndByDateRange = (range: string): string => {
  if (range === 'now') {
    return dayjs().format();
  }
  if (range === 'yesterday') {
    return dayjs().endOf('day').subtract(1, 'day').format();
  }
  return dayjs().endOf('day').format();
};

const trendChart = shallowRef<HTMLElement>();
const initTrendChart = async (range: string) => {
  const list = await queryTrendStat({ begin: getBeginByDateRange(range), end: getEndByDateRange(range) });
  const option: ECOption = {
    tooltip: { trigger: 'axis' },
    legend: { data: [t('visit.pv'), t('visit.uv'), t('visit.ip')] },
    grid: { left: 16, right: 24, top: 40, bottom: 8, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, axisTick: { show: false }, data: list.map((item) => item.dateString) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: t('visit.pv'),
        type: 'line',
        symbol: list.length > 30 ? 'none' : 'circle',
        data: list.map((item) => Number(item.pvCount)),
      },
      {
        name: t('visit.uv'),
        type: 'line',
        symbol: list.length > 30 ? 'none' : 'circle',
        data: list.map((item) => Number(item.uvCount)),
      },
      {
        name: t('visit.ip'),
        type: 'line',
        symbol: list.length > 30 ? 'none' : 'circle',
        data: list.map((item) => Number(item.ipCount)),
      },
    ],
  };
  const chartDom = trendChart.value;
  if (chartDom == null) {
    return;
  }
  let chart = echarts.getInstanceByDom(chartDom);
  if (chart == null) {
    chart = echarts.init(chartDom);
  }
  chart.setOption(option);
  window.addEventListener('resize', function () {
    chart?.resize();
  });
};

onMounted(async () => {
  initTrendChart(dateRange.value);
});
</script>

<template>
  <div>
    <div class="p-3 mt-3 app-block">
      <div>
        <el-radio-group v-model="dateRange" @change="(value: any) => initTrendChart(value)">
          <el-radio-button v-for="item in ['today', 'yesterday', 'last7day', 'last30day', 'last3month', 'last6month', 'lastYear', 'all']" :key="item" :value="item">
            {{ $t(`visit.${item}`) }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div ref="trendChart" class="h-80"></div>
    </div>
  </div>
</template>
