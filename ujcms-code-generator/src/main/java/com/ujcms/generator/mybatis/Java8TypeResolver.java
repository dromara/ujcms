package com.ujcms.generator.mybatis;

import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.internal.types.JavaTypeResolverDefaultImpl;

import java.sql.Types;

public class Java8TypeResolver extends JavaTypeResolverDefaultImpl {
    public Java8TypeResolver() {
        super();
        // SMALLINT 最大 32767。用于 order 等不需要太大的整数，节省存储空间。为了方便 Java 代码编写，作为Short处理。
        // typeMap.put(Types.SMALLINT, new JdbcTypeInformation("SMALLINT",
        //   new FullyQualifiedJavaType(Short.class.getName())));
        // TINYINT 最大 127。用于 status 等状态字段，节省存储空间。为了方便 Java 代码编写，作为Short处理。
        // 使用 TINYINT(1) 或 BIT(1) 作为 boolean 问题太多。应该使用 TINYINT 作为 boolean。
        typeMap.put(Types.TINYINT,
                new JdbcTypeInformation("BOOLEAN", new FullyQualifiedJavaType(Boolean.class.getName())));
        typeMap.put(Types.BIT,
                new JdbcTypeInformation("BOOLEAN", new FullyQualifiedJavaType(Boolean.class.getName())));
        // PostgreSQL 的 jsonb 数据类型时 OTHER 类型，按 MySQL 的 LONGVARCHAR 处理。
        typeMap.put(Types.OTHER,
                new JdbcTypeInformation("LONGVARCHAR", new FullyQualifiedJavaType(String.class.getName())));
        // Oracle、DM 的 Clob 按 MySQL 的 LONGVARCHAR 处理。
        typeMap.put(Types.CLOB,
                new JdbcTypeInformation("LONGVARCHAR", new FullyQualifiedJavaType(String.class.getName())));
        // 默认为 LONGVARCHAR，使用 StringTypeHandler，兼容 PostgreSQL，没有问题
        // typeMap.put(Types.LONGVARCHAR,
        //         new JdbcTypeInformation("CLOB", new FullyQualifiedJavaType(String.class.getName())));
        // typeMap.put(Types.LONGNVARCHAR,
        //         new JdbcTypeInformation("NCLOB", new FullyQualifiedJavaType(String.class.getName())));
    }

    @Override
    protected FullyQualifiedJavaType overrideDefaultType(IntrospectedColumn column, FullyQualifiedJavaType defaultType) {
        FullyQualifiedJavaType answer = super.overrideDefaultType(column, defaultType);
        // char(1)作为Boolean处理
        if (column.getJdbcType() == Types.CHAR && column.getLength() == 1) {
            answer = new FullyQualifiedJavaType(Boolean.class.getName());
        }
        return answer;
    }

    @Override
    protected FullyQualifiedJavaType calculateTimestampType(
            IntrospectedColumn column, FullyQualifiedJavaType defaultType) {
        FullyQualifiedJavaType answer;
        if (useJSR310Types) {
            answer = new FullyQualifiedJavaType("java.time.OffsetDateTime"); //$NON-NLS-1$
        } else {
            answer = defaultType;
        }
        return answer;
    }

}
