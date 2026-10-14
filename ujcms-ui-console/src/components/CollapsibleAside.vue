<script setup lang="ts">
import { DArrowLeft, DArrowRight, Search } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { asideRowClass } from '@/components/asideRow';

// 内容区左侧导航的统一外框（docs/spec/console-aside）：头部（搜索 + #actions 图标按钮 + 折叠键）与可选的根行由本组件独占，
// 视图只给数据与主体。不设自由头部插槽：各视图自行拼头部，正是此前各屏样式不一的来源
defineOptions({ name: 'CollapsibleAside' });

withDefaults(
  defineProps<{
    width?: string;
    // 仅包裹默认插槽；头部与根行的内缩固定，不随之变化
    bodyClass?: string;
    searchPlaceholder?: string;
    // 传入即显示根行（根栏目 / 全部栏目 / 全部组织）
    rootLabel?: string;
    rootActive?: boolean;
  }>(),
  { width: '220px', bodyClass: 'px-2 pb-2', searchPlaceholder: undefined, rootLabel: undefined, rootActive: false },
);
const collapsed = defineModel<boolean>('collapsed', { default: false });
const keyword = defineModel<string>('keyword', { default: '' });
const emit = defineEmits<{ root: [] }>();
const { t } = useI18n();
</script>

<template>
  <!-- 折叠后卡片宽 32px；与主区的间距两态同为 12px（pr-3），只有宽度参与过渡 -->
  <el-aside :width="collapsed ? '44px' : width" class="pr-3 transition-all">
    <el-scrollbar class="bg-white rounded-sm">
      <!-- 右内缩 3px 而非 8px：按钮盒内图标两侧还有留白，这样折叠键的可见字形右缘恰与下方各行的 8px 右缘对齐；3px 也正好容下按钮的键盘焦点环 -->
      <div :class="['pt-2 pb-1', collapsed ? 'px-1' : 'pl-2 pr-[3px]']">
        <!-- 本行带 flex，不可加 v-show（tailwind important: true 下 display 工具类压过 v-show 的内联样式）；折叠时隐藏的是其内不带 display 类的包裹层 -->
        <div :class="['aside-header flex items-center h-6', collapsed ? 'justify-center' : '']">
          <div v-show="!collapsed" class="flex-1 min-w-0">
            <!-- 间距只留在搜索框之后（4px）；图标按钮之间不再加间距，按钮盒内的留白已足够分隔 -->
            <div class="flex items-center">
              <el-input v-model="keyword" :placeholder="searchPlaceholder" :suffix-icon="Search" size="small" clearable class="flex-1 min-w-0 mr-1" />
              <slot name="actions"></slot>
            </div>
          </div>
          <!-- trigger-keys 置空：el-tooltip 默认对回车 / 空格 preventDefault（用来开合提示），会吞掉所包按钮的键盘点击 -->
          <el-tooltip :content="t(collapsed ? 'expand' : 'collapse')" :placement="collapsed ? 'right' : 'top'" :trigger-keys="[]">
            <el-button
              :icon="collapsed ? DArrowRight : DArrowLeft"
              :aria-label="t(collapsed ? 'expand' : 'collapse')"
              link
              class="!text-gray-secondary hover:!text-primary"
              @click="collapsed = !collapsed"
            />
          </el-tooltip>
        </div>
      </div>
      <!-- 主体用 v-show 而非 v-if：视图直接调用树的 filter()，且折叠再展开须保留树的展开状态 -->
      <div v-show="!collapsed">
        <div v-if="rootLabel != null" class="px-2 py-0.5">
          <div class="flex items-center">
            <div
              role="button"
              tabindex="0"
              :class="[asideRowClass(rootActive), 'flex-1 min-w-0 h-8']"
              :title="rootLabel"
              @click="() => emit('root')"
              @keydown.enter.prevent="() => emit('root')"
              @keydown.space.prevent="() => emit('root')"
            >
              <span class="truncate">{{ rootLabel }}</span>
            </div>
            <!-- 行尾控件（如编辑模式开关）作用于整棵树，不属于根行：放在行块之外，也免得行块的空格键处理吞掉开关的按键 -->
            <div v-if="$slots['root-extra']" class="flex items-center ml-2 shrink-0"><slot name="root-extra"></slot></div>
          </div>
        </div>
        <div :class="['aside-body', bodyClass]"><slot></slot></div>
      </div>
    </el-scrollbar>
  </el-aside>
</template>

<style scoped>
/* 头部所有图标按钮同一规格：20×24 点击区（与 small 输入框同高；14px 图标的字形只有 9～11px 宽，盒子再宽，按钮之间就显得松散）；
   清掉 EP 相邻按钮的 12px 左外边距（el-tooltip 不产生包裹元素） */
.aside-header :deep(.el-button) {
  width: 20px;
  height: 24px;
  padding: 0;
  margin-left: 0;
}
.aside-header :deep(.el-input__inner) {
  text-overflow: ellipsis;
}
/* EP 为 clearable 常驻一个 visibility:hidden 的清除图标（占 22px）。占位符显示时输入框必为空、清除图标必不可见：
   此时不占位，把宽度还给占位符（英文“Search organizations”等才放得下）；一旦输入内容，图标照常出现 */
.aside-header :deep(.el-input__inner:placeholder-shown ~ .el-input__suffix .el-input__clear) {
  display: none;
}
/* 树与行块同一套选中语言：圆角 + 主色文字；节点高度保持 EP 的 26px（大栏目树的密度） */
.aside-body :deep(.el-tree-node__content) {
  border-radius: 4px;
}
.aside-body :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) {
  color: var(--el-color-primary);
}
</style>
