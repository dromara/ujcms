<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { Plus, Delete, Collection, ScaleToOriginal } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm, currentUser } from '@/stores/useCurrentUser';
import { enumParam, useRouteState } from '@/composables/useRouteState';
import {
  queryBackupTemplatesList,
  backupBackupTemplates,
  restoreBackupTemplates,
  deleteBackupTemplates,
  queryBackupUploadsList,
  backupBackupUploads,
  restoreBackupUploads,
  deleteBackupUploads,
  queryIncrementalUploadsList,
  backupIncrementalUploads,
  restoreIncrementalUploads,
  mergeIncrementalUploads,
  deleteIncrementalUploads,
  queryBackupDatabaseList,
  backupBackupDatabase,
  restoreBackupDatabase,
  deleteBackupDatabase,
} from '@/api/file';
import { ColumnList, ColumnSetting } from '@/components/TableList';

defineOptions({
  name: 'BackupList',
});
const { t } = useI18n();

/**
 * 备份管理：模板备份 / 上传备份 / 增量备份 / 数据备份四种整库快照共用一张表（console-menu D24）。
 * code 是权限码、消息键、列设置名共用的前缀（backupTemplates …），也是操作日志的模块名——四个权限族原样保留，
 * 页签按各自的 :list 码显示，路由本身只由 backup:page 把守。
 */
interface Kind {
  code: string;
  query: () => Promise<any>;
  backup: () => Promise<any>;
  restore: (name: string) => Promise<any>;
  remove: (names: string[]) => Promise<any>;
  merge?: (names: string[]) => Promise<any>;
}
const kinds = {
  templates: { code: 'backupTemplates', query: queryBackupTemplatesList, backup: backupBackupTemplates, restore: restoreBackupTemplates, remove: deleteBackupTemplates },
  uploads: { code: 'backupUploads', query: queryBackupUploadsList, backup: backupBackupUploads, restore: restoreBackupUploads, remove: deleteBackupUploads },
  incremental: {
    code: 'incrementalUploads',
    query: queryIncrementalUploadsList,
    backup: backupIncrementalUploads,
    restore: restoreIncrementalUploads,
    remove: deleteIncrementalUploads,
    merge: mergeIncrementalUploads,
  },
  database: { code: 'backupDatabase', query: queryBackupDatabaseList, backup: backupBackupDatabase, restore: restoreBackupDatabase, remove: deleteBackupDatabase },
} satisfies Record<string, Kind>;
type Tab = keyof typeof kinds;
// 无 :list 权限的页签不显示；手改地址指向它时回落到首个可见页签（perm() 返回“是否无权限”）
const tabs = (Object.keys(kinds) as Tab[]).filter((key) => !perm(`${kinds[key].code}:list`));
// 当前页签存于 query.tab（docs/spec/console-view-state）：刷新或页签往返时停留在原备份种类，也可深链
const { tab } = useRouteState({ tab: enumParam(tabs[0] ?? 'templates', tabs) });
const kind = computed<Kind>(() => kinds[tab.value]);
// 数据库备份只支持部分数据库；其余种类不受影响
const unsupported = computed<boolean>(() => tab.value === 'database' && currentUser.databaseType == null);

const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const fetchData = async () => {
  if (tabs.length <= 0) return;
  loading.value = true;
  try {
    data.value = await kind.value.query();
  } finally {
    loading.value = false;
  }
};
onMounted(() => {
  fetchData();
});
watch(tab, () => {
  selection.value = [];
  fetchData();
});

const backupLoading = ref<boolean>(false);
const handleBackup = async () => {
  backupLoading.value = true;
  try {
    await kind.value.backup();
    fetchData();
    ElMessage.success(t('success'));
  } finally {
    backupLoading.value = false;
  }
};
const restoreLoading = ref<boolean>(false);
const handleRestore = async (name: string) => {
  restoreLoading.value = true;
  try {
    await kind.value.restore(name);
    fetchData();
    ElMessage.success(t('success'));
  } finally {
    restoreLoading.value = false;
  }
};
const handleMerge = async (names: string[]) => {
  await kind.value.merge?.(names);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDelete = async (names: string[]) => {
  await kind.value.remove(names);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-popconfirm :title="$t(`${kind.code}.confirm.backup`)" @confirm="() => handleBackup()">
        <template #reference>
          <el-button type="primary" :loading="backupLoading" :disabled="perm(`${kind.code}:backup`) || unsupported" :icon="Plus">{{ $t(`${kind.code}.op.backup`) }}</el-button>
        </template>
      </el-popconfirm>
      <el-popconfirm v-if="kind.merge" :title="$t(`${kind.code}.confirm.merge`)" @confirm="() => handleMerge(selection.map((row) => row.name))">
        <template #reference>
          <el-button :disabled="selection.length < 2 || perm(`${kind.code}:merge`)" :icon="ScaleToOriginal">{{ $t(`${kind.code}.op.merge`) }}</el-button>
        </template>
      </el-popconfirm>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.name))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm(`${kind.code}:delete`) || unsupported" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting :name="kind.code" />
    </div>
    <el-alert v-if="unsupported" :title="$t('backupDatabase.error.unsupported')" type="warning" show-icon :closable="false" class="mt-3" />
    <div class="mt-3 app-block">
      <el-tabs v-model="tab" class="block-tabs">
        <el-tab-pane v-for="item in tabs" :key="item" :name="item" :label="$t(`backup.tab.${item}`)"></el-tab-pane>
      </el-tabs>
      <el-table :key="tab" v-loading="loading" :data="data" @selection-change="(rows: any[]) => (selection = rows)">
        <column-list :name="kind.code">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="name" :label="$t('webFile.name')" show-overflow-tooltip min-width="280">
            <template #default="{ row }">
              <el-icon class="mr-1 text-primary"><Collection /></el-icon>
              <span>{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column
            property="lastModified"
            :label="$t('webFile.lastModified')"
            show-overflow-tooltip
            :formatter="(row) => (row.lastModified != null ? dayjs(row.lastModified).format('YYYY-MM-DD HH:mm') : '')"
            min-width="150"
          ></el-table-column>
          <el-table-column property="length" :label="$t('webFile.size')" :formatter="(row) => (row.directory ? '' : row.size)" min-width="120" />
          <el-table-column :label="$t('table.action')" :width="200">
            <template #default="{ row }">
              <el-popconfirm :title="$t(`${kind.code}.confirm.restore`)" width="285" @confirm="() => handleRestore(row.name)">
                <template #reference>
                  <el-button type="primary" :loading="restoreLoading" :disabled="perm(`${kind.code}:restore`) || unsupported" size="small" link>
                    {{ $t(`${kind.code}.op.restore`) }}
                  </el-button>
                </template>
              </el-popconfirm>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.name])">
                <template #reference>
                  <el-button type="primary" :disabled="perm(`${kind.code}:delete`) || unsupported" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
    </div>
  </div>
</template>
