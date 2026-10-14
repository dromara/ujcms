<script setup lang="ts">
import { ref, watch } from 'vue';
import { queryProcessBackActivityList } from '@/api/system';
import LabelTip from '@/components/LabelTip.vue';

const visible = defineModel<boolean>();
const props = defineProps<{ taskId: string }>();
const emit = defineEmits<{ finished: [payload: any] }>();

const activities = ref<any[]>([]);
const form = ref<any>();
const bean = ref<any>({});

const confirm = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    visible.value = false;
    emit('finished', bean.value);
  });
};
const fetchProcessBackActivityList = async () => {
  activities.value = await queryProcessBackActivityList(props.taskId);
};
watch(visible, () => {
  if (visible.value) {
    fetchProcessBackActivityList();
    bean.value = {};
  } else {
    activities.value = [];
  }
});
</script>

<template>
  <el-dialog v-model="visible" :title="$t('flowable.back')" width="800" append-to-body>
    <el-form ref="form" :model="bean" label-width="120px">
      <el-form-item prop="activityId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="flowable.backActivity" /></template>
        <el-radio-group v-if="activities.length > 0" v-model="bean.activityId">
          <el-radio v-for="item in activities" :key="item.id" :label="item.activityName" :value="item.activityId">{{ item.activityName }}</el-radio>
        </el-radio-group>
        <el-tag v-else type="warning">{{ $t('preview.noBackStep') }}</el-tag>
      </el-form-item>
      <el-form-item prop="comment" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="flowable.backReason" /></template>
        <el-input v-model="bean.comment" />
      </el-form-item>
      <div class="flex justify-end mt-2">
        <el-button type="primary" native-type="submit" :disabled="activities.length <= 0" @click.prevent="confirm">{{ $t('ok') }}</el-button>
      </div>
    </el-form>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
