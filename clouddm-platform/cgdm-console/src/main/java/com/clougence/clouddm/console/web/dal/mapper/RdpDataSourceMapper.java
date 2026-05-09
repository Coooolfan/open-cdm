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
package com.clougence.clouddm.console.web.dal.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clougence.clouddm.base.metadata.ds.DataSourceType;
import com.clougence.clouddm.base.metadata.rdp.enumeration.SecurityType;
import com.clougence.clouddm.console.web.dal.enumeration.DeployEnvType;
import com.clougence.clouddm.console.web.dal.enumeration.LifeCycleState;
import com.clougence.clouddm.console.web.dal.enumeration.SecurityFileStoreType;
import com.clougence.clouddm.console.web.dal.model.RdpDataSourceDO;
import com.clougence.clouddm.console.web.dal.model.queryobj.DsQueryParam;

/**
 * @author wanshao create time is 2019/12/12 9:25 下午
 **/
public interface RdpDataSourceMapper extends BaseMapper<RdpDataSourceDO> {

    List<RdpDataSourceDO> listByUser(String uid);

    List<RdpDataSourceDO> listByUserWithGmtOrder(String uid);

    /** Query the list of data sources based on the primary account */
    List<RdpDataSourceDO> listByUidAndEnvId(@Param("uid") String uid, @Param("envId") String envId);

    /** Query the list of data sources based on the sub account */
    List<RdpDataSourceDO> listByDsIdsAndEnvId(@Param("ids") List<Long> ids, @Param("envId") String envId);

    RdpDataSourceDO queryDsIdentityById(Long id);

    //List<RdpDataSourceDO> listByTypesAndEnv(DataSourceType dataSourceType, DeployEnvType deployEnvType, List<Long> ids, LifeCycleState lifeCycleState, Long dsEnvId);

    List<RdpDataSourceDO> listByDesc(String dataSourceDesc);

    List<RdpDataSourceDO> listByCondition(DsQueryParam dsQueryParam);

    List<RdpDataSourceDO> listByIds(@Param("ids") List<Long> ids);

    List<RdpDataSourceDO> listByIdsIncludeDeleted(@Param("ids") Collection<Long> ids);

    List<RdpDataSourceDO> listByIdsAndType(@Param("ids") List<Long> ids, DataSourceType dataSourceType);

    //@Deprecated
    //List<RdpDataSourceDO> listByTypes(DataSourceType dataSourceType, DeployEnvType deployType, LifeCycleState lifeCycleState);

    //@Deprecated
    //List<RdpDataSourceDO> listByTypesAndIds(DataSourceType dataSourceType, DeployEnvType deployType, List<Long> ids, LifeCycleState lifeCycleState);

    List<RdpDataSourceDO> listByDeployTypesAndIds(DeployEnvType deployType, Set<Long> ids);

    List<RdpDataSourceDO> listDataSourceByHost(String host, String uid);

    List<RdpDataSourceDO> listDataSourceByHosts(String privateHost, String publicHost, String uid);

    List<RdpDataSourceDO> listByParentDsId(Long parentDsId);

    // List<RdpDataSourceDO> listByBindClusterId(long bindClusterId);

    List<RdpDataSourceDO> listByDsEnvId(long dsEnvId);

    //List<RdpDataSourceDO> listUserDsByDefaultHost(String defaultHost, @CanBeReplaced String uid);

    int updateSecurityInfo(Long id, String account, String password, SecurityType securityType);

    int updateSecurityAllInfo(Long id, String account, String password, SecurityType securityType, SecurityFileStoreType storeType, String accessKey, String secretKey,
                              String securityFileUrl, String securityFilePassword, String clientSecurityFileUrl, String clientSecurityFilePassword, String secretFileUrl,
                              String secretFilePassword);

    int updateAccountAndPasswordById(Long id, String account, String password);

    int updateAkAndSk(Long id, String accessKey, String secretKey);

    int updateAliyunRdsAkAndSk(Long id, String accessKey, String secretKey);

    int updateVersionByInstanceId(Long id, String version);

    int updateDescByInstanceId(Long id, String instanceDesc);

    int updatePublicHostByInstanceId(Long id, String publicHost);

    int updatePrivateHostByInstanceId(Long id, String privateHost);

    int updateLifeCycleStateById(Long id, LifeCycleState lifeCycleState);

    int updateConsoleJobId(Long id, Long consoleJobId);

    int updateEnvIdByInstanceId(Long id, long dsEnvId);

    int updateClusterIdByInstanceId(Long id, long bindClusterId);

    //    int updateEnableQueryAndBindClusterId(Long id, boolean enableQuery, long bindClusterId);

    int updateInstanceIdByDataSourceId(Long id, String instanceId);

    int updateHostsByInstanceId(Long id, String defaultHost, String privateHost, String publicHost);

    RdpDataSourceDO getByInstanceId(String instanceId);

    RdpDataSourceDO queryOneByUid(String uid);

    void cleanDataSourceAccount(Long id);

    void updateUid(String srcUid, String dstUid);

    Set<DataSourceType> queryCurrentDsTypes();
}
