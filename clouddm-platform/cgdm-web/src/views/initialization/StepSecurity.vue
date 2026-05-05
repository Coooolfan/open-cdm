<template>
  <div class="step-security">
    <h3>{{ $t('initialization.step.security') }}</h3>
    <a-form layout="vertical">
      <a-form-item v-for="field in fieldDefs" :key="field.propertyKey" :label="field.label" :required="field.required">
        <a-input
          v-if="field.inputType === 'text'"
          :value="formValues[field.propertyKey] || ''"
          @change="(e) => onChange(field.propertyKey, e.target.value)"
          :placeholder="field.description"
        >
          <template v-if="field.propertyKey === 'jwt.secret'" #suffix>
            <a-button size="small" type="link" @click="generateJwt">
              {{ $t('initialization.generate') }}
            </a-button>
          </template>
        </a-input>
        <a-input-password
          v-else-if="field.inputType === 'password'"
          :value="formValues[field.propertyKey] || ''"
          @change="(e) => onChange(field.propertyKey, e.target.value)"
          :placeholder="field.description"
        />
      </a-form-item>
    </a-form>
    <a-alert v-if="hasAdminFields" type="info" show-icon :message="$t('initialization.adminNote')" style="margin-top: 16px" />
  </div>
</template>

<script>
export default {
  name: 'StepSecurity',
  props: {
    fieldDefs: { type: Array, default: () => [] },
    formValues: { type: Object, default: () => ({}) }
  },
  computed: {
    hasAdminFields() {
      return this.fieldDefs.some((f) => f.propertyKey && f.propertyKey.startsWith('clougence.init.admin'));
    }
  },
  methods: {
    onChange(key, value) {
      this.$emit('update:formValues', { [key]: value });
    },
    generateJwt() {
      const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789' + '!@#$%^&*()_+-=[]{}|;:,.<>?';
      let result = '';
      for (let i = 0; i < 64; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
      }
      this.$emit('update:formValues', { 'jwt.secret': result });
    }
  }
};
</script>

<style scoped>
.step-security h3 {
  margin-bottom: 24px;
}
</style>
