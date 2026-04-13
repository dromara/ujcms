<script setup lang="ts">
import { toRefs, watch, ref, shallowRef } from 'vue';
import { useI18n } from 'vue-i18n';
import echarts, { ECOption } from '@/utils/echarts';
import { querySystemInfo } from '@/api/personal';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'SystemInfo',
});
const props = defineProps({ modelValue: { type: Boolean, required: true } });
defineEmits({ 'update:modelValue': null });

const { t, n } = useI18n();
const { modelValue: visible } = toRefs(props);
const loading = ref<boolean>(false);
const values = ref<any>({});
const title = import.meta.env.VITE_APP_NAME || 'UJCMS';

const memoryChart = shallowRef<HTMLElement>();

const initMemoryChart = () => {
  const option: ECOption = {
    tooltip: { trigger: 'item', valueFormatter: (value: any) => `${n(Number(value))} MB` },
    series: [
      {
        name: t('systemInfo.memory'),
        type: 'pie',
        radius: ['52%', '92%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: false } },
        labelLine: { show: false },
        data: [
          { value: values.value.usedMemory, name: t('systemInfo.usedMemory') },
          { value: values.value.freeMemory, name: t('systemInfo.freeMemory') },
          { value: values.value.remainingMemory, name: t('systemInfo.remainingMemory'), itemStyle: { color: '#ccc' } },
        ],
      },
    ],
  };

  const chartDom = memoryChart.value;
  if (chartDom == null) {
    return;
  }
  let chart = echarts.getInstanceByDom(chartDom);
  if (chart == null) {
    chart = echarts.init(chartDom);
  }
  chart.setOption(option);
};

watch(visible, async () => {
  if (visible.value) {
    loading.value = true;
    try {
      values.value = await querySystemInfo();
    } finally {
      loading.value = false;
    }
    initMemoryChart();
  }
});
</script>

<template>
  <el-dialog :title="$t('menu.personal.homepage.systemInfo')" :model-value="modelValue" :width="768" top="5vh" @update:model-value="(event) => $emit('update:modelValue', event)">
    <el-form v-loading="loading" :model="values" label-width="150px" label-position="right">
      <el-row>
        <el-col :span="24">
          <el-form-item :label="title + $t('systemInfo.version')">
            <el-input :value="values.version" :readonly="true"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item :label="$t('systemInfo.os')">
            <el-input :value="[values.osName, values.osArch, values.osVersion].join(', ')" :readonly="true"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item :label="$t('systemInfo.java')">
            <el-input :value="[values.javaRuntimeName, values.javaRuntimeVersion, values.javaVendor].join(', ')" :readonly="true"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item :label="$t('systemInfo.javaVm')">
            <el-input :value="[values.javaVmName, values.javaVmVersion, values.javaVmVendor].join(', ')" :readonly="true"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item :label="$t('systemInfo.userName')">
            <el-input :value="values.userName" :readonly="true"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item :label="$t('systemInfo.userDir')">
            <el-input :value="values.userDir" :readonly="true"></el-input>
          </el-form-item>
          <el-form-item :label="$t('systemInfo.javaIoTmpdir')">
            <el-input :value="values.javaIoTmpdir" :readonly="true"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item>
            <template #label><label-tip message="systemInfo.upDays" help /></template>
            <el-input :value="values.upDays" :readonly="true" input-style="text-align: right">
              <template #append>{{ $t('systemInfo.upDays.unit') }}</template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <template #label><label-tip message="systemInfo.maxMemory" help /></template>
            <el-input :value="$n(Number(values.maxMemory ?? 0))" :readonly="true" input-style="text-align: right"><template #append>MB</template></el-input>
          </el-form-item>
          <el-form-item>
            <template #label><label-tip message="systemInfo.totalMemory" help /></template>
            <el-input :value="$n(Number(values.totalMemory ?? 0))" :readonly="true" input-style="text-align: right"><template #append>MB</template></el-input>
          </el-form-item>
          <el-form-item>
            <template #label><label-tip message="systemInfo.usedMemory" /></template>
            <el-input :value="$n(Number(values.usedMemory ?? 0))" :readonly="true" input-style="text-align: right"><template #append>MB</template></el-input>
          </el-form-item>
          <el-form-item>
            <template #label><label-tip message="systemInfo.freeMemory" help /></template>
            <el-input :value="$n(Number(values.freeMemory ?? 0))" :readonly="true" input-style="text-align: right"><template #append>MB</template></el-input>
          </el-form-item>
          <el-form-item>
            <template #label><label-tip message="systemInfo.availableMemory" help /></template>
            <el-input :value="$n(Number(values.availableMemory ?? 0))" :readonly="true" input-style="text-align: right"><template #append>MB</template></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12" class="flex items-center justify-center">
          <div ref="memoryChart" class="w-60 h-60"></div>
        </el-col>
      </el-row>
    </el-form>
  </el-dialog>
</template>
