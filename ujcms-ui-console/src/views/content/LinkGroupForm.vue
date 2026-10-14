<script setup lang="ts">
import { onMounted, ref, type PropType } from 'vue';
import { localizedLabel } from '@/i18n';
import { queryLinkGroup, createLinkGroup, updateLinkGroup, deleteLinkGroup, linkGroupAliasExist, queryLinkGroupSlots } from '@/api/content';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'LinkGroupForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
// 展示位词汇表：当前站点桌面∪手机主题的清单声明（linkSlots；空 slot = 不自动展示，模板按别名引用）
const slots = ref<any[]>([]);
onMounted(async () => {
  slots.value = await queryLinkGroupSlots();
});
const slotLabel = (slot: any) => localizedLabel(slot.label, slot.name);
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('linkGroup.title')"
    :query-bean="queryLinkGroup"
    :create-bean="createLinkGroup"
    :update-bean="updateLinkGroup"
    :delete-bean="deleteLinkGroup"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ type: 1, imageWidth: 200, imageHeight: 60 })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="linkGroup"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <!-- 删除链接组在列表页的顶栏：那里的确认文案带链接数（删除会级联链接），此处不再提供。
         插槽内容为空时 Vue 会回落到默认的删除按钮，故放一个 hidden 占位（带 hidden 属性的元素不参与 space-x 间距） -->
    <template #header-action><span hidden></span></template>
    <template #default="{ bean }">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="linkGroup.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item
        prop="alias"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            asyncValidator: async (rule, value, callback) => {
              if (value !== bean.alias && (await linkGroupAliasExist(value))) {
                callback($t('linkGroup.error.aliasExist'));
                return;
              }
              callback();
            },
          },
        ]"
      >
        <template #label><label-tip message="linkGroup.alias" /></template>
        <el-input v-model="values.alias" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="slot">
        <template #label><label-tip message="linkGroup.slot" help /></template>
        <el-select v-model="values.slot" class="w-full" clearable :placeholder="$t('linkGroup.slot.none')">
          <el-option v-for="item in slots" :key="item.name" :label="slotLabel(item)" :value="item.name" />
        </el-select>
      </el-form-item>
      <el-form-item prop="type">
        <template #label><label-tip message="linkGroup.type" /></template>
        <el-radio-group v-model="values.type">
          <el-radio :value="1">{{ $t('linkGroup.type.text') }}</el-radio>
          <el-radio :value="2">{{ $t('linkGroup.type.image') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="values.type === 2">
        <el-form-item prop="imageWidth" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="linkGroup.imageWidth" /></template>
          <el-input-number v-model="values.imageWidth" :min="16" :max="9999"></el-input-number>
        </el-form-item>
        <el-form-item prop="imageHeight" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="linkGroup.imageHeight" /></template>
          <el-input-number v-model="values.imageHeight" :min="16" :max="9999"></el-input-number>
        </el-form-item>
      </template>
      <el-form-item prop="description">
        <template #label><label-tip message="linkGroup.description" /></template>
        <el-input v-model="values.description" type="textarea" :rows="3" maxlength="300"></el-input>
      </el-form-item>
    </template>
  </dialog-form>
</template>
