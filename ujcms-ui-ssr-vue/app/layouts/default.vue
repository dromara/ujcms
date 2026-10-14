<script setup lang="ts">
/**
 * Standard site chrome — header + footer around the page body.
 */
const { session } = useSessionState();
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader :site="session.site" />
    <main class="flex-1">
      <slot />
    </main>
    <AppFooter :site="session.site" />
  </div>
</template>
