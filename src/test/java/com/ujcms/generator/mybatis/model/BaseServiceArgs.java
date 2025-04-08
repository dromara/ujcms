package com.ujcms.generator.mybatis.model;

import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.codegen.AbstractJavaGenerator;

import java.util.ArrayList;
import java.util.List;

import static org.mybatis.generator.internal.util.messages.Messages.getString;

public class BaseServiceArgs extends AbstractJavaGenerator {
    public BaseServiceArgs(String project) {
        super(project);
    }

    protected String getJavaType() {
        String recordType = introspectedTable.getBaseRecordType();
        int end = recordType.lastIndexOf(".");
        int begin = recordType.lastIndexOf(".", end - 1);
        return recordType.substring(0, begin) + ".service.args" + recordType.substring(end) + "Args";
    }

    protected String getModelType() {
        return new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()).getShortName();
    }

    protected String getJavaTypeShortName() {
        return getModelType() + "Args";
    }

    @Override
    public List<CompilationUnit> getCompilationUnits() {
        FullyQualifiedTable table = introspectedTable.getFullyQualifiedTable();
        progressCallback.startTask(getString("Progress.8", table.toString()));
        FullyQualifiedJavaType type = new FullyQualifiedJavaType(getJavaType());
        TopLevelClass topLevelClass = new TopLevelClass(type);
        topLevelClass.setVisibility(JavaVisibility.PUBLIC);
        topLevelClass.setSuperClass(new FullyQualifiedJavaType("BaseQueryArgs"));

        topLevelClass.addJavaDocLine("/**");
        topLevelClass.addJavaDocLine(" * " + getModelType() + " 查询参数");
        topLevelClass.addJavaDocLine(" *");
        topLevelClass.addJavaDocLine(" * @author Generator");
        topLevelClass.addJavaDocLine(" */");

        // 公共import
        topLevelClass.addImportedType("com.ujcms.commons.query.BaseQueryArgs");
        topLevelClass.addImportedType("java.util.HashMap");
        topLevelClass.addImportedType("java.util.Map");
        // 构造器
        Method constructor = new Method(topLevelClass.getType().getShortName());
        constructor.setConstructor(true);
        constructor.setVisibility(JavaVisibility.PRIVATE);
        FullyQualifiedJavaType mapType = new FullyQualifiedJavaType("Map");
        mapType.addTypeArgument(new FullyQualifiedJavaType("String"));
        mapType.addTypeArgument(new FullyQualifiedJavaType("Object"));
        Parameter queryMapParam = new Parameter(mapType, "queryMap");
        constructor.addParameter(queryMapParam);
        constructor.addBodyLine("super(queryMap);");
        topLevelClass.addMethod(constructor);
        // of 方法
        Method of = new Method("of");
        of.setVisibility(JavaVisibility.PUBLIC);
        of.setStatic(true);
        of.setReturnType(new FullyQualifiedJavaType(getJavaTypeShortName()));
        of.addBodyLine("return of(new HashMap<>(16));");
        topLevelClass.addMethod(of);
        // of Map 方法
        Method ofMap = new Method("of");
        ofMap.setVisibility(JavaVisibility.PUBLIC);
        ofMap.setStatic(true);
        ofMap.addParameter(queryMapParam);
        ofMap.setReturnType(new FullyQualifiedJavaType(getJavaTypeShortName()));
        ofMap.addBodyLine("return new " + getJavaTypeShortName() + "(queryMap);");
        topLevelClass.addMethod(ofMap);

        List<CompilationUnit> answer = new ArrayList<>();
        if (!"false".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("service"))
        && !"false".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("serviceArgs"))) {
            answer.add(topLevelClass);
        }
        return answer;
    }
}
