<template>
  <a-modal
    :open="visible"
    :title="$t('workspace-select-cluster-title')"
    :ok-text="$t('que-ding')"
    :cancel-text="$t('qu-xiao')"
    :ok-button-props="{ disabled: !selectedClusterId }"
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <p class="workspace-cluster-tip">{{ $t('workspace-select-cluster-tip') }}</p>
    <a-radio-group v-model:value="selectedClusterId" class="workspace-cluster-list">
      <div
        v-for="cluster in clusters"
        :key="cluster.clusterId"
        class="workspace-cluster-item"
        :class="{ 'workspace-cluster-item--disabled': !cluster.workspaceWorkerCount }"
      >
        <a-radio :value="cluster.clusterId" :disabled="!cluster.workspaceWorkerCount">
          <span class="workspace-cluster-name">{{ cluster.clusterDesc || cluster.clusterName }}</span>
          <a-tag v-if="cluster.workspaceWorkerCount > 0" color="cyan" style="margin-left: 8px">
            {{ $t('workspace-worker-count-badge', [cluster.workspaceWorkerCount]) }}
          </a-tag>
          <a-tag v-else color="default" style="margin-left: 8px">
            {{ $t('workspace-no-worker-short') }}
          </a-tag>
        </a-radio>
      </div>
    </a-radio-group>
  </a-modal>
</template>

<script>
export default {
  name: 'WorkspaceClusterModal',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    clusters: {
      type: Array,
      default: () => []
    }
  },
  emits: ['confirm', 'cancel'],
  data() {
    return {
      selectedClusterId: null
    };
  },
  watch: {
    visible(val) {
      if (val) {
        const firstAvailable = this.clusters.find((c) => c.workspaceWorkerCount > 0);
        this.selectedClusterId = firstAvailable ? firstAvailable.clusterId : null;
      }
    }
  },
  methods: {
    handleConfirm() {
      const cluster = this.clusters.find((c) => c.clusterId === this.selectedClusterId);
      if (cluster) {
        this.$emit('confirm', cluster);
      }
    },
    handleCancel() {
      this.$emit('cancel');
    }
  }
};
</script>

<style scoped lang="less">
.workspace-cluster-tip {
  margin-bottom: 12px;
  color: var(--text-secondary, #666);
}

.workspace-cluster-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.workspace-cluster-item {
  padding: 8px 12px;
  border: 1px solid var(--border-primary, #e5e7eb);
  border-radius: 6px;

  &--disabled {
    opacity: 0.6;
  }
}

.workspace-cluster-name {
  font-weight: 500;
}
</style>
