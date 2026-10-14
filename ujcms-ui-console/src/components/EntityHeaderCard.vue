<script setup lang="ts">
import { Delete, Edit, CopyDocument } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';

// 主区顶栏（docs/spec/console-master-detail）：左侧列表所选实体的元数据与实体级操作（编辑/删除）就地可见。
// 各实体的字段不同，经 tags / meta 插槽由视图给出；本组件只承载各处相同的行为：复制别名、编辑、带确认与禁用说明的删除。
defineOptions({ name: 'EntityHeaderCard' });

withDefaults(
  defineProps<{
    name: string;
    // 别名供模板指令取用，故可一键复制；无别名的实体不传
    alias?: string;
    editLabel: string;
    deleteLabel: string;
    editDisabled?: boolean;
    deleteDisabled?: boolean;
    // 删除被禁用的原因（如“正被使用”）；有值才显示提示
    deleteTip?: string;
    confirmText: string;
  }>(),
  { alias: undefined, editDisabled: false, deleteDisabled: false, deleteTip: undefined },
);
const emit = defineEmits<{ edit: []; delete: [] }>();
const { t } = useI18n();

const handleCopyAlias = async (alias: string) => {
  try {
    await navigator.clipboard.writeText(alias);
    ElMessage.success(t('success'));
  } catch {
    ElMessage.error(t('error.title'));
  }
};
</script>

<template>
  <div class="mb-3 app-block flex items-start justify-between gap-4 px-4 py-3">
    <div class="min-w-0">
      <div class="flex items-center flex-wrap gap-2">
        <span class="text-base font-medium text-gray-primary">{{ name }}</span>
        <slot name="tags"></slot>
      </div>
      <div class="mt-1.5 flex items-center flex-wrap gap-x-3 gap-y-1 text-xs text-gray-secondary">
        <span v-if="alias" class="inline-flex items-center">
          <span class="font-mono">{{ alias }}</span>
          <el-tooltip :content="$t('copyAlias')" placement="top">
            <el-button :icon="CopyDocument" size="small" link class="!ml-1" @click="() => handleCopyAlias(alias!)" />
          </el-tooltip>
        </span>
        <slot name="meta"></slot>
      </div>
    </div>
    <div class="flex items-center shrink-0">
      <el-button type="primary" :disabled="editDisabled" :icon="Edit" size="small" link @click="() => emit('edit')">{{ editLabel }}</el-button>
      <el-tooltip :content="deleteTip" :disabled="!deleteTip" placement="top">
        <span class="ml-3 inline-flex">
          <el-popconfirm :title="confirmText" :width="260" @confirm="() => emit('delete')">
            <template #reference>
              <el-button type="danger" :disabled="deleteDisabled" :icon="Delete" size="small" link>{{ deleteLabel }}</el-button>
            </template>
          </el-popconfirm>
        </span>
      </el-tooltip>
    </div>
  </div>
</template>
