<script setup lang="ts">
import { ref, watch, computed, onMounted, toRefs } from 'vue';
import { useI18n } from 'vue-i18n';
import { currentUser } from '@/stores/useCurrentUser';
import { queryUser, updateUserPermission, queryRoleList } from '@/api/user';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'UserPermissionForm',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null } });
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { beanId, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const form = ref<any>();
const bean = ref<any>({});
const values = ref<any>({});
const buttonLoading = ref<boolean>(false);

const roleList = ref<any[]>([]);
const disabled = computed(() => currentUser.rank > bean.value.rank);
const fetchUser = async () => {
  if (beanId.value != null) {
    bean.value = await queryUser(beanId.value);
    values.value = { ...bean.value, roleIds: bean.value.roleList.map((item: any) => item.id) ?? [] };
  }
};
const fetchRoleList = async () => {
  roleList.value = await queryRoleList();
};

watch(visible, () => {
  if (visible.value) {
    fetchUser();
  }
});
onMounted(() => {
  fetchRoleList();
});

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      await updateUserPermission(values.value);
      emit('finished');
      emit('update:modelValue', false);
      ElMessage.success(t('success'));
    } finally {
      buttonLoading.value = false;
    }
  });
};
</script>

<template>
  <el-drawer :title="$t('permissionSettings')" :model-value="modelValue" :size="768" @update:model-value="(event) => $emit('update:modelValue', event)">
    <template #default>
      <el-form ref="form" :model="values" :disabled="disabled" label-width="150px">
        <el-form-item prop="roleIds">
          <template #label><label-tip message="user.role" help /></template>
          <el-checkbox-group v-model="values.roleIds">
            <el-checkbox v-for="item in roleList" :key="item.id" :value="item.id" :disabled="values.rank > item.rank">{{ `${item.name}(${item.rank})` }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item prop="rank" :rules="[{ required: true, message: () => $t('v.required') }]">
          <template #label><label-tip message="user.rank" help /></template>
          <el-input-number v-model.number="values.rank" :min="disabled ? 0 : currentUser.rank" :max="32767" />
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <div class="flex items-center justify-between">
        <div>
          <el-tag>{{ values?.username }}</el-tag>
        </div>
        <div>
          <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
          <el-button type="primary" :loading="buttonLoading" :disabled="disabled" @click="() => handleSubmit()">{{ $t('save') }}</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>
