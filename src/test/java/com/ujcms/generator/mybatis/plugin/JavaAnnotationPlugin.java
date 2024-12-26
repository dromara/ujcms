package com.ujcms.generator.mybatis.plugin;

import org.apache.commons.lang3.StringUtils;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.Field;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.api.dom.java.Interface;
import org.mybatis.generator.api.dom.java.Method;
import org.mybatis.generator.api.dom.java.TopLevelClass;
import org.mybatis.generator.config.JavaModelGeneratorConfiguration;

import java.sql.Types;
import java.util.List;

public class JavaAnnotationPlugin extends PluginAdapter {

    @Override
    public boolean validate(List<String> warnings) {
        return true;
    }

    @Override
    public boolean clientGenerated(Interface interfaze, IntrospectedTable introspectedTable) {
        if (introspectedTable.getTargetRuntime() == IntrospectedTable.TargetRuntime.MYBATIS3) {
            // don't need to do this for MYBATIS3_DSQL as that runtime already adds this annotation
            interfaze.addImportedType(new FullyQualifiedJavaType("org.apache.ibatis.annotations.Mapper"));
            interfaze.addAnnotation("@Mapper");
            interfaze.addImportedType(new FullyQualifiedJavaType("org.springframework.stereotype.Repository"));
            interfaze.addAnnotation("@Repository");
        }
        return true;
    }

