<script setup lang="ts">
defineProps({ collapse: { type: Boolean, required: true } });
const title = import.meta.env.VITE_APP_NAME || 'UJCMS';
</script>

<template>
  <div class="flex h-12 items-center justify-center overflow-hidden">
    <router-link class="whitespace-nowrap text-center" to="/">
      <svg class="inline fill-current text-success" width="32" height="32" viewBox="0 0 280 280" xmlns="http://www.w3.org/2000/svg">
        <path
          d="M200,40h-40h-20c-11,0-20,9-20,20v20.4v40v40V200H80v-39.6v-40v-40V60c0-11-9-20-20-20h0c-11,0-20,9-20,20v20.4v40v40V200v0.4V220c0,11,9,20,20,20h20h40h20c11,0,20-9,20-20v-19.6V200v-39.6v-40V80h40v40.4v40V200l0,0h-10c-11,0-20,9-20,20v0c0,11,9,20,20,20h10l0,0h20c11,0,20-9,20-20v-19.6V200v-39.6v-40v-40V60c0-11-9-20-20-20H200z"
        />
      </svg>
      <h1 v-if="!collapse" class="inline align-middle ml-1 text-lg font-bold text-gray-100">{{ title }}</h1>
    </router-link>
  </div>
</template>
