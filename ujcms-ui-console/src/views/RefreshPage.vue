<script lang="ts">
import { defineComponent } from 'vue';

export default defineComponent({
  beforeRouteEnter(to, from, next) {
    next((vm) => {
      vm.$router.replace(from.path);
    });
  },
  render() {
    return undefined;
  },
});
</script>
