<script setup lang="ts">
import { ref, watch } from 'vue';
import draggable from 'vuedraggable';
import { is, isAny, getBusinessObject } from 'bpmn-js/lib/util/ModelUtil';
import { Plus, Grid, Delete, Tickets } from '@element-plus/icons-vue';
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

const BPMN_TYPE = 'flowable:FormProperty';
const properties = ref<any[]>([]);

const fromModdle = (item: any) => {
  return {
    id: item.id,
    name: item.name,
    type: item.type,
    default: item.default,
    required: item.required,
    values: item.values?.map((value: any) => ({ id: value.id, name: value.name })),
  };
};

const createPropertyElement = (item: any, parent: any, bpmnFacotry: any) => {
  const itemElements = item.values?.map((value: any) => createElement('flowable:Value', { id: value.id, name: value.name }, undefined, bpmnFacotry));
  return createElement(BPMN_TYPE, { id: item.id, name: item.name, type: item.type, default: item.default, required: item.required, values: itemElements }, parent, bpmnFacotry);
};

watch(
  () => props.selection,
  () => {
    properties.value =
      getBusinessObject(props.selection)
        ?.get('extensionElements')
        ?.get('values')
        ?.filter((item: any) => is(item, BPMN_TYPE))
        ?.map((item: any) => fromModdle(item)) ?? [];
    if (properties.value?.length > 0) {
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
  const propertyElements = properties.value.map((item: any) => createPropertyElement(item, extensionElements, bpmnFactory));
  const otherValues = extensionElements.get('values').filter((item: any) => !is(item, BPMN_TYPE)) ?? [];
  modeling.updateModdleProperties(props.selection, extensionElements, { values: [...otherValues, ...propertyElements] });
};

const visible = ref<boolean>(false);
const form = ref<any>();
const property = ref<any>();
let propertyIndex: number = 0;

const addProperty = () => {
  const now = Date.now();
  properties.value.push({ id: `id${now}`, name: `name${now}`, type: 'string' });
  updateModdle();
};
const deleteProperty = (index: number) => {
  properties.value.splice(index, 1);
  updateModdle();
};
const editProperty = (item: any, index: number) => {
  property.value = { ...item };
  propertyIndex = index;
  visible.value = true;
};
const updateProperty = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    properties.value[propertyIndex] = property.value;
    visible.value = false;
    updateModdle();
  });
};

const optionTypeChange = (value: string) => {
  if (value === 'enum') {
    property.value.values = property.value.values ?? [];
  } else {
    property.value.values = undefined;
  }
};

const addOption = () => {
  const now = Date.now();
  property.value.values.push({ id: `value${now}`, name: `name${now}`, key: Date.now() });
  form.value.validate(() => {});
};
const deleteOption = (option: any) => {
  property.value.values = property.value.values.filter((item: any) => item.id !== option.id);
};
</script>

<template>
  <el-collapse-item v-if="isAny(selection, ['bpmn:UserTask', 'bpmn:StartEvent'])" :name="collapseItemName">
    <template #title>
      <el-icon class="text-base"><Tickets /></el-icon>
      <span class="ml-1 text-sm font-bold">{{ $t('flowable.groups.form') }}</span>
    </template>
    <div class="mb-2"><el-button :icon="Plus" size="small" circle @click="addProperty" /></div>
    <draggable v-model="properties" :animation="250" handle=".drag-handle" item-key="id" @update="updateModdle">
      <template #item="{ element: item, index }">
        <div class="flex items-center mb-2 mr-1">
          <el-icon class="text-lg cursor-move text-gray-secondary drag-handle">
            <Grid />
          </el-icon>
          <div class="mx-1 grow bpmn-property-item" @click="() => editProperty(item, index)">{{ `${item.name}(${item.id})` }}</div>
          <el-icon class="text-lg cursor-pointer text-gray-secondary" @click="() => deleteProperty(item.id)">
            <Delete />
          </el-icon>
        </div>
      </template>
    </draggable>

    <el-dialog v-model="visible" :title="$t('flowable.form.property')" width="800" append-to-body>
      <el-form ref="form" :model="property" label-width="120px">
        <el-form-item
          prop="id"
          :rules="[
            { required: true, message: () => $t('v.required') },
            {
              validator: (rule: any, value: any, callback: any) => {
                if (properties.map((item) => item.id).includes(value) && properties[propertyIndex].id !== value) {
                  callback($t('flowable.form.error.duplicate'));
                  return;
                }
                callback();
              },
            },
          ]"
        >
          <template #label><label-tip message="flowable.form.id" /></template>
          <el-input v-model="property.id" />
        </el-form-item>
        <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="flowable.form.name" /></template>
          <el-input v-model="property.name" />
        </el-form-item>
        <el-form-item prop="type" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="flowable.form.type" /></template>
          <el-select v-model="property.type" class="w-full" @change="optionTypeChange">
            <el-option v-for="item in ['string', 'long', 'boolean', 'enum']" :key="item" :label="$t(`flowable.form.type.${item}`)" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="property.type === 'date'">
          <template #label><label-tip message="flowable.form.datePattern" /></template>
          <el-select v-model="property.datePattern" class="w-full">
            <el-option v-for="item in ['yyyy-MM-dd HH:mm:dd', 'yyyy-MM-dd']" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="property.type === 'enum'" prop="values" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="flowable.form.options" /></template>
          <table class="w-full">
            <thead>
              <tr>
                <td class="w-8"><el-button :icon="Plus" size="small" circle @click="addOption" /></td>
                <td class="w-7"></td>
                <td class="font-bold">{{ $t('flowable.form.options.name') }}</td>
                <td class="font-bold">{{ $t('flowable.form.options.id') }}</td>
              </tr>
            </thead>
            <draggable v-model="property.values" tag="tbody" :animation="250" handle=".drag-handle" item-key="key">
              <template #item="{ element: option, index }">
                <tr>
                  <td><el-button :icon="Delete" size="small" circle @click="() => deleteOption(option)" /></td>
                  <td>
                    <el-icon class="text-lg align-middle cursor-move text-gray-secondary drag-handle">
                      <Grid />
                    </el-icon>
                  </td>
                  <td>
                    <el-form-item :prop="`values[${index}].name`" :rules="{ required: true, message: () => $t('v.required') }">
                      <el-input v-model="option.name" />
                    </el-form-item>
                  </td>
                  <td>
                    <el-form-item
                      :prop="`values[${index}].id`"
                      :rules="[
                        { required: true, message: () => $t('v.required') },
                        {
                          validator: (rule: any, value: any, callback: any) => {
                            if (property.values.map((item: any) => item.id).filter((item: any) => item === value).length > 1) {
                              callback($t('flowable.form.error.duplicate'));
                              return;
                            }
                            callback();
                          },
                        },
                      ]"
                    >
                      <el-input v-model="option.id" />
                    </el-form-item>
                  </td>
                </tr>
              </template>
            </draggable>
          </table>
        </el-form-item>
        <el-form-item prop="default" :rules="{ required: property.required, message: () => $t('v.required') }">
          <template #label><label-tip message="flowable.form.default" /></template>
          <el-input v-model="property.default" />
        </el-form-item>
        <el-form-item>
          <template #label><label-tip message="flowable.form.required" /></template>
          <el-switch v-model="property.required" />
        </el-form-item>
        <div class="flex justify-end mt-2">
          <el-button type="primary" native-type="submit" @click.prevent="updateProperty">{{ $t('ok') }}</el-button>
        </div>
      </el-form>
    </el-dialog>
  </el-collapse-item>
</template>

<style lang="scss" scoped></style>
