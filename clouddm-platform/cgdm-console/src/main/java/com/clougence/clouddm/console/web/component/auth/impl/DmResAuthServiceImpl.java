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
package com.clougence.clouddm.console.web.component.auth.impl;

import static com.clougence.clouddm.sdk.security.auth.def.SecDataAuthLabel.DM_DAUTH_QUERY;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.clougence.clouddm.console.web.dal.model.DmResAuthDO;
import org.springframework.stereotype.Service;

import com.clougence.clouddm.console.web.component.auth.BizResOwnerCacheService;
import com.clougence.clouddm.console.web.component.auth.DmResAuthService;
import com.clougence.clouddm.console.web.component.auth.model.ResourceAccessInfo;
import com.clougence.clouddm.console.web.component.auth.model.UserCacheEntry;
import com.clougence.clouddm.console.web.component.dsconfig.mode.DsLevels;
import com.clougence.clouddm.sdk.model.analysis.resource.DsResPath;
import com.clougence.clouddm.sdk.security.auth.AuthInfo;
import com.clougence.clouddm.sdk.security.auth.AuthKind;
import com.clougence.clouddm.sdk.security.auth.def.SecDataAuthLabel;
import com.clougence.clouddm.console.web.dal.enumeration.AccountType;
import com.clougence.clouddm.console.web.dal.mapper.RdpDataSourceMapper;
import com.clougence.clouddm.console.web.dal.mapper.DmResAuthMapper;
import com.clougence.clouddm.console.web.dal.mapper.RdpUserMapper;
import com.clougence.clouddm.console.web.dal.model.RdpDataSourceDO;
import com.clougence.clouddm.console.web.dal.model.RdpUserDO;
import com.clougence.rdp.service.RdpAuthServiceForBiz;
import com.clougence.rdp.service.RdpAuthServiceForManage;
import com.clougence.utils.CollectionUtils;
import com.clougence.utils.StringUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * @author bucketli 2021/1/13 10:50
 */
@Service
@Slf4j
public class DmResAuthServiceImpl implements DmResAuthService {

    @Resource
    private RdpDataSourceMapper       rdpDataSourceMapper;
    @Resource
    private RdpUserMapper             rdpUserMapper;
    @Resource
    private RdpAuthServiceForBiz      authServiceForBiz;
    @Resource
    private RdpAuthServiceForManage authServiceForManage;
    @Resource
    private DmResAuthMapper         resAuthMapper;
    @Resource
    private BizResOwnerCacheService ownerCacheService;

    private static final List<String> DM_DS_ANY_AUTH = Arrays
        .asList(DM_DAUTH_QUERY, SecDataAuthLabel.DM_DAUTH_DML, SecDataAuthLabel.DM_DAUTH_DDL, SecDataAuthLabel.DM_DAUTH_CALL, SecDataAuthLabel.DM_DAUTH_DCL, SecDataAuthLabel.DM_DAUTH_OTHER);

    @Override
    public boolean checkResAuth(String puid, String uid, long dsId, DsResPath dsObj, String resAuth) {
        return this.authServiceForBiz.checkResAuthWithoutError(puid, uid, dsId, dsObj, resAuth, AuthKind.DataSource);
    }

