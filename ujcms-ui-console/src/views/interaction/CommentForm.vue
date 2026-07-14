<script setup lang="ts">
import { ref, PropType } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import { queryComment, updateComment, deleteComment } from '@/api/interaction';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'CommentForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.interaction.comment')"
    :query-bean="queryComment"
    :create-bean="updateComment"
    :update-bean="updateComment"
    :delete-bean="deleteComment"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({})"
    :to-values="(bean) => ({ ...bean })"
    :addable="false"
    perms="comment"
    :model-value="modelValue"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #header-status="{ isEdit }">
      <template v-if="isEdit">
        <span v-if="values.status != null">
          <el-tag v-if="values.status === 0" type="success" size="small" disable-transitions>{{ $t(`comment.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 1" type="info" size="small" disable-transitions>{{ $t(`comment.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 2" type="danger" size="small" disable-transitions>{{ $t(`comment.status.${values.status}`) }}</el-tag>
          <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
        </span>
      </template>
    </template>
    <template #default>
      <el-row>
        <el-col :span="24">
          <el-form-item prop="article">
            <template #label><label-tip message="comment.article" /></template>
            <el-input :model-value="values.article?.title" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="text" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="comment.text" /></template>
            <el-input ref="focus" v-model="values.text" type="textarea" :rows="8" maxlength="65535"></el-input>
          </el-form-item>
        </el-col>
        <el-col v-if="values.images?.length > 0" :span="24">
          <el-form-item prop="images">
            <template #label><label-tip message="comment.images" /></template>
            <div v-for="(url, index) in values.images" :key="url" class="relative mr-2">
              <el-image :src="url" :preview-src-list="values.images" :initial-index="index" preview-teleported fit="cover" style="width: 96px; height: 96px"></el-image>
              <el-button
                type="danger"
                size="small"
                circle
                class="absolute -top-2 -right-2"
                :icon="Delete"
                @click="() => values.images.splice(index, 1)"
              ></el-button>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="recommended">
            <template #label><label-tip message="comment.recommended" /></template>
            <el-switch v-model="values.recommended" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="ip">
            <template #label><label-tip message="comment.ip" /></template>
            <el-input :model-value="values.ip" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="user">
            <template #label><label-tip message="comment.user" /></template>
            <el-input :model-value="values.user?.username" disabled></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="created">
            <template #label><label-tip message="comment.created" /></template>
            <el-date-picker :model-value="values.created" type="datetime" class="w-full" disabled></el-date-picker>
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>
