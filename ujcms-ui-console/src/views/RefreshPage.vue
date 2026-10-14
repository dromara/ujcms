<script lang="ts">
import { defineComponent } from 'vue';

export default defineComponent({
  beforeRouteEnter(to, from, next) {
    next((vm) => {
      // 回到 fullPath（而非 path）：保留 query，否则以 query 定身份的视图（如主题详情 site/theme）刷新后丢失上下文
      vm.$router.replace(from.fullPath);
    });
  },
  render() {
    return undefined;
  },
});
</script>
