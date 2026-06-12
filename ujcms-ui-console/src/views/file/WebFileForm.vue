<script setup lang="ts">
import { ref, PropType } from 'vue';
import { Codemirror } from 'vue-codemirror';
import { html } from '@codemirror/lang-html';
import { useI18n } from 'vue-i18n';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'WebFileForm',
});
defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  parentId: { type: String, required: true },
  type: { type: String, required: true },
  queryWebFile: { type: Function as PropType<(id: string) => Promise<any>>, required: true },
  createWebFile: { type: Function as PropType<(data: Record<string, any>) => Promise<any>>, required: true },
  updateWebFile: { type: Function as PropType<(data: Record<string, any>) => Promise<any>>, required: true },
  deleteWebFile: { type: Function as PropType<(data: string[]) => Promise<any>>, required: true },
});
defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const focus = ref<any>();
const values = ref<any>({});
const dialog = ref<any>();

const handleKeydown = (e: KeyboardEvent) => {
  if (e.ctrlKey == true) {
    e.preventDefault();
    dialog.value.submit(
      async (
        values: any,
        { isEdit, focus, emit, props, form, resetOrigValues }: { isEdit: boolean; focus: any; emit: any; props: any; form: any; resetOrigValues: () => void },
      ) => {
        emit('beforeSubmit', values);
        if (isEdit) {
          await props.updateBean(values);
          resetOrigValues();
        } else {
          await props.createBean(values);
          focus?.focus?.();
          emit('update:values', props.initValues(values));
          form.resetFields();
        }
        ElMessage.success(t('success'));
      },
    );
  }
};
</script>

<template>
  <dialog-form
    ref="dialog"
    v-model:values="values"
    :name="$t(`menu.file.webFile${type}`)"
    :query-bean="queryWebFile"
    :create-bean="createWebFile"
    :update-bean="updateWebFile"
    :delete-bean="deleteWebFile"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="(): any => ({ parentId })"
    :to-values="(bean) => ({ ...bean })"
    :perms="`webFile${type}`"
    :model-value="modelValue"
    :show-id="false"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{ bean, isEdit }">
      <el-row>
        <el-col v-if="isEdit" :span="24">
          <el-form-item>
            <template #label>ID</template>
            <el-input :model-value="values.id" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col v-else :span="24">
          <el-form-item>
            <template #label><label-tip message="webFile.dir" /></template>
            <el-input :model-value="values.parentId" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="webFile.name" /></template>
            <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col v-if="!isEdit || values.editable" :span="24">
          <el-form-item prop="text">
            <template #label><label-tip message="webFile.text" /></template>
            <codemirror v-model="values.text" :extensions="[html()]" :style="{ minHeight: '200px', width: '100%', border: '1px solid silver' }" @keydown.s="handleKeydown" />
          </el-form-item>
        </el-col>
        <el-col v-if="bean.image">
          <el-form-item>
            <template #label><label-tip message="webFile.image" /></template>
            <el-image :src="values.url" />
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>
