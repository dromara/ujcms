<script setup lang="ts">
import { ref, watch, computed, toRefs, type PropType } from 'vue';
import { Finished, DocumentRemove, Delete, CircleCheck, CircleClose, Back, SetUp, Compass } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { perm, currentUser } from '@/stores/useCurrentUser';
import { promptRejectReason } from '@/utils/review';
import {
  queryForm,
  queryFormReview,
  createForm,
  updateForm,
  deleteForm,
  completelyDeleteForm,
  passForm,
  rejectForm,
  submitForm,
  backForm,
  transferForm,
  delegateForm,
} from '@/api/content';
import { queryTaskFormProperties } from '@/api/system';
import DialogForm from '@/components/DialogForm.vue';
import FieldItem from '@/views/config/components/FieldItem.vue';
import LabelTip from '@/components/LabelTip.vue';
import ReviewFormProperties from '@/views/content/components/ReviewFormProperties.vue';
import ReviewDelegate from '@/views/content/components/ReviewDelegate.vue';
import ReviewTransfer from '@/views/content/components/ReviewTransfer.vue';
import ReviewBack from '@/views/content/components/ReviewBack.vue';

defineOptions({
  name: 'FormForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  formType: { type: Object, required: true },
  modelList: { type: Array as PropType<any[]>, required: true },
  isReview: { type: Boolean, default: false },
});
defineEmits({ 'update:modelValue': null, finished: null });
const { formType, modelList } = toRefs(props);
const { t } = useI18n();
const dialog = ref<any>();
const modelId = ref<string>(formType.value.modelId);
const fields = computed(() => JSON.parse(modelList.value.find((item: any) => item.id === modelId.value)?.customs || '[]'));
const focus = ref<any>();
const values = ref<any>({});

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

// 表单模型的自定义字段变了（表单类型的模型迟到、切到另一类型）：给新增的空白表单补默认值。这不是用户的改动——
// 表单与基线一起补（dialog.patch）。已有的数据不补：访客没填的就是没填，不替他填上默认值
watch(fields, () => {
  if (dialog.value?.isEdit) {
    return;
  }
  dialog.value?.patch((target: any) => {
    if (target?.customs != null) {
      initCustoms(target.customs);
    }
  });
});

// 存为草稿：提交的是表单的副本，草稿状态只写在副本上，不落到打开着的表单里
const handleSaveAsDraft = () => {
  dialog.value.submit(async (data: any, { resetForm }: { resetForm: () => void }) => {
    await createForm({ ...data, status: 10 });
    resetForm();
    ElMessage.success(t('success'));
  });
};

const taskId = ref<string>('');
const formProperties = ref<any[]>([]);
const formPropertiesVisible = ref<boolean>(false);
const backVisible = ref<boolean>(false);
const delegateVisible = ref<boolean>(false);
const transferVisible = ref<boolean>(false);

const handlePass = async (bean: any) => {
  taskId.value = bean.taskId;
  formProperties.value = await queryTaskFormProperties(bean.taskId);
  formPropertiesVisible.value = true;
};
const submitPass = async (params: any) => {
  dialog.value.remove(async () => {
    await passForm(taskId.value, params.properties, params.comment);
  });
};

const handleDelegate = async (bean: any) => {
  taskId.value = bean.taskId;
  delegateVisible.value = true;
};
const submitDelegate = async (params: any) => {
  dialog.value.remove(async () => {
    await delegateForm(taskId.value, params.toUserId, params.comment);
  });
};
const handleTransfer = async (bean: any) => {
  taskId.value = bean.taskId;
  transferVisible.value = true;
};
const submitTransfer = async (params: any) => {
  dialog.value.remove(async () => {
    await transferForm(taskId.value, params.toUserId, params.comment);
  });
};
const handleBack = (bean: any) => {
  taskId.value = bean.taskId;
  backVisible.value = true;
};
const submitBack = (params: any) => {
  dialog.value.remove(async () => {
    await backForm(taskId.value, params.activityId, params.comment);
  });
};
const handleReject = async (bean: any) => {
  const reason = await promptRejectReason(t);
  if (reason == null) {
    return;
  }
  dialog.value.remove(async () => {
    await rejectForm([bean.taskId], reason);
  });
};

// 状态操作不保存表单内容：不必先通过表单校验
const handleExecute = async (action: string, id: string) => {
  dialog.value.submit(async (values: any, { loadBean }: { loadBean: () => Promise<any> }) => {
    if (action === 'delete') {
      await deleteForm([id]);
    } else if (action === 'submit') {
      await submitForm([id]);
    } else {
      throw new Error(`not support action: ${action}`);
    }
    loadBean();
  }, { skipValidate: true });
};
</script>

