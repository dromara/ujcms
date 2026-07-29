<script setup lang="ts">
import { ref, PropType } from 'vue';
import { queryFriendLinkType, createFriendLinkType, updateFriendLinkType, deleteFriendLinkType, friendLinkTypeAliasExist } from '@/api/content';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'FriendLinkTypeForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.config.friendLinkType')"
    :query-bean="queryFriendLinkType"
    :create-bean="createFriendLinkType"
    :update-bean="updateFriendLinkType"
    :delete-bean="deleteFriendLinkType"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ type: 1, imageWidth: 200, imageHeight: 60 })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="friendLinkType"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{ bean }">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="friendLinkType.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item
        prop="alias"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            asyncValidator: async (rule, value, callback) => {
              if (value !== bean.alias && (await friendLinkTypeAliasExist(value))) {
                callback($t('friendLinkType.error.aliasExist'));
                return;
              }
              callback();
            },
          },
        ]"
      >
        <template #label><label-tip message="friendLinkType.alias" /></template>
        <el-input v-model="values.alias" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="type">
        <template #label><label-tip message="friendLinkType.type" /></template>
        <el-radio-group v-model="values.type">
          <el-radio :value="1">{{ $t('friendLinkType.type.text') }}</el-radio>
          <el-radio :value="2">{{ $t('friendLinkType.type.image') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="values.type === 2">
        <el-form-item prop="imageWidth" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="friendLinkType.imageWidth" /></template>
          <el-input-number v-model="values.imageWidth" :min="16" :max="9999"></el-input-number>
        </el-form-item>
        <el-form-item prop="imageHeight" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="friendLinkType.imageHeight" /></template>
          <el-input-number v-model="values.imageHeight" :min="16" :max="9999"></el-input-number>
        </el-form-item>
      </template>
      <el-form-item prop="description">
        <template #label><label-tip message="friendLinkType.description" /></template>
        <el-input v-model="values.description" type="textarea" :rows="3" maxlength="300"></el-input>
      </el-form-item>
    </template>
  </dialog-form>
</template>
