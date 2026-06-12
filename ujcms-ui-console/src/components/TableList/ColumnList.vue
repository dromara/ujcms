<script lang="ts">
import { computed, watch, defineComponent, toRefs } from 'vue';
import { useI18n } from 'vue-i18n';
import { useColumnSettingsStore, ColumnState } from '@/stores/columnSettingsStore';

export default defineComponent({
  name: 'ColumnList',
  props: { name: { type: String, required: true } },
  setup(props, { slots }) {
    const { name } = toRefs(props);
    const { t } = useI18n();
    const slotColumns = computed<any[]>(() => slots.default?.().flatMap((item: any) => (item.children?.length > 0 ? item.children : item)) ?? []);
    // 获取栏目名称
    const getColumnTitle = (columnProps: any) => {
      // 如果是checkbox列，则名称为“选择框”
      if (columnProps?.type === 'selection') return t('table.selection');
      return columnProps?.label;
    };
    const settingsStore = useColumnSettingsStore();
    // 获取el-table-column的名称、是否显示
    const origins = computed<ColumnState[]>(() => slotColumns.value.map((column) => ({ title: getColumnTitle(column.props), display: column.props?.display !== 'none' })));
    watch(
      [name, origins],
      () => {
        settingsStore.setOriginSettings(name.value, origins.value);
      },
      { deep: true, immediate: true },
    );

    const settings = computed<ColumnState[]>(() => settingsStore.getCurrentSettings(name.value));
    const columns = computed(() =>
      slotColumns.value
        .filter((column) => {
          const matched = settings.value.find((item) => getColumnTitle(column.props) === item.title);
          return matched?.display ?? column.props?.display !== 'none';
        })
        .map((column) => ({ ...column, key: getColumnTitle(column.props) }))
        .sort((a, b) => {
          let indexA = settings.value.findIndex((item) => item.title === getColumnTitle(a));
          if (indexA < 0) indexA = slotColumns.value.findIndex((item) => getColumnTitle(item) === getColumnTitle(a));
          let indexB = settings.value.findIndex((item) => item.title === getColumnTitle(b));
          if (indexB < 0) indexB = slotColumns.value.findIndex((item) => getColumnTitle(item) === getColumnTitle(b));
          return indexA - indexB;
        }),
    );
    return { columns };
  },
  render() {
    return this.columns;
  },
});
</script>
