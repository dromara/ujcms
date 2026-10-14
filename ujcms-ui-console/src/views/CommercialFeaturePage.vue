<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { commercialFeatures, findCommercialFeature } from '@/data/commercialFeatures';

const route = useRoute();
const { t } = useI18n();
// 由路由守卫以 ?perm=<权限码> 跳转而来；未知权限码时退化为通用提示
const feature = computed(() => findCommercialFeature(route.query.perm as string | undefined));
// 同版本的其它功能：回答“升级到这一版还能得到什么”；没有控制台文案的注册表条目不列
const siblings = computed(() => {
  const current = feature.value;
  if (!current) {
    return [];
  }
  return commercialFeatures
    .filter((item) => item.tier === current.tier && item.id !== current.id)
    .map((item) => t(`commercial.feature.${item.id}`))
    .filter((name) => !name.startsWith('commercial.feature.'));
});
</script>

<template>
  <div class="p-3">
    <div class="app-block px-8 py-8">
      <div class="max-w-3xl">
        <template v-if="feature">
          <div class="flex flex-wrap items-center gap-3">
            <h2 class="text-xl font-bold">{{ $t(`commercial.feature.${feature.id}`) }}</h2>
            <span class="inline-flex items-center gap-1 rounded-full bg-warning-lighter px-3 py-0.5 text-xs font-medium text-[#a16207]">
              <commercial-badge :tier="feature.tier" :size="13" />
              {{ $t(`commercial.tier.${feature.tier}`) }}
            </span>
          </div>
          <p class="mt-3 leading-7 text-gray-regular">{{ $t(`commercial.feature.${feature.id}.description`) }}</p>
          <commercial-notice class="mt-6" :tier="feature.tier" :show-icon="false" />
          <template v-if="siblings.length">
            <div class="mt-8 text-sm text-gray-secondary">{{ $t('commercial.notice.siblings') }}</div>
            <div class="mt-3 flex flex-wrap gap-2">
              <span v-for="name in siblings" :key="name" class="rounded-full border border-gray-200 px-3 py-1 text-sm text-gray-regular">{{ name }}</span>
            </div>
          </template>
        </template>
        <commercial-notice v-else />
      </div>
    </div>
  </div>
</template>
