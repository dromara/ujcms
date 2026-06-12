<script setup lang="ts">
import { ref, watch } from 'vue';
import { is, getBusinessObject } from 'bpmn-js/lib/util/ModelUtil';
import { User } from '@element-plus/icons-vue';
import { toTree } from '@/utils/tree';
import { currentUser } from '@/stores/useCurrentUser';
import UserSelectMulti from '@/components/user/UserSelectMulti.vue';
import LabelTip from '@/components/LabelTip.vue';

const props = defineProps<{
  modeler: any;
  selection: any;
  collapseItemName: string;
  queryRoles: (params?: Record<string, any>) => Promise<any>;
  queryOrgs: (params?: Record<string, any>) => Promise<any>;
}>();

const roleList = ref<any[]>([]);
const orgList = ref<any[]>([]);

const propertyRoleIds = ref<string[]>([]);
const propertyOrgIds = ref<string[]>([]);
const propertyUserIds = ref<string[]>([]);
const propertyAssignee = ref<string>();

const fetchRoleList = async () => {
  roleList.value = await props.queryRoles();
};
const fetchOrgList = async () => {
  orgList.value = toTree((await props.queryOrgs({ current: true })).map((item) => ({ ...item, id: `org:${item.id}`, parentId: `org:${item.parentId}` })));
};

watch(
  () => props.selection,
  () => {
    fetchRoleList();
    fetchOrgList();
    const businessObject = getBusinessObject(props.selection);
    propertyOrgIds.value = [];
    propertyRoleIds.value = [];
    businessObject.candidateGroups?.split(',').forEach((item: string) => {
      if (item.indexOf('org:') !== -1) {
        propertyOrgIds.value.push(item);
      } else if (item !== '') {
        propertyRoleIds.value.push(item);
      }
    });
    const candidateUsers = businessObject.candidateUsers;
    if (candidateUsers) {
      propertyUserIds.value = candidateUsers.split(',');
    } else {
      propertyUserIds.value = [];
    }
    propertyAssignee.value = businessObject.assignee;
  },
);

const changeRoles = (roleIds: string[]) => {
  const modeling = props.modeler.get('modeling');
  modeling.updateProperties(props.selection, { candidateGroups: [...roleIds, ...propertyOrgIds.value].join(',') });
};
const changeUsers = (ids: string[]) => {
  propertyUserIds.value = ids;
  const modeling = props.modeler.get('modeling');
  modeling.updateProperties(props.selection, { candidateUsers: propertyUserIds.value.join(',') });
};
const changeAssignee = (assignee: string) => {
  const modeling = props.modeler.get('modeling');
  modeling.updateProperties(props.selection, { assignee });
};

const checkOrgs = (_: any, { checkedKeys }: { checkedKeys: string[] }) => {
  const modeling = props.modeler.get('modeling');
  console.log('repropertyOrgIds', propertyOrgIds.value);
  modeling.updateProperties(props.selection, { candidateGroups: [...propertyRoleIds.value, ...checkedKeys].join(',') });
};
const removeOrgs = () => {
  const modeling = props.modeler.get('modeling');
  console.log('repropertyOrgIds', propertyOrgIds.value);
  modeling.updateProperties(props.selection, { candidateGroups: [...propertyRoleIds.value, ...propertyOrgIds.value].join(',') });
};
</script>

<template>
  <el-collapse-item v-if="is(selection, 'bpmn:UserTask')" :name="collapseItemName">
    <template #title>
      <el-icon class="text-base"><User /></el-icon><span class="ml-1 text-sm font-bold">{{ $t('flowable.groups.assignment') }}</span>
    </template>
    <el-form label-width="100px">
      <el-form-item>
        <template #label><label-tip message="flowable.assignment.candidateGroups" /></template>
        <el-select v-model="propertyRoleIds" multiple class="w-full" @change="changeRoles">
          <el-option v-for="item in roleList" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="currentUser.epRank >= 3">
        <template #label><label-tip message="flowable.assignment.candidateOrgs" /></template>
        <el-tree-select
          v-model="propertyOrgIds"
          :data="orgList"
          node-key="id"
          :props="{ label: 'name' }"
          :render-after-expand="false"
          :default-expanded-keys="orgList.map((item) => item.id)"
          multiple
          show-checkbox
          check-on-click-node
          class="w-full"
          @check="checkOrgs"
          @remove-tag="removeOrgs"
        />
      </el-form-item>
      <el-form-item v-if="currentUser.epRank >= 3">
        <template #label><label-tip message="flowable.assignment.candidateUsers" /></template>
        <user-select-multi :user-ids="propertyUserIds" @finished="changeUsers" />
      </el-form-item>
      <el-form-item v-if="currentUser.epRank >= 3">
        <template #label><label-tip message="flowable.assignment.assignee" help /></template>
        <el-input v-model="propertyAssignee" @change="changeAssignee" />
      </el-form-item>
    </el-form>
  </el-collapse-item>
</template>

<style lang="scss" scoped></style>
