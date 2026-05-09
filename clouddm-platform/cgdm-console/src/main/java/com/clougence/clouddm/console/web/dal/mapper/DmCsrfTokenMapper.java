package com.clougence.clouddm.console.web.dal.mapper;

import java.util.Date;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clougence.clouddm.console.web.dal.model.DmCsrfTokenDO;

public interface DmCsrfTokenMapper extends BaseMapper<DmCsrfTokenDO> {

    int deleteByToken(String token);

    int deleteBeforeTime(Date date);

    DmCsrfTokenDO queryByToken(String token);

    int updateToken(String token, DmCsrfTokenDO data);
}
