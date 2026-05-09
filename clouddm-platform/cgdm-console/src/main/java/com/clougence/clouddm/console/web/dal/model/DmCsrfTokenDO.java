package com.clougence.clouddm.console.web.dal.model;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName(value = "dm_csrf_token")
public class DmCsrfTokenDO {

    @TableId(type = IdType.AUTO)
    private Long   id;

    @TableField(insertStrategy = FieldStrategy.NOT_NULL, updateStrategy = FieldStrategy.NOT_NULL)
    private Date   gmtCreate;

    @TableField(insertStrategy = FieldStrategy.NOT_NULL, updateStrategy = FieldStrategy.NOT_NULL)
    private Date   gmtModified;

    private String token;

    private String jumpUrl;

    private String secretToken;
}
