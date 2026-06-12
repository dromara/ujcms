<script lang="ts">
import { defineComponent, toRefs } from 'vue';

export default defineComponent({
  name: 'QueryInput',
  props: { inputs: { type: Array, required: true }, name: { type: String, required: true } },
  setup(props) {
    const { inputs, name } = toRefs(props);
    return () => inputs.value.find((item: any) => item.props.name === name.value);
  },
});
</script>
