package com.ujcms.generator.mybatis.mapper;

import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.codegen.mybatis3.javamapper.elements.AbstractJavaMapperMethodGenerator;

import java.util.Set;
import java.util.TreeSet;

public class MapperSelectAllMethod extends AbstractJavaMapperMethodGenerator {

    public MapperSelectAllMethod() {
        super();
    }

    @Override
    public void addInterfaceElements(Interface interfaze) {
        Set<FullyQualifiedJavaType> importedTypes = new TreeSet<>();
        importedTypes.add(FullyQualifiedJavaType.getNewListInstance());
        importedTypes.add(new FullyQualifiedJavaType("com.ujcms.commons.query.QueryInfo"));
        importedTypes.add(new FullyQualifiedJavaType("org.apache.ibatis.annotations.Param"));
        importedTypes.add(new FullyQualifiedJavaType("org.springframework.lang.Nullable"));

        Method method = new Method(introspectedTable.getSelectAllStatementId());

        method.addJavaDocLine("/**");
        method.addJavaDocLine(" * 根据查询条件获取列表");
        method.addJavaDocLine(" *");
        method.addJavaDocLine(" * @param queryInfo 查询条件");
        method.addJavaDocLine(" * @return 数据列表");
        method.addJavaDocLine(" */");

        method.setVisibility(JavaVisibility.PUBLIC);
        method.setAbstract(true);

        FullyQualifiedJavaType returnType = FullyQualifiedJavaType.getNewListInstance();
        FullyQualifiedJavaType listType;
        listType = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());

        importedTypes.add(listType);
        returnType.addTypeArgument(listType);
        method.setReturnType(returnType);

        Parameter parameterType = new Parameter(
                new FullyQualifiedJavaType("QueryInfo"), "queryInfo");
        parameterType.addAnnotation("@Nullable @Param(\"queryInfo\")");
        method.addParameter(parameterType);

        context.getCommentGenerator().addGeneralMethodComment(method, introspectedTable);

        addMapperAnnotations(interfaze, method);

        if (context.getPlugins().clientSelectAllMethodGenerated(method, interfaze, introspectedTable)) {
            addExtraImports(interfaze);
            interfaze.addImportedTypes(importedTypes);
            interfaze.addMethod(method);
        }
    }

    public void addMapperAnnotations(Interface interfaze, Method method) {
        // extension point for subclasses
    }

    public void addExtraImports(Interface interfaze) {
        // extension point for subclasses
    }
}
