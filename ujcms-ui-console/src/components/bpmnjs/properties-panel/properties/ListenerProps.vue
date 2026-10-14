<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import draggable from 'vuedraggable';
import { is, isAny, getBusinessObject } from 'bpmn-js/lib/util/ModelUtil';
import { Plus, Grid, Delete, Bell } from '@element-plus/icons-vue';
import LabelTip from '@/components/LabelTip.vue';
import { createElement } from '../utils';

const props = defineProps<{
  modeler: any;
  selection: any;
  collapseItemName: string;
  type: 'task' | 'execution';
}>();
const emit = defineEmits<{
  show: [name: string];
  hide: [name: string];
}>();

const supported = computed(() =>
  props.type === 'task'
    ? is(props.selection, 'bpmn:UserTask')
    : isAny(props.selection, ['bpmn:Activity', 'bpmn:Event', 'bpmn:Gateway', 'bpmn:SequenceFlow', 'bpmn:Process', 'bpmn:Participant']),
);
const listeners = ref<any[]>([]);
const events = computed(() => (props.type === 'task' ? ['create', 'assignment', 'complete', 'delete'] : ['start', 'end', 'take']));
const bpmnType = computed(() => (props.type === 'task' ? 'flowable:TaskListener' : 'flowable:ExecutionListener'));

const toModdle = (item: any) => {
  if (item.type === 'class') {
    return { event: item.event, class: item.class };
  }
  if (item.type === 'expression') {
    return { event: item.event, expression: item.expression };
  }
  return { event: item.event, delegateExpression: item.delegateExpression };
};

const fromModdle = (item: any, index: number) => {
  const now = Date.now();
  if (item.class) {
    return { id: now + index, event: item.event, class: item.class, type: 'class' };
  }
  if (item.class) {
    return { id: now + index, event: item.event, expression: item.expression, type: 'expression' };
  }
  return { id: now + index, event: item.event, delegateExpression: item.delegateExpression, type: 'delegateExpression' };
};

watch(
  () => props.selection,
  () => {
    listeners.value =
      getBusinessObject(props.selection)
        ?.get('extensionElements')
        ?.get('values')
        ?.filter((item: any) => is(item, bpmnType.value))
        ?.map((item: any, index: number) => fromModdle(item, index)) ?? [];
    if (listeners.value?.length > 0) {
      emit('show', props.collapseItemName);
    } else {
      emit('hide', props.collapseItemName);
    }
  },
);

const updateModdle = () => {
  const bpmnFactory = props.modeler.get('bpmnFactory');
  const modeling = props.modeler.get('modeling');
  const businessObject = getBusinessObject(props.selection);
  let extensionElements = businessObject.get('extensionElements');
  if (!extensionElements) {
    extensionElements = createElement('bpmn:ExtensionElements', { values: [] }, businessObject, bpmnFactory);
    modeling.updateModdleProperties(props.selection, businessObject, { extensionElements });
  }
  const listenerProperties = listeners.value.map((item: any) => createElement(bpmnType.value, toModdle(item), extensionElements, bpmnFactory));
  const otherValues = extensionElements.get('values').filter((item: any) => !is(item, bpmnType.value)) ?? [];
  modeling.updateModdleProperties(props.selection, extensionElements, { values: [...otherValues, ...listenerProperties] });
};

const visible = ref<boolean>(false);
const bean = ref<any>();
let listenerIndex: number = 0;

const addListener = () => {
  const now = Date.now();
  listeners.value.push({ id: `id${now}`, event: props.type === 'task' ? 'create' : 'start', type: 'delegateExpression' });
  updateModdle();
};
const deleteListener = (index: number) => {
  listeners.value.splice(index, 1);
  updateModdle();
};
const editListener = (item: any, index: number) => {
  bean.value = { ...item };
  listenerIndex = index;
  visible.value = true;
};
const updateListener = () => {
  listeners.value[listenerIndex] = bean.value;
  visible.value = false;
  updateModdle();
};
</script>

<template>
  <el-collapse-item v-if="supported" :name="collapseItemName">
    <template #title>
      <el-icon class="text-base"><Bell /></el-icon>
      <span class="ml-1 text-sm font-bold">{{ $t(`flowable.groups.listener.${type}`) }}</span>
    </template>
    <div class="mb-2"><el-button :icon="Plus" size="small" circle @click="addListener" /></div>
    <draggable v-model="listeners" :animation="250" handle=".drag-handle" item-key="id" @update="updateModdle">
      <template #item="{ element: item, index }">
        <div class="flex items-center mb-2 mr-1">
          <el-icon class="text-lg cursor-move text-gray-secondary drag-handle">
            <Grid />
          </el-icon>
          <div class="mx-1 grow bpmn-property-item" @click="() => editListener(item, index)">
            {{ `${item.event} ${item.class ?? item.expression ?? item.delegateExpression ?? ''}` }}
          </div>
          <el-icon class="text-lg cursor-pointer text-gray-secondary" @click="() => deleteListener(index)">
            <Delete />
          </el-icon>
        </div>
      </template>
    </draggable>

    <el-dialog v-model="visible" :title="$t(`flowable.groups.listener.${type}`)" width="500" append-to-body>
      <el-form :model="bean" label-width="120px">
        <el-form-item>
          <template #label><label-tip :message="`flowable.listener.${type}.event`" /></template>
          <el-select v-model="bean.event" class="w-full">
            <el-option v-for="item in events" :key="item" :label="$t(`flowable.listener.${type}.event.${item}`)" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <template #label><label-tip :message="`flowable.listener.${type}.type`" /></template>
          <el-select v-model="bean.type" class="w-full">
            <el-option v-for="item in ['class', 'expression', 'delegateExpression']" :key="item" :label="$t(`flowable.listener.${type}.type.${item}`)" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="bean.type === 'class'">
          <template #label><label-tip :message="`flowable.listener.${type}.type.class`" help /></template>
          <el-input v-model="bean.class" />
        </el-form-item>
        <el-form-item v-if="bean.type === 'expression'">
          <template #label><label-tip :message="`flowable.listener.${type}.type.expression`" help /></template>
          <el-input v-model="bean.expression" />
        </el-form-item>
        <el-form-item v-if="bean.type === 'delegateExpression'">
          <template #label><label-tip :message="`flowable.listener.${type}.type.delegateExpression`" help /></template>
          <el-input v-model="bean.delegateExpression" />
        </el-form-item>
      </el-form>
      <div class="flex justify-end mt-2">
        <el-button type="primary" @click="updateListener">{{ $t('ok') }}</el-button>
      </div>
    </el-dialog>
  </el-collapse-item>
</template>

<style lang="scss" scoped></style>