    @Override
    public boolean clientSelectByPrimaryKeyMethodGenerated(Method method, Interface interfaze, IntrospectedTable introspectedTable) {
        interfaze.addImportedType(new FullyQualifiedJavaType("org.springframework.lang.Nullable"));
        method.addAnnotation("@Nullable");

        if("true".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("order"))) {
            method.addAnnotation("@Override");
        }
        return true;
    }

    @Override
    public boolean modelBaseRecordClassGenerated(TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        return true;
    }

    @Override
    public boolean modelFieldGenerated(Field field, TopLevelClass topLevelClass, IntrospectedColumn introspectedColumn,
                                       IntrospectedTable introspectedTable, ModelClassType modelClassType) {
        int jdbcType = introspectedColumn.getJdbcType();
        // is开头的认为是boolean类型，java类型中不要is。
        // 目前使用char(1)代替布尔值，而不适用tinyint，因为最常用的数据库PostgreSQL没有这个字段，这将导致无法针对该数据库做原生开发。
        // char(1)代替boolean值输入会和需要使用char(1)字段类型的情况发送冲突。
        // 需要使用char(1)字段的情况非常罕见，大部分其实都是枚举型，应该使用smallint代替，比如性别。
        // 即使真的需要char(1)，也可以用varchar(1)代替，虽然会多占一些空间，但这种情况实属罕见，无需在意。
        // 数据库的boolean字段用is开头，是很多数据库设计者的习惯，作用是可以很快的看出哪个字段是boolean值，特别是使用char(1)作为boolean值的情况。
        // 但前面说过，char(1)就是boolean值的代替，其它需要使用一个字符的情况用varchar(1)代替。
        // 而不加is更符合java习惯，java和数据库习惯统一，是更好的选择，不用来回切换习惯和适配。
        // if (introspectedColumn.getActualColumnName().startsWith("is_")) {
        //     field.setName(Strings.toLowerCase(String.valueOf(field.getName().charAt(2))) + field.getName().substring(3));
        // }
        // 有默认值的，给默认值
        if (introspectedColumn.getDefaultValue() != null) {
            String defaultValue = introspectedColumn.getDefaultValue();
            switch (jdbcType) {
                // 字符串需加引号
                case Types.CHAR:
                    if (introspectedColumn.getLength() == 1) {
                        // postgresql 的 defaultValue 值为 '0'::bpchar
                        defaultValue = "0".equals(defaultValue) || "'0'::bpchar".equals(defaultValue)
                                ? "false" : "true";
                    } else {
                        defaultValue = "\"" + defaultValue + "\"";
                    }
                    break;
                case Types.NCHAR:
                case Types.VARCHAR:
                case Types.NVARCHAR:
                    defaultValue = "\"" + defaultValue + "\"";
                    break;
                // tinyint、bit 作为boolean型
                case Types.TINYINT:
                case Types.BIT:
                case Types.BOOLEAN:
                    defaultValue = "0".equalsIgnoreCase(defaultValue) ? "false" : "true";
                    break;
                case Types.BIGINT:
                    defaultValue = defaultValue + "L";
                    break;
                case Types.DECIMAL:
                    String javaType = introspectedColumn.getFullyQualifiedJavaType().getFullyQualifiedName();
                    if ("java.lang.Long".equals(javaType)) {
                        defaultValue = defaultValue + "L";
                    } else if ("java.math.BigDecimal".equals(javaType)) {
                        defaultValue = "new java.math.BigDecimal(\"" + defaultValue + "\")";
                    }
                    break;
                case Types.DATE:
                case Types.TIMESTAMP:
                case Types.TIME:
                case Types.TIMESTAMP_WITH_TIMEZONE:
                case Types.TIME_WITH_TIMEZONE:
                    if (defaultValue.equalsIgnoreCase("current_timestamp")) {
                        defaultValue = "OffsetDateTime.now()";
                    }
                default:
            }
            field.setInitializationString(defaultValue);
        }
        // 字符串增加 @Length 校验注解
        if ((jdbcType == Types.CHAR && introspectedColumn.getLength() != 1)
                || jdbcType == Types.NCHAR || jdbcType == Types.VARCHAR || jdbcType == Types.NVARCHAR) {
            int length = introspectedColumn.getLength();
            // 长度能被 3 整除，且没有个位，代表用于存储中文字符串。为兼容以字节为长度的数据库，长度需除于3（3个字节一个汉字）
            if (length % 3 == 0 && length % 10 == 0) {
                length /= 3;
            }
            topLevelClass.addImportedType(new FullyQualifiedJavaType("org.hibernate.validator.constraints.Length"));
            field.addAnnotation("@Length(max = " + length + ")");
        }

        if (introspectedColumn.isNullable()) {
            // 可为空的，给 @Nullable 注解
            topLevelClass.addImportedType(new FullyQualifiedJavaType("org.springframework.lang.Nullable"));
            field.addAnnotation("@Nullable");
        } else {
            // 不可为空的，给 @NotNull 校验注解
            topLevelClass.addImportedType(new FullyQualifiedJavaType("javax.validation.constraints.NotNull"));
            field.addAnnotation("@NotNull");
        }

        // 不可为空的且没有默认值的
        if (!introspectedColumn.isNullable() && introspectedColumn.getDefaultValue() == null) {
            switch (jdbcType) {
                case Types.TIMESTAMP:
                case Types.TIMESTAMP_WITH_TIMEZONE:
                    // 日期型用当前时间为默认值
                    topLevelClass.addImportedType(new FullyQualifiedJavaType("java.time.OffsetDateTime"));
                    field.setInitializationString("OffsetDateTime.now()");
                    break;
                case Types.BIT:
                case Types.TINYINT:
                    // 布尔值
                    field.setInitializationString("false");
                    break;
                case Types.SMALLINT:
                case Types.INTEGER:
                    // 整形、长整型用0作为默认值(一般为主键)
                    field.setInitializationString("0");
                    break;
                case Types.BIGINT:
                    // 整形、长整型用0作为默认值(一般为主键)
                    field.setInitializationString("0L");
                    break;
                case Types.DECIMAL:
                    String javaType = introspectedColumn.getFullyQualifiedJavaType().getFullyQualifiedName();
                    if ("java.lang.Long".equals(javaType)) {
                        field.setInitializationString("0L");
                    } else if ("java.math.BigDecimal".equals(javaType)) {
                        field.setInitializationString("new java.math.BigDecimal(0)");
                    } else {
                        field.setInitializationString("0");
                    }
                    break;
                case Types.CHAR:
                case Types.NCHAR:
                case Types.VARCHAR:
                case Types.NVARCHAR:
                    // 字符串用空串为默认值
                    field.setInitializationString("\"\"");
                    break;
            }
        }

        JavaModelGeneratorConfiguration config = context.getJavaModelGeneratorConfiguration();
        if ("true".equalsIgnoreCase(config.getProperty("swagger"))) {
            topLevelClass.addImportedType(new FullyQualifiedJavaType("io.swagger.v3.oas.annotations.media.Schema"));
        }
        String remark = introspectedColumn.getRemarks();
        if (StringUtils.isNotBlank(remark)) {
            field.addJavaDocLine("/**");
            field.addJavaDocLine(" * " + introspectedColumn.getRemarks());
            field.addJavaDocLine(" */");
            if ("true".equalsIgnoreCase(config.getProperty("swagger"))) {
                field.addAnnotation("@Schema(description=\"" + introspectedColumn.getRemarks() + "\")");
            }
        }
        return true;
    }

    @Override
    public boolean modelGetterMethodGenerated(Method method, TopLevelClass topLevelClass, IntrospectedColumn introspectedColumn,
                                              IntrospectedTable introspectedTable, ModelClassType modelClassType) {
        // is开头的认为是boolean类型，java类型中不要is。
        // if (introspectedColumn.getActualColumnName().startsWith("is_")) {
        //     field.setName(Strings.toLowerCase(String.valueOf(field.getName().charAt(2))) + field.getName().substring(3));
        // }
        if (introspectedColumn.isNullable()) {
            topLevelClass.addImportedType(new FullyQualifiedJavaType("org.springframework.lang.Nullable"));
            method.addAnnotation("@Nullable");
        }
        return true;
    }

    @Override
    public boolean modelSetterMethodGenerated(Method method, TopLevelClass topLevelClass, IntrospectedColumn introspectedColumn,
                                              IntrospectedTable introspectedTable, ModelClassType modelClassType) {
        if (introspectedColumn.isNullable()) {
            topLevelClass.addImportedType(new FullyQualifiedJavaType("org.springframework.lang.Nullable"));
            method.getParameters().forEach(parameter -> parameter.addAnnotation("@Nullable"));
        }
        return true;
    }
}
