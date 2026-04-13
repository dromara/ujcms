<script setup lang="ts">
import { ref, PropType } from 'vue';
import { Plus, Minus, Grid } from '@element-plus/icons-vue';
import draggable from 'vuedraggable';
import { queryVote, createVote, updateVote, deleteVote } from '@/api/interaction';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'VoteForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});

let optionIndex = -1;
const addNewLine = (index: number) => {
  optionIndex -= 1;
  values.value.options.splice(index, 0, { id: optionIndex, count: 0 });
};
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.interaction.vote')"
    :query-bean="queryVote"
    :create-bean="createVote"
    :update-bean="updateVote"
    :delete-bean="deleteVote"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ maxChoice: 0, mode: 1, interval: 0, total: 0, enabled: true, options: [{ id: -1, count: 0 }] })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="vote"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-row>
        <el-col :span="24">
          <el-form-item prop="title" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="vote.title" /></template>
            <el-input ref="focus" v-model="values.title" maxlength="1000"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="description">
            <template #label><label-tip message="vote.description" /></template>
            <el-input v-model="values.description" type="textarea" :rows="3" maxlength="1000"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="beginDate">
            <template #label><label-tip message="vote.beginDate" help /></template>
            <el-date-picker v-model="values.beginDate" type="datetime" class="w-full"></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="endDate">
            <template #label><label-tip message="vote.endDate" help /></template>
            <el-date-picker v-model="values.endDate" type="datetime" class="w-full"></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="mode" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="vote.mode" help /></template>
            <el-select v-model="values.mode" class="w-full"><el-option v-for="n in [1, 2, 3]" :key="n" :value="n" :label="$t(`vote.mode.${n}`)" /> </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="created">
            <template #label><label-tip message="vote.created" /></template>
            <el-date-picker v-model="values.created" type="datetime" class="w-full" disabled></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="multiple">
            <template #label><label-tip message="vote.multiple" help /></template>
            <el-switch v-model="values.multiple" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="interval" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="vote.interval" help /></template>
            <el-input-number v-model="values.interval" :min="0" :max="32767" class="w-48" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="enabled">
            <template #label><label-tip message="vote.enabled" /></template>
            <el-switch v-model="values.enabled" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="times">
            <template #label><label-tip message="vote.times" /></template>
            <el-input-number :model-value="values.times" disabled class="w-48" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item>
            <template #label><label-tip message="vote.options" /></template>
            <table class="w-full table-auto border-slate-400">
              <thead>
                <tr>
                  <th class="w-32"></th>
                  <th class="">{{ $t('voteOption.title') }}</th>
                  <th class="w-40">{{ $t('voteOption.count') }}</th>
                </tr>
              </thead>
              <draggable v-model="values.options" :animation="250" tag="tbody" handle=".draggable-handle" item-key="id">
                <template #item="{ element: option, index }">
                  <tr>
                    <td class="p-2">
                      <el-icon class="text-2xl align-middle cursor-move draggable-handle"><Grid /></el-icon>
                      <el-button class="ml-3" :icon="Plus" circle @click="() => addNewLine(index + 1)"></el-button>
                      <el-button
                        :disabled="values.options.length <= 1"
                        :icon="Minus"
                        circle
                        @click="() => (values.options = values.options.filter((item) => item.id !== option.id))"
                      ></el-button>
                    </td>
                    <td class="p-2">
                      <el-form-item :prop="`options.${index}.title`" :rules="{ required: true, message: () => $t('v.required') }">
                        <el-input v-model="option.title" maxlength="1000" />
                      </el-form-item>
                    </td>
                    <td class="p-2"><el-input-number v-model="option.count" :min="0" :max="2147483647" class="w-48" /></td>
                  </tr>
                </template>
              </draggable>
            </table>
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>
