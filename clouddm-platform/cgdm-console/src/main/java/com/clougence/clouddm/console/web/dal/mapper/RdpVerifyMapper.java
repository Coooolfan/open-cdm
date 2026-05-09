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

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clougence.clouddm.console.web.constants.VerifyCodeType;
import com.clougence.clouddm.console.web.constants.VerifyType;
import com.clougence.clouddm.console.web.dal.enumeration.AreaCode;
import com.clougence.clouddm.console.web.dal.model.RdpVerifyDO;

/**
 * @author bucketli 2020/2/27 19:57
 */
public interface RdpVerifyMapper extends BaseMapper<RdpVerifyDO> {

    /** query by uid ,get all uid's {@link RdpVerifyDO}s */
    List<RdpVerifyDO> queryByUid(String uid);

    RdpVerifyDO queryByUidAndType(VerifyType verifyType, VerifyCodeType verifyCodeType, String uid);

    /** query by email（sql condition contain {@link VerifyType#EMAIL_VERIFY_CODE},{@link VerifyCodeType},email） */
    RdpVerifyDO queryByPrimaryEmail(VerifyType verifyType, VerifyCodeType verifyCodeType, String email);

    /** query by phone (sql condition contain {@link VerifyType#SMS_VERIFY_CODE},{@link VerifyCodeType},phone) */
    RdpVerifyDO queryByPrimaryPhone(VerifyType verifyType, VerifyCodeType verifyCodeType, String phone);

    RdpVerifyDO queryByPrimaryPhoneAndAreaCode(VerifyType verifyType, VerifyCodeType verifyCodeType, String phone, AreaCode areaCode);

    /** update failTimes by {@link RdpVerifyDO id} */
    void updateFailTimesAndDateById(Integer failTimes, Date failDate, Long id);

    /** update sent verify code and time by {@link RdpVerifyDO id} */
    int updateVerifyCodeAndSendTime(String verifyCode, Date sendTime, Long id);

    void deleteByUid(String uid);

    /** query by uid ,get all uid's {@link RdpVerifyDO}s */
    void deleteOldData(Date beforeThisTime);

    void updatePhoneOrEmailByUid(String uid, String phone, String email, VerifyCodeType verifyCodeType, VerifyType verifyType);
}
