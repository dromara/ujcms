<script setup lang="ts">
import { toRefs, watch, ref, shallowRef } from 'vue';
import echarts, { ECOption } from '@/utils/echarts';
import { querySystemMonitor, querySystemLoad } from '@/api/personal';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'SystemMonitor',
});
const props = defineProps({ modelValue: { type: Boolean, required: true } });
defineEmits({ 'update:modelValue': null });

const { modelValue: visible } = toRefs(props);
const loading = ref<boolean>(false);
const values = ref<any>({});

const cpuLoadChart = shallowRef<HTMLElement>();
const initCpuLoadChart = (data: number[]) => {
  const chartDom = cpuLoadChart.value;
  if (chartDom == null) {
    return;
  }
  let chart = echarts.getInstanceByDom(chartDom);
  if (chart == null) {
    chart = echarts.init(chartDom);
  }
  const option: ECOption = {
    tooltip: { trigger: 'axis', formatter: '{a}: {c}%' },
    grid: { left: 0, right: 4, top: 16, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      inverse: true,
      splitLine: { show: true, interval: 4, lineStyle: { width: 1, type: 'solid', color: '#d9eaf4' } },
      axisLabel: { interval: 4 },
      axisLine: { show: false },
      axisTick: { show: false },
      data: Array.from(Array(61).keys()),
    },
    yAxis: {
      type: 'value',
      axisLabel: { formatter: '{value}%' },
      splitLine: { show: true, lineStyle: { width: 1, type: 'solid', color: '#d9eaf4' } },
      minInterval: 1,
      min: 0,
      max: 100,
    },
    series: [{ name: '利用率', type: 'line', animation: false, showSymbol: false, lineStyle: { color: '#66abd3', width: 1 }, areaStyle: { color: '#f1f6fa' }, data }],
  };
  chart.setOption(option);
};

const memoryLoadChart = shallowRef<HTMLElement>();
const initMemoryLoadChart = (data: number[]) => {
  const chartDom = memoryLoadChart.value;
  if (chartDom == null) {
    return;
  }
  let chart = echarts.getInstanceByDom(chartDom);
  if (chart == null) {
    chart = echarts.init(chartDom);
  }
  const option: ECOption = {
    tooltip: { trigger: 'axis', formatter: '{a}: {c}%' },
    grid: { left: 0, right: 4, top: 16, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      inverse: true,
      splitLine: { show: true, interval: 4, lineStyle: { width: 1, type: 'solid', color: '#ecdef0' } },
      axisLabel: { interval: 4 },
      axisLine: { show: false },
      axisTick: { show: false },
      data: Array.from(Array(61).keys()),
    },
    yAxis: {
      type: 'value',
      axisLabel: { formatter: '{value}%' },
      splitLine: { show: true, lineStyle: { width: 1, type: 'solid', color: '#ecdef0' } },
      minInterval: 1,
      min: 0,
      max: 100,
    },
    series: [{ name: '利用率', type: 'line', animation: false, showSymbol: false, lineStyle: { color: '#ca93d9', width: 1 }, areaStyle: { color: '#f4f2f4' }, data }],
  };
  chart.setOption(option);
};

const cpuLoad = ref<number>(0);
const memoryLoad = ref<number>(0);
const systemLoadInterval = async () => {
  const systemLoad = await querySystemLoad();
  cpuLoad.value = systemLoad.cpuLoads[0];
  memoryLoad.value = systemLoad.memoryLoads[0];
  initCpuLoadChart(systemLoad.cpuLoads);
  initMemoryLoadChart(systemLoad.memoryLoads);
  values.value.memoryTotal = systemLoad.memoryTotal;
  values.value.memoryUsed = systemLoad.memoryUsed;
  values.value.memoryAvailable = systemLoad.memoryAvailable;
};

const systemLoadTimer = ref<any>();
watch(visible, async () => {
  if (visible.value) {
    loading.value = true;
    try {
      values.value = await querySystemMonitor();
    } finally {
      loading.value = false;
    }

    systemLoadTimer.value = window.setInterval(systemLoadInterval, 2000);
    systemLoadInterval();
  } else {
    window.clearInterval(systemLoadTimer.value);
  }
});
</script>

<template>
  <el-dialog
    :title="$t('menu.personal.homepage.systemMonitor')"
    :model-value="modelValue"
    :width="768"
    top="5vh"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <el-form v-loading="loading" :model="values" label-position="top">
      <el-row>
        <el-col :span="24">
          <el-form-item>
            <template #label><label-tip message="systemMonitor.cpuLoad" /> ({{ cpuLoad }}%)</template>
            <div ref="cpuLoadChart" class="w-full h-48"></div>
            <div class="w-full mt-2 text-xs text-center">
              <div>{{ values.cpuName }}</div>
              <div class="mt-1">
                {{ `${$t('systemMonitor.cpuVendorFreq')}: ${$n((values.cpuVendorFreq ?? 0) / 1000, 'decimal')}GHz,` }}
                {{ `${$t('systemMonitor.cpuCores')}: ${values.cpuCores},` }}
                {{ `${$t('systemMonitor.cpuLogicalCores')}: ${values.cpuLogicalCores},` }}
                {{ `${$t('systemMonitor.osUpDays')}: ${values.osUpDays}` }}
              </div>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item>
            <template #label><label-tip message="systemMonitor.memory" /> ({{ memoryLoad }}%)</template>
            <div ref="memoryLoadChart" class="w-full h-48"></div>
            <div class="w-full mt-2 text-xs text-center">
              <div>{{ values.osName }}</div>
              <div class="mt-1">
                {{ `${$t('systemMonitor.memoryTotal')}: ${$n(Number(values.memoryTotal ?? 10))} MB,` }}
                {{ `${$t('systemMonitor.memoryUsed')}: ${$n(Number(values.memoryUsed ?? 10))} MB,` }}
                {{ `${$t('systemMonitor.memoryAvailable')}: ${$n(Number(values.memoryAvailable ?? 10))} MB` }}
              </div>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item>
            <template #label><label-tip message="systemMonitor.fileStores" /></template>
            <el-table :data="values.fileStores" class="w-full" :border="true">
              <el-table-column prop="mount" :label="$t('systemMonitor.fileStore.mount')" min-width="60" />
              <el-table-column prop="type" :label="$t('systemMonitor.fileStore.type')" min-width="60" />
              <el-table-column :label="$t('systemMonitor.fileStore.space')" min-width="280">
                <template #default="{ row }">
                  <div><el-progress :percentage="Math.round((row.used * 100) / row.total)" /></div>
                  <div class="text-xs">
                    {{ `${$t('systemMonitor.fileStore.total')}: ${row.total} GB,` }}
                    {{ `${$t('systemMonitor.fileStore.used')}: ${row.used} GB,` }}
                    {{ `${$t('systemMonitor.fileStore.usable')}: ${row.usable} GB` }}
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </el-dialog>
</template>
