package com.ujcms.generator.mybatis.xml;

import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.AbstractXmlElementGenerator;

import java.util.Iterator;

public class XmlColumnListElement extends AbstractXmlElementGenerator {

    public XmlColumnListElement() {
        super();
    }

    @Override
    public void addElements(XmlElement parentElement) {
        XmlElement answer = new XmlElement("sql"); //$NON-NLS-1$

        answer.addAttribute(new Attribute("id", introspectedTable.getBaseColumnListId()));

        context.getCommentGenerator().addComment(answer);

        StringBuilder sb = new StringBuilder();
        Iterator<IntrospectedColumn> iter = introspectedTable.getAllColumns().iterator();
        while (iter.hasNext()) {
            sb.append("t.");
            sb.append(MyBatis3FormattingUtilities.getSelectListPhrase(iter.next()));

            if (iter.hasNext()) {
                sb.append(", "); //$NON-NLS-1$
            }

            // if (sb.length() > 80) {
            //     answer.addElement(new TextElement(sb.toString()));
            //     sb.setLength(0);
            // }
        }

        if (sb.length() > 0) {
            answer.addElement(new TextElement(sb.toString()));
        }

        if (context.getPlugins().sqlMapBaseColumnListElementGenerated(
                answer, introspectedTable)) {
            parentElement.addElement(answer);
        }
    }
}
