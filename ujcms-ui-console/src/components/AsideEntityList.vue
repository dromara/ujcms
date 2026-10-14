<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import { Plus, Grid, Edit } from '@element-plus/icons-vue';
import draggable from 'vuedraggable';
import CollapsibleAside from '@/components/CollapsibleAside.vue';
import { asideRowClass } from '@/components/asideRow';

// 左侧实体列表（docs/spec/console-master-detail）：既是导航也是管理——搜索、分组、组内拖拽排序、列表级“新增”、行级“编辑”。
// 选中项经 v-model 传入传出（路由状态由所在视图持有，本组件不接触路由）；没有“新增”“编辑”权限与排序权限时即退化为纯导航。
// 行上只有“编辑”，没有“删除”：删除的禁用条件（被模型使用、被其他站点使用）只对选中项取数，且父级删除会级联其成员——
// 删除留在主区顶栏（EntityHeaderCard），操作者删的是眼前看到的那一项。
defineOptions({ name: 'AsideEntityList' });

export interface AsideEntityGroup {
  key: string | number;
  // 组名：仅在多于一组有数据时显示
  caption?: string;
  // 须为视图所持 ref 的数组本身，不可是 computed 或过滤后的副本：vuedraggable 就地改写 :list
  items: any[];
  sortable?: boolean;
}
export interface AsideEntityMoved {
  group: AsideEntityGroup;
  element: any;
  oldIndex: number;
  newIndex: number;
  // 该组拖拽后的 ID 序列：供“整组 ID 列表”式排序接口（PUT /order）
  ids: any[];
  // 被拖动项 / 拖拽前占据落点的项：供 fromId → toId 式排序接口（update-order）
  fromId: any;
  toId: any;
}

const props = withDefaults(
  defineProps<{
    groups: AsideEntityGroup[];
    width?: string;
    // 参与搜索的字段。取值为空的字段不参与匹配（String(undefined) 会匹配到字母）
    searchKeys?: string[];
    // 行尾计数取自该字段，缺省不显示
    countKey?: string;
    // “全部”伪行：不属于任何分组，不可拖拽，不受搜索影响（始终是回到默认视图的入口）
    allOption?: { value: string; label: string };
    searchPlaceholder?: string;
    noMatchText?: string;
    // “新增”按钮的提示兼无障碍名称；不传即不显示按钮
    addLabel?: string;
    addDisabled?: boolean;
    // 行尾“编辑”按钮（悬停或键盘聚焦该行时才出现）的提示兼无障碍名称；不传即无此按钮。
    // 无权限时不渲染而非禁用：只在悬停时出现的按钮，禁用态只是噪音，还白占行尾的宽度
    editLabel?: string;
    editDisabled?: boolean;
    // 一项都没有时的说明；不传即不显示空状态。loading 期间不显示，避免首次取数前一闪
    emptyText?: string;
    loading?: boolean;
  }>(),
  {
    width: '220px',
    searchKeys: () => ['name', 'alias'],
    countKey: undefined,
    allOption: undefined,
    searchPlaceholder: undefined,
    noMatchText: undefined,
    addLabel: undefined,
    addDisabled: false,
    editLabel: undefined,
    editDisabled: false,
    emptyText: undefined,
    loading: false,
  },
);
const selected = defineModel<string>();
const collapsed = defineModel<boolean>('collapsed', { default: false });
const emit = defineEmits<{
  // 仅在选中项确实变化时触发，且晚于 update:modelValue：视图在此清过滤词、取数
  select: [id: string];
  add: [];
  // 不改变选中项：可以留在 A 的数据上改 B 的名称
  edit: [item: any];
  moved: [event: AsideEntityMoved];
}>();

const uid = useId();
const keyword = ref<string>('');
const filtering = computed(() => keyword.value.trim() !== '');
const matches = (item: any) => {
  const text = keyword.value.trim().toLowerCase();
  return text === '' || props.searchKeys.some((key) => item[key] != null && String(item[key]).toLowerCase().includes(text));
};
const visibleGroups = computed(() => props.groups.filter((group) => group.items.length > 0));
const noMatch = computed(() => filtering.value && !visibleGroups.value.some((group) => group.items.some(matches)));
const empty = computed(() => !props.loading && props.emptyText != null && visibleGroups.value.length <= 0);

const select = (id: string) => {
  if (id === selected.value) {
    return;
  }
  selected.value = id;
  emit('select', id);
};
const handleChange = (group: AsideEntityGroup, event: any) => {
  if (event.moved == null) {
    return;
  }
  const { element, oldIndex, newIndex } = event.moved;
  // vuedraggable 此时已就地改写数组：拖拽前占据落点的项，现位于落点朝原位置方向的相邻位
  const displaced = group.items[newIndex > oldIndex ? newIndex - 1 : newIndex + 1];
  emit('moved', { group, element, oldIndex, newIndex, ids: group.items.map((item) => item.id), fromId: element.id, toId: displaced?.id });
};
// 行是卡片内的圆角块，四周留白（留白在外层容器上，见模板）；外观与根行共用 asideRowClass，本列表的行高 36px、行距 40px
const rowClass = (active: boolean) => [...asideRowClass(active), 'h-9'];
// 实体行的键盘焦点落在行块之内（名称、编辑按钮），行块自身不可聚焦：聚焦态的底色改由 :has(:focus-visible) 给出；group 供行尾的互换使用
const entityRowClass = (active: boolean) => [...rowClass(active), 'group', active ? '' : 'has-[:focus-visible]:bg-gray-100'];
const editable = computed(() => props.editLabel != null && !props.editDisabled);
const hasCount = (item: any) => props.countKey != null && item[props.countKey] != null;
</script>

