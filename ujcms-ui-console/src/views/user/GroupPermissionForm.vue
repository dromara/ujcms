<script setup lang="ts">
import { ref, watch, computed, onMounted, toRefs } from 'vue';
import { useI18n } from 'vue-i18n';
import { toTree } from '@/utils/tree';
import { currentUser } from '@/stores/useCurrentUser';
import { queryGroup, updateGroupPermission, groupAccessPermissions } from '@/api/user';
import { queryChannelList } from '@/api/content';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'GroupPermissionForm',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null } });
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { beanId, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const form = ref<any>();
const bean = ref<any>({});
const values = ref<any>({});
const buttonLoading = ref<boolean>(false);

const accessPermissionExpand = ref<boolean>(true);
const accessPermissionCheck = ref<boolean>(false);
const accessPermissionTree = ref<any>();
const channelData = ref<any[]>([]);

const disabled = computed(() => currentUser.rank > bean.value.rank);
const fetchGroup = async () => {
  if (beanId?.value != null) {
    bean.value = await queryGroup(beanId.value);
    values.value = { ...bean.value };
  }
};
const fetchChannelData = async () => {
  channelData.value = toTree(await queryChannelList());
};
const fetchAccessPermissions = async () => {
  if (beanId?.value != null) {
    const accessPermissions = await groupAccessPermissions(beanId.value);
    accessPermissionTree.value?.setCheckedKeys([]);
    accessPermissions.forEach((key: string) => {
      accessPermissionTree.value?.setChecked(key, true, false);
    });
  }
};

watch(visible, () => {
  if (visible.value) {
    fetchGroup();
    fetchAccessPermissions();
  }
});
onMounted(() => {
  fetchChannelData();
});

const expandTree = (checked: boolean, tree: any, data: any[], key?: string) => {
  data.forEach((item) => {
    if (item.children) {
      tree.getNode(item[key ?? 'key']).expanded = checked;
      expandTree(checked, tree, item.children, key);
    }
  });
};
const checkTree = (checked: boolean, tree: any, data: any[], key?: string) => {
  tree.setCheckedKeys(checked ? data.map((item: any) => item[key ?? 'key']) : []);
};

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      await updateGroupPermission(values.value);
      emit('finished');
      emit('update:modelValue', false);
      ElMessage.success(t('success'));
    } finally {
      buttonLoading.value = false;
    }
  });
};
const handleAccessPermission = () => {
  if (accessPermissionTree.value != null) {
    values.value.accessPermissions = [...accessPermissionTree.value.getCheckedNodes(), ...accessPermissionTree.value.getHalfCheckedNodes()].map((item) => item.id);
  }
};
</script>

<template>
  <el-drawer :title="$t('permissionSettings')" :model-value="modelValue" :size="414" @update:model-value="(event) => $emit('update:modelValue', event)">
    <template #default>
      <el-form ref="form" :model="values" :disabled="disabled" label-width="150px">
        <el-form-item prop="allAccessPermission">
          <template #label><label-tip message="group.allAccessPermission" help /></template>
          <el-switch v-model="values.allAccessPermission"></el-switch>
        </el-form-item>
        <template v-if="!values.allAccessPermission">
          <div class="border-t">
            <el-checkbox v-model="accessPermissionExpand" :label="$t('expand/collapse')" @change="(checked: any) => expandTree(checked, accessPermissionTree, channelData, 'id')" />
            <el-checkbox
              v-model="accessPermissionCheck"
              :label="$t('checkAll/uncheckAll')"
              @change="
                (checked: any) => {
                  checkTree(checked, accessPermissionTree, channelData, 'id');
                  handleAccessPermission();
                }
              "
            />
          </div>
          <el-tree
            ref="accessPermissionTree"
            :data="channelData"
            node-key="id"
            :props="{ label: 'name' }"
            class="border rounded"
            default-expand-all
            show-checkbox
            @check="() => handleAccessPermission()"
          />
        </template>
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
