<script setup lang="ts">
import { ref, watch, markRaw } from 'vue';
import NormalProps from './properties/NormalProps.vue';
import ConditionProps from './properties/ConditionProps.vue';
import AssignmentProps from './properties/AssignmentProps.vue';
import MultiInstanceProps from './properties/MultiInstanceProps.vue';
import TimerProps from './properties/TimerProps.vue';
import FormProps from './properties/FormProps.vue';
import ListenerProps from './properties/ListenerProps.vue';
/**
 * https://github.com/bpmn-io/bpmn-js/blob/develop/lib/features/modeling/Modeling.js
 */
const props = defineProps<{
  modeler: any;
  queryRoles: (params?: Record<string, any>) => Promise<any>;
  queryOrgs: (params?: Record<string, any>) => Promise<any>;
}>();

const selection = ref<any>();
const activeNames = ref(['1', '2']);

const addActiveName = (name: string) => {
  if (!activeNames.value.includes(name)) {
    activeNames.value.push(name);
  }
};

const removeActiveName = (name: string) => {
  const index = activeNames.value.indexOf(name);
  if (index !== -1) {
    activeNames.value.splice(index, 1);
  }
};

watch(
  () => props.modeler,
  () => {
    if (props.modeler) {
      props.modeler.on('selection.changed', (event: any) => {
        const { newSelection = [] } = event;
        selection.value = markRaw(newSelection[0] ?? props.modeler.get('canvas').getRootElement());
      });
    }
  },
);
</script>

<template>
  <el-collapse v-model="activeNames" class="pl-2 border-t-0">
    <normal-props :modeler :selection="selection" collapse-item-name="1" />
    <assignment-props :modeler :selection="selection" collapse-item-name="2" :query-orgs="queryOrgs" :query-roles="queryRoles" />
    <condition-props :modeler :selection="selection" collapse-item-name="3" @show="addActiveName" @hide="removeActiveName" />
    <form-props :modeler :selection="selection" collapse-item-name="4" @show="addActiveName" @hide="removeActiveName" />
    <multi-instance-props :modeler :selection="selection" collapse-item-name="5" @show="addActiveName" @hide="removeActiveName" />
    <listener-props :modeler :selection="selection" collapse-item-name="6" type="execution" @show="addActiveName" @hide="removeActiveName" />
    <listener-props :modeler :selection="selection" collapse-item-name="7" type="task" @show="addActiveName" @hide="removeActiveName" />
    <timer-props :modeler :selection="selection" collapse-item-name="8" @show="addActiveName" @hide="removeActiveName" />
  </el-collapse>
</template>

<style lang="scss" scoped>
:deep(.el-collapse-item__content) {
  padding-bottom: 0;
}
</style>
