<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { Picture } from '@element-plus/icons-vue';
import { fetchMarketplaceAsset } from '@/api/marketplace';

// 市场图片经带鉴权的资产代理以 blob 加载（D-C1-4：控制台永不跨源直连，<img> 又带不了 JWT 头）。
// path 为目录响应里的不透明相对引用；加载失败静默回退占位图。
const props = defineProps<{ path?: string | null }>();

const objectUrl = ref<string>();
const failed = ref<boolean>(false);

const revoke = () => {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value);
    objectUrl.value = undefined;
  }
};

const load = async (path?: string | null) => {
  revoke();
  failed.value = false;
  if (!path) {
    return;
  }
  try {
    const blob = await fetchMarketplaceAsset(path);
    objectUrl.value = URL.createObjectURL(blob);
  } catch {
    failed.value = true;
  }
};

watch(() => props.path, load, { immediate: true });
onBeforeUnmount(revoke);
</script>

<template>
  <img v-if="objectUrl" :src="objectUrl" class="h-full w-full object-cover" />
  <div v-else class="flex h-full w-full items-center justify-center text-gray-300">
    <el-icon :size="48"><Picture /></el-icon>
  </div>
</template>
