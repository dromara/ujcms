<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { Codemirror } from 'vue-codemirror';
import { html } from '@codemirror/lang-html';
import { editorTheme } from '@/views/design/template/editorTheme';
import { useI18n } from 'vue-i18n';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import WebFileHistoryDrawer from './WebFileHistoryDrawer.vue';

defineOptions({
  name: 'WebFileForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  parentId: { type: String, required: true },
  type: { type: String, required: true },
  queryWebFile: { type: Function as PropType<(id: string) => Promise<any>>, required: true },
  createWebFile: { type: Function as PropType<(data: Record<string, any>) => Promise<any>>, required: true },
  updateWebFile: { type: Function as PropType<(data: Record<string, any>) => Promise<any>>, required: true },
  deleteWebFile: { type: Function as PropType<(data: string[]) => Promise<any>>, required: true },
  // 是否提供模板编辑历史（仅模板文件管理器传 true——历史只对模板存储捕获）
  history: { type: Boolean, default: false },
  // 对话框标题名称；默认按 menu.file.webFile{type} 取词（模板管理器迁入设计模块后由包装组件传入）
  formName: { type: String, default: null },
});
defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const focus = ref<any>();
const values = ref<any>({});
const dialog = ref<any>();
const historyVisible = ref<boolean>(false);

// 恢复后重载文件内容（历史恢复直接落盘，编辑器里的旧文本已过期）
const handleRestored = async () => {
  if (props.beanId != null) {
    const bean = await props.queryWebFile(props.beanId);
    // 恢复的内容已经在磁盘上：表单与基线一起换，不算未保存的改动
    dialog.value?.patch((target: any) => {
      target.text = bean.text;
    });
  }
};

// Ctrl+S / ⌘S：保存（连续模式下留在对话框里）
const handleKeydown = (e: KeyboardEvent) => {
  if (!e.ctrlKey && !e.metaKey) {
    return;
  }
  e.preventDefault();
  if (e.repeat) {
    return;
  }
  dialog.value.submit(
    async (
      data: any,
      { isEdit, props, markSaved, resetForm }: { isEdit: boolean; props: any; markSaved: () => void; resetForm: () => void },
    ) => {
      if (isEdit) {
        await props.updateBean(data);
        markSaved();
      } else {
        await props.createBean(data);
        resetForm();
      }
      ElMessage.success(t('success'));
    },
  );
};
</script>

<template>
  <dialog-form
    ref="dialog"
    v-model:values="values"
    :name="formName ?? $t(`menu.file.webFile${type}`)"
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
            <el-input :model-value="values.id" disabled>
              <template v-if="history" #append>
                <el-button @click="() => (historyVisible = true)">{{ $t('webFile.op.history') }}</el-button>
              </template>
            </el-input>
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
            <codemirror
              v-model="values.text"
              :extensions="[html(), ...editorTheme]"
              :style="{ minHeight: '200px', width: '100%', border: '1px solid #dcdfe6' }"
              @keydown.s="handleKeydown"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="bean.image">
          <el-form-item>
            <template #label><label-tip message="webFile.image" /></template>
            <div class="w-full rounded-md border border-gray-200 bg-gray-100 p-6 text-center">
              <el-image :src="values.url" class="max-w-full rounded bg-white p-3 align-top shadow-sm" />
            </div>
          </el-form-item>
        </el-col>
      </el-row>
      <web-file-history-drawer v-if="history && isEdit" v-model="historyVisible" :file-id="beanId" @restored="() => handleRestored()" />
    </template>
  </dialog-form>
</template>
