<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { queryDisclosure, createDisclosure, updateDisclosure, deleteDisclosure } from '@/api/interaction';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'DisclosureForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.government.disclosure')"
    :query-bean="queryDisclosure"
    :create-bean="createDisclosure"
    :update-bean="updateDisclosure"
    :delete-bean="deleteDisclosure"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ type: 1, provideMethod: 1, obtainMethod: 1 })"
    :to-values="(bean) => ({ ...bean })"
    perms="disclosure"
    :model-value="modelValue"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #header-status="{ isEdit }">
      <template v-if="isEdit">
        <span v-if="values.status != null">
          <el-tag v-if="values.status === 0" type="warning" size="small" disable-transitions>{{ $t(`disclosure.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 1" type="primary" size="small" disable-transitions>{{ $t(`disclosure.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 2" type="danger" size="small" disable-transitions>{{ $t(`disclosure.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 3" type="success" size="small" disable-transitions>{{ $t(`disclosure.status.${values.status}`) }}</el-tag>
          <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
          <el-tag v-if="values.extended" type="warning" size="small" disable-transitions class="ml-1">{{ $t('disclosure.op.extend') }}</el-tag>
        </span>
      </template>
    </template>
    <template #default="{ isEdit }">
      <el-row>
        <el-col :span="24">
          <el-form-item prop="type" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="disclosure.type" /></template>
            <el-radio-group v-model="values.type">
              <el-radio :value="1">{{ $t('disclosure.type.1') }}</el-radio>
              <el-radio :value="2">{{ $t('disclosure.type.2') }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="disclosure.name" /></template>
            <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <template v-if="values.type === 1">
          <el-col :span="12">
            <el-form-item prop="org">
              <template #label><label-tip message="disclosure.org" /></template>
              <el-input v-model="values.org" maxlength="150"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="certType">
              <template #label><label-tip message="disclosure.certType" /></template>
              <el-input v-model="values.certType" maxlength="30"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="certNumber">
              <template #label><label-tip message="disclosure.certNumber" /></template>
              <el-input v-model="values.certNumber" maxlength="50"></el-input>
            </el-form-item>
          </el-col>
        </template>
        <template v-else>
          <el-col :span="12">
            <el-form-item prop="creditCode">
              <template #label><label-tip message="disclosure.creditCode" /></template>
              <el-input v-model="values.creditCode" maxlength="50"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="legalPerson">
              <template #label><label-tip message="disclosure.legalPerson" /></template>
              <el-input v-model="values.legalPerson" maxlength="30"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="contactName">
              <template #label><label-tip message="disclosure.contactName" /></template>
              <el-input v-model="values.contactName" maxlength="30"></el-input>
            </el-form-item>
          </el-col>
        </template>
        <el-col :span="12">
          <el-form-item prop="phone" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="disclosure.phone" /></template>
            <el-input v-model="values.phone" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="email">
            <template #label><label-tip message="disclosure.email" /></template>
            <el-input v-model="values.email" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="address">
            <template #label><label-tip message="disclosure.address" /></template>
            <el-input v-model="values.address" maxlength="150"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item prop="zipCode">
            <template #label><label-tip message="disclosure.zipCode" /></template>
            <el-input v-model="values.zipCode" maxlength="20"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item prop="fax">
            <template #label><label-tip message="disclosure.fax" /></template>
            <el-input v-model="values.fax" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="title" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="disclosure.title" /></template>
            <el-input v-model="values.title" maxlength="150"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="text">
            <template #label><label-tip message="disclosure.text" /></template>
            <el-input v-model="values.text" type="textarea" :rows="6" maxlength="65535"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="purpose">
            <template #label><label-tip message="disclosure.purpose" /></template>
            <el-input v-model="values.purpose" maxlength="300"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="provideMethod" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="disclosure.provideMethod" /></template>
            <el-radio-group v-model="values.provideMethod">
              <el-radio v-for="n in [1, 2, 3, 4]" :key="n" :value="n">{{ $t(`disclosure.provideMethod.${n}`) }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="obtainMethod" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="disclosure.obtainMethod" /></template>
            <el-radio-group v-model="values.obtainMethod">
              <el-radio v-for="n in [1, 2, 3, 4]" :key="n" :value="n">{{ $t(`disclosure.obtainMethod.${n}`) }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <template v-if="isEdit">
          <el-col :span="12">
            <el-form-item prop="code">
              <template #label><label-tip message="disclosure.code" /></template>
              <el-input :model-value="values.code" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="user">
              <template #label><label-tip message="disclosure.user" /></template>
              <el-input :model-value="values.user?.username" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="created">
              <template #label><label-tip message="disclosure.created" /></template>
              <el-date-picker :model-value="values.created" type="datetime" class="w-full" disabled></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="ip">
              <template #label><label-tip message="disclosure.ip" /></template>
              <el-input :model-value="values.ip" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="acceptDate">
              <template #label><label-tip message="disclosure.acceptDate" /></template>
              <el-date-picker :model-value="values.acceptDate" type="datetime" class="w-full" disabled></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="extended">
              <template #label><label-tip message="disclosure.extended" /></template>
              <el-switch :model-value="values.extended" disabled />
            </el-form-item>
          </el-col>
          <el-col v-if="values.correctText" :span="24">
            <el-form-item prop="correctText">
              <template #label><label-tip message="disclosure.correctText" /></template>
              <el-input :model-value="values.correctText" type="textarea" :rows="3" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col v-if="values.supplementText" :span="24">
            <el-form-item prop="supplementText">
              <template #label><label-tip message="disclosure.supplementText" /></template>
              <el-input :model-value="values.supplementText" type="textarea" :rows="3" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="replyType">
              <template #label><label-tip message="disclosure.replyType" /></template>
              <el-input :model-value="values.replyType != null ? $t(`disclosure.replyType.${values.replyType}`) : ''" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="replyUser">
              <template #label><label-tip message="disclosure.replyUser" /></template>
              <el-input :model-value="values.replyUser?.username" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="replyDate">
              <template #label><label-tip message="disclosure.replyDate" /></template>
              <el-date-picker :model-value="values.replyDate" type="datetime" class="w-full" disabled></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col v-if="values.replyText" :span="24">
            <el-form-item prop="replyText">
              <template #label><label-tip message="disclosure.replyText" /></template>
              <div class="w-full rounded border border-gray-300 p-2" v-html="values.replyText"></div>
            </el-form-item>
          </el-col>
        </template>
      </el-row>
    </template>
  </dialog-form>
</template>
