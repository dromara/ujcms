<script setup lang="ts">
import { toRefs, watch, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage } from 'element-plus';
import { queryGeneratedKey } from '@/api/personal';

defineOptions({
  name: 'GeneratedKey',
});
const props = defineProps({ modelValue: { type: Boolean, required: true } });
defineEmits({ 'update:modelValue': null });

interface GeneratedKeyResult {
  yaml: string;
  resetSql: string;
  resetPassword: string;
}

const { t } = useI18n();
const { modelValue: visible } = toRefs(props);
const loading = ref<boolean>(false);
const result = ref<GeneratedKeyResult>({ yaml: '', resetSql: '', resetPassword: '' });

const fetchGeneratedKey = async () => {
  loading.value = true;
  try {
    result.value = await queryGeneratedKey();
  } finally {
    loading.value = false;
  }
};
// 每次打开都重新生成：密钥只存在于本次对话框，关闭即弃，服务端不保存
watch(visible, () => {
  if (visible.value) {
    fetchGeneratedKey();
  }
});

// 等宽字体便于逐字核对（O/0、l/1）；显示时 trimEnd 去掉末尾换行免得多出一空行，复制仍带换行，粘贴进配置文件即成整行
const MONO = 'font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace';

const handleCopy = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success(t('success'));
  } catch {
    ElMessage.error(t('error.title'));
  }
};
</script>

<template>
  <el-dialog :title="$t('menu.personal.homepage.generatedKey')" :model-value="modelValue" :width="820" top="5vh" @update:model-value="(event) => $emit('update:modelValue', event)">
    <div v-loading="loading">
      <el-alert :title="$t('generatedKey.intro')" type="info" :closable="false" show-icon class="mb-3" />
      <div class="flex items-center justify-between mb-1">
        <span class="font-bold">{{ $t('generatedKey.yaml') }}</span>
        <el-button size="small" :disabled="!result.yaml" @click="() => handleCopy(result.yaml)">{{ $t('copy') }}</el-button>
      </div>
      <el-input :model-value="result.yaml.trimEnd()" type="textarea" autosize readonly resize="none" :input-style="MONO" />
      <div class="mt-1 text-sm text-gray-secondary">{{ $t('generatedKey.yaml.tip') }}</div>
      <div class="flex items-center justify-between mt-4 mb-1">
        <span class="font-bold">{{ $t('generatedKey.resetSql') }}</span>
        <el-button size="small" :disabled="!result.resetSql" @click="() => handleCopy(result.resetSql)">{{ $t('copy') }}</el-button>
      </div>
      <el-input :model-value="result.resetSql.trimEnd()" type="textarea" autosize readonly resize="none" :input-style="MONO" />
      <div class="mt-1 text-sm text-gray-secondary">{{ $t('generatedKey.resetSql.tip', { password: result.resetPassword }) }}</div>
    </div>
    <template #footer>
      <el-button type="primary" :loading="loading" @click="() => fetchGeneratedKey()">{{ $t('generatedKey.regenerate') }}</el-button>
    </template>
  </el-dialog>
</template>
