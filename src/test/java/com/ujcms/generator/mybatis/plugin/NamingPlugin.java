package com.ujcms.generator.mybatis.plugin;

import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;

import java.util.List;

/**
 * 简化MyBatis生成的方法名
 *
 * <li> selectByPrimaryKey -> select
 * <li> UpdateByPrimaryKey -> update
 * <li> deleteByPrimaryKey -> delete
 */
public class NamingPlugin extends PluginAdapter {
    @Override
    public boolean validate(List<String> warnings) {
        return true;
    }

    @Override
    public void initialized(IntrospectedTable introspectedTable) {
        introspectedTable.setDeleteByPrimaryKeyStatementId("delete");
        introspectedTable.setUpdateByPrimaryKeyStatementId("update");
        introspectedTable.setSelectByPrimaryKeyStatementId("select");
        // introspectedTable.setResultMapWithBLOBsId("ResultMap");
        introspectedTable.setBaseColumnListId("Column_List");
    }
}
