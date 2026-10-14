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
 * package org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.
 * First modified before 2026-04-19, the start of this repository's history; later changes are in the VCS log.
 */
package com.ujcms.generator.mybatis.xml;

import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.AbstractXmlElementGenerator;

public class XmlResultMapElement extends AbstractXmlElementGenerator {
    public XmlResultMapElement() {
        super();
    }

    @Override
    public void addElements(XmlElement parentElement) {
        XmlElement answer = new XmlElement("resultMap"); //$NON-NLS-1$

        answer.addAttribute(new Attribute("id", "ResultMap"));

        String returnType;
        // if (introspectedTable.getRules().generateRecordWithBLOBsClass()) {
        //     returnType = introspectedTable.getRecordWithBLOBsType();
        // } else {
        //     // table has BLOBs, but no BLOB class - BLOB fields must be in the base class
        //     returnType = introspectedTable.getBaseRecordType();
        // }

        returnType = introspectedTable.getBaseRecordType();

        answer.addAttribute(new Attribute("type", returnType));

        if (!introspectedTable.isConstructorBased()) {
            answer.addAttribute(new Attribute("extends", introspectedTable.getResultMapWithBLOBsId()));
        }

        context.getCommentGenerator().addComment(answer);

        // 用于占位，无需生成result。
        // if (introspectedTable.isConstructorBased()) {
        //     addResultMapConstructorElements(answer);
        // } else {
        //     addResultMapElements(answer);
        // }

        if (context.getPlugins().sqlMapResultMapWithBLOBsElementGenerated(answer, introspectedTable)) {
            parentElement.addElement(answer);
        }
    }

    private Attribute generateColumnAttribute(IntrospectedColumn introspectedColumn) {
        return new Attribute("column", MyBatis3FormattingUtilities.getRenamedColumnNameForResultMap(introspectedColumn));
    }
}
