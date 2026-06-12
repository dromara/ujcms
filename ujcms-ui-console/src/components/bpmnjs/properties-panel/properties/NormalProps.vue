<script setup lang="ts">
import { ref, watch } from 'vue';
import { getBusinessObject } from 'bpmn-js/lib/util/ModelUtil';
import { Setting } from '@element-plus/icons-vue';
import LabelTip from '@/components/LabelTip.vue';

const props = defineProps<{
  modeler: any;
  selection: any;
  collapseItemName: string;
}>();

const propertyName = ref<string>();
const propertyId = ref<string>();

watch(
  () => props.selection,
  () => {
    const businessObject = getBusinessObject(props.selection);

    propertyName.value = businessObject.name;
    propertyId.value = businessObject.id;
  },
);

const changeName = (name: string) => {
  const modeling = props.modeler.get('modeling');
  modeling.updateProperties(props.selection, { name });
};
const changeId = (id: string) => {
  const modeling = props.modeler.get('modeling');
  modeling.updateProperties(props.selection, { id });
};
</script>

<template>
  <el-collapse-item :name="collapseItemName">
    <template #title>
      <el-icon class="text-base"><Setting /></el-icon><span class="ml-1 text-sm font-bold">{{ $t('flowable.groups.normal') }}</span>
    </template>
    <el-form label-width="100px">
      <el-form-item>
        <template #label><label-tip message="flowable.normal.name" /></template>
        <!-- :disabled="is(selection, 'bpmn:Process')" -->
        <el-input v-model="propertyName" @input="changeName" />
      </el-form-item>
      <el-form-item>
        <template #label><label-tip message="flowable.normal.id" /></template>
        <el-input v-model="propertyId" disabled @input="changeId" />
      </el-form-item>
    </el-form>
  </el-collapse-item>
</template>

<style lang="scss" scoped></style>