    @Override
    public List<Long> listResByUser(String targetUid, AuthKind authKind) {
        if (authKind == AuthKind.DataSource) {
            RdpUserDO userDO = rdpUserMapper.queryByUid(targetUid);
            if (userDO.getAccountType() == AccountType.PRIMARY_ACCOUNT) {
                List<RdpDataSourceDO> dsDOs = this.rdpDataSourceMapper.listByUserWithGmtOrder(targetUid);
                return dsDOs.stream().map(RdpDataSourceDO::getId).collect(Collectors.toList());
            } else if (userDO.isResourceManageEnable()) {
                UserCacheEntry cacheEntry = ownerCacheService.queryByUserNumberId(userDO.getParentId());
                List<RdpDataSourceDO> dsDOs = this.rdpDataSourceMapper.listByUserWithGmtOrder(cacheEntry.getUid());
                return dsDOs.stream().map(RdpDataSourceDO::getId).collect(Collectors.toList());
            } else {
                List<DmResAuthDO> result = this.resAuthMapper.listByKind(targetUid, AuthKind.DataSource);
                return result.stream()
                    .filter(r -> CollectionUtils.containsAny(r.getAuthLabels(), DM_DS_ANY_AUTH) && r.isEffective())
                    .map(DmResAuthDO::getResId)
                    .distinct()
                    .collect(Collectors.toList());
            }
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Long> listResByUserContainAnyAuth(String targetUid, AuthKind authKind) {
        if (authKind == AuthKind.DataSource) {
            RdpUserDO userDO = rdpUserMapper.queryByUid(targetUid);
            if (userDO.getAccountType() == AccountType.PRIMARY_ACCOUNT) {
                List<RdpDataSourceDO> dsDOs = this.rdpDataSourceMapper.listByUserWithGmtOrder(targetUid);
                return dsDOs.stream().map(RdpDataSourceDO::getId).collect(Collectors.toList());
            } else if (userDO.isResourceManageEnable()) {
                UserCacheEntry cacheEntry = ownerCacheService.queryByUserNumberId(userDO.getParentId());
                List<RdpDataSourceDO> dsDOs = this.rdpDataSourceMapper.listByUserWithGmtOrder(cacheEntry.getUid());
                return dsDOs.stream().map(RdpDataSourceDO::getId).collect(Collectors.toList());
            } else {
                List<DmResAuthDO> result = this.resAuthMapper.listByKind(targetUid, AuthKind.DataSource);
                return result.stream()
                    .filter(r -> CollectionUtils.isNotEmpty(r.getAuthLabels()) && r.isEffective())
                    .map(DmResAuthDO::getResId)
                    .distinct()
                    .collect(Collectors.toList());
            }
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DmResAuthDO> listAuthByUser(String targetUid, AuthKind authKind) {
        return this.authServiceForBiz.listAuthByUser(targetUid, authKind);
    }

    @Override
    public List<DmResAuthDO> listAuthByUser(long dsId, String targetUid, AuthKind authKind, List<String> resPathList) {
        List<DmResAuthDO> resAuthDOList = this.resAuthMapper.queryByPathLike(dsId, targetUid, authKind, resPathList);
        return resAuthDOList.stream().filter(authDO -> {
            return authDO.isEffective() && authDO.getAuthLabels().contains(SecDataAuthLabel.DM_DAUTH_SENSITIVE);
        }).collect(Collectors.toList());
    }

    @Override
    public <T extends DsResPath> List<T> filterAuthByUser(String puid, String uid, long dsId, List<T> dsResource, String resAuth) {
        // primary not check
        if (StringUtils.equals(puid, uid)) {
            return dsResource;
        }

        // filter resAuth in dsObjs
        List<Predicate<String>> authedPathNames = new ArrayList<>();
        List<String> queryPathList = dsResource.stream().map(DsResPath::getResPath).collect(Collectors.toList());
        List<DmResAuthDO> dsAuthDOList = this.resAuthMapper.queryByPathLike(dsId, uid, AuthKind.DataSource, queryPathList);
        for (DmResAuthDO dsAuthDO : dsAuthDOList) {
            // filter resAuth
            if (dsAuthDO.getAuthLabels() == null || !dsAuthDO.getAuthLabels().contains(resAuth)) {
                continue;
            }

            // diffuse
            authedPathNames.add(s -> dsAuthDO.getResPath().startsWith(s) || s.startsWith(dsAuthDO.getResPath()));
        }

        List<T> result = new ArrayList<>();
        for (T dsSchema : dsResource) {
            for (Predicate<String> authedPath : authedPathNames) {
                if (authedPath.test(dsSchema.getResPath())) {
                    result.add(dsSchema);
                }
            }
        }
        return result;
    }

    @Override
    public ResourceAccessInfo getAllowBrowseInfo(DsLevels levels, String uid) {
        RdpUserDO userDO = rdpUserMapper.queryByUid(uid);
        if (userDO.getAccountType() == AccountType.PRIMARY_ACCOUNT || userDO.isResourceManageEnable()) {
            return new ResourceAccessInfo(true);
        }

        Long dsId = levels.dsDO().getId();
        String path = levels.asResPath().getResPath();
        List<DmResAuthDO> parentAndSelfAuth = this.resAuthMapper.queryByPathLike(dsId, uid, AuthKind.DataSource, Collections.singletonList(path));
        List<DmResAuthDO> subAuth = this.resAuthMapper.queryByLikePath(dsId, uid, AuthKind.DataSource, path);

        parentAndSelfAuth = parentAndSelfAuth.stream().filter(auth -> {
            return auth.getAuthLabels().contains(DM_DAUTH_QUERY) && auth.isEffective();
        }).collect(Collectors.toList());

        subAuth = subAuth.stream().filter(auth -> {
            return auth.getAuthLabels().contains(DM_DAUTH_QUERY) && auth.isEffective();
        }).collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(parentAndSelfAuth)) {
            return new ResourceAccessInfo(true);
        }

        Set<String> allowQueryList = new HashSet<>();
        int size = levels.levels().size() - 1;
        for (DmResAuthDO resAuthDO : subAuth) {
            switch (size) {
                case 1: {
                    if (resAuthDO.getLevelOne() != null) {
                        allowQueryList.add(resAuthDO.getLevelOne());
                    }
                    break;
                }
                case 2: {
                    if (resAuthDO.getLevelTwo() != null) {
                        allowQueryList.add(resAuthDO.getLevelTwo());
                    }
                    break;
                }
                case 3: {
                    if (resAuthDO.getLevelThree() != null) {
                        allowQueryList.add(resAuthDO.getLevelThree());
                    }
                    break;
                }
            }
        }

        return new ResourceAccessInfo(allowQueryList);
    }

    @Override
    public AuthInfo getAuthInfo(String authKey) {
        return this.authServiceForManage.getAuthLabel(authKey);
    }
}
