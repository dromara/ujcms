<script setup lang="ts">
import { ref, watch, type PropType } from 'vue';
import { currentUser } from '@/stores/useCurrentUser';
import { queryBlock, createBlock, updateBlock, deleteBlock, blockAliasExist, blockScopeNotAllowed, queryBlockCategories, queryModelList } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'BlockForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
});
defineEmits({ 'update:modelValue': null, finished: null });
const dialog = ref<any>();
const focus = ref<any>();
const values = ref<any>({});
// 全局区块属全局数据：无全局数据权限时只读（服务端同规），不给出必然 403 的操作
const readonly = (bean: any) => bean?.scope === 2 && !currentUser.globalPermission;
// 别名的唯一性以“别名 × 共享范围”为命名空间（全局一组、每站一组）：共享范围变了，未改动的别名也要重新校验
const handleScopeChange = () => {
  dialog.value?.form?.validateField('alias').catch(() => undefined);
  dialog.value?.form?.validateField('blockItemModelId').catch(() => undefined);
};
const blockItemModelList = ref<any[]>([]);
const categories = ref<string[]>([]);

const fetchBlockItemModelList = async () => {
  blockItemModelList.value = await queryModelList({ type: 'blockItem' });
};
const fetchCategories = async () => {
  categories.value = await queryBlockCategories();
};
const queryCategorySuggestions = (queryString: string, cb: (data: { value: string }[]) => void) => {
  const list = categories.value.map((c) => ({ value: c }));
  cb(queryString ? list.filter((it) => it.value.toLowerCase().includes(queryString.toLowerCase())) : list);
};

// 每次打开时取数而非挂载时取一次：区块就在同一屏增删，分类候选随之变化（immediate：热更新重挂时对话框可能已是打开状态）
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      fetchBlockItemModelList();
      fetchCategories();
    }
  },
  { immediate: true },
);
</script>

<template>
  <dialog-form
    ref="dialog"
    v-model:values="values"
    :name="$t('block.label')"
    :query-bean="queryBlock"
    :create-bean="createBlock"
    :update-bean="updateBlock"
    :delete-bean="deleteBlock"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="
      () => ({
        scope: 0,
        enabled: true,
        recommendable: true,
      })
    "
    :to-values="(bean: any) => ({ ...bean })"
    :disable-edit="(bean: any) => readonly(bean)"
    perms="block"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <!-- 删除区块在列表页的顶栏：那里的确认文案带区块项数（删除会级联区块项），此处不再提供。
         插槽内容为空时 Vue 会回落到默认的删除按钮，故放一个 hidden 占位（带 hidden 属性的元素不参与 space-x 间距） -->
    <template #header-action><span hidden></span></template>
    <template #default="{ bean }">
      <el-row>
        <el-col :span="24">
          <el-form-item prop="name" :label="$t('block.name')" :rules="{ required: true, message: () => $t('v.required') }">
            <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item
            prop="alias"
            :label="$t('block.alias')"
            :rules="[
              { required: true, message: () => $t('v.required') },
              { pattern: /^[一-龥\w-]*$/, message: () => $t('block.error.aliasPattern') },
              {
                asyncValidator: async (rule: any, value: any, callback: any) => {
                  if ((value !== bean.alias || values.scope !== bean.scope) && (await blockAliasExist(value, values.scope))) {
                    callback($t('block.error.aliasExist'));
                    return;
                  }
                  callback();
                },
              },
            ]"
          >
            <el-input v-model="values.alias" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item
            prop="scope"
            :label="$t('block.scope')"
            :rules="[
              { required: true, message: () => $t('v.required') },
              {
                asyncValidator: async (rule: any, value: any, callback: any) => {
                  if (value !== bean.scope && (await blockScopeNotAllowed(values.scope, values.id))) {
                    callback($t('block.error.scopeNotAllowd'));
                    return;
                  }
                  callback();
                },
              },
            ]"
          >
            <el-radio-group v-model="values.scope" @change="handleScopeChange">
              <el-radio v-for="n in [0, 2]" :key="n" :value="n" :disabled="n === 2 && !currentUser.globalPermission">{{ $t(`block.scope.${n}`) }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="category" :label="$t('block.category')">
            <el-autocomplete v-model="values.category" class="w-full" clearable :fetch-suggestions="queryCategorySuggestions" :maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            prop="blockItemModelId"
            :label="$t('block.blockItemModel')"
            :rules="{
              validator: (rule: any, value: any, callback: any) => {
                const model = blockItemModelList.find((item) => item.id === value);
                if (value != null && values.scope === 2 && model != null && model.scope !== 2) {
                  callback($t('block.error.modelScope'));
                  return;
                }
                callback();
              },
            }"
          >
            <!-- 全局区块的区块项分属各站，模型须对每个站点可见：全局区块只可绑定全局共享的模型（服务端同规） -->
            <el-select v-model="values.blockItemModelId" class="w-full" clearable>
              <el-option v-for="item in blockItemModelList" :key="item.id" :label="item.name" :value="item.id" :disabled="values.scope === 2 && item.scope !== 2"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="title" :label="$t('block.title')">
            <el-input v-model="values.title" maxlength="150"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="moreUrl" :label="$t('block.moreUrl')" :rules="{ pattern: /^(http|\/).*$/, message: () => $t('blockItem.error.linkUrl') }">
            <el-input v-model="values.moreUrl" maxlength="255"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="enabled" :label="$t('block.enabled')" :rules="{ required: true, message: () => $t('v.required') }">
            <el-switch v-model="values.enabled"></el-switch>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="recommendable" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="block.recommendable"></label-tip></template>
            <el-switch v-model="values.recommendable"></el-switch>
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>