<template>
  <div>
    <dialog-form
      ref="dialog"
      v-model:values="values"
      :name="$t('form.label')"
      :query-bean="isReview ? queryFormReview : queryForm"
      :create-bean="createForm"
      :update-bean="updateForm"
      :delete-bean="completelyDeleteForm"
      :bean-id="beanId"
      :bean-ids="beanIds"
      :focus="focus"
      :init-values="
        () => {
          // 先定下模型再取默认值：模型ID只在这里和 bean-change 里更新，表单类型迟到或切换后，第一次新增要用新类型的模型
          modelId = formType.modelId;
          return { typeId: formType.id, customs: initCustoms({}) };
        }
      "
      :to-values="(bean) => ({ ...bean })"
      :model-value="modelValue"
      :addable="!isReview && formType.id != null"
      :disable-edit="(bean) => bean.id != null && !bean.editable && !currentUser.allStatusPermission && !isReview"
      perms="form"
      large
      @update:model-value="(event) => $emit('update:modelValue', event)"
      @finished="() => $emit('finished')"
      @bean-change="
        (bean) => {
          modelId = bean.type?.modelId ?? formType.modelId;
        }
      "
    >
      <template #header-action="{ isEdit, bean, unsaved, handleDelete }">
        <el-button-group v-if="isReview">
          <el-button v-if="isEdit && isReview" :disabled="perm('formReview:pass') || unsaved" type="primary" :icon="CircleCheck" @click="() => handlePass(bean)">
            {{ $t('review.pass') }}
          </el-button>
          <el-button :disabled="perm('formReview:reject') || unsaved" :icon="CircleClose" @click="() => handleReject(bean)">{{ $t('review.reject') }}</el-button>
          <el-button :disabled="perm('formReview:back') || unsaved" :icon="Back" @click="() => handleBack(bean)">{{ $t('review.back') }}</el-button>
          <el-button :disabled="perm('formReview:delegate') || unsaved" :icon="SetUp" @click="() => handleDelegate(bean)">
            {{ $t('review.delegate') }}
          </el-button>
          <el-button :disabled="perm('formReview:transfer') || unsaved" :icon="Compass" @click="() => handleTransfer(bean)">
            {{ $t('review.transfer') }}
          </el-button>
        </el-button-group>

        <el-button-group v-if="!isReview">
          <el-popconfirm v-if="isEdit" :title="$t('confirmExecute')" @confirm="() => handleExecute('submit', bean.id)">
            <template #reference>
              <el-button :disabled="[0, 12].includes(bean.status) || perm('form:submit') || unsaved" :icon="Finished">{{ $t('form.op.submit') }}</el-button>
            </template>
          </el-popconfirm>
        </el-button-group>
        <el-button-group>
          <el-popconfirm v-if="isEdit && !isReview" :title="$t('confirmDelete')" @confirm="() => handleExecute('delete', bean.id)">
            <template #reference>
              <el-button :disabled="perm('form:delete') || unsaved" :icon="DocumentRemove">{{ $t('delete') }}</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-if="isEdit" :title="$t('confirmCompletelyDelete')" @confirm="() => handleDelete()">
            <template #reference>
              <el-button :disabled="perm('form:completelyDelete')" :icon="Delete">{{ $t('form.op.completelyDelete') }}</el-button>
            </template>
          </el-popconfirm>
        </el-button-group>
      </template>
      <template #header-status="{ isEdit }">
        <template v-if="isEdit">
          <span v-if="values.status != null">
            <el-tag v-if="values.status === 0" type="success" size="small" disable-transitions>{{ $t(`form.status.${values.status}`) }}</el-tag>
            <el-tag v-else-if="values.status === 20" type="danger" size="small" disable-transitions>{{ $t(`form.status.${values.status}`) }}</el-tag>
            <el-tag v-else-if="values.status === 22" type="warning" size="small" disable-transitions>{{ $t(`form.status.${values.status}`) }}</el-tag>
            <el-tag v-else type="info" size="small" disable-transitions>{{ $t(`form.status.${values.status}`) }}</el-tag>
          </span>
        </template>
      </template>
      <template #default="{ isEdit }">
        <el-row>
          <template v-for="field in fields" :key="field.code">
            <el-col :span="field.double ? 12 : 24">
              <el-form-item :prop="`customs.${field.code}`" :rules="field.required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="field.name" /></template>
                <field-item v-model="values.customs[field.code]" v-model:model-key="values.customs[field.code + 'Key']" :field="field"></field-item>
              </el-form-item>
            </el-col>
          </template>
          <el-col v-if="isEdit" :span="12">
            <el-form-item>
              <template #label><label-tip message="form.user" /></template>
              <el-input :model-value="values.user?.username" disabled />
            </el-form-item>
          </el-col>
          <el-col v-if="isEdit" :span="12">
            <el-form-item>
              <template #label><label-tip message="form.modifiedUser" /></template>
              <el-input :model-value="values.modifiedUser?.username" disabled />
            </el-form-item>
          </el-col>
          <el-col v-if="isEdit" :span="12">
            <el-form-item>
              <template #label><label-tip message="form.created" /></template>
              <el-date-picker :model-value="values.created" type="datetime" disabled></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col v-if="isEdit" :span="12">
            <el-form-item>
              <template #label><label-tip message="form.modified" /></template>
              <el-date-picker :model-value="values.modified" type="datetime" disabled></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col v-if="isEdit" :span="12">
            <el-form-item>
              <template #label><label-tip message="form.org" /></template>
              <el-input :model-value="values.org?.name" disabled />
            </el-form-item>
          </el-col>
          <el-col v-if="isEdit" :span="12">
            <el-form-item>
              <template #label><label-tip message="form.ip" /></template>
              <el-input :model-value="values.ip" disabled />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
      <template #footer-action="{ isEdit, handleSubmit }">
        <el-button :disabled="perm(isEdit ? 'form:update' : 'form:create')" type="primary" native-type="submit" @click.prevent="() => handleSubmit()">
          {{ $t(isEdit ? 'save' : 'submit') }}
        </el-button>
        <el-button v-if="!isEdit" @click="() => handleSaveAsDraft()">{{ $t('form.op.saveAsDraft') }}</el-button>
      </template>
    </dialog-form>
    <review-form-properties v-model="formPropertiesVisible" v-model:form-properties="formProperties" @finished="submitPass" />
    <review-delegate v-model="delegateVisible" @finished="submitDelegate" />
    <review-transfer v-model="transferVisible" @finished="submitTransfer" />
    <review-back v-model="backVisible" :task-id @finished="submitBack" />
  </div>
</template>
