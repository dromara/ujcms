package com.ujcms.generator.mybatis.model;

import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.codegen.AbstractJavaGenerator;

import java.util.ArrayList;
import java.util.List;

import static org.mybatis.generator.internal.util.messages.Messages.getString;

public class BaseModel extends AbstractJavaGenerator {

    public BaseModel(String project) {
        super(project);
    }

    @Override
    public List<CompilationUnit> getCompilationUnits() {
        FullyQualifiedTable table = introspectedTable.getFullyQualifiedTable();
        progressCallback.startTask(getString("Progress.8", table.toString())); //$NON-NLS-1$
        FullyQualifiedJavaType type = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
        TopLevelClass topLevelClass = new TopLevelClass(type);
        topLevelClass.setVisibility(JavaVisibility.PUBLIC);

        FullyQualifiedJavaType superClass = getSuperClass();
        topLevelClass.setSuperClass(superClass);
        topLevelClass.addImportedType(superClass);
        topLevelClass.addImportedType(new FullyQualifiedJavaType("java.io.Serializable"));
        topLevelClass.addSuperInterface(new FullyQualifiedJavaType("Serializable"));
        if("true".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("order"))) {
            topLevelClass.addImportedType(new FullyQualifiedJavaType("com.ujcms.commons.db.order.OrderEntity"));
            topLevelClass.addSuperInterface(new FullyQualifiedJavaType("OrderEntity"));
        }

        Field serialVersionField = new Field("serialVersionUID",new FullyQualifiedJavaType("long"));
        serialVersionField.setVisibility(JavaVisibility.PRIVATE);
        serialVersionField.setStatic(true);
        serialVersionField.setFinal(true);
        serialVersionField.setInitializationString("1L");
        topLevelClass.addField(serialVersionField);

        // NotFound 属性
        Field notFound = new Field("NOT_FOUND", new FullyQualifiedJavaType("String"));
        notFound.setVisibility(JavaVisibility.PUBLIC);
        notFound.setStatic(true);
        notFound.setFinal(true);
        notFound.setInitializationString("\"" + getModelType() + " not found. ID: \"");
        topLevelClass.addField(notFound);

        // JavaModelGeneratorConfiguration config = context.getJavaModelGeneratorConfiguration();
        // if ("true".equalsIgnoreCase(config.getProperty("swagger"))) {
            // topLevelClass.addImportedType(new FullyQualifiedJavaType("io.swagger.v3.oas.annotations.media.Schema"));
            // topLevelClass.addAnnotation("@Schema(description=\"" + topLevelClass.getType().getShortName() + "\")");
        // }

        // 无法获取到表的remark，应该是MyBatis生成器有BUG。应该获取TABLE_COMMENT。
        // topLevelClass.addAnnotation("@Schema(description=\"" + introspectedTable.getRemarks() + "\")");

        List<CompilationUnit> answer = new ArrayList<>();
        if (context.getPlugins().modelBaseRecordClassGenerated(topLevelClass, introspectedTable)) {
            answer.add(topLevelClass);
        }
        return answer;
    }

    protected String getModelType() {
        return new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()).getShortName();
    }

    private FullyQualifiedJavaType getSuperClass() {
        String recordType = introspectedTable.getBaseRecordType();
        int len = recordType.lastIndexOf(".");
        return new FullyQualifiedJavaType(recordType.substring(0, len) + ".base" + recordType.substring(len) + "Base");
    }
}
