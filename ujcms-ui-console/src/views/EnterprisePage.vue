<script setup lang="ts"></script>
<template>
  <div>
    <div class="app-block p-3 flex">
      <el-alert type="warning" :closable="false" :show-icon="true">
        <!-- eslint-disable-next-line vue/no-v-html -->
        <template #title><span v-html="$t('error.enterprise')"></span></template>
      </el-alert>
    </div>
  </div>
</template>
