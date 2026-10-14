<script setup lang="ts">
import type { PropType } from 'vue';

/**
 * “多图文 N 篇”标签：多图文（草稿、推送记录、已发布内容）在列表中只显示头条，此标签表示篇数，
 * 鼠标悬停列出各篇标题，有链接的篇目可点击打开。单图文不显示任何内容。
 * 放在 flex 容器中并让标题 truncate，标签才不会被单元格的省略号裁掉。
 */
defineOptions({
  name: 'WechatItemsTag',
});
defineProps({
  /** 各篇文章，至少含 title；链接字段名由 urlField 指定；isDeleted 为 true 时显示“已删除” */
  items: { type: Array as PropType<any[]>, required: true },
  /** 链接字段名：草稿和已发布内容为 url，推送记录为 articleUrl */
  urlField: { type: String, default: 'url' },
});
</script>

<template>
  <el-popover v-if="items.length > 1" placement="bottom-start" :width="360" trigger="hover">
    <template #reference>
      <el-tag size="small" type="info" class="ml-1 shrink-0">{{ $t('wechatArticle.items.count', { count: items.length }) }}</el-tag>
    </template>
    <ol class="pl-4 text-xs space-y-1">
      <li v-for="(item, index) in items" :key="index">
        <a v-if="item[urlField]" :href="item[urlField]" target="_blank" class="text-primary">{{ item.title }}</a>
        <span v-else>{{ item.title }}</span>
        <el-tag v-if="item.isDeleted" type="danger" size="small" class="ml-1">{{ $t('wechatPublish.isDeleted') }}</el-tag>
      </li>
    </ol>
  </el-popover>
</template>
