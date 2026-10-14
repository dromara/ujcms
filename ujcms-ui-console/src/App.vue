<script setup lang="ts">
import { computed } from 'vue';
import { ElConfigProvider } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { getElementPlusLocale } from '@/i18n';
import ReLoginDialog from '@/views/ReLoginDialog.vue';

const { locale } = useI18n({ useScope: 'global' });
const lang = computed(() => getElementPlusLocale(locale.value as string));
</script>

<template>
  <!-- tinymce 对话框的层级太低，必须调低 ElementPlus 的 对话框层级（默认为2000） -->
  <el-config-provider :locale="lang" :z-index="500">
    <!-- 构建器按 fullPath 键控（visual-builder P5）：BuilderPage 在 setup 捕获 route.query.page，
         hash 查询变化会复用组件而不重载（页面列表 → 构建器 → 另一页面时状态残留）；其余路由不键控
         （Layout 保持稳定实例）。 -->
    <router-view v-slot="{ Component, route }">
      <component :is="Component" :key="route.name === 'Builder' ? route.fullPath : undefined" />
    </router-view>
    <!-- 会话超时后的就地重新登录框。放在此处而非 Layout：构建器等全屏路由不经过 Layout，而令牌刷新定时器全局运行 -->
    <re-login-dialog />
  </el-config-provider>
</template>
