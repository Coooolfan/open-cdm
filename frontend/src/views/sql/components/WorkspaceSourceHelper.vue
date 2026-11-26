<template>
  <a-popover trigger="click" placement="bottomRight" overlayClassName="workspace-source-popover">
    <template #content>
      <div class="workspace-source-helper">
        <div class="workspace-helper-section">
          <div class="workspace-helper-title">{{ $t('workspace-available-datasources') }}</div>
          <div v-if="!dataSources.length" class="workspace-helper-empty">{{ $t('workspace-no-datasources-in-cluster') }}</div>
          <div v-else class="workspace-helper-list">
            <div v-for="ds in dataSources" :key="ds.dsId" class="workspace-helper-row">
              <div class="workspace-helper-ds">
                <span class="workspace-helper-name">{{ ds.instanceDesc || ds.instanceId }}</span>
                <a-tag size="small">{{ ds.dataSourceType }}</a-tag>
              </div>
              <div class="workspace-helper-actions">
                <a-button type="link" size="small" @click="emitInsert(buildTableSnippet(ds))">
                  {{ $t('workspace-insert-table-ref') }}
                </a-button>
                <a-button type="link" size="small" @click="emitInsert(buildQuerySnippet(ds))">
                  {{ $t('workspace-insert-query-ref') }}
                </a-button>
              </div>
            </div>
          </div>
        </div>
        <div class="workspace-helper-section workspace-helper-syntax">
          <div class="workspace-helper-title">{{ $t('workspace-syntax-guide') }}</div>
          <code v-for="example in syntaxExamples" :key="example">{{ example }}</code>
        </div>
      </div>
    </template>
    <a-button size="small" type="text" class="workspace-helper-trigger">
      {{ $t('workspace-sources-button', [dataSources.length]) }}
    </a-button>
  </a-popover>
</template>

<script>
export default {
  name: 'WorkspaceSourceHelper',
  props: {
    dataSources: {
      type: Array,
      default: () => []
    }
  },
  emits: ['insert'],
  data() {
    return {
      syntaxExamples: ['#ds.schema.table', '#ds.schema.table.#filter($$id > 0$$)', '#ds.#query($$SELECT * FROM t LIMIT 100$$)']
    };
  },
  methods: {
    buildTableSnippet(ds) {
      const parts = [ds.referenceToken];
      if (ds.defaultDatabase) parts.push(ds.defaultDatabase);
      if (ds.defaultSchema) parts.push(ds.defaultSchema);
      parts.push('table_name');
      return parts.join('.');
    },
    buildQuerySnippet(ds) {
      return `${ds.referenceToken}.#query($$SELECT 1$$)`;
    },
    emitInsert(snippet) {
      this.$emit('insert', snippet);
    }
  }
};
</script>

<style scoped lang="less">
.workspace-source-helper {
  width: 360px;
  max-width: 85vw;
}

.workspace-helper-section + .workspace-helper-section {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--border-primary, #e5e7eb);
}

.workspace-helper-title {
  font-weight: 600;
  margin-bottom: 6px;
  font-size: 12px;
}

.workspace-helper-list {
  max-height: 220px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.workspace-helper-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.workspace-helper-ds {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.workspace-helper-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 150px;
}

.workspace-helper-actions {
  flex-shrink: 0;
}

.workspace-helper-empty {
  color: var(--text-secondary, #888);
  font-size: 12px;
}

.workspace-helper-syntax {
  display: flex;
  flex-direction: column;
  gap: 4px;

  code {
    display: block;
    font-size: 11px;
    padding: 3px 6px;
    border-radius: 4px;
    background: var(--bg-secondary, #f5f5f5);
    word-break: break-all;
  }
}

.workspace-helper-trigger {
  font-size: 12px;
  padding: 0 6px;
}
</style>
