package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.base.ImportDataBase;

import java.io.Serializable;

/**
 * 导入数据 实体类
 *
 * @author PONY
 */
public class ImportData extends ImportDataBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String TABLE_CHANNEL = "ujcms_channel";
    public static final String TABLE_ARTICLE = "ujcms_article";

    /**
     * 类型：数据迁移。从其它系统中迁移数据
     */
    public static final Short TYPE_MIGRATION = 1;
    /**
     * 类型：数据导入。
     */
    public static final Short TYPE_IMPORT = 2;

    public static final String NOT_FOUND = "ImportData not found. ID: ";
}