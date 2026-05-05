<template>
  <div class="step-connectivity">
    <h3>{{ $t('initialization.step.connectivity') }}</h3>
    <a-form layout="vertical">
      <a-form-item v-for="field in fieldDefs" :key="field.propertyKey" :label="field.label" :required="field.required">
        <a-input
          v-if="field.inputType === 'text'"
          :value="formValues[field.propertyKey] || ''"
          @change="(e) => onChange(field.propertyKey, e.target.value)"
          :placeholder="field.description"
        />
        <a-input
          v-else-if="field.inputType === 'number'"
          :value="formValues[field.propertyKey]"
          type="number"
          @change="(e) => onChange(field.propertyKey, e.target.value)"
          :placeholder="field.description"
        />
      </a-form-item>
    </a-form>
  </div>
</template>

<script>
export default {
  name: 'StepConnectivity',
  props: {
    fieldDefs: { type: Array, default: () => [] },
    formValues: { type: Object, default: () => ({}) }
  },
  methods: {
    onChange(key, value) {
      this.$emit('update:formValues', { [key]: value });
    }
  }
};
</script>

<style scoped>
.step-connectivity h3 {
  margin-bottom: 24px;
}
</style>
