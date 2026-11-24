<template>
  <div class="step-db">
    <a-form layout="horizontal" class="step-db-form">
      <div v-if="jdbcUrlField" class="jdbc-generated-editor">
        <a-form-item :label="$t('initialization.jdbcDataSourceType')" class="jdbc-database-type-form-item">
          <div v-if="readonly" class="jdbc-database-type-value">
            <cc-data-source-icon
              class="jdbc-database-type-icon"
              :type="selectedDbOption.iconType"
              :size="18"
              :color="selectedDbOption.iconColor"
              aria-hidden="true"
            />
            <span>{{ $t(selectedDbOption.labelKey) }}</span>
          </div>
          <a-radio-group
            v-else
            class="jdbc-database-type-group"
            :value="generatedState.dbType"
            @change="(e) => onDbTypeChange(normalizeInputValue(e))"
          >
            <a-radio-button v-for="option in dbTypeOptions" :key="option.value" :value="option.value" class="jdbc-database-type-option">
              <span class="jdbc-database-type-value">
                <cc-data-source-icon
                  class="jdbc-database-type-icon"
                  :type="option.iconType"
                  :size="16"
                  :color="option.iconColor"
                  aria-hidden="true"
                />
                <span>{{ $t(option.labelKey) }}</span>
              </span>
            </a-radio-button>
          </a-radio-group>
        </a-form-item>

        <a-form-item :label="$t('initialization.jdbcHostPort')" required>
          <div class="jdbc-host-port-row">
            <div class="jdbc-inline-field jdbc-inline-field-host">
              <a-input
                :value="generatedState.host"
                :disabled="readonly"
                :placeholder="$t('initialization.jdbcHostPlaceholder')"
                @input="(value) => onGeneratedFieldChange('host', normalizeInputValue(value))"
              />
            </div>
            <div class="jdbc-inline-field jdbc-inline-field-port">
              <span class="jdbc-inline-label jdbc-inline-label-required">{{ $t('initialization.jdbcPortLabel') }}</span>
              <a-input
                :value="generatedState.port"
                :disabled="readonly"
                :placeholder="portPlaceholder"
                @input="(value) => onGeneratedFieldChange('port', normalizeInputValue(value))"
              />
            </div>
          </div>
        </a-form-item>

        <a-form-item v-if="dbUsernameField" :label="dbUsernameField.label" required class="jdbc-form-item-full">
          <a-input
            class="jdbc-full-width-control"
            :value="formValues[dbUsernameField.propertyKey] || ''"
            :disabled="readonly"
            :placeholder="dbUsernameField.description"
            @input="(value) => onChange(dbUsernameField.propertyKey, normalizeInputValue(value))"
          />
        </a-form-item>

        <a-form-item v-if="dbPasswordField" :label="dbPasswordField.label" :required="dbPasswordField.required" class="jdbc-form-item-full">
          <a-input-password
            class="jdbc-full-width-control"
            :value="formValues[dbPasswordField.propertyKey] || ''"
            :disabled="readonly"
            :placeholder="dbPasswordField.description"
            @input="(value) => onChange(dbPasswordField.propertyKey, normalizeInputValue(value))"
          />
        </a-form-item>

        <a-form-item :label="$t('initialization.jdbcDatabase')" required class="jdbc-form-item-full jdbc-database-form-item">
          <div class="jdbc-database-row">
            <div
              class="jdbc-database-input-wrap"
              :class="{ 'is-pending-create': hasPendingCreateDatabase, 'is-focused': databaseInputFocused }"
              :data-pending-create-text="pendingCreateDatabaseDisplay"
            >
              <a-input
                class="jdbc-full-width-control"
                :value="generatedState.database"
                :disabled="readonly"
                :placeholder="$t('initialization.jdbcDatabasePlaceholder')"
                @focus="databaseInputFocused = true"
                @blur="databaseInputFocused = false"
                @input="(value) => onGeneratedFieldChange('database', normalizeInputValue(value))"
              />
            </div>
            <a-button v-if="showTestButton" class="jdbc-test-button" :disabled="testingDb" @click="$emit('test-db')">
              <span v-if="testingDb" class="button-inline-spinner" aria-hidden="true"></span>
              <span>{{ $t('initialization.testConnection') }}</span>
            </a-button>
          </div>
        </a-form-item>
      </div>

      <a-form-item v-for="field in remainingFields" :key="field.propertyKey" :label="field.label" required>
        <a-input
          v-if="field.inputType === 'text'"
          :value="formValues[field.propertyKey] || ''"
          :disabled="readonly"
          @input="(value) => onChange(field.propertyKey, normalizeInputValue(value))"
          :placeholder="field.description"
        />
        <a-input-password
          v-else-if="field.inputType === 'password'"
          :value="formValues[field.propertyKey] || ''"
          :disabled="readonly"
          @input="(value) => onChange(field.propertyKey, normalizeInputValue(value))"
          :placeholder="field.description"
        />
        <a-input
          v-else-if="field.inputType === 'number'"
          :value="formValues[field.propertyKey]"
          :disabled="readonly"
          type="number"
          @input="(value) => onChange(field.propertyKey, normalizeInputValue(value))"
          :placeholder="field.description"
        />
      </a-form-item>
    </a-form>
  </div>
