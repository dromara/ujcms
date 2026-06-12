<script setup lang="ts">
import { ref, toRefs, watch, PropType } from 'vue';
import { queryOrg, createOrg, updateOrg, deleteOrg, queryOrgList } from '@/api/user';
import { toTree, disableSubtree } from '@/utils/tree';
import DialogForm from '@/components/DialogForm.vue';

defineOptions({
  name: 'OrgForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  parentId: { type: String, required: true },
  showGlobalData: { type: Boolean, required: true },
});
const { parentId, beanId, showGlobalData, modelValue: visible } = toRefs(props);
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const focus = ref<any>();
const values = ref<any>({});
const orgList = ref<any[]>([]);

const fetchOrgList = async (bean?: any) => {
  orgList.value = disableSubtree(toTree(await queryOrgList({ current: !showGlobalData.value })), bean?.id);
};
const finished = async (bean?: any) => {
  await fetchOrgList(bean);
  emit('finished');
};
watch(visible, () => {
  if (visible.value) {
    fetchOrgList();
  }
});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.user.org')"
    :query-bean="queryOrg"
    :create-bean="createOrg"
    :update-bean="updateOrg"
    :delete-bean="deleteOrg"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="(bean: any): any => ({ parentId: parentId })"
    :to-values="(bean) => ({ ...bean })"
    :disable-delete="(bean) => bean.id <= 1 || bean.id === orgList[0]?.id"
    perms="org"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="finished"
    @bean-change="() => fetchOrgList()"
  >
    <template #default="{ isEdit }">
      <el-form-item v-if="!isEdit || values.id !== orgList[0]?.id" prop="parentId" :label="$t('org.parent')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-tree-select
          v-model="values.parentId"
          :data="orgList"
          node-key="id"
          :props="{ label: 'name', disabled: 'disabled' }"
          :default-expanded-keys="orgList.map((item) => item.id)"
          :render-after-expand="false"
          :disabled="isEdit"
          check-strictly
          class="w-full"
        />
      </el-form-item>
      <el-form-item prop="name" :label="$t('org.name')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="address" :label="$t('org.address')"><el-input v-model="values.address" maxlength="255"></el-input></el-form-item>
      <el-form-item prop="phone" :label="$t('org.phone')"><el-input v-model="values.phone" maxlength="100"></el-input></el-form-item>
      <el-form-item prop="contacts" :label="$t('org.contacts')"><el-input v-model="values.contacts" maxlength="100"></el-input></el-form-item>
    </template>
  </dialog-form>
</template>
