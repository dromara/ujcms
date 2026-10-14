<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { fulltextReindexAll, fulltextReindexSite, htmlAll, htmlHome, htmlChannel, htmlArticle } from '@/api/content';
import { perm, isUnlocked } from '@/stores/useCurrentUser';
import LabelTip from '@/components/LabelTip.vue';
import TaskList from './TaskList.vue';

// 生成管理只负责“执行”（console-menu D23）：静态化设置在 配置 → 站点设置，一键置灰在 配置 → 全局设置
defineOptions({
  name: 'GeneratorForm',
});
const { t } = useI18n();
const buttonLoading = ref<boolean>(false);
const taskListRef = ref<any>();

const handleFulltextReindexAll = async () => {
  buttonLoading.value = true;
  try {
    await fulltextReindexAll();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleFulltextReindexSite = async () => {
  buttonLoading.value = true;
  try {
    await fulltextReindexSite();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleHtmlAll = async () => {
  buttonLoading.value = true;
  try {
    await htmlAll();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleHtmlHome = async () => {
  buttonLoading.value = true;
  try {
    await htmlHome();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleHtmlChannel = async () => {
  buttonLoading.value = true;
  try {
    await htmlChannel();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleHtmlArticle = async () => {
  buttonLoading.value = true;
  try {
    await htmlArticle();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
</script>

<template>
  <div>
    <div class="p-3 app-block">
      <el-form label-width="200px">
        <el-form-item>
          <template #label><label-tip message="generator.fulltext" help /></template>
          <el-button :disabled="perm('generator:fulltext:reindexAll')" :loading="buttonLoading" type="primary" plain @click.prevent="handleFulltextReindexAll">
            {{ $t('generator.op.fulltext.reindexAll') }}
          </el-button>
          <el-button
            v-if="isUnlocked('generator:fulltext:reindexSite')"
            :disabled="perm('generator:fulltext:reindexSite')"
            :loading="buttonLoading"
            type="primary"
            plain
            @click.prevent="handleFulltextReindexSite"
          >
            {{ $t('generator.op.fulltext.reindexSite') }}
          </el-button>
        </el-form-item>
        <el-form-item>
          <template #label><label-tip message="generator.html" help /></template>
          <el-button :disabled="perm('generator:html')" :loading="buttonLoading" type="primary" plain @click.prevent="handleHtmlAll">
            {{ $t('generator.op.html.all') }}
          </el-button>
          <span class="mx-2">|</span>
          <el-button :disabled="perm('generator:html')" :loading="buttonLoading" type="primary" plain @click.prevent="handleHtmlHome">
            {{ $t('generator.op.html.home') }}
          </el-button>
          <el-button :disabled="perm('generator:html')" :loading="buttonLoading" type="primary" plain @click.prevent="handleHtmlChannel">
            {{ $t('generator.op.html.channel') }}
          </el-button>
          <el-button :disabled="perm('generator:html')" :loading="buttonLoading" type="primary" plain @click.prevent="handleHtmlArticle">
            {{ $t('generator.op.html.article') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="p-3 mt-3 app-block">
      <div class="text-gray-primary">{{ $t('generator.taskList') }}</div>
    </div>
    <task-list ref="taskListRef" class="mt-3" />
  </div>
</template>