</template>

<script>
const DB_TYPE_OPTIONS = Object.freeze([
  Object.freeze({
    value: 'mysql',
    protocol: 'jdbc:mysql://',
    defaultPort: '3306',
    iconType: 'MySQL',
    iconColor: '#0087c7',
    labelKey: 'initialization.jdbcDataSourceTypeValue',
    portPlaceholderKey: 'initialization.jdbcPortPlaceholder'
  }),
  Object.freeze({
    value: 'postgresql',
    protocol: 'jdbc:postgresql://',
    defaultPort: '5432',
    iconType: 'PostgreSQL',
    iconColor: '#336791',
    labelKey: 'initialization.jdbcDataSourceTypePostgresValue',
    portPlaceholderKey: 'initialization.jdbcPgPortPlaceholder'
  })
]);

const DEFAULT_GENERATED_STATE = Object.freeze({
  dbType: 'mysql',
  host: '',
  port: '3306',
  database: 'cdmgr',
  query: ''
});

function getDbTypeOption(dbType) {
  return DB_TYPE_OPTIONS.find((item) => item.value === dbType) || DB_TYPE_OPTIONS[0];
}

function createGeneratedState(overrides = {}) {
  const option = getDbTypeOption(overrides.dbType || DEFAULT_GENERATED_STATE.dbType);
  return {
    dbType: option.value,
    host: overrides.host ?? DEFAULT_GENERATED_STATE.host,
    port: overrides.port ?? option.defaultPort,
    database: overrides.database ?? DEFAULT_GENERATED_STATE.database,
    query: overrides.query ?? DEFAULT_GENERATED_STATE.query
  };
}

function decodeJdbcValue(value) {
  try {
    return decodeURIComponent(value || '');
  } catch (e) {
    return value || '';
  }
}

