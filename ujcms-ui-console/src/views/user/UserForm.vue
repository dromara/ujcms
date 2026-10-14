<script setup lang="ts">
import { computed, onMounted, ref, toRefs, watch, type PropType } from 'vue';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { currentUser } from '@/stores/useCurrentUser';
import { queryUser, createUser, updateUser, deleteUser, usernameExist, emailExist, mobileExist, queryGroupList, queryOrgList } from '@/api/user';
import { queryModelList } from '@/api/config';
import { getModelData, mergeModelFields, arr2obj } from '@/data';
import { toTree } from '@/utils/tree';
import DialogForm from '@/components/DialogForm.vue';
import FieldItem from '@/views/config/components/FieldItem.vue';
import LabelTip from '@/components/LabelTip.vue';
import { ImageUpload } from '@/components/Upload';

defineOptions({
  name: 'UserForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  org: { type: Object, default: null },
  showGlobalData: { type: Boolean, required: true },
});
const { showGlobalData, modelValue: visible } = toRefs(props);
defineEmits({ 'update:modelValue': null, finished: null });

const sysConfig = useSysConfigStore();
const focus = ref<any>();
const values = ref<any>({});
const groupList = ref<any[]>([]);
const orgIds = ref<any[]>([]);
const orgList = ref<any[]>([]);
const userModelList = ref<any[]>([]);
// 服务端按固定 ID 2 取用户模型（Model.USER_MODEL_ID）：表单也取它，历史库里多出的用户模型不渲染服务端会丢弃的字段
const userModel = computed(() => userModelList.value.find((item) => String(item.id) === '2') ?? userModelList.value[0]);
const mains = computed(() => arr2obj(mergeModelFields(getModelData().user.mains, userModel.value?.mains, 'user')));
const fields = computed(() => JSON.parse(userModel.value?.customs || '[]'));

const fetchGroupList = async () => {
  groupList.value = await queryGroupList();
};
const fetchOrgList = async () => {
  const flatOrgList = await queryOrgList({ current: !showGlobalData.value });
  orgIds.value = flatOrgList.map((item: any) => item.id);
  orgList.value = toTree(flatOrgList);
};
const fetchUserModelList = async () => {
  userModelList.value = await queryModelList({ type: 'user' });
};

const initCustoms = (customs: any) => {
  fields.value.forEach((field: any) => {
    if (customs[field.code] == null) {
      customs[field.code] = field.defaultValue;
      if (field.defaultValueKey != null) {
        customs[field.code + 'Key'] = field.defaultValueKey;
      }
    }
  });
  return customs;
};

watch(fields, () => {
  if (values.value.customs == null) {
    values.value.customs = {};
  }
  initCustoms(values.value.customs);
});

onMounted(() => {
  fetchGroupList();
  fetchUserModelList();
});

