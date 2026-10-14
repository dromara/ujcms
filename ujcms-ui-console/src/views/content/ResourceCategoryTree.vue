<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { Delete, Edit, Folder, FolderOpened, Plus } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { createResourceCategory, deleteResourceCategory, queryResourceCategoryList, updateResourceCategory } from '@/api/system';
import { findTreeItem } from '@/utils/tree';

defineOptions({ name: 'ResourceCategoryTree' });

// readonly：仅作导航（如文件选择器内），隐藏新建/重命名/删除
const props = withDefaults(defineProps<{ modelValue: string | null; scope?: 'shared' | 'my'; readonly?: boolean }>(), {
  scope: 'shared',
  readonly: false,
});
const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>();
const { t } = useI18n();

// 未分类筛选哨兵值：'0' 表示“未分类”（后端将 categoryId=0 解释为 category_id_ is null）
const UNCATEGORIZED_KEY = '0';
const isMy = computed(() => props.scope === 'my');
const titleLabel = computed(() => (isMy.value ? t('resource.scope.my') : t('resource.scope.shared')));
const allLabel = computed(() => (isMy.value ? t('resource.allMy') : t('resource.allShared')));
const scopeParam = computed(() => (isMy.value ? { scope: 'my' } : undefined));

const tree = ref<any[]>([]);
const currentKey = ref<string | null>(props.modelValue ?? null);

const load = async () => {
  const list = await queryResourceCategoryList(scopeParam.value);
  // 我的资源库：把“未分类”作为树的首个虚拟节点，与共享资源库的真实“未分类”节点保持一致的层级与样式
  tree.value = isMy.value ? [{ id: UNCATEGORIZED_KEY, name: t('resource.uncategorized'), virtual: true }, ...list] : list;
  // 选中值可能来自地址（资源库的路由 query）：分类已删除或属于他人时回落“全部”
  if (currentKey.value !== null && findTreeItem(tree.value, (item: any) => String(item.id) === currentKey.value) === undefined) {
    selectAll();
  }
};
onMounted(load);
watch(
  () => props.modelValue,
  (v) => (currentKey.value = v ?? null),
);

const selectAll = () => {
  currentKey.value = null;
  emit('update:modelValue', null);
};
const handleNodeClick = (node: any) => {
  currentKey.value = String(node.id);
  emit('update:modelValue', String(node.id));
};

const addCategory = async (parentId: string | null) => {
  try {
    const { value } = await ElMessageBox.prompt(
      t('resource.category.namePlaceholder'),
      parentId ? t('resource.category.addChild') : t('resource.category.add'),
      { inputPattern: /\S+/, inputErrorMessage: t('resource.category.namePlaceholder') },
    );
    await createResourceCategory({ name: value, parentId }, scopeParam.value);
    await load();
    ElMessage.success(t('success'));
  } catch {
    /* cancelled or failed (error toast handled globally) */
  }
};
const renameCategory = async (node: any) => {
  try {
    const { value } = await ElMessageBox.prompt(t('resource.category.namePlaceholder'), t('resource.category.rename'), {
      inputValue: node.name,
      inputPattern: /\S+/,
      inputErrorMessage: t('resource.category.namePlaceholder'),
    });
    await updateResourceCategory({ id: node.id, name: value });
    await load();
    ElMessage.success(t('success'));
  } catch {
    /* cancelled */
  }
};
const removeCategory = async (node: any) => {
  try {
    await ElMessageBox.confirm(t('confirmDelete'), { type: 'warning' });
    await deleteResourceCategory([node.id]);
    if (currentKey.value === String(node.id)) {
      selectAll();
    }
    await load();
    ElMessage.success(t('success'));
  } catch {
    /* cancelled or not-empty error (toast handled globally) */
  }
};

defineExpose({ refresh: load });
</script>

<template>
  <div class="category-tree">
    <div class="category-toolbar">
      <span class="title">{{ titleLabel }}</span>
      <el-tooltip v-if="!readonly && !perm('resourceCategory:create')" :content="t('resource.category.add')" placement="top">
        <el-button type="primary" :icon="Plus" :aria-label="t('resource.category.add')" link @click="addCategory(null)" />
      </el-tooltip>
    </div>
    <div class="all-shared" :class="{ active: currentKey === null }" @click="selectAll">
      <el-icon><FolderOpened /></el-icon>
      <span>{{ allLabel }}</span>
    </div>
    <el-tree
      :data="tree"
      node-key="id"
      :props="{ label: 'name', children: 'children' }"
      :current-node-key="currentKey ?? undefined"
      highlight-current
      default-expand-all
      empty-text=" "
      @node-click="handleNodeClick"
    >
      <template #default="{ node, data }">
        <span class="tree-node">
          <span class="node-main">
            <el-icon><Folder /></el-icon>
            <span class="node-label" :title="node.label">{{ node.label }}</span>
          </span>
          <span v-if="!readonly && !data.virtual" class="node-actions">
            <el-icon v-if="!perm('resourceCategory:create')" @click.stop="addCategory(String(data.id))"><Plus /></el-icon>
            <el-icon v-if="!perm('resourceCategory:update')" @click.stop="renameCategory(data)"><Edit /></el-icon>
            <el-icon v-if="!perm('resourceCategory:delete')" @click.stop="removeCategory(data)"><Delete /></el-icon>
          </span>
        </span>
      </template>
    </el-tree>
  </div>
</template>

<style scoped>
/* 外观随内容区左侧导航（docs/spec/console-aside）：无边框白卡片、20×24 图标按钮（距卡片右缘 3px，字形与各行右缘对齐）、32px 圆角“全部”行、圆角树节点。
   本面板在页签之内、又以只读形态用于文件选择器，故保留标题栏，不设搜索与折叠；选择器的白底对话框内由其自行加边框 */
.category-tree {
  width: 220px;
  flex-shrink: 0;
  border-radius: 2px;
  padding: 8px;
  background: var(--el-bg-color);
}
.category-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 24px;
  padding-left: 8px;
  /* 卡片内边距 8px，按钮盒须到右缘 3px 处 */
  margin-right: -5px;
  margin-bottom: 4px;
}
.category-toolbar :deep(.el-button) {
  width: 20px;
  height: 24px;
  padding: 0;
}
.category-toolbar .title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.all-shared {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 8px;
  margin-bottom: 2px;
  font-size: 14px;
  border-radius: 4px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}
.all-shared:hover {
  background: var(--el-fill-color-light);
}
.all-shared.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.category-tree :deep(.el-tree-node__content) {
  border-radius: 4px;
}
.category-tree :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) {
  color: var(--el-color-primary);
}
.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
}
.node-main {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}
.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-actions {
  display: none;
  gap: 6px;
  color: var(--el-text-color-secondary);
}
.tree-node:hover .node-actions {
  display: inline-flex;
}
.node-actions .el-icon:hover {
  color: var(--el-color-primary);
}
</style>
