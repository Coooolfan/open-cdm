<template>
  <div class="step-db">
    <h3>{{ $t('initialization.step.db') }}</h3>
    <a-form layout="vertical">
      <a-form-item v-for="field in fieldDefs" :key="field.propertyKey" :label="field.label" :required="field.required">
        <a-input
          v-if="field.inputType === 'text'"
          :value="formValues[field.propertyKey] || ''"
          @change="(e) => onChange(field.propertyKey, e.target.value)"
          :placeholder="field.description"
        />
        <a-input-password
          v-else-if="field.inputType === 'password'"
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

    <div class="test-db-section">
      <a-button type="primary" @click="$emit('testDb')" :loading="testing">
        {{ $t('initialization.testConnection') }}
      </a-button>
      <span v-if="dbTestResult" class="test-result" :class="{ success: dbTestResult.success, error: !dbTestResult.success }">
        {{ dbTestResult.message }}
        <span v-if="dbTestResult.success && dbTestResult.isEmpty" class="sep">{{ $t('initialization.dbEmpty') }}</span>
        <span v-if="dbTestResult.success && dbTestResult.isInstalled" class="sep">{{ $t('initialization.dbInstalled') }}</span>
      </span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'StepDb',
  props: {
    fieldDefs: { type: Array, default: () => [] },
    formValues: { type: Object, default: () => ({}) },
    dbTestResult: { type: Object, default: null }
  },
  data() {
    return { testing: false };
  },
  methods: {
    onChange(key, value) {
      this.$emit('update:formValues', { [key]: value });
    }
  }
};
</script>

<style scoped>
.step-db h3 {
  margin-bottom: 24px;
}
.test-db-section {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.test-result.success {
  color: #52c41a;
}
.test-result.error {
  color: #ff4d4f;
}
.sep::before {
  content: ' \\2014 ';
}
</style>
