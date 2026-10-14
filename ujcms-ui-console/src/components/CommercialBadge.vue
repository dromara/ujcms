<script setup lang="ts">
import { computed } from 'vue';
import { findCommercialFeature } from '@/data/commercialFeatures';
import { CROWN_SVG_SHAPES } from '@/utils/commercial';

// 社区版锁定的商业功能标识：琥珀色皇冠图标（取代原“商业”文字标签——侧栏里整组逐项重复时文字标签太重）。
// 悬停提示与读屏名称为所需版本：tier 优先，否则按 permission 查功能注册表；都查不到时为“商业版功能”。
// 只标入口（菜单、页签、工具栏按钮、下拉项），每屏一次；表格里逐行重复的操作按钮只给 :title="lockedTip(n)"，不放皇冠
const props = defineProps({
  tier: { type: Number, default: undefined },
  permission: { type: String, default: undefined },
  size: { type: Number, default: 14 },
});

const tier = computed(() => props.tier ?? findCommercialFeature(props.permission)?.tier);
</script>

<template>
  <span
    class="inline-flex flex-none items-center text-warning"
    role="img"
    :aria-label="tier ? $t(`commercial.tier.${tier}`) : $t('commercial.badge')"
    :title="tier ? $t(`commercial.tier.${tier}`) : $t('commercial.badge')"
  >
    <!-- eslint-disable-next-line vue/no-v-html -- 静态常量，与编辑器锁定按钮共用同一皇冠图形 -->
    <svg :width="size" :height="size" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" v-html="CROWN_SVG_SHAPES" />
  </span>
</template>
