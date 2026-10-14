<script setup lang="ts">
import { TopRight } from '@element-plus/icons-vue';
import { PRICING_URL, currentEditionLabel, tierLabel } from '@/utils/commercial';

// 商业版功能的锁定说明（功能介绍页、全局设置 / 站点设置与角色权限的锁定页签共用）：当前版本、所需版本、版本与价格页入口。
// 这是版本说明而不是错误，不用 warning 警告条；tier 缺省时只说“商业版”

defineProps({
  tier: { type: Number, default: undefined },
  // 所在页面已有版本标识（功能介绍页标题旁的版本标签）时不再重复皇冠
  showIcon: { type: Boolean, default: true },
});
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-4 gap-y-3 rounded-lg bg-gray-50 px-4 py-3">
    <commercial-badge v-if="showIcon" :tier="tier" :size="18" />
    <i18n-t keypath="commercial.notice.upgrade" tag="span" class="flex-1 min-w-[16rem] leading-6 text-gray-regular">
      <template #current>{{ currentEditionLabel() }}</template>
      <template #tier>
        <span class="whitespace-nowrap font-medium text-gray-primary">{{ tierLabel(tier) }}</span>
      </template>
    </i18n-t>
    <el-button type="primary" tag="a" :href="PRICING_URL" target="_blank" rel="noopener">
      {{ $t('commercial.notice.pricing') }}<el-icon class="el-icon--right"><TopRight /></el-icon>
    </el-button>
  </div>
</template>
