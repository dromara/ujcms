<script setup lang="ts">
import { computed, ref, watch, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import type { UploadFile } from 'element-plus';
import { importSensitiveWord, importErrorWord, WORD_IMPORT_MAX_SIZE } from '@/api/system';
import { decodeText } from '@/utils/textFile';
import { parseWordLines, type WordMode } from './wordFile';

// 敏感词 / 易错词共用的导入对话框（docs/spec/word-dictionaries/plan.md Phase 7）：粘贴为主，选择文件只是把内容读进同一个文本框
defineOptions({
  name: 'WordImportDialog',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  mode: { type: String as PropType<WordMode>, required: true },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

/** 可读入的文件大小上限：远大于任何实际词库，只为挡住误选的大文件 */
const FILE_MAX_SIZE = 2 * 1024 * 1024;
const { t } = useI18n();
const text = ref<string>('');
const loading = ref<boolean>(false);
const conflicts = ref<string[]>([]);
const keyPrefix = computed(() => (props.mode === 'sensitive' ? 'sensitiveWord' : 'errorWord'));
const parsed = computed(() => parseWordLines(text.value, props.mode));
// 单字敏感词会命中大量正常文字，而词库各站点共用：提醒确认
const singleCharCount = computed(() => (props.mode === 'sensitive' ? parsed.value.entries.filter((entry) => [...entry.name].length === 1).length : 0));
const badLinesPreview = computed(() => {
  const lines = parsed.value.badLines;
  return lines.slice(0, 10).join(t('punct.enum')) + (lines.length > 10 ? '…' : '');
});
const conflictsPreview = computed(() => conflicts.value.slice(0, 20).join(t('punct.enum')) + (conflicts.value.length > 20 ? '…' : ''));

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      text.value = '';
      conflicts.value = [];
    }
  },
);

// auto-upload=false 时 ElementPlus 只触发 on-change；文件在浏览器里读成文本，不上传
const handleFileSelect = async (file: UploadFile) => {
  const raw = file.raw;
  if (raw == null) {
    return;
  }
  if (raw.size > FILE_MAX_SIZE) {
    ElMessage.error(t('wordDict.import.fileError'));
    return;
  }
  try {
    text.value = decodeText(await raw.arrayBuffer());
    conflicts.value = [];
  } catch {
    ElMessage.error(t('wordDict.import.fileError'));
  }
};

const handleImport = async () => {
  const entries = parsed.value.entries;
  if (entries.length === 0) {
    return;
  }
  loading.value = true;
  // 无法识别的行没有提交，一并计入“无效”，结果里的数字才与文本框里的行数对得上
  const total = { created: 0, duplicated: 0, invalid: parsed.value.badLines.length };
  const allConflicts: string[] = [];
  try {
    // 服务端单次有上限（操作日志记录整个请求体），大词库分批提交
    for (let i = 0; i < entries.length; i += WORD_IMPORT_MAX_SIZE) {
      const batch = entries.slice(i, i + WORD_IMPORT_MAX_SIZE);
      const result =
        props.mode === 'sensitive' ? await importSensitiveWord(batch.map((entry) => entry.name)) : await importErrorWord(batch.map((entry) => ({ name: entry.name, correct: entry.correct ?? '' })));
      total.created += result.created;
      total.duplicated += result.duplicated;
      total.invalid += result.invalid;
      allConflicts.push(...(result.conflicts ?? []));
    }
  } finally {
    loading.value = false;
    // 中途失败时已提交的批次也已入库：列表照常刷新
    emit('finished');
  }
  ElMessage.success(t('wordDict.import.result', total));
  conflicts.value = allConflicts;
  text.value = '';
  // 有冲突时留在对话框里列出，由操作员核对；否则关闭
  if (allConflicts.length === 0) {
    emit('update:modelValue', false);
  }
};
</script>

<template>
  <el-dialog :model-value="modelValue" :title="$t('wordDict.op.import')" width="640" :close-on-click-modal="false" append-to-body @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <el-alert :title="$t(`${keyPrefix}.import.tip`)" type="info" :closable="false" class="mb-3" />
    <el-alert v-if="conflicts.length > 0" :title="$t('errorWord.import.conflicts', { count: conflicts.length, words: conflictsPreview })" type="warning" :closable="false" class="mb-3" />
    <el-input v-model="text" type="textarea" :rows="12" :placeholder="$t(`${keyPrefix}.import.placeholder`)" spellcheck="false"></el-input>
    <div class="flex items-center mt-3">
      <el-upload :on-change="handleFileSelect" :auto-upload="false" :show-file-list="false" accept=".txt,text/plain">
        <el-button>{{ $t('wordDict.import.chooseFile') }}</el-button>
      </el-upload>
      <div class="ml-3 text-sm text-gray-secondary">
        <span>{{ $t('wordDict.import.recognized', { count: parsed.entries.length }) }}</span>
        <span v-if="parsed.badLines.length > 0" class="ml-2 text-danger">{{ $t('wordDict.import.unreadable', { count: parsed.badLines.length, lines: badLinesPreview }) }}</span>
      </div>
    </div>
    <el-alert v-if="singleCharCount > 0" :title="$t('sensitiveWord.import.singleChar', { count: singleCharCount })" type="warning" :closable="false" class="mt-3" />
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="loading" :disabled="parsed.entries.length === 0" @click="() => handleImport()">{{ $t('wordDict.op.import') }}</el-button>
    </template>
  </el-dialog>
</template>