<template>
  <collapsible-aside v-model:collapsed="collapsed" v-model:keyword="keyword" :width="width" :search-placeholder="searchPlaceholder" body-class="pb-2">
    <!-- 新增是列表级操作，与搜索同在列表头；不放进列表体（紧贴首个组名，易被读成该组的一行） -->
    <template v-if="addLabel" #actions>
      <!-- trigger-keys 置空：el-tooltip 默认对回车 / 空格 preventDefault（用来开合提示），会吞掉所包按钮的键盘点击 -->
      <el-tooltip :content="addLabel" placement="top" :trigger-keys="[]">
        <el-button type="primary" :disabled="addDisabled" :icon="Plus" :aria-label="addLabel" link @click="() => emit('add')" />
      </el-tooltip>
    </template>
    <!-- “全部”是各项之上的范围，不是其中一项：文字顶到行块左缘（与树屏的“全部栏目 / 根栏目”行一致，也与上方搜索框文字、下方拖拽柄同一条左线），
         不给图标位留白——留白只会读成“这一行缺了图标” -->
    <div v-if="allOption" class="px-2 py-0.5">
      <div
        role="button"
        tabindex="0"
        :class="rowClass(allOption.value === selected)"
        @click="() => select(allOption!.value)"
        @keydown.enter.prevent="() => select(allOption!.value)"
        @keydown.space.prevent="() => select(allOption!.value)"
      >
        <span class="flex-1 truncate">{{ allOption.label }}</span>
      </div>
    </div>
    <div v-for="group in visibleGroups" :key="group.key">
      <div v-if="visibleGroups.length > 1 && group.caption && group.items.some(matches)" class="px-4 pt-3 pb-1 text-xs text-gray-placeholder">{{ group.caption }}</div>
      <draggable
        :list="group.items"
        item-key="id"
        handle=".drag-handle"
        ghost-class="opacity-50"
        :animation="200"
        :group="{ name: `${uid}-${group.key}`, pull: false, put: false }"
        :disabled="!group.sortable || filtering"
        @change="(event: any) => handleChange(group, event)"
      >
        <!-- item 插槽须恰为一个原生元素（vuedraggable 在其 el 上登记拖拽上下文）；搜索用 v-show 隐藏而非 v-if，行号才与数组下标一致。
             行距用这层的 padding 而非行的 margin：margin 会穿过本层与相邻行合并 -->
        <template #item="{ element }">
          <div v-show="matches(element)" class="px-2 py-0.5">
            <!-- 行块只承载外观与鼠标点击；role=button 与键盘焦点在名称上，行尾的编辑按钮与之并列而非嵌套：
                 按钮套按钮，读屏会把里层拍平，行的空格键处理也会吞掉里层的按键（同根行的 #root-extra） -->
            <div :class="entityRowClass(String(element.id) === selected)" @click="() => select(String(element.id))">
              <el-icon v-if="group.sortable && !filtering" class="drag-handle cursor-move mr-1.5 shrink-0 text-gray-placeholder"><Grid /></el-icon>
              <span v-else class="w-[14px] mr-1.5 shrink-0"></span>
              <span
                role="button"
                tabindex="0"
                class="flex-1 truncate outline-none"
                :title="element.name"
                @keydown.enter.prevent="() => select(String(element.id))"
                @keydown.space.prevent="() => select(String(element.id))"
              >
                {{ element.name }}
              </span>
              <slot name="suffix" :item="element" :group="group"></slot>
              <!-- 计数与编辑按钮叠在同一格（格宽取两者较大），悬停或键盘聚焦该行时互换：名称的可用宽度两态相同，鼠标经过时不会忽然多出省略号。
                   用 visibility 而非 display 互换，格宽才不变；suffix（锁、停用）是状态，不参与互换 -->
              <span v-if="editable || hasCount(element)" class="ml-1.5 shrink-0 grid items-center justify-items-end">
                <span
                  v-if="hasCount(element)"
                  :class="['col-start-1 row-start-1 text-xs text-gray-placeholder tabular-nums', editable ? 'group-hover:invisible group-has-[:focus-visible]:invisible' : '']"
                >
                  {{ element[countKey!] }}
                </span>
                <!-- 与头部图标按钮同一规格（20×24 点击区、灰 → 主色）；右移 3px，让可见字形而非按钮盒的右缘落在计数的右缘上 -->
                <el-tooltip v-if="editable" :content="editLabel" placement="top" :trigger-keys="[]">
                  <el-button
                    :icon="Edit"
                    :aria-label="editLabel"
                    link
                    class="col-start-1 row-start-1 w-5 h-6 p-0 -mr-[3px] invisible group-hover:visible group-has-[:focus-visible]:visible !text-gray-secondary hover:!text-primary"
                    @click.stop="() => emit('edit', element)"
                  />
                </el-tooltip>
              </span>
            </div>
          </div>
        </template>
      </draggable>
    </div>
    <el-empty v-if="noMatch" :image-size="48" :description="noMatchText" />
    <el-empty v-else-if="empty" :image-size="48" :description="emptyText">
      <el-button v-if="addLabel && !addDisabled" type="primary" link @click="() => emit('add')">{{ addLabel }}</el-button>
    </el-empty>
  </collapsible-aside>
</template>
