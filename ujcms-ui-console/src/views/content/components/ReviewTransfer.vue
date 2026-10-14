<script setup lang="ts">
import { ref, watch } from 'vue';
import UserSelect from '@/components/user/UserSelect.vue';
import LabelTip from '@/components/LabelTip.vue';

const visible = defineModel<boolean>();
const emit = defineEmits<{ finished: [payload: any] }>();

const form = ref<any>();
const values = ref<any>({});
const confirm = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    visible.value = false;
    emit('finished', values.value);
  });
};
watch(visible, () => {
  if (visible.value) {
    values.value = {};
  }
});
</script>

<template>
  <el-dialog v-model="visible" :title="$t('flowable.transfer')" width="800" append-to-body>
    <el-form ref="form" :model="values" label-width="120px">
      <el-form-item prop="toUserId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="flowable.transferUser" /></template>
        <user-select v-model="values.toUserId" />
      </el-form-item>
      <el-form-item prop="comment" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="flowable.transferReason" /></template>
        <el-input v-model="values.comment" />
      </el-form-item>
    </el-form>
    <div class="flex justify-end mt-2">
      <el-button type="primary" @click="confirm">{{ $t('ok') }}</el-button>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
