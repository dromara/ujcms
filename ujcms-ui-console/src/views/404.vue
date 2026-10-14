<script lang="ts">
import { defineComponent } from 'vue';

export default defineComponent({ name: 'ErrorPage404' });
</script>

<template>
  <div class="h-full p-4 bg-gray-200">
    <div class="p-4 app-block">
      <h1 class="font-bold text-3xl">404</h1>
      <p class="mt-4">{{ $t('error.pageNotFound') }}</p>
      <p class="mt-4">
        <el-button type="primary" plain @click="() => $router.push('/')">{{ $t('error.backHome') }}</el-button>
      </p>
    </div>
  </div>
</template>
