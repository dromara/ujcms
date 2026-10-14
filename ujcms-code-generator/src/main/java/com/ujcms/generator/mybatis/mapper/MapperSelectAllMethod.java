/*
 *    Copyright 2006-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
/*
 * Modified for UJCMS by 南昌蓝智科技有限公司 (https://www.ujcms.com). Derived from MyBatis Generator 1.4.2:
 * org.mybatis.generator.codegen.mybatis3.javamapper.elements.SelectAllMethodGenerator.
 * First modified before 2026-04-19, the start of this repository's history; later changes are in the VCS log.
 */
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
        importedTypes.add(new FullyQualifiedJavaType("com.ujcms.common.query.QueryInfo"));
        importedTypes.add(new FullyQualifiedJavaType("org.apache.ibatis.annotations.Param"));
        importedTypes.add(new FullyQualifiedJavaType("org.jspecify.annotations.Nullable"));

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
