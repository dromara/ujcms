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
 * org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.SimpleSelectAllElementGenerator.
 * First modified before 2026-04-19, the start of this repository's history; later changes are in the VCS log.
 */
package com.ujcms.generator.mybatis.xml;

import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.AbstractXmlElementGenerator;
import org.mybatis.generator.config.PropertyRegistry;
import org.mybatis.generator.internal.util.StringUtility;

public class XmlSelectAllElement extends AbstractXmlElementGenerator {

    public XmlSelectAllElement() {
        super();
    }

    @Override
    public void addElements(XmlElement parentElement) {
        XmlElement answer = new XmlElement("select"); //$NON-NLS-1$

        answer.addAttribute(new Attribute("id", introspectedTable.getSelectAllStatementId())); //$NON-NLS-1$
        answer.addAttribute(new Attribute("resultMap", "ResultMap"));

        context.getCommentGenerator().addComment(answer);

        StringBuilder sb = new StringBuilder();
        // sb.append("select ");
        // answer.addElement(new TextElement(sb.toString()));

        answer.addElement(getSelectAllElement());
        // 使用BaseColumnList
        // Iterator<IntrospectedColumn> iter = introspectedTable.getAllColumns().iterator();
        // while (iter.hasNext()) {
        //     sb.append(MyBatis3FormattingUtilities.getSelectListPhrase(iter.next()));
        //
        //     if (iter.hasNext()) {
        //         sb.append(", "); //$NON-NLS-1$
        //     }
        //
        //     if (sb.length() > 80) {
        //         answer.addElement(new TextElement(sb.toString()));
        //         sb.setLength(0);
        //     }
        // }

        // if (sb.length() > 0) {
        //     answer.addElement(new TextElement(sb.toString()));
        // }

        // sb.setLength(0);
        // sb.append("from "); //$NON-NLS-1$
        // sb.append(introspectedTable.getAliasedFullyQualifiedTableNameAtRuntime());
        // sb.append(" t");
        // answer.addElement(new TextElement(sb.toString()));

        String orderByClause = introspectedTable.getTableConfigurationProperty(PropertyRegistry.TABLE_SELECT_ALL_ORDER_BY_CLAUSE);
        boolean hasOrderBy = StringUtility.stringHasValue(orderByClause);
        if (hasOrderBy) {
            sb.setLength(0);
            sb.append("order by "); //$NON-NLS-1$
            sb.append(orderByClause);
            answer.addElement(new TextElement(sb.toString()));
        }

        if (context.getPlugins().sqlMapSelectAllElementGenerated(answer, introspectedTable)) {
            parentElement.addElement(answer);
        }
    }

    protected XmlElement getSelectAllElement() {
        XmlElement answer = new XmlElement("include");
        answer.addAttribute(new Attribute("refid", "com.ujcms.cms.core.mapper.SeqMapper.Select_All"));

        XmlElement property = new XmlElement("property");
        property.addAttribute(new Attribute("name", "tableName"));
        property.addAttribute(new Attribute("value", introspectedTable.getAliasedFullyQualifiedTableNameAtRuntime()));
        answer.addElement(property);

        return answer;
    }
}
