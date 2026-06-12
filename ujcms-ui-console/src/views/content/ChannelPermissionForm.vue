<script setup lang="ts">
import { ref, watch, computed, onMounted, toRefs } from 'vue';
import { useI18n } from 'vue-i18n';
import { currentUser } from '@/stores/useCurrentUser';
import { queryGroupList } from '@/api/user';
import { queryChannel } from '@/api/content';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'ChannelPermissionForm',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null } });
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { beanId, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const tabName = ref<string>('permission');
const form = ref<any>();
const bean = ref<any>({});
const values = ref<any>({});
const buttonLoading = ref<boolean>(false);

const groupList = ref<any[]>([]);

const disabled = computed(() => (bean.value.global && !currentUser.globalPermission) || currentUser.rank > bean.value.rank);

const fetchChannel = async () => {
  if (beanId?.value != null) {
    bean.value = await queryChannel(beanId.value);
    values.value = { ...bean.value };
  }
};
const fetchGroupList = async () => {
  groupList.value = await queryGroupList();
};

watch(visible, () => {
  if (visible.value) {
    fetchChannel();
  }
});
onMounted(() => {
  fetchGroupList();
});

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      // await updateChannelPermission(values.value);
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
  <el-drawer :title="$t('permissionSettings')" :with-header="false" :model-value="modelValue" :size="576" @update:model-value="(event) => $emit('update:modelValue', event)">
    <template #default>
      <el-form ref="form" :model="values" :disabled="disabled" label-width="150px">
        <el-tabs v-model="tabName">
          <el-tab-pane :label="$t('role.permission')" name="permission">
            <el-form-item prop="groupIds">
              <template #label><label-tip message="channel.group" /></template>
              <el-checkbox-group v-model="values.groupIds">
                <el-tooltip v-for="item in groupList" :key="item.id" :content="item.description">
                  <el-checkbox :value="item.id">{{ item.name }}</el-checkbox>
                </el-tooltip>
              </el-checkbox-group>
            </el-form-item>
          </el-tab-pane>
          <el-tab-pane :label="$t('role.grantPermission')" name="grantPermission"> </el-tab-pane>
          <el-tab-pane :label="$t('role.articlePermission')" name="articlePermission"> </el-tab-pane>
        </el-tabs>
      </el-form>
    </template>
    <template #footer>
      <div class="flex items-center justify-between">
        <div>
          <el-tag>{{ values?.name }}</el-tag>
        </div>
        <div>
          <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
          <el-button type="primary" :loading="buttonLoading" :disabled="disabled" @click="() => handleSubmit()">{{ $t('save') }}</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>
