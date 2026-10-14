<script setup lang="ts">
import { ref, watch } from 'vue';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { toTree } from '@/utils/tree';
import { querySiteList } from '@/api/system';
import { queryChannelList } from '@/api/content';

/**
 * 语言组范围选择（两个批量新增译文弹窗的头部；曾与“关联已有译文”共用，该助手 2026-09-04 移除）：
 * 目标站点（同语言组其它站点，可多选，加载后默认全选——常见意图是把全部语言站点一次同步）+ 本站栏目树。
 * active 变为 true 时重新加载，先经 ready 事件回报语言组站点与本站栏目，再回报默认全选的目标站点；
 * 当前站点不在任何语言组时原地渲染提示
 */
defineOptions({
  name: 'LangGroupScope',
});
const props = defineProps<{
  active: boolean;
  targetSiteIds: string[];
  channelId?: string;
  channelPlaceholder: string;
  channelClearable?: boolean;
}>();
const emit = defineEmits({ 'update:targetSiteIds': null, 'update:channelId': null, ready: null });
const currentSiteStore = useCurrentSiteStore();

// 未加载完成前不渲染（避免闪现“不在语言组”提示）
const groupSites = ref<any[]>();
const channels = ref<any[]>([]);
const channelTree = ref<any[]>([]);

const fetchOptions = async () => {
  const current = currentSiteStore.currentSite;
  if (current == null) {
    groupSites.value = [];
    emit('ready', [], []);
    return;
  }
  const masterId = current.langParentId ?? current.id;
  const sites = await querySiteList();
  // 语言组成员：同组主站点（排除当前站点自身）
  groupSites.value = sites.filter((site: any) => (site.langParentId ?? site.id) === masterId && site.id !== current.id);
  channels.value = await queryChannelList();
  channelTree.value = toTree(channels.value);
  emit('ready', groupSites.value, channels.value);
  emit(
    'update:targetSiteIds',
    (groupSites.value ?? []).map((site: any) => site.id),
  );
};
watch(
  () => props.active,
  (active) => {
    if (active) {
      fetchOptions();
    }
  },
  { immediate: true },
);
</script>

<template>
  <div>
    <el-alert v-if="groupSites != null && groupSites.length <= 0" :title="$t('article.i18n.scope.empty')" type="info" :closable="false" />
    <div v-else-if="groupSites != null" class="flex items-start gap-2">
      <el-select
        :model-value="targetSiteIds"
        multiple
        :placeholder="$t('article.i18n.scope.targetSite')"
        class="w-80"
        @update:model-value="(value: string[]) => $emit('update:targetSiteIds', value)"
      >
        <!-- 站点名为主、语言为弱化后缀（候选与选中标签同一形态） -->
        <template #label="{ label, value }">
          <span>{{ label }}</span>
          <span class="ml-1 text-xs text-gray-400">{{ groupSites.find((site) => site.id === value)?.lang }}</span>
        </template>
        <el-option v-for="item in groupSites" :key="item.id" :label="item.name" :value="item.id">
          <span>{{ item.name }}</span>
          <span class="ml-2 text-xs text-gray-400">{{ item.lang }}</span>
        </el-option>
      </el-select>
      <el-tree-select
        :model-value="channelId"
        :data="channelTree"
        node-key="id"
        :props="{ label: 'name' }"
        :render-after-expand="false"
        check-strictly
        :clearable="channelClearable"
        :placeholder="channelPlaceholder"
        class="w-56"
        @update:model-value="(value: string | undefined) => $emit('update:channelId', value)"
      />
      <slot></slot>
    </div>
  </div>
</template>
