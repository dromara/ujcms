<script setup lang="ts">
import { computed } from 'vue';
import { ElConfigProvider } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { getElementPlusLocale } from '@/i18n';

const { locale } = useI18n({ useScope: 'global' });
const lang = computed(() => getElementPlusLocale(locale.value as string));
</script>

<template>
  <!-- tinymce 对话框的层级太低，必须调低 ElementPlus 的 对话框层级（默认为2000） -->
  <el-config-provider :locale="lang" :z-index="500">
    <router-view />
  </el-config-provider>
</template>
