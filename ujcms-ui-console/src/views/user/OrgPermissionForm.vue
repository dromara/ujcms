<script setup lang="ts">
import { ref, watch, computed, onMounted, toRefs } from 'vue';
import { useI18n } from 'vue-i18n';
import { getPermsTreeData } from '@/data';
import { toTree, flatTree, disablePermissionTree } from '@/utils/tree';
import { currentUser } from '@/stores/useCurrentUser';
import { queryOrg, updateOrgPermission, queryOrgArticlePermissions, queryOrgChannelPermissions, queryOrgPermPermissions, queryOrgList } from '@/api/user';
import { queryChannelList } from '@/api/content';
import { loadOrClose } from '@/utils/common';

defineOptions({
  name: 'OrgPermissionForm',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, showGlobalData: { type: Boolean, required: true } });
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { beanId, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const tabName = ref<string>('articlePermission');
const form = ref<any>();
const bean = ref<any>({});
const values = ref<any>({});
const buttonLoading = ref<boolean>(false);

const articlePermissionExpand = ref<boolean>(false);
const articlePermissionCheck = ref<boolean>(false);
const articlePermissionTree = ref<any>();
const channelPermissionExpand = ref<boolean>(false);
const channelPermissionCheck = ref<boolean>(false);
const channelPermissionTree = ref<any>();
const orgPermissionExpand = ref<boolean>(false);
const orgPermissionCheck = ref<boolean>(false);
const orgPermissionTree = ref<any>();

const permsData: any[] = getPermsTreeData();
disablePermissionTree(permsData, currentUser.grantPermissions ?? []);
const channelData = ref<any[]>([]);
const orgData = ref<any[]>([]);
const disabled = computed(() => (bean.value.global && !currentUser.globalPermission) || currentUser.rank > bean.value.rank);
const fetchOrg = async () => {
  if (beanId.value != null) {
    bean.value = await loadOrClose(() => queryOrg(beanId.value), () => emit('update:modelValue', false));
    values.value = { ...bean.value };
  }
};
const fetchArticlePermissions = async () => {
  if (beanId.value != null) {
    const articlePermissions = await queryOrgArticlePermissions(beanId.value);
    articlePermissionTree.value?.setCheckedKeys([]);
    articlePermissions.forEach((key: string) => {
      articlePermissionTree.value?.setChecked(key, true, false);
    });
  }
};
const fetchChannelPermissions = async () => {
  if (beanId.value != null) {
    const channelPermissions = await queryOrgChannelPermissions(beanId.value);
    channelPermissionTree.value?.setCheckedKeys([]);
    channelPermissions.forEach((key: string) => {
      channelPermissionTree.value?.setChecked(key, true, false);
    });
  }
};
const fetchOrgPermissions = async () => {
  if (beanId.value != null) {
    const orgPermissions = await queryOrgPermPermissions(beanId.value, props.showGlobalData);
    orgPermissionTree.value?.setCheckedKeys([]);
    orgPermissions.forEach((key: string) => {
      orgPermissionTree.value?.setChecked(key, true, false);
    });
  }
};
const fetchChannelData = async () => {
  channelData.value = toTree(await queryChannelList());
};
const fetchOrgData = async () => {
  orgData.value = toTree(await queryOrgList({ current: true }));
};

watch(visible, async () => {
  if (visible.value) {
    fetchOrg();
    fetchArticlePermissions();
    fetchChannelPermissions();
    fetchOrgPermissions();
  }
});
onMounted(() => {
  fetchChannelData();
  fetchOrgData();
});

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      handleArticlePermission();
      handleChannelPermission();
      handleOrgPermission();
      await updateOrgPermission(values.value);
      emit('finished');
      emit('update:modelValue', false);
      ElMessage.success(t('success'));
    } finally {
      buttonLoading.value = false;
    }
  });
};

