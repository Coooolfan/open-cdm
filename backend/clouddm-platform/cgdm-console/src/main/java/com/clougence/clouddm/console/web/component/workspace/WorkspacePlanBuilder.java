/*
 * Copyright 2026 杭州开云集致科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.clougence.clouddm.console.web.component.workspace;

import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.clougence.clouddm.api.common.exception.ErrorMessageException;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceQueryPlanDTO;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourceKind;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourcePlanDTO;
import com.clougence.clouddm.base.metadata.ds.DataSourceConfig;
import com.clougence.clouddm.base.metadata.ds.DataSourceType;
import com.clougence.clouddm.console.web.component.analysis.AnalysisQueryOptions;
import com.clougence.clouddm.console.web.component.analysis.BehaviorRelations;
import com.clougence.clouddm.console.web.component.analysis.BehaviorRequest;
import com.clougence.clouddm.console.web.component.analysis.QueryAnalysisService;
import com.clougence.clouddm.console.web.component.auth.DmAuthServiceForBiz;
import com.clougence.clouddm.console.web.component.auth.model.QueryRelationAuthResult;
import com.clougence.clouddm.console.web.component.detectrule.SecRulesCheckContext;
import com.clougence.clouddm.console.web.component.detectrule.SecRulesCheckResult;
import com.clougence.clouddm.console.web.component.detectrule.SecRulesCheckSession;
import com.clougence.clouddm.console.web.component.detectrule.SecRulesEngine;
import com.clougence.clouddm.console.web.component.dsconfig.DmDsConfigService;
import com.clougence.clouddm.console.web.component.dsconfig.mode.DsConfig;
import com.clougence.clouddm.console.web.component.dsconfig.mode.DsLevels;
import com.clougence.clouddm.console.web.global.i18n.DmI18nUtils;
import com.clougence.clouddm.console.web.global.i18n.I18nDmMsgKeys;
import com.clougence.clouddm.console.web.model.fo.editor.query.WsQueryFO;
import com.clougence.clouddm.console.web.util.DmDsUtils;
import com.clougence.clouddm.platform.dal.access.DataSourceDal;
import com.clougence.clouddm.platform.dal.access.ObjectCacheDao;
import com.clougence.clouddm.platform.dal.model.datasource.DmDsDO;
import com.clougence.clouddm.platform.dal.model.secrule.WarnLevel;
import com.clougence.clouddm.platform.plugin.PluginManager;
import com.clougence.clouddm.sdk.execute.dsconf.DsConfigField;
import com.clougence.clouddm.sdk.execute.resultset.echo.ReceiveMode;
import com.clougence.clouddm.sdk.execute.session.QueryRequest;
import com.clougence.clouddm.sdk.execute.session.QueryResultConf;
import com.clougence.clouddm.sdk.execute.session.SessionContextDTO;
import com.clougence.clouddm.sdk.execute.session.SessionSpi;
import com.clougence.clouddm.sdk.security.auth.SecDataAuthKind;
import com.clougence.clouddm.sdk.service.secrules.Requester;
import com.clougence.clouddm.sdk.service.secrules.RuleLevel;
import com.clougence.clouddm.sdk.sql.SqlEngineSpi;
import com.clougence.clouddm.sdk.sql.SqlParserParameters;
import com.clougence.clouddm.sdk.sql.analysis.sysobj.SysObjectRegistrySpi;
import com.clougence.clouddm.sdk.sql.parser.SplitQueryType;
import com.clougence.schema.umi.struts.UmiTypes;
import com.clougence.utils.CollectionUtils;
import com.clougence.utils.StringUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkspacePlanBuilder {

    private static final Set<String> BACKTICK_TYPES = Set.of("MYSQL", "MARIADB", "TIDB", "POLARDB", "OCEANBASE", "DORIS", "STARROCKS", "CLICKHOUSE", "ADB", "GOLDENDB");
    private static final Set<String> BRACKET_TYPES  = Set.of("SQLSERVER");

    @Resource
    private DataSourceDal            dsDal;
    @Resource
    private ObjectCacheDao           objectCacheDao;
    @Resource
    private DmDsConfigService        dmDsConfigService;
    @Resource
    private QueryAnalysisService     analysisService;
    @Resource
    private DmAuthServiceForBiz      authCheckService;
    @Resource
    private SecRulesEngine           ruleCheckService;

    public List<WorkspaceQueryPlanDTO> buildPlans(WsQueryFO queryFO, long clusterId, boolean isExplain, boolean cacheResult) {
        List<WorkspaceStatementPlan> stmtPlans = WorkspaceSqlRewriter.parseAndRewrite(queryFO.getQueryString(), isExplain);
        if (stmtPlans.isEmpty()) {
            return Collections.emptyList();
        }

        String puid = queryFO.getPrimaryUserId();
        String uid = queryFO.getCurrentUserId();
        List<DmDsDO> ownerDsList = this.dsDal.dsMapper().listByOwner(puid);

        List<WorkspaceQueryPlanDTO> result = new ArrayList<>(stmtPlans.size());
        for (int idx = 0; idx < stmtPlans.size(); idx++) {
            WorkspaceStatementPlan stmtPlan = stmtPlans.get(idx);
            List<WorkspaceSourcePlanDTO> sourcePlans = new ArrayList<>();
            for (WorkspaceSourceRef ref : stmtPlan.getSourceRefs()) {
                WorkspaceSourcePlanDTO sourcePlan = resolveAndAuthorizeSource(puid, uid, clusterId, queryFO, ref, ownerDsList);
                sourcePlans.add(sourcePlan);
            }

            QueryResultConf resultConf = new QueryResultConf();
            DmDsUtils.fillWorkspaceResultConf(resultConf);
            resultConf.setCacheResult(cacheResult);
            if (queryFO.getReceiveMode() != null) {
                resultConf.setReceiveMode(queryFO.getReceiveMode());
            } else {
                resultConf.setReceiveMode(cacheResult ? ReceiveMode.PAGINATED : ReceiveMode.PAGE_FULL);
            }
            resultConf.setRefreshStatus(idx == stmtPlans.size() - 1);

            WorkspaceQueryPlanDTO dto = new WorkspaceQueryPlanDTO();
            dto.setIndex(idx);
            dto.setQueryId("wsq_" + UUID.randomUUID().toString().replace("-", ""));
            dto.setOriginalSql(stmtPlan.getOriginalSql());
            dto.setRewrittenDuckDbSql(stmtPlan.getRewrittenDuckDbSql());
            dto.setUseExplain(isExplain);
            dto.setResultConf(resultConf);
            dto.setSources(sourcePlans);
            result.add(dto);
        }
        return result;
    }

    private WorkspaceSourcePlanDTO resolveAndAuthorizeSource(String puid, String uid, long clusterId, WsQueryFO queryFO, WorkspaceSourceRef ref, List<DmDsDO> ownerDsList) {
        DmDsDO dsDO = resolveDatasource(ref.getDsName(), clusterId, ownerDsList);
        this.objectCacheDao.ownDataSource(puid, dsDO.getId());

        DataSourceType dsType = dsDO.getDataSourceType();
        DsLevels baseLevels = this.dmDsConfigService.parseLevels(List.of(String.valueOf(dsDO.getDsEnvId()), String.valueOf(dsDO.getId())));
        DsConfig constantSettings = this.dmDsConfigService.dsConstantSettings(dsType);
        List<UmiTypes> levelsDef = new ArrayList<>();
        if (constantSettings != null && constantSettings.getCategories() != null && constantSettings.getCategories().getLevels() != null) {
            List<String> rawLevels = constantSettings.getCategories().getLevels();
            for (int i = 2; i < rawLevels.size(); i++) {
                UmiTypes umiType = UmiTypes.valueOfCode(rawLevels.get(i));
                if (umiType != null) {
                    levelsDef.add(umiType);
                }
            }
        }

        String[] quotes = resolveQuotes(dsType);
        String quoteLeft = quotes[0];
        String quoteRight = quotes[1];

        String catalog = null;
        String schema = null;
        String table = null;
        String qualifiedTable = null;
        List<String> parts = ref.getPathParts();

        if (ref.getKind() == WorkspaceSourceKind.TABLE) {
            table = parts.get(parts.size() - 1);
            List<String> prefix = parts.subList(0, parts.size() - 1);
            if (levelsDef.contains(UmiTypes.Catalog) && levelsDef.contains(UmiTypes.Schema)) {
                if (prefix.size() >= 2) {
                    catalog = prefix.get(prefix.size() - 2);
                    schema = prefix.get(prefix.size() - 1);
                } else if (prefix.size() == 1) {
                    schema = prefix.get(0);
                }
                if (StringUtils.isNotBlank(schema)) {
                    qualifiedTable = quoteIdent(schema, quoteLeft, quoteRight) + "." + quoteIdent(table, quoteLeft, quoteRight);
                } else {
                    qualifiedTable = quoteIdent(table, quoteLeft, quoteRight);
                }
            } else if (levelsDef.contains(UmiTypes.Catalog)) {
                if (!prefix.isEmpty()) {
                    catalog = prefix.get(prefix.size() - 1);
                    qualifiedTable = quoteIdent(catalog, quoteLeft, quoteRight) + "." + quoteIdent(table, quoteLeft, quoteRight);
                } else {
                    qualifiedTable = quoteIdent(table, quoteLeft, quoteRight);
                }
            } else if (levelsDef.contains(UmiTypes.Schema)) {
                if (!prefix.isEmpty()) {
                    schema = prefix.get(prefix.size() - 1);
                    qualifiedTable = quoteIdent(schema, quoteLeft, quoteRight) + "." + quoteIdent(table, quoteLeft, quoteRight);
                } else {
                    qualifiedTable = quoteIdent(table, quoteLeft, quoteRight);
                }
            } else {
                qualifiedTable = quoteIdent(table, quoteLeft, quoteRight);
            }
        } else {
            if (levelsDef.contains(UmiTypes.Catalog) && levelsDef.contains(UmiTypes.Schema)) {
                if (parts.size() >= 2) {
                    catalog = parts.get(parts.size() - 2);
                    schema = parts.get(parts.size() - 1);
                } else if (parts.size() == 1) {
                    catalog = parts.get(0);
                }
            } else if (levelsDef.contains(UmiTypes.Catalog) && !parts.isEmpty()) {
                catalog = parts.get(parts.size() - 1);
            } else if (levelsDef.contains(UmiTypes.Schema) && !parts.isEmpty()) {
                schema = parts.get(parts.size() - 1);
            }
        }

        List<String> targetLevelList = new ArrayList<>();
        targetLevelList.add(String.valueOf(dsDO.getDsEnvId()));
        targetLevelList.add(String.valueOf(dsDO.getId()));
        if (levelsDef.contains(UmiTypes.Catalog)) {
            if (StringUtils.isBlank(catalog)) {
                catalog = resolveDefaultDatabase(dsDO.getId());
            }
            if (StringUtils.isNotBlank(catalog)) {
                targetLevelList.add(catalog);
            }
        }
        if (levelsDef.contains(UmiTypes.Schema)) {
            if (StringUtils.isBlank(schema)) {
                schema = this.dmDsConfigService.fetchDsConfig(dsDO.getId(), DsConfigField.DEFAULT_SCHEMA.getConfigName());
            }
            if (StringUtils.isNotBlank(schema)) {
                targetLevelList.add(schema);
            }
        }

        DsLevels dsLevels;
        try {
            dsLevels = this.dmDsConfigService.parseLevels(targetLevelList);
        } catch (Exception e) {
            dsLevels = baseLevels;
        }

        Map<String, String> overrides = new HashMap<>();
        if (StringUtils.isNotBlank(catalog)) {
            overrides.put(DsConfigField.DEFAULT_DATABASE.getConfigName(), catalog);
        }
        if (StringUtils.isNotBlank(schema)) {
            overrides.put(DsConfigField.DEFAULT_SCHEMA.getConfigName(), schema);
        }
        DataSourceConfig dsConfig = this.dmDsConfigService.fetchDsConfigFromExists(dsDO.getId(), overrides);

        String sourceSql;
        if (ref.getKind() == WorkspaceSourceKind.TABLE) {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM ").append(qualifiedTable);
            if (StringUtils.isNotBlank(ref.getFilterExpr())) {
                sb.append(" WHERE (").append(ref.getFilterExpr()).append(")");
            }
            sourceSql = sb.toString();
        } else {
            sourceSql = ref.getRawQuery().trim();
        }

        QueryRequest analyzedReq = analyzeAndAuthorizeSourceSql(puid, uid, queryFO, dsDO, dsLevels, dsConfig, sourceSql);

        SessionSpi sessionSpi = PluginManager.findSessionSpi(dsType);
        Map<String, Object> ctxParams = new HashMap<>();
        if (StringUtils.isNotBlank(catalog)) {
            ctxParams.put(SessionSpi.PARAMS_DEFAULT_DB, catalog);
        }
        if (StringUtils.isNotBlank(schema)) {
            ctxParams.put(SessionSpi.PARAMS_DEFAULT_SCHEMA, schema);
        }
        SessionContextDTO sessionCtx = sessionSpi.createSessionContext(dsConfig, ctxParams);
        sessionCtx.setRdbAutoCommit(true);
        sessionCtx.setRdbReadOnly(true);

        WorkspaceSourcePlanDTO plan = new WorkspaceSourcePlanDTO();
        plan.setRefId(ref.getRefId());
        plan.setKind(ref.getKind());
        plan.setDsId(dsDO.getId());
        plan.setDsName(StringUtils.isNotBlank(dsDO.getInstanceDesc()) ? dsDO.getInstanceDesc() : dsDO.getInstanceId());
        plan.setDsType(dsType);
        plan.setDsConfig(dsConfig);
        plan.setSessionContext(sessionCtx);
        plan.setCatalog(catalog);
        plan.setSchema(schema);
        plan.setTable(table);
        plan.setFilterExpr(ref.getFilterExpr());
        plan.setQualifiedTable(qualifiedTable);
        plan.setQuoteLeft(quoteLeft);
        plan.setQuoteRight(quoteRight);
        plan.setSourceQueryRequest(analyzedReq);
        return plan;
    }

    private QueryRequest analyzeAndAuthorizeSourceSql(String puid, String uid, WsQueryFO queryFO, DmDsDO dsDO, DsLevels dsLevels, DataSourceConfig dsConfig, String sourceSql) {
        SqlParserParameters parameters = this.dmDsConfigService.fetchSqlParserParameters(dsConfig, dsLevels.levelsParam());
        AnalysisQueryOptions options = AnalysisQueryOptions.builder()
            .currentUid(uid)
            .dataSourceId(dsDO.getId())
            .levels(dsLevels.levelsParam())
            .parameters(parameters)
            .build();

        List<QueryRequest> analyzedList;
        try (StringReader reader = new StringReader(sourceSql);
             Stream<QueryRequest> stream = this.analysisService.analysisRequestsStream(dsConfig, reader, null, 0, 0, options)) {
            analyzedList = stream.collect(Collectors.toList());
        }

        if (analyzedList.size() != 1) {
            String dsLabel = StringUtils.isNotBlank(dsDO.getInstanceDesc()) ? dsDO.getInstanceDesc() : dsDO.getInstanceId();
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SOURCE_SINGLE_STMT_ERROR.name(), dsLabel));
        }

        QueryRequest req = analyzedList.get(0);
        Set<SplitQueryType> queryTypes = req.getQueryTypes();
        if (CollectionUtils.isEmpty(queryTypes) || queryTypes.contains(SplitQueryType.UNKNOWN)
            || req.hasQueryType(SplitQueryType.SWITCH_CATALOG) || req.hasQueryType(SplitQueryType.SWITCH_SCHEMA)
            || req.hasQueryType(SplitQueryType.TRANSACTION)) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SOURCE_READONLY_ERROR.name(), sourceSql));
        }

        // Enforce strict READ-only on remote datasource
        SqlEngineSpi sqlEngine = this.dmDsConfigService.fetchSqlEngineSpi(dsDO.getId());
        SysObjectRegistrySpi registry = PluginManager.findSysObjectRegistry(dsDO.getDataSourceType(), sqlEngine.name());
        boolean hasNonReadBehavior = BehaviorRelations.flattenResource(registry, parameters.version(), req.getRelations())
            .stream()
            .filter(behavior -> behavior.authKind() != null)
            .anyMatch(behavior -> behavior.authKind() != SecDataAuthKind.READ);
        if (hasNonReadBehavior) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SOURCE_READONLY_ERROR.name(), sourceSql));
        }

        // Enforce fine-grained data permission
        QueryRelationAuthResult authResult = this.authCheckService.checkQueryRelationAuth(puid, uid, dsLevels, analyzedList);
        if (!authResult.isPassed()) {
            BehaviorRequest denied = authResult.getDeniedRequests().get(0);
            String authLabel = denied.authKind().getAuthLabel();
            String authLabelI18n = DmI18nUtils.getMessage(authLabel);
            String resourcePath = BehaviorRelations.resourcePath(denied.resource(), DmDsUtils.currentResourcePath(dsLevels.levelsParam()),
                DmDsUtils.instanceResourcePath(dsLevels.levelsParam()));
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.CONSOLE_QUERY_NO_PERMISSION_MESSAGE.name(), resourcePath, authLabelI18n));
        }

        // Enforce security rules on source SQL
        SecRulesCheckContext ruleCtx = SecRulesCheckContext.builder()
            .basicCodeLine(0)
            .basicCodeColumn(0)
            .dsId(dsDO.getId())
            .currentUID(uid)
            .currentCatalog(asString(dsLevels.levelsParam().get(UmiTypes.Catalog)))
            .currentSchema(asString(dsLevels.levelsParam().get(UmiTypes.Schema)))
            .sqlParameters(parameters)
            .requester(Requester.CONSOLE)
            .unsupportedLevel(WarnLevel.PASS)
            .build();
        SecRulesCheckSession ruleSession = this.ruleCheckService.openQueryCheck(uid, dsConfig, ruleCtx);
        SecRulesCheckResult ruleResult = ruleSession.applyCheck(sourceSql, 0, 0);
        RuleLevel[] blocked = queryFO.isForce() ? new RuleLevel[] { RuleLevel.FAILURE, RuleLevel.TICKET } : new RuleLevel[] { RuleLevel.FAILURE, RuleLevel.TICKET, RuleLevel.SUGGEST };
        if (ruleResult != null && ruleResult.hasAnyTarget(blocked)) {
            String firstHint = ruleResult.toSecHintList().isEmpty() ? sourceSql : ruleResult.toSecHintList().get(0).getMessage();
            throw new ErrorMessageException(firstHint);
        }

        SessionSpi sessionSpi = PluginManager.findSessionSpi(dsDO.getDataSourceType());
        req.setQueryId(sessionSpi.newQueryId());
        req.setDsId(dsDO.getId());
        req.setDsType(dsDO.getDataSourceType());
        req.setRequester(Requester.CONSOLE);
        req.setQueryBody(sourceSql);
        req.setOriginalBody(sourceSql);
        return req;
    }

    private DmDsDO resolveDatasource(String dsName, long clusterId, List<DmDsDO> ownerDsList) {
        if (ownerDsList == null || ownerDsList.isEmpty()) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_DS_NOT_FOUND_ERROR.name(), dsName));
        }

        List<DmDsDO> inClusterExact = new ArrayList<>();
        List<DmDsDO> inClusterIgnoreCase = new ArrayList<>();
        boolean existsInOtherCluster = false;

        for (DmDsDO ds : ownerDsList) {
            boolean exactMatch = StringUtils.equals(dsName, ds.getInstanceDesc()) || StringUtils.equals(dsName, ds.getInstanceId());
            boolean ciMatch = exactMatch || StringUtils.equalsIgnoreCase(dsName, ds.getInstanceDesc()) || StringUtils.equalsIgnoreCase(dsName, ds.getInstanceId());
            if (!ciMatch) {
                continue;
            }
            if (Objects.equals(ds.getBindClusterId(), clusterId)) {
                if (exactMatch) {
                    inClusterExact.add(ds);
                }
                inClusterIgnoreCase.add(ds);
            } else {
                existsInOtherCluster = true;
            }
        }

        List<DmDsDO> matched = inClusterExact.isEmpty() ? inClusterIgnoreCase : inClusterExact;
        if (matched.size() == 1) {
            return matched.get(0);
        }
        if (matched.size() > 1) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_DS_AMBIGUOUS_ERROR.name(), dsName));
        }
        if (existsInOtherCluster) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_DS_CLUSTER_MISMATCH_ERROR.name(), dsName));
        }
        throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_DS_NOT_FOUND_ERROR.name(), dsName));
    }

    private static String[] resolveQuotes(DataSourceType dsType) {
        if (dsType == null) {
            return new String[] { "\"", "\"" };
        }
        String name = dsType.name().toUpperCase(Locale.ROOT);
        if (BACKTICK_TYPES.contains(name)) {
            return new String[] { "`", "`" };
        }
        if (BRACKET_TYPES.contains(name)) {
            return new String[] { "[", "]" };
        }
        return new String[] { "\"", "\"" };
    }

    private static String quoteIdent(String ident, String left, String right) {
        String escaped = ident.replace(right, right + right);
        return left + escaped + right;
    }

    private String resolveDefaultDatabase(long dsId) {
        String defaultDb = this.dmDsConfigService.fetchDsConfig(dsId, DsConfigField.DEFAULT_DATABASE.getConfigName());
        if (StringUtils.isNotBlank(defaultDb)) {
            return defaultDb;
        }
        DataSourceConfig tmpCfg = this.dmDsConfigService.fetchDsConfigFromExists(dsId);
        if (tmpCfg != null && tmpCfg.asDriverProperties() != null) {
            return tmpCfg.asDriverProperties().getProperty("database");
        }
        return null;
    }

    private static String asString(Object val) {
        return val == null ? null : String.valueOf(val);
    }
}
