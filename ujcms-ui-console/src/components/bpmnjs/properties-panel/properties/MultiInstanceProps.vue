<script setup lang="ts">
import { ref, watch } from 'vue';
import { is, getBusinessObject } from 'bpmn-js/lib/util/ModelUtil';
import { Orange } from '@element-plus/icons-vue';
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

const propertyMultiType = ref<string>('none');
const propertyCollection = ref<string>();
const propertyElementVariable = ref<string>();
const propertyLoopCardinality = ref<string>();
const propertyCompletionCondition = ref<string>();

watch(
  () => props.selection,
  () => {
    const businessObject = getBusinessObject(props.selection);
    const loopCharacteristics = businessObject.get('loopCharacteristics');
    if (loopCharacteristics) {
      propertyMultiType.value = loopCharacteristics.isSequential ? 'sequential' : 'parallel';
      emit('show', props.collapseItemName);
    } else {
      propertyMultiType.value = 'none';
      emit('hide', props.collapseItemName);
    }
    propertyCollection.value = loopCharacteristics?.collection;
    propertyElementVariable.value = loopCharacteristics?.elementVariable;
    propertyLoopCardinality.value = loopCharacteristics?.get('loopCardinality')?.get('body');
    propertyCompletionCondition.value = loopCharacteristics?.get('completionCondition')?.get('body');
  },
);

const changeMultiType = (multiTypeValue: string) => {
  const businessObject = getBusinessObject(props.selection);
  const bpmnFactory = props.modeler.get('bpmnFactory');
  const modeling = props.modeler.get('modeling');
  if (multiTypeValue === 'none') {
    modeling.updateModdleProperties(props.selection, businessObject, { loopCharacteristics: undefined });
    return;
  }
  const loopCharacteristics = createElement(
    'bpmn:MultiInstanceLoopCharacteristics',
    { isSequential: multiTypeValue === 'sequential' ? 'true' : 'false', collection: propertyCollection.value, elementVariable: propertyElementVariable.value },
    businessObject,
    bpmnFactory,
  );
  modeling.updateModdleProperties(props.selection, businessObject, { loopCharacteristics });
};

const changeCollection = (collection: string) => {
  const businessObject = getBusinessObject(props.selection);
  const modeling = props.modeler.get('modeling');
  modeling.updateModdleProperties(props.selection, businessObject.get('loopCharacteristics'), { collection });
};
const changeElementVariable = (elementVariable: string) => {
  const businessObject = getBusinessObject(props.selection);
  const modeling = props.modeler.get('modeling');
  modeling.updateModdleProperties(props.selection, businessObject.get('loopCharacteristics'), { elementVariable });
};
const changeLoopCardinality = (loopCardinalityValue: string) => {
  const businessObject = getBusinessObject(props.selection);
  const bpmnFactory = props.modeler.get('bpmnFactory');
  const modeling = props.modeler.get('modeling');
  const loopCharacteristics = businessObject.get('loopCharacteristics');
  const loopCardinality = createElement('bpmn:Expression', { body: loopCardinalityValue }, loopCharacteristics, bpmnFactory);
  modeling.updateModdleProperties(props.selection, loopCharacteristics, { loopCardinality });
};
const changeCompletionCondition = (completionConditionValue: string) => {
  const businessObject = getBusinessObject(props.selection);
  const bpmnFactory = props.modeler.get('bpmnFactory');
  const modeling = props.modeler.get('modeling');
  const loopCharacteristics = businessObject.get('loopCharacteristics');
  const completionCondition = createElement('bpmn:Expression', { body: completionConditionValue }, loopCharacteristics, bpmnFactory);
  modeling.updateModdleProperties(props.selection, businessObject.get('loopCharacteristics'), { completionCondition });
};
</script>

<template>
  <el-collapse-item v-if="is(selection, 'bpmn:UserTask')" :name="collapseItemName">
    <template #title>
      <el-icon class="text-base"><Orange /></el-icon>
      <span class="ml-1 text-sm font-bold">{{ $t('flowable.groups.multiInstance') }}</span>
    </template>
    <el-form label-width="100px">
      <el-form-item>
        <template #label><label-tip message="flowable.multiInstance.type" /></template>
        <el-select v-model="propertyMultiType" class="w-full" @change="changeMultiType">
          <el-option v-for="item in ['none', 'parallel', 'sequential']" :key="item" :label="$t(`flowable.multiInstance.type.${item}`)" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="propertyMultiType !== 'none'">
        <template #label><label-tip message="flowable.multiInstance.collection" help /></template>
        <el-input v-model="propertyCollection" @input="changeCollection" />
      </el-form-item>
      <el-form-item v-if="propertyMultiType !== 'none'">
        <template #label><label-tip message="flowable.multiInstance.elementVariable" help /></template>
        <el-input v-model="propertyElementVariable" @input="changeElementVariable" />
      </el-form-item>
      <el-form-item v-if="propertyMultiType !== 'none'">
        <template #label><label-tip message="flowable.multiInstance.loopCardinality" /></template>
        <el-input v-model="propertyLoopCardinality" @input="changeLoopCardinality" />
      </el-form-item>
      <el-form-item v-if="propertyMultiType !== 'none'">
        <template #label><label-tip message="flowable.multiInstance.completionCondition" help /></template>
        <el-input v-model="propertyCompletionCondition" @input="changeCompletionCondition" />
      </el-form-item>
    </el-form>
  </el-collapse-item>
</template>

<style lang="scss" scoped></style>