watch(visible, () => {
  if (visible.value) {
    fetchOrgList();
  }
});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.user.user')"
    :query-bean="queryUser"
    :create-bean="createUser"
    :update-bean="updateUser"
    :delete-bean="deleteUser"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ orgId: org?.id, gender: 0, orgIds: [], customs: {} })"
    :to-values="(bean) => ({ ...bean, orgIds: bean.orgList.filter((item) => showGlobalData || orgIds.indexOf(item.id) !== -1).map((item) => item.id), global: showGlobalData, customs: bean.customs ?? {} })"
    :disable-delete="(bean) => bean.id <= 1"
    :disable-edit="(bean) => currentUser.rank > bean.rank"
    perms="user"
    :model-value="modelValue"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #header-status="{ isEdit }">
      <template v-if="isEdit">
        <el-tag v-if="values.status === 0" type="success" class="ml-2">{{ $t(`user.status.${values.status}`) }}</el-tag>
        <el-tag v-else-if="values.status === 1" type="info" class="ml-2">{{ $t(`user.status.${values.status}`) }}</el-tag>
        <el-tag v-else-if="values.status === 2" type="warning" class="ml-2">{{ $t(`user.status.${values.status}`) }}</el-tag>
        <el-tag v-else-if="values.status === 3" type="danger" class="ml-2">{{ $t(`user.status.${values.status}`) }}</el-tag>
        <el-tag v-else type="danger" class="ml-2">{{ values.status }}</el-tag>
      </template>
    </template>
    <template #default="{ bean, isEdit, disabled }">
      <el-row>
        <el-col :span="12">
          <el-form-item prop="orgId" :label="$t('user.org')" :rules="{ required: true, message: () => $t('v.required') }">
            <el-tree-select
              v-model="values.orgId"
              :data="orgList"
              node-key="id"
              :default-expanded-keys="orgList.map((item) => item.id)"
              :props="{ label: 'name' }"
              :render-after-expand="false"
              check-strictly
              class="w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="groupId" :label="$t('user.group')" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="user.group" help /></template>
            <el-select v-model="values.groupId" class="w-full">
              <el-option v-for="item in groupList" :key="item.id" :value="item.id" :label="item.name" :disabled="item.type !== 2"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="currentUser.commercialTier >= 3" :span="24">
          <el-form-item prop="orgIds" :label="$t('user.orgs')">
            <template #label><label-tip message="user.orgs" help /></template>
            <el-tree-select
              v-model="values.orgIds"
              :data="orgList"
              node-key="id"
              :props="{ label: 'name' }"
              :render-after-expand="false"
              :default-expanded-keys="orgList.map((item) => item.id)"
              multiple
              show-checkbox
              check-strictly
              check-on-click-node
              class="w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            prop="username"
            :label="$t('user.username')"
            :rules="[
              { required: true, message: () => $t('v.required') },
              {
                asyncValidator: async (rule: any, value: any, callback: any) => {
                  if (value && value !== bean.username && (await usernameExist(value))) {
                    callback($t('user.error.usernameExist'));
                    return;
                  }
                  callback();
                },
              },
            ]"
          >
            <el-input ref="focus" v-model="values.username" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="realName" :label="$t('user.realName')">
            <el-input v-model="values.realName" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            prop="mobile"
            :label="$t('user.mobile')"
            :rules="[
              { pattern: /^\+?[\d\s()\-./]{5,30}$/, message: () => $t('user.error.mobileFormat') },
              {
                asyncValidator: async (rule: any, value: any, callback: any) => {
                  if (value && value !== bean.mobile && (await mobileExist(value))) {
                    callback($t('user.error.mobileExist'));
                    return;
                  }
                  callback();
                },
              },
            ]"
          >
            <el-input v-model="values.mobile" maxlength="50"></el-input>
            <!-- 双因子验证码发往此号码：未登记或登记有误的后台用户将无法登录 -->
            <div v-if="sysConfig.security.twoFactor" class="text-xs leading-normal text-gray-secondary">{{ $t('user.mobile.twoFactorHint') }}</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            prop="email"
            :label="$t('user.email')"
            :rules="[
              {
                asyncValidator: async (rule: any, value: any, callback: any) => {
                  if (value && value !== bean.email && (await emailExist(value))) {
                    callback($t('user.error.emailExist'));
                    return;
                  }
                  callback();
                },
              },
            ]"
          >
            <el-input v-model="values.email" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="gender" :label="$t('user.gender')" :rules="{ required: true, message: () => $t('v.required') }">
            <el-radio-group v-model="values.gender">
              <el-radio :value="1">{{ $t('gender.male') }}</el-radio>
              <el-radio :value="2">{{ $t('gender.female') }}</el-radio>
              <el-radio :value="0">{{ $t('gender.none') }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="birthday" :label="$t('user.birthday')">
            <el-date-picker v-model="values.birthday" type="date"></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="avatar" :label="$t('user.avatar')">
            <image-upload
              v-model="values.avatar"
              :width="sysConfig.register.largeAvatarSize"
              :height="sysConfig.register.largeAvatarSize"
              mode="manual"
              type="avatar"
              :disabled="disabled"
              @crop-success="(url: string) => (values.mediumAvatar = url + '@medium' + url.substring(url.lastIndexOf('.')))"
            ></image-upload>
            <el-avatar v-if="values.mediumAvatar != null" :src="values.mediumAvatar" :size="100" class="ml-2" />
          </el-form-item>
        </el-col>
        <el-col v-if="mains['location']?.show" :span="mains['location'].double ? 12 : 24">
          <el-form-item
            prop="location"
            :label="mains['location'].name ?? $t('user.location')"
            :rules="mains['location'].required ? { required: true, message: () => $t('v.required') } : undefined"
          >
            <el-input v-model="values.location" maxlength="255"></el-input>
          </el-form-item>
        </el-col>
        <el-col v-if="mains['bio']?.show" :span="mains['bio'].double ? 12 : 24">
          <el-form-item
            prop="bio"
            :label="mains['bio'].name ?? $t('user.bio')"
            :rules="mains['bio'].required ? { required: true, message: () => $t('v.required') } : undefined"
          >
            <el-input v-model="values.bio" type="textarea" :rows="3" maxlength="2000"></el-input>
          </el-form-item>
        </el-col>
        <template v-for="field in fields" :key="field.code">
          <el-col :span="field.double ? 12 : 24">
            <el-form-item :prop="`customs.${field.code}`" :label="field.name" :rules="field.required ? { required: true, message: () => $t('v.required') } : undefined">
              <field-item v-model="values.customs[field.code]" v-model:model-key="values.customs[field.code + 'Key']" :field="field"></field-item>
            </el-form-item>
          </el-col>
        </template>
        <el-col v-if="isEdit" :span="12">
          <el-form-item prop="created" :label="$t('user.created')">
            <template #label><label-tip message="user.created" /></template>
            <el-date-picker v-model="values.created" type="datetime" disabled></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col v-if="isEdit" :span="12">
          <el-form-item prop="loginDate">
            <template #label><label-tip message="user.loginDate" /></template>
            <el-date-picker v-model="values.loginDate" type="datetime" disabled></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col v-if="isEdit" :span="12">
          <el-form-item prop="loginIp">
            <template #label><label-tip message="user.loginIp" /></template>
            <el-input v-model="values.loginIp" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col v-if="isEdit" :span="12">
          <el-form-item prop="loginCount">
            <template #label><label-tip message="user.loginCount" /></template>
            <el-input v-model="values.loginCount" disabled></el-input>
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>
