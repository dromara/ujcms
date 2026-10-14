<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { currentUser } from '@/stores/useCurrentUser';
import { queryDictType, createDictType, updateDictType, deleteDictType, dictTypeAliasExist, queryDictTypeUsages } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'DictTypeForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const dialog = ref<any>();
const focus = ref<any>();
const values = ref<any>({});
const usages = ref<any[]>([]);
// 系统类型（sys）由安装骨架写入，按别名取用：别名/共享范围锁定、不可删除（服务端同规）
const locked = (bean: any) => !!bean?.sys;
// 全局类型属全局数据：无全局数据权限时只可查看（服务端同规）
const readonly = (bean: any) => bean?.scope === 2 && !currentUser.globalPermission;
// 全局类型被本站之外的模型（其它站点的模型、全局模型）绑定时，不可改为本站私有——改后它们的字段即无选项可选（服务端同规）
const privateLocked = (bean: any) => bean?.scope === 2 && usages.value.some((item) => item.external);
// 随类型一并取其使用情况（表单内可上一条/下一条切换类型，故不能由列表页传入）。使用情况是辅助信息，取不到不报错
const queryBean = async (id: string) => {
  const [bean, list] = await Promise.all([queryDictType(id), queryDictTypeUsages(id, true).catch(() => [])]);
  usages.value = list;
  return bean;
};
// 别名的唯一性以“别名 × 共享范围”为命名空间：共享范围变了，未改动的别名也要重新校验
const handleScopeChange = () => dialog.value?.form?.validateField('alias').catch(() => undefined);
</script>

<template>
  <dialog-form
    ref="dialog"
    v-model:values="values"
    :name="$t('dictType.title')"
    :query-bean="queryBean"
    :create-bean="createDictType"
    :update-bean="updateDictType"
    :delete-bean="deleteDictType"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ scope: 0, dataType: 0 })"
    :to-values="(bean) => ({ ...bean })"
    :disable-edit="(bean: any) => readonly(bean)"
    perms="dictType"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <!-- 删除类型在列表页的类型顶栏：那里的确认文案带条目数（删除会级联条目），此处不再提供。
         插槽内容为空时 Vue 会回落到默认的删除按钮，故放一个 hidden 占位（带 hidden 属性的元素不参与 space-x 间距） -->
    <template #header-action><span hidden></span></template>
    <template #default="{ bean, isEdit }">
      <el-form-item prop="name" :label="$t('dictType.name')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item
        prop="alias"
        :label="$t('dictType.alias')"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            asyncValidator: async (rule, value, callback) => {
              if ((value !== bean.alias || values.scope !== bean.scope) && (await dictTypeAliasExist(value, values.scope))) {
                callback($t('dictType.error.aliasExist'));
                return;
              }
              callback();
            },
          },
        ]"
      >
        <el-input v-model="values.alias" :disabled="isEdit && locked(values)" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="remark" :label="$t('dictType.remark')">
        <el-input v-model="values.remark" type="textarea" :rows="2" maxlength="255"></el-input>
      </el-form-item>
      <el-form-item prop="dataType" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="dictType.dataType" help /></template>
        <el-radio-group v-model="values.dataType" :disabled="isEdit">
          <el-radio v-for="n in [0, 1]" :key="n" :value="n">{{ $t(`dictType.dataType.${n}`) }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item prop="scope" :label="$t('dictType.scope')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-radio-group v-model="values.scope" :disabled="isEdit && locked(values)" @change="handleScopeChange">
          <el-radio :value="0" :disabled="isEdit && privateLocked(bean)">{{ $t('dictType.scope.0') }}</el-radio>
          <el-radio :value="2" :disabled="!currentUser.globalPermission">{{ $t('dictType.scope.2') }}</el-radio>
        </el-radio-group>
        <div v-if="isEdit && privateLocked(bean) && !locked(values)" class="w-full text-xs text-gray-secondary leading-5">{{ $t('dictType.scope.lockedTip') }}</div>
      </el-form-item>
      <el-form-item v-if="isEdit" prop="sys">
        <template #label><label-tip message="dict.sys" help /></template>
        <el-switch v-model="values.sys" disabled></el-switch>
      </el-form-item>
    </template>
  </dialog-form>
</template>
