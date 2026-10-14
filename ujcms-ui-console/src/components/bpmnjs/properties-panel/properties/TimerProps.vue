<script setup lang="ts">
import { ref, watch } from 'vue';
import { is, getBusinessObject } from 'bpmn-js/lib/util/ModelUtil';
import { Timer } from '@element-plus/icons-vue';
import { createElement } from '../utils';
import LabelTip from '@/components/LabelTip.vue';

const props = defineProps<{
  modeler: any;
  selection: any;
  collapseItemName: string;
}>();
const emit = defineEmits<{
  show: [name: string];
  hide: [name: string];
}>();

const propertyTimerType = ref<string>();
const propertyTimeDate = ref<string>();
const propertyTimeDuration = ref<string>();
const propertyTimeCycle = ref<string>();

const getTimer = (businessObject: any) => {
  const eventDefinitions = businessObject.get('eventDefinitions');
  return eventDefinitions?.find((item: any) => is(item, 'bpmn:TimerEventDefinition'));
};

watch(
  () => props.selection,
  () => {
    const businessObject = getBusinessObject(props.selection);
    const timer = getTimer(businessObject);

    propertyTimeDate.value = timer?.get('timeDate')?.get('body');
    propertyTimeDuration.value = timer?.get('timeDuration')?.get('body');
    propertyTimeCycle.value = timer?.get('timeCycle')?.get('body');
    if (propertyTimeDate.value) {
      propertyTimerType.value = 'timeDate';
    } else if (propertyTimeDuration.value) {
      propertyTimerType.value = 'timeDuration';
    } else if (propertyTimeCycle.value) {
      propertyTimerType.value = 'timeCycle';
    } else {
      propertyTimerType.value = timer ? 'timeDuration' : undefined;
    }

    if (timer) {
      emit('show', props.collapseItemName);
    } else {
      emit('hide', props.collapseItemName);
    }
  },
);

const getTimeValue = (timerType: string) => {
  if (timerType === 'timeDate') {
    return propertyTimeDate.value;
  } else if (timerType === 'timeDuration') {
    return propertyTimeDuration.value;
  } else if (timerType === 'timeCycle') {
    return propertyTimeCycle.value;
  } else {
    return '';
  }
};

const changeTimerType = (timerType: string) => {
  const businessObject = getBusinessObject(props.selection);
  const bpmnFactory = props.modeler.get('bpmnFactory');
  const modeling = props.modeler.get('modeling');
  const timer = getTimer(businessObject);
  const timeDefinition = createElement('bpmn:Expression', { body: getTimeValue(timerType) }, timer, bpmnFactory);
  modeling.updateModdleProperties(props.selection, timer, ({
	timeDate: undefined,
	timeDuration: undefined,
	timeCycle: undefined,
	[timerType]: timeDefinition
}));
};
const changeTimeDate = (timeDateValue: string) => {
  const businessObject = getBusinessObject(props.selection);
  const bpmnFactory = props.modeler.get('bpmnFactory');
  const modeling = props.modeler.get('modeling');
  const timer = getTimer(businessObject);
  const timeDate = createElement('bpmn:Expression', { body: timeDateValue }, timer, bpmnFactory);
  modeling.updateModdleProperties(props.selection, timer, { timeDate, timeDuration: undefined, timeCycle: undefined });
};
const changeTimeDuration = (timeDurationValue: string) => {
  const businessObject = getBusinessObject(props.selection);
  const bpmnFactory = props.modeler.get('bpmnFactory');
  const modeling = props.modeler.get('modeling');
  const timer = getTimer(businessObject);
  const timeDuration = createElement('bpmn:Expression', { body: timeDurationValue }, timer, bpmnFactory);
  modeling.updateModdleProperties(props.selection, timer, { timeDuration, timeDate: undefined, timeCycle: undefined });
};
const changeTimeCycle = (timeCycleValue: string) => {
  const businessObject = getBusinessObject(props.selection);
  const bpmnFactory = props.modeler.get('bpmnFactory');
  const modeling = props.modeler.get('modeling');
  const timer = getTimer(businessObject);
  const timeCycle = createElement('bpmn:Expression', { body: timeCycleValue }, timer, bpmnFactory);
  modeling.updateModdleProperties(props.selection, timer, { timeCycle, timeDate: undefined, timeDuration: undefined });
};
</script>

<template>
  <el-collapse-item v-if="propertyTimerType" :name="collapseItemName">
    <template #title>
      <el-icon class="text-base"><Timer /></el-icon>
      <span class="ml-1 text-sm font-bold">{{ $t('flowable.groups.timer') }}</span>
    </template>
    <el-form label-width="100px">
      <el-form-item>
        <template #label><label-tip message="flowable.timer.type" /></template>
        <el-select v-model="propertyTimerType" class="w-full" @change="changeTimerType">
          <el-option v-for="item in ['timeDate', 'timeDuration', 'timeCycle']" :key="item" :label="$t(`flowable.timer.type.${item}`)" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="propertyTimerType === 'timeDate'">
        <template #label><label-tip message="flowable.timer.timeDate" help /></template>
        <el-input v-model="propertyTimeDate" @input="changeTimeDate" />
      </el-form-item>
      <el-form-item v-if="propertyTimerType === 'timeDuration'">
        <template #label><label-tip message="flowable.timer.timeDuration" help /></template>
        <el-input v-model="propertyTimeDuration" @input="changeTimeDuration" />
      </el-form-item>
      <el-form-item v-if="propertyTimerType === 'timeCycle'">
        <template #label><label-tip message="flowable.timer.timeCycle" help /></template>
        <el-input v-model="propertyTimeCycle" @input="changeTimeCycle" />
      </el-form-item>
    </el-form>
  </el-collapse-item>
</template>

<style lang="scss" scoped></style>
