package com.clougence.clouddm.init.model;

import com.clougence.clouddm.console.web.constants.SystemStatus;

/**
 * 系统状态检测结果。
 */
public class SystemStatusResult {

    private SystemStatus status;
    private String       initReason;
    private String       dbError;

    public SystemStatus getStatus() { return status; }

    public void setStatus(SystemStatus status) { this.status = status; }

    public String getInitReason() { return initReason; }

    public void setInitReason(String initReason) { this.initReason = initReason; }

    public String getDbError() { return dbError; }

    public void setDbError(String dbError) { this.dbError = dbError; }
}
