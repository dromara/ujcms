<script setup lang="ts">
import { ref, watchEffect } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { compile } from 'path-to-regexp';

defineOptions({
  name: 'BreadCrumb',
});
const router = useRouter();
const route = useRoute();
const itemList = ref<any[]>([]);

const pathCompile = (path: string) => {
  const { params } = route;
  const toPath = compile(path);
  return toPath(params);
};
const handleLink = (item: any) => {
  const { redirect, path } = item;
  router.push(redirect || pathCompile(path));
};
watchEffect(() => {
  itemList.value = route.matched.filter((item) => item.meta?.title);
});
</script>

<template>
  <el-breadcrumb separator="/">
    <transition-group name="breadcrumb">
      <el-breadcrumb-item v-for="(item, index) in itemList" :key="item.path">
        <span v-if="index === itemList.length - 1" class="text-gray-400">{{ $t(item.meta.title) }}</span>
        <a v-else @click.prevent="() => handleLink(item)">{{ $t(item.meta.title) }}</a>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>
