<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { TODO_QUEUES, isQueueAllowed } from '../todoQueues';

/**
 * 待办：页面顶端一排胶囊——每个有数的队列一枚（标签 + 计数徽标），按数量降序；不带标题、合计和卡片外框
 * （console-home D2：一条只剩标签和“没有待办事项”的白条就是一块空白）。胶囊是页面上唯一带底色的面（警示色淡底 +
 * 淡边），白底灰边会与下方的快捷入口格子混为一体——待办是页面上唯一要人动手的东西，得一眼看出来。计数未齐或全为零时整段不渲染、不占位，
 * 胶囊一次到位，不逐个蹦出。有权限的队列并行静默计数：一个接口失败只是少一枚胶囊并 console.warn 留痕，不弹全局错误框
 * （首页是登录后第一屏；计数接口与目标列表同权限，出错只可能是后端故障，同一故障会在列表页再现）。
 */
defineOptions({
  name: 'TodoCard',
});
const router = useRouter();
const allowed = TODO_QUEUES.filter(isQueueAllowed);
const counts = ref<Record<string, number>>({});
const loaded = ref<boolean>(false);

onMounted(async () => {
  await Promise.all(
    allowed.map(async (queue) => {
      try {
        counts.value[queue.key] = Number(await queue.fetch()) || 0;
      } catch (err) {
        console.warn(`[home] todo count failed: ${queue.key}`, err);
      }
    }),
  );
  loaded.value = true;
});

const tiles = computed(() => allowed.filter((queue) => (counts.value[queue.key] ?? 0) > 0).sort((a, b) => counts.value[b.key] - counts.value[a.key]));
</script>

<template>
  <div v-if="loaded && tiles.length > 0" class="flex flex-wrap gap-2">
    <button
      v-for="queue in tiles"
      :key="queue.key"
      type="button"
      class="inline-flex items-center h-9 pl-4 pr-1.5 rounded-full bg-warning-lighter border border-warning-light text-sm text-gray-primary transition-colors hover:border-warning hover:text-warning"
      @click="() => router.push(queue.to)"
    >
      <span>{{ $t(queue.labelKey) }}</span>
      <span class="inline-flex items-center justify-center min-w-[1.375rem] h-[1.375rem] ml-2.5 px-1.5 rounded-full bg-warning text-white text-xs font-semibold leading-none tabular-nums">
        {{ $n(counts[queue.key]) }}
      </span>
    </button>
  </div>
</template>
