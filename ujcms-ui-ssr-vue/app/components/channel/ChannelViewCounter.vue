<script setup lang="ts">
/**
 * Fire-and-forget channel-view beacon. Mirrors the template-theme baseline's tail
 * `<script>fetch('{{ api }}/channel/view/{{ channel.id }}').catch(()=>{});<\/script>`.
 *
 * Mounted as a hydration-only side-effect — stays out of SSR so bot
 * prerenders never bump the counter.
 */
const props = defineProps<{ channelId: string }>();

onMounted(() => {
  fetch(`/api/channel/view/${encodeURIComponent(props.channelId)}`, { credentials: 'include' }).catch(() => {});
});
</script>

<template>
  <span hidden aria-hidden="true" />
</template>
