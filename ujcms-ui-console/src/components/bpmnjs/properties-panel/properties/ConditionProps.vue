<script setup lang="ts">
import { ref, watch } from 'vue';
import { is, getBusinessObject } from 'bpmn-js/lib/util/ModelUtil';
import { createElement } from '../utils';
import { TopRight } from '@element-plus/icons-vue';
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

const propertyConditionExpression = ref<string>();

watch(
  () => props.selection,
  () => {
    const businessObject = getBusinessObject(props.selection);
    const conditionExpression = businessObject.conditionExpression;
    propertyConditionExpression.value = conditionExpression?.get('body');
    if (conditionExpression) {
      emit('show', props.collapseItemName);
    } else {
      emit('hide', props.collapseItemName);
    }
  },
);

const changeConditionExpression = (conditionExpression: string) => {
  const businessObject = getBusinessObject(props.selection);
  const bpmnFactory = props.modeler.get('bpmnFactory');
  const modeling = props.modeler.get('modeling');
  const formalExpressionElement = createElement('bpmn:FormalExpression', { body: conditionExpression }, businessObject, bpmnFactory);
  modeling.updateProperties(props.selection, { conditionExpression: formalExpressionElement });
};
</script>

<template>
  <el-collapse-item v-if="is(selection, 'bpmn:SequenceFlow')" :name="collapseItemName">
    <template #title>
      <el-icon class="text-base"><TopRight /></el-icon><span class="ml-1 text-sm font-bold">{{ $t('flowable.groups.condition') }}</span>
    </template>
    <el-form label-width="100px">
      <el-form-item>
        <template #label><label-tip message="flowable.normal.conditionExpression" help /></template>
        <el-input v-model="propertyConditionExpression" @input="changeConditionExpression" />
      </el-form-item>
    </el-form>
  </el-collapse-item>
</template>

<style lang="scss" scoped></style>
