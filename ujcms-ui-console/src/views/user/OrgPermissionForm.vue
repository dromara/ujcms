<script setup lang="ts">
import { ref, watch, computed, toRefs, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
import { getPermsTreeData } from '@/data';
import { toTree, flatTree, disablePermissionTree, restoreCheckedKeys } from '@/utils/tree';
import { currentUser, hasPermission } from '@/stores/useCurrentUser';
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
const loading = ref<boolean>(false);
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
// 组织、各项权限、栏目和组织列表一起加载，到齐后先赋值，等树按新数据重建好再勾选。
// 分头加载时权限可能先于栏目数据到达，勾选落空；连续打开时，上一个组织的慢响应还会盖掉当前组织的勾选
let loadSeq = 0;
const fetchPermissions = async () => {
  const id = beanId.value;
  if (id == null) return;
  const seq = ++loadSeq;
  bean.value = {};
  values.value = {};
  loading.value = true;
  try {
    const [org, articlePermissions, channelPermissions, orgPermissions, channels, orgs] = await loadOrClose(
      () =>
        Promise.all([
          queryOrg(id),
          queryOrgArticlePermissions(id),
          queryOrgChannelPermissions(id),
          queryOrgPermPermissions(id, props.showGlobalData),
          // 早先保存的角色里，组织的权限设置可能还不带栏目列表权限（channel:list），没有就不请求，栏目树为空
          hasPermission('channel:list') ? queryChannelList() : [],
          queryOrgList({ current: true }),
        ]),
      () => {
        if (seq === loadSeq) emit('update:modelValue', false);
      },
    );
    // 加载期间又打开了其它组织，丢弃过期结果
    if (seq !== loadSeq) return;
    bean.value = org;
    values.value = { ...org };
    // 三棵树都随新数据重建，是折叠、未勾选的，开关跟着复位
    channelData.value = toTree(channels);
    orgData.value = toTree(orgs);
    articlePermissionExpand.value = false;
    articlePermissionCheck.value = false;
    channelPermissionExpand.value = false;
    channelPermissionCheck.value = false;
    orgPermissionExpand.value = false;
    orgPermissionCheck.value = false;
    await nextTick();
    restoreCheckedKeys(articlePermissionTree.value, articlePermissions);
    restoreCheckedKeys(channelPermissionTree.value, channelPermissions);
    restoreCheckedKeys(orgPermissionTree.value, orgPermissions);
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
};

watch(visible, () => {
  if (visible.value) fetchPermissions();
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
      <el-form ref="form" v-loading="loading" :model="values" :disabled="disabled" label-width="150px">
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
          <el-button type="primary" :loading="buttonLoading" :disabled="disabled || loading" @click="() => handleSubmit()">{{ $t('save') }}</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>
