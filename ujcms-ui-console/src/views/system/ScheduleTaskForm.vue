<script setup lang="ts">
import { onMounted, ref, watch, type PropType } from 'vue';
import { queryScheduleTask, createScheduleTask, updateScheduleTask, deleteScheduleTask, queryScheduleTaskTypes, scheduleTaskCronValid } from '@/api/system';
import { queryCollectionPage } from '@/api/interaction';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'ScheduleTaskForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
const types = ref<string[]>([]);
const collections = ref<any[]>([]);
// 备份类任务：阻塞式执行，表单多一个“保留份数”（与后端 ScheduleTask.TYPE_BACKUP_* 对应）
const BACKUP_TYPES = ['backupTemplates', 'backupUploads', 'incrementalUploads', 'backupDatabase'];
const isBackupType = (type?: string) => BACKUP_TYPES.includes(type ?? '');
const DEFAULT_CRON = '0 0 2 * * ?';
const weekdays = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
const numericWeekdays = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const cronType = ref<string>('day');
const cronMinute = ref<number>(0);
const cronTime = ref<string>('02:00');
const cronWeekday = ref<string>('MON');
const cronDay = ref<number>(1);
const pad = (num: number) => String(num).padStart(2, '0');
const buildCron = (): string => {
  const [hour, minute] = cronTime.value.split(':').map(Number);
  switch (cronType.value) {
    case 'hour':
      return `0 ${cronMinute.value} * * * ?`;
    case 'day':
      return `0 ${minute} ${hour} * * ?`;
    case 'week':
      return `0 ${minute} ${hour} ? * ${cronWeekday.value}`;
    case 'month':
      return `0 ${minute} ${hour} ${cronDay.value} * ?`;
    default:
      return values.value.cron;
  }
};
const applyCron = () => {
  if (cronType.value !== 'custom') {
    values.value.cron = buildCron();
  }
};
const parseCron = (cron?: string) => {
  if (!cron) {
    cronType.value = 'custom';
    return;
  }
  let matches = /^0\s+(\d{1,2})\s+\*\s+\*\s+\*\s+\?$/.exec(cron);
  if (matches != null) {
    cronType.value = 'hour';
    cronMinute.value = Number(matches[1]);
    return;
  }
  matches = /^0\s+(\d{1,2})\s+(\d{1,2})\s+\*\s+\*\s+\?$/.exec(cron);
  if (matches != null) {
    cronType.value = 'day';
    cronTime.value = `${pad(Number(matches[2]))}:${pad(Number(matches[1]))}`;
    return;
  }
  matches = /^0\s+(\d{1,2})\s+(\d{1,2})\s+\?\s+\*\s+(MON|TUE|WED|THU|FRI|SAT|SUN|[1-7])$/i.exec(cron);
  if (matches != null) {
    cronType.value = 'week';
    cronTime.value = `${pad(Number(matches[2]))}:${pad(Number(matches[1]))}`;
    const weekday = matches[3] ?? '';
    cronWeekday.value = /^[1-7]$/.test(weekday) ? (numericWeekdays[Number(weekday) - 1] ?? weekday) : weekday.toUpperCase();
    return;
  }
  matches = /^0\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+\*\s+\?$/.exec(cron);
  if (matches != null) {
    cronType.value = 'month';
    cronTime.value = `${pad(Number(matches[2]))}:${pad(Number(matches[1]))}`;
    cronDay.value = Number(matches[3]);
    return;
  }
  cronType.value = 'custom';
};
watch(
  () => values.value.cron,
  (cron) => {
    if (cronType.value !== 'custom' && cron !== buildCron()) {
      parseCron(cron);
    }
  }
);
const handleCronTypeChange = () => applyCron();
const fetchTypes = async () => {
  types.value = await queryScheduleTaskTypes();
};
const fetchCollections = async () => {
  const { content } = await queryCollectionPage({ pageSize: 1000 });
  collections.value = content;
};
onMounted(() => {
  fetchTypes();
});
const handleTypeChange = () => {
  values.value.refId = undefined;
  values.value.keepCount = undefined;
  if (values.value.type === 'collection' && collections.value.length <= 0) {
    fetchCollections();
  }
};
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.system.scheduleTask')"
    :query-bean="queryScheduleTask"
    :create-bean="createScheduleTask"
    :update-bean="updateScheduleTask"
    :delete-bean="deleteScheduleTask"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ type: types[0], enabled: true, cron: DEFAULT_CRON })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="scheduleTask"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
    @bean-change="
      (bean) => {
        bean?.type === 'collection' && fetchCollections();
        parseCron(values.cron);
      }
    "
  >
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="scheduleTask.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="150"></el-input>
      </el-form-item>
      <el-form-item prop="type" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="scheduleTask.type" /></template>
        <div class="w-full">
          <el-select v-model="values.type" class="w-full" @change="() => handleTypeChange()">
            <el-option v-for="item in types" :key="item" :label="$t(`scheduleTask.type.${item}`)" :value="item"></el-option>
          </el-select>
          <div v-if="isBackupType(values.type)" class="mt-1 text-xs leading-4 text-gray-secondary">{{ $t('scheduleTask.backupHint') }}</div>
        </div>
      </el-form-item>
      <el-form-item v-if="values.type === 'collection'" prop="refId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="scheduleTask.collection" /></template>
        <el-select v-model="values.refId" class="w-full">
          <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="isBackupType(values.type)" prop="keepCount">
        <template #label><label-tip message="scheduleTask.keepCount" help /></template>
        <el-input-number v-model="values.keepCount" :min="0" :max="999"></el-input-number>
      </el-form-item>
      <el-form-item
        prop="cron"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            asyncValidator: async (rule, value, callback) => {
              if (value && !(await scheduleTaskCronValid(value))) {
                callback($t('scheduleTask.error.cronInvalid'));
                return;
              }
              callback();
            },
            trigger: 'blur',
          },
        ]"
      >
        <template #label><label-tip message="scheduleTask.schedule" help /></template>
        <div class="w-full">
          <div class="flex items-center gap-2">
            <el-select v-model="cronType" style="width: 150px" @change="() => handleCronTypeChange()">
              <el-option v-for="item in ['hour', 'day', 'week', 'month', 'custom']" :key="item" :label="$t(`scheduleTask.schedule.${item}`)" :value="item"></el-option>
            </el-select>
            <el-select v-if="cronType === 'hour'" v-model="cronMinute" style="width: 130px" @change="() => applyCron()">
              <el-option v-for="minute in 60" :key="minute - 1" :label="$t('scheduleTask.schedule.atMinute', { minute: minute - 1 })" :value="minute - 1"></el-option>
            </el-select>
            <el-select v-if="cronType === 'week'" v-model="cronWeekday" style="width: 110px" @change="() => applyCron()">
              <el-option v-for="item in weekdays" :key="item" :label="$t(`scheduleTask.schedule.weekday.${item}`)" :value="item"></el-option>
            </el-select>
            <el-select v-if="cronType === 'month'" v-model="cronDay" style="width: 110px" @change="() => applyCron()">
              <el-option v-for="day in 31" :key="day" :label="$t('scheduleTask.schedule.dayOfMonth', { day })" :value="day"></el-option>
            </el-select>
            <el-time-picker
              v-if="['day', 'week', 'month'].includes(cronType)"
              v-model="cronTime"
              format="HH:mm"
              value-format="HH:mm"
              :clearable="false"
              style="width: 120px"
              @change="() => applyCron()"
            />
            <el-input v-if="cronType === 'custom'" v-model="values.cron" maxlength="120" class="flex-1" :placeholder="$t('scheduleTask.cron.placeholder')"></el-input>
          </div>
          <div v-if="cronType !== 'custom' && values.cron" class="mt-1 text-xs leading-4 text-gray-secondary">{{ $t('scheduleTask.cron') }}: {{ values.cron }}</div>
        </div>
      </el-form-item>
      <el-form-item prop="enabled">
        <template #label><label-tip message="scheduleTask.enabled" /></template>
        <el-switch v-model="values.enabled" />
      </el-form-item>
    </template>
  </dialog-form>
</template>
