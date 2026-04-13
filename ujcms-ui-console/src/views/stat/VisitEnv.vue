<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import echarts, { ECOption } from '@/utils/echarts';
import { queryDeviceStat, queryOsStat, queryBrowserStat } from '@/api/stat';

const { t, n } = useI18n();

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

const deviceChartRef = shallowRef<HTMLElement>();
const initDeviceChart = async (range: string) => {
  const list = await queryDeviceStat({ begin: getBeginByDateRange(range), end: getEndByDateRange(range) });
  const total = list.reduce((acc, curr) => acc + Number(curr.pvCount), 0);
  const option: ECOption = {
    title: { text: t('visitDevice.name'), textStyle: { color: '#909399', fontWeight: 'normal', fontSize: 16 } },
    tooltip: { trigger: 'item', valueFormatter: (value: any) => n((value * 100) / total, 'decimal') + '%' },
    legend: { type: 'scroll', orient: 'vertical', right: '10%', top: 16, bottom: 16 },
    series: [
      {
        name: t('visitDevice.name'),
        type: 'pie',
        radius: '72%',
        center: ['40%', '56%'],
        data: list.map((item) => ({ value: Number(item.pvCount), name: item.name })),
      },
    ],
  };
  const chartDom = deviceChartRef.value;
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

const osChartRef = shallowRef<HTMLElement>();
const initOsChart = async (range: string) => {
  const list = await queryOsStat({ begin: getBeginByDateRange(range), end: getEndByDateRange(range) });
  const total = list.reduce((acc, curr) => acc + Number(curr.pvCount), 0);
  const option: ECOption = {
    title: { text: t('visitOs.name'), textStyle: { color: '#909399', fontWeight: 'normal', fontSize: 16 } },
    tooltip: { trigger: 'item', valueFormatter: (value: any) => n((value * 100) / total, 'decimal') + '%' },
    legend: { type: 'scroll', orient: 'vertical', right: '10%', top: 16, bottom: 16 },
    series: [
      {
        name: t('visitOs.name'),
        type: 'pie',
        radius: '72%',
        center: ['40%', '56%'],
        data: list.map((item) => ({ value: Number(item.pvCount), name: item.name })),
      },
    ],
  };
  const chartDom = osChartRef.value;
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

const browserChartRef = shallowRef<HTMLElement>();
const initBrowserChart = async (range: string) => {
  const list = await queryBrowserStat({ begin: getBeginByDateRange(range), end: getEndByDateRange(range) });
  const total = list.reduce((acc, curr) => acc + Number(curr.pvCount), 0);
  const option: ECOption = {
    title: { text: t('visitBrowser.name'), textStyle: { color: '#909399', fontWeight: 'normal', fontSize: 16 } },
    tooltip: { trigger: 'item', valueFormatter: (value: any) => n((value * 100) / total, 'decimal') + '%' },
    legend: { type: 'scroll', orient: 'vertical', right: '10%', top: 16, bottom: 16 },
    series: [
      {
        name: t('visitBrowser.name'),
        type: 'pie',
        radius: '72%',
        center: ['40%', '56%'],
        data: list.map((item) => ({ value: Number(item.pvCount), name: item.name })),
      },
    ],
  };
  const chartDom = browserChartRef.value;
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

const initChart = async (range: string) => {
  initDeviceChart(range);
  initOsChart(range);
  initBrowserChart(range);
};

onMounted(async () => {
  initChart(dateRange.value);
});
</script>

<template>
  <div>
    <div class="p-3 mt-3 app-block">
      <div>
        <el-radio-group v-model="dateRange" @change="(value: any) => initChart(value)">
          <el-radio-button v-for="item in ['today', 'yesterday', 'last7day', 'last30day', 'last3month', 'last6month', 'lastYear', 'all']" :key="item" :value="item">
            {{ $t(`visit.${item}`) }}
          </el-radio-button>
        </el-radio-group>
      </div>
    </div>
    <el-row :gutter="12">
      <el-col :span="12">
        <div class="p-3 mt-3 app-block">
          <div ref="deviceChartRef" class="h-80"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="p-3 mt-3 app-block">
          <div ref="osChartRef" class="h-80"></div>
        </div>
      </el-col>
    </el-row>
    <div class="p-3 mt-3 app-block">
      <div ref="browserChartRef" class="h-80"></div>
    </div>
  </div>
</template>
