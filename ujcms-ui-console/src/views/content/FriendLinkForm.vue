<script setup lang="ts">
import { computed, onMounted, ref, PropType } from 'vue';
import { queryFriendLink, createFriendLink, updateFriendLink, deleteFriendLink, queryFriendLinkTypeList } from '@/api/content';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import { ImageUpload } from '@/components/Upload';

defineOptions({
  name: 'FriendLinkForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
const typeList = ref<any[]>([]);
const currentType = computed(() => typeList.value.find((item) => item.id === values.value.typeId));
onMounted(async () => {
  typeList.value = await queryFriendLinkTypeList();
});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.content.friendLink')"
    :query-bean="queryFriendLink"
    :create-bean="createFriendLink"
    :update-bean="updateFriendLink"
    :delete-bean="deleteFriendLink"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ typeId: typeList[0]?.id, targetBlank: true, enabled: true })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="friendLink"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="friendLink.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item
        prop="url"
        :rules="[
          { required: true, message: () => $t('v.required') },
          { pattern: /^(http|\/).*$/, message: () => $t('friendLink.error.url') },
        ]"
      >
        <template #label><label-tip message="friendLink.url" help /></template>
        <el-input v-model="values.url" maxlength="255"></el-input>
      </el-form-item>
      <el-form-item prop="typeId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="friendLink.type" /></template>
        <el-select v-model="values.typeId" class="w-full">
          <el-option v-for="item in typeList" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="currentType?.type === 2" prop="logo" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="friendLink.logo" /></template>
        <image-upload v-model="values.logo" :width="currentType.imageWidth" :height="currentType.imageHeight" mode="manual"></image-upload>
      </el-form-item>
      <el-form-item prop="description">
        <template #label><label-tip message="friendLink.description" /></template>
        <el-input v-model="values.description" type="textarea" :rows="3" maxlength="300"></el-input>
      </el-form-item>
      <el-form-item prop="targetBlank">
        <template #label><label-tip message="friendLink.targetBlank" /></template>
        <el-switch v-model="values.targetBlank" />
      </el-form-item>
      <el-form-item prop="enabled">
        <template #label><label-tip message="friendLink.enabled" /></template>
        <el-switch v-model="values.enabled" />
      </el-form-item>
    </template>
  </dialog-form>
</template>
