package com.clougence.clouddm.init.model;

import lombok.Data;

/**
 * 初始化配置字段定义。
 * 字段 schema 来源于 init-fields.json，字段值来源于运行时 classpath *.properties。
 */
@Data
public class InitFieldDef {

    /** 配置键名，对应 .properties 文件中的 key */
    private String  propertyKey;

    /** 从运行时配置文件读取的当前值 */
    private String  defaultValue;

    /** 分类：database / security / connectivity */
    private String  category;

    /** 输入类型：text / password / number */
    private String  inputType;

    /** 是否必填 */
    private boolean required;

    /** 前端表单标签 */
    private String  label;

    /** 字段说明 */
    private String  description;
}