const expandTree = (checked: any, tree: any, data: any[], key?: string) => {
  data.forEach((item) => {
    if (item.children) {
      tree.getNode(item[key ?? 'key']).expanded = checked;
      expandTree(checked, tree, item.children, key);
    }
  });
};
const checkTree = (checked: any, tree: any, data: any[], key?: string) => {
  tree.setCheckedKeys(checked ? data.map((item: any) => item[key ?? 'key']) : []);
};

const handleArticlePermission = () => {
  if (articlePermissionTree.value != null) {
    values.value.articlePermissions = [...articlePermissionTree.value.getCheckedNodes(), ...articlePermissionTree.value.getHalfCheckedNodes()].map((item) => item.id);
  }
};
const handleChannelPermission = () => {
  if (channelPermissionTree.value != null) {
    values.value.channelPermissions = [...channelPermissionTree.value.getCheckedNodes(), ...channelPermissionTree.value.getHalfCheckedNodes()].map((item) => item.id);
  }
};
const handleOrgPermission = () => {
  if (orgPermissionTree.value != null) {
    values.value.orgPermissions = orgPermissionTree.value.getCheckedNodes().map((item: any) => item.id);
  }
};
</script>

<template>
  <!-- eslint-disable vue/no-v-html -->
  <el-drawer :title="$t('permissionSettings')" :with-header="false" :model-value="modelValue" :size="576" @update:model-value="(event) => $emit('update:modelValue', event)">
    <template #default>
      <el-form ref="form" :model="values" :disabled="disabled" label-width="150px">
        <el-tabs v-model="tabName">
          <el-tab-pane :label="$t('org.articlePermission')" name="articlePermission">
            <div class="border-t">
              <el-checkbox v-model="articlePermissionExpand" :label="$t('expand/collapse')" @change="(checked) => expandTree(checked, articlePermissionTree, channelData, 'id')" />
              <el-checkbox
                v-model="articlePermissionCheck"
                :label="$t('checkAll/uncheckAll')"
                @change="
                  (checked) => {
                    checkTree(checked, articlePermissionTree, flatTree(channelData), 'id');
                    handleArticlePermission();
                  }
                "
              />
            </div>
            <el-tree
              ref="articlePermissionTree"
              :data="channelData"
              node-key="id"
              :props="{ label: 'name' }"
              class="border rounded"
              show-checkbox
              @check="() => handleArticlePermission()"
            />
          </el-tab-pane>
          <el-tab-pane :label="$t('role.channelPermission')" name="channelPermission">
            <div class="border-t">
              <el-checkbox v-model="channelPermissionExpand" :label="$t('expand/collapse')" @change="(checked) => expandTree(checked, channelPermissionTree, channelData, 'id')" />
              <el-checkbox
                v-model="channelPermissionCheck"
                :label="$t('checkAll/uncheckAll')"
                @change="
                  (checked) => {
                    checkTree(checked, channelPermissionTree, flatTree(channelData), 'id');
                    handleChannelPermission();
                  }
                "
              />
            </div>
            <el-tree
              ref="channelPermissionTree"
              :data="channelData"
              node-key="id"
              :props="{ label: 'name' }"
              class="border rounded"
              check-strictly
              show-checkbox
              @check="() => handleChannelPermission()"
            />
          </el-tab-pane>
          <el-tab-pane :label="$t('org.orgPermission')" name="orgPermission">
            <div class="border-t">
              <el-checkbox v-model="orgPermissionExpand" :label="$t('expand/collapse')" @change="(checked) => expandTree(checked, orgPermissionTree, orgData, 'id')" />
              <el-checkbox
                v-model="orgPermissionCheck"
                :label="$t('checkAll/uncheckAll')"
                @change="
                  (checked) => {
                    checkTree(checked, orgPermissionTree, flatTree(orgData), 'id');
                    handleOrgPermission();
                  }
                "
              />
            </div>
            <el-tree
              ref="orgPermissionTree"
              :data="orgData"
              node-key="id"
              :props="{ label: 'name' }"
              class="border rounded"
              :default-expanded-keys="orgData.map((it) => it.id)"
              show-checkbox
              check-strictly
              @check="() => handleOrgPermission()"
            />
          </el-tab-pane>
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
