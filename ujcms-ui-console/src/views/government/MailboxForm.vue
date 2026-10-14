<script setup lang="ts">
import { computed, ref, type PropType } from 'vue';
import { queryMailbox, createMailbox, updateMailbox, deleteMailbox } from '@/api/interaction';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'MailboxForm',
});
// 信箱类型由列表页传入：类型就在同一屏（“类型管理”）增删，挂载时自取一次会过期
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  types: { type: Array as PropType<any[]>, required: true },
});
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
const mailboxTypeList = computed(() => props.types);
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.government.mailbox')"
    :query-bean="queryMailbox"
    :create-bean="createMailbox"
    :update-bean="updateMailbox"
    :delete-bean="deleteMailbox"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ type: 1, agreePublished: false })"
    :to-values="(bean) => ({ ...bean })"
    perms="mailbox"
    :model-value="modelValue"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #header-status="{ isEdit }">
      <template v-if="isEdit">
        <span v-if="values.status != null">
          <el-tag v-if="values.status === 0" type="warning" size="small" disable-transitions>{{ $t(`mailbox.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 1" type="primary" size="small" disable-transitions>{{ $t(`mailbox.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 2" type="danger" size="small" disable-transitions>{{ $t(`mailbox.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 3" type="success" size="small" disable-transitions>{{ $t(`mailbox.status.${values.status}`) }}</el-tag>
          <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
          <el-tag v-if="values.extended" type="warning" size="small" disable-transitions class="ml-1">{{ $t('mailbox.op.extend') }}</el-tag>
          <el-tag v-if="values.published" type="success" size="small" disable-transitions class="ml-1">{{ $t('mailbox.op.publish') }}</el-tag>
        </span>
      </template>
    </template>
    <template #default="{ isEdit }">
      <el-row>
        <el-col v-if="mailboxTypeList.length > 0" :span="24">
          <el-form-item prop="mailboxTypeId">
            <template #label><label-tip message="mailbox.mailboxType" help /></template>
            <el-select v-model="values.mailboxTypeId" class="w-full" clearable :placeholder="$t('mailbox.mailboxType.unclassified')">
              <el-option v-for="item in mailboxTypeList" :key="item.id" :label="item.name" :value="item.id"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="type" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="mailbox.type" /></template>
            <el-radio-group v-model="values.type">
              <el-radio v-for="n in [1, 2, 3, 4, 5]" :key="n" :value="n">{{ $t(`mailbox.type.${n}`) }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="mailbox.name" /></template>
            <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="phone" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="mailbox.phone" /></template>
            <el-input v-model="values.phone" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="email">
            <template #label><label-tip message="mailbox.email" /></template>
            <el-input v-model="values.email" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="address">
            <template #label><label-tip message="mailbox.address" /></template>
            <el-input v-model="values.address" maxlength="150"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="title" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="mailbox.title" /></template>
            <el-input v-model="values.title" maxlength="150"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="text" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="mailbox.text" /></template>
            <el-input v-model="values.text" type="textarea" :rows="6" maxlength="65535"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="agreePublished">
            <template #label><label-tip message="mailbox.agreePublished" help /></template>
            <el-switch v-model="values.agreePublished" />
          </el-form-item>
        </el-col>
        <template v-if="isEdit">
          <el-col :span="12">
            <el-form-item prop="code">
              <template #label><label-tip message="mailbox.code" /></template>
              <el-input :model-value="values.code" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="user">
              <template #label><label-tip message="mailbox.user" /></template>
              <el-input :model-value="values.user?.username" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="created">
              <template #label><label-tip message="mailbox.created" /></template>
              <el-date-picker :model-value="values.created" type="datetime" class="w-full" disabled></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="ip">
              <template #label><label-tip message="mailbox.ip" /></template>
              <el-input :model-value="values.ip" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="acceptDate">
              <template #label><label-tip message="mailbox.acceptDate" /></template>
              <el-date-picker :model-value="values.acceptDate" type="datetime" class="w-full" disabled></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="extended">
              <template #label><label-tip message="mailbox.extended" /></template>
              <el-switch :model-value="values.extended" disabled />
            </el-form-item>
          </el-col>
          <el-col v-if="values.rejectText" :span="24">
            <el-form-item prop="rejectText">
              <template #label><label-tip message="mailbox.rejectText" /></template>
              <el-input :model-value="values.rejectText" type="textarea" :rows="3" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="replyUser">
              <template #label><label-tip message="mailbox.replyUser" /></template>
              <el-input :model-value="values.replyUser?.username" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="replyDate">
              <template #label><label-tip message="mailbox.replyDate" /></template>
              <el-date-picker :model-value="values.replyDate" type="datetime" class="w-full" disabled></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col v-if="values.replyText" :span="24">
            <el-form-item prop="replyText">
              <template #label><label-tip message="mailbox.replyText" /></template>
              <div class="w-full rounded border border-gray-300 p-2" v-html="values.replyText"></div>
            </el-form-item>
          </el-col>
          <template v-if="values.rate != null">
            <el-col :span="12">
              <el-form-item prop="rate">
                <template #label><label-tip message="mailbox.rate" /></template>
                <el-input :model-value="$t(`mailbox.rate.${values.rate}`)" disabled></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="rateDate">
                <template #label><label-tip message="mailbox.rateDate" /></template>
                <el-date-picker :model-value="values.rateDate" type="datetime" class="w-full" disabled></el-date-picker>
              </el-form-item>
            </el-col>
            <el-col v-if="values.rateText" :span="24">
              <el-form-item prop="rateText">
                <template #label><label-tip message="mailbox.rateText" /></template>
                <el-input :model-value="values.rateText" type="textarea" :rows="3" disabled></el-input>
              </el-form-item>
            </el-col>
          </template>
        </template>
      </el-row>
    </template>
  </dialog-form>
</template>