function parseMetaJdbcUrl(jdbcUrl) {
  if (!jdbcUrl || typeof jdbcUrl !== 'string') {
    return null;
  }

  const match = jdbcUrl.trim().match(/^jdbc:(mysql|postgresql):\/\/([^/:?#]*)(?::(\d+))?\/([^?]*)(?:\?(.*))?$/i);
  if (!match) {
    return null;
  }

  const [, rawType, host, port, database, query] = match;
  const option = getDbTypeOption(rawType.toLowerCase());

  return createGeneratedState({
    dbType: option.value,
    host,
    port: port || option.defaultPort,
    database: decodeJdbcValue(database),
    query: query || ''
  });
}

function buildMetaJdbcUrl(generatedState) {
  const host = generatedState.host || '';
  const port = generatedState.port || '';
  const database = generatedState.database || '';
  const query = generatedState.query || '';

  if (!host) {
    return '';
  }

  const option = getDbTypeOption(generatedState.dbType);
  const querySuffix = option.value === 'postgresql' && query ? `?${query}` : '';
  return `${option.protocol}${host}${port ? `:${port}` : ''}/${database}${querySuffix}`;
}

export default {
  name: 'StepDb',
  emits: ['update:formValues', 'validation-change', 'test-db'],
  props: {
    fieldDefs: { type: Array, default: () => [] },
    formValues: { type: Object, default: () => ({}) },
    dbTestResult: { type: Object, default: null },
    readonly: { type: Boolean, default: false },
    showTestButton: { type: Boolean, default: false },
    testingDb: { type: Boolean, default: false }
  },
  data() {
    return {
      generatedState: createGeneratedState(),
      databaseInputFocused: false,
      lastEmittedJdbcUrl: null
    };
  },
  computed: {
    dbTypeOptions() {
      return DB_TYPE_OPTIONS;
    },
    selectedDbOption() {
      return getDbTypeOption(this.generatedState.dbType);
    },
    portPlaceholder() {
      return this.$t(this.selectedDbOption.portPlaceholderKey);
    },
    jdbcUrlField() {
      return this.fieldDefs.find((field) => field.propertyKey === 'spring.datasource.jdbcurl') || null;
    },
    dbUsernameField() {
      return this.fieldDefs.find((field) => field.propertyKey === 'spring.datasource.username') || null;
    },
    dbPasswordField() {
      return this.fieldDefs.find((field) => field.propertyKey === 'spring.datasource.password') || null;
    },
    remainingFields() {
      const excludedKeys = ['spring.datasource.jdbcurl', 'spring.datasource.username', 'spring.datasource.password'];
      return this.fieldDefs.filter((field) => !excludedKeys.includes(field.propertyKey));
    },
    jdbcUrlValue() {
      return this.formValues['spring.datasource.jdbcurl'] || '';
    },
    hasPendingCreateDatabase() {
      const databaseName = `${this.generatedState.database || ''}`.trim();
      if (!this.dbTestResult || !this.dbTestResult.success || this.dbTestResult.databaseExists || !databaseName) {
        return false;
      }

      return true;
    },
    pendingCreateDatabaseDisplay() {
      if (!this.hasPendingCreateDatabase) {
        return '';
      }

      const databaseName = `${this.generatedState.database || ''}`.trim();
      return `${databaseName}${this.$t('initialization.jdbcDatabasePendingCreate')}`;
    },
    missingRequiredFields() {
      if (this.readonly) {
        return [];
      }

      const missingFields = [];

      if (!this.generatedState.host) {
        missingFields.push(this.$t('initialization.jdbcHostPort'));
      }
      if (!this.generatedState.port) {
        missingFields.push(this.$t('initialization.jdbcPortLabel'));
      }
      if (!(this.formValues['spring.datasource.username'] || '').trim()) {
        missingFields.push(this.dbUsernameField ? this.dbUsernameField.label : this.$t('initialization.dbUsernameFallback'));
      }
      if (this.dbPasswordField && this.dbPasswordField.required && !(this.formValues['spring.datasource.password'] || '').trim()) {
        missingFields.push(this.dbPasswordField ? this.dbPasswordField.label : this.$t('initialization.dbPasswordFallback'));
      }
      if (!this.generatedState.database) {
        missingFields.push(this.$t('initialization.jdbcDatabase'));
      }

      return missingFields;
    }
  },
  watch: {
    jdbcUrlValue: {
      immediate: true,
      handler(value) {
        this.syncJdbcState(value);
      }
    },
    missingRequiredFields: {
      immediate: true,
      handler(value) {
        this.$emit('validation-change', value);
      }
    }
  },
  methods: {
    normalizeInputValue(payload) {
      if (payload && typeof payload === 'object' && 'target' in payload) {
        return payload.target ? payload.target.value : '';
      }
      return payload;
    },
    onChange(key, value) {
      if (this.readonly) {
        return;
      }
      this.$emit('update:formValues', { [key]: value });
    },
    syncJdbcState(jdbcUrl) {
      if (this.lastEmittedJdbcUrl !== null && jdbcUrl === this.lastEmittedJdbcUrl) {
        return;
      }
      this.lastEmittedJdbcUrl = null;
      const parsed = parseMetaJdbcUrl(jdbcUrl);
      if (parsed) {
        this.generatedState = parsed;
        return;
      }

      if (!jdbcUrl) {
        this.generatedState = createGeneratedState({ dbType: this.generatedState.dbType });
      }
    },
    onDbTypeChange(dbType) {
      if (this.readonly || !dbType || dbType === this.generatedState.dbType) {
        return;
      }
      const prevOption = getDbTypeOption(this.generatedState.dbType);
      const nextOption = getDbTypeOption(dbType);
      const currentPort = `${this.generatedState.port || ''}`.trim();
      const nextPort = !currentPort || currentPort === prevOption.defaultPort ? nextOption.defaultPort : currentPort;
      this.generatedState = {
        ...this.generatedState,
        dbType: nextOption.value,
        port: nextPort,
        query: nextOption.value === 'postgresql' ? this.generatedState.query : ''
      };
      this.emitGeneratedJdbcUrl();
    },
    onGeneratedFieldChange(key, value) {
      if (this.readonly) {
        return;
      }
      this.generatedState = {
        ...this.generatedState,
        [key]: value
      };
      this.emitGeneratedJdbcUrl();
    },
    emitGeneratedJdbcUrl() {
      const jdbcUrl = buildMetaJdbcUrl(this.generatedState);
      this.lastEmittedJdbcUrl = jdbcUrl;
      this.$emit('update:formValues', { 'spring.datasource.jdbcurl': jdbcUrl });
    }
  }
};
</script>

<style scoped>
.step-db-form :deep(.ant-form-item) {
  display: flex;
  align-items: flex-start;
  width: 100%;
  margin-bottom: 14px;
}
.step-db-form :deep(.ant-form-item-row) {
  display: flex;
  width: 100%;
}
.step-db-form :deep(.ant-form-item-label) {
  flex: 0 0 136px;
  max-width: 136px;
  padding-right: 12px;
  text-align: left;
  line-height: 32px;
}
.step-db-form :deep(.ant-form-item-label > label) {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  position: relative;
  min-height: 32px;
  padding-left: 12px;
  white-space: nowrap;
  text-align: left;
}
.step-db-form :deep(.ant-form-item-required::before) {
  position: absolute;
  left: 0;
  margin-right: 0;
}
.step-db-form :deep(.ant-form-item-control-wrapper) {
  flex: 1;
  max-width: calc(100% - 136px);
}
.step-db-form :deep(.ant-form-item-control) {
  flex: 1 1 0;
  min-width: 0;
}
.step-db-form :deep(.ant-form-item-control-input) {
  flex: 1 1 auto;
  min-width: 0;
}
.jdbc-generated-editor {
  width: 100%;
}
.jdbc-database-type-group {
  display: inline-flex;
  flex-wrap: wrap;
}
.jdbc-database-type-group :deep(.ant-radio-button-wrapper) {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 12px;
}
.jdbc-database-type-value {
  display: inline-flex;
  align-items: center;
  color: rgba(0, 0, 0, 0.85);
  font-size: 14px;
  line-height: 28px;
  white-space: nowrap;
}
.jdbc-database-type-value :deep(.datasource-icon) {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  margin-right: 6px;
  line-height: 18px;
}
.jdbc-host-port-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.jdbc-inline-field {
  display: flex;
  align-items: center;
}
.jdbc-inline-field-host {
  flex: 1;
  min-width: 0;
}
.jdbc-inline-field-port {
  flex: 0 0 auto;
}
.jdbc-inline-field :deep(.ant-input) {
  width: 100%;
}
.jdbc-inline-field-port :deep(.ant-input) {
  width: 96px;
}
.jdbc-inline-label {
  margin-right: 8px;
  white-space: nowrap;
  color: rgba(0, 0, 0, 0.85);
}
.jdbc-inline-label-required::before {
  display: inline-block;
  margin-right: 4px;
  color: #ff4d4f;
  font-family: SimSun, sans-serif;
  line-height: 1;
  content: '*';
}
.jdbc-form-item-full :deep(.ant-form-item-control-input),
.jdbc-form-item-full :deep(.ant-form-item-control-input-content),
.jdbc-form-item-full :deep(.ant-form-item-control-wrapper),
.jdbc-full-width-control,
.jdbc-full-width-control :deep(.ant-input),
.jdbc-full-width-control :deep(.ant-input-password) {
  width: 100%;
}
.jdbc-database-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.jdbc-database-input-wrap {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
}
.jdbc-database-input-wrap.is-pending-create:not(.is-focused)::after {
  position: absolute;
  top: 50%;
  left: 12px;
  z-index: 1;
  max-width: calc(100% - 24px);
  overflow: hidden;
  color: #389e0d;
  font-size: 14px;
  line-height: 1;
  white-space: nowrap;
  text-overflow: ellipsis;
  content: attr(data-pending-create-text);
  transform: translateY(-50%);
  pointer-events: none;
}
.jdbc-database-input-wrap.is-pending-create:not(.is-focused) .jdbc-full-width-control {
  color: transparent;
  -webkit-text-fill-color: transparent;
}
.jdbc-database-input-wrap.is-pending-create.is-focused .jdbc-full-width-control {
  color: #389e0d;
  -webkit-text-fill-color: #389e0d;
}
.jdbc-test-button {
  flex: 0 0 auto;
  min-width: 96px;
}
.button-inline-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  margin-right: 8px;
  vertical-align: -2px;
  border-radius: 50%;
  border: 2px solid rgba(0, 0, 0, 0.18);
  border-top-color: #1677ff;
  animation: buttonInlineSpin 0.8s linear infinite;
}

@keyframes buttonInlineSpin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .step-db-form :deep(.ant-form-item) {
    flex-direction: column;
    align-items: stretch;
  }

  .step-db-form :deep(.ant-form-item-row) {
    flex-direction: column;
  }

  .step-db-form :deep(.ant-form-item-label) {
    flex: none;
    max-width: 100%;
    width: 100%;
    padding: 0 0 6px;
    line-height: 22px;
  }

  .step-db-form :deep(.ant-form-item-label > label) {
    min-height: 22px;
  }

  .step-db-form :deep(.ant-form-item-control-wrapper) {
    flex: none;
    max-width: 100%;
    width: 100%;
  }

  .step-db-form :deep(.ant-form-item-control),
  .step-db-form :deep(.ant-form-item-control-input),
  .step-db-form :deep(.ant-form-item-control-input-content) {
    flex: none;
    height: auto;
    min-height: 0;
    width: 100%;
  }

  .jdbc-host-port-row,
  .jdbc-database-row {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .jdbc-inline-field-port {
    width: 100%;
  }

  .jdbc-inline-field-port :deep(.ant-input) {
    width: 100%;
  }

  .jdbc-inline-label {
    flex: 0 0 auto;
  }

  .jdbc-test-button {
    width: 100%;
  }
}
</style>
