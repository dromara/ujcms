<script setup lang="ts">
import { ref, watchEffect } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { compile } from 'path-to-regexp';

/**
 * 面包屑：route.matched 里带标题的记录。只有带 name 的祖先（真正的页面）才是链接；
 * 分组记录（/content、/stat/visit …）只是容器、没有页面，点了会落到空白的布局上，因此只显示文本，
 * 与侧栏里分组标题只展开、不跳转一致（console-menu「The header」）。
 */
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
const isLink = (item: any, index: number): boolean => index < itemList.value.length - 1 && item.name != null;
watchEffect(() => {
  itemList.value = route.matched.filter((item) => item.meta?.title);
});
</script>

<template>
  <el-breadcrumb separator="/">
    <transition-group name="breadcrumb">
      <el-breadcrumb-item v-for="(item, index) in itemList" :key="item.path">
        <a v-if="isLink(item, index)" @click.prevent="() => handleLink(item)">{{ $t(item.meta.title) }}</a>
        <span v-else :class="index === itemList.length - 1 ? 'text-gray-primary' : 'text-gray-secondary'">{{ $t(item.meta.title) }}</span>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>
