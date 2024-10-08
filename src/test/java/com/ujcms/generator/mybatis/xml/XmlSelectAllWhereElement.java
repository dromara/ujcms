package com.ujcms.generator.mybatis.xml;

import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.AbstractXmlElementGenerator;

public class XmlSelectAllWhereElement extends AbstractXmlElementGenerator {
    public XmlSelectAllWhereElement() {
        super();
    }

    @Override
    public void addElements(XmlElement parentElement) {
        XmlElement answer = new XmlElement("sql");
        answer.addAttribute(new Attribute("id", "selectAllWhere"));
        // context.getCommentGenerator().addComment(answer);
        if (context.getPlugins().sqlMapSelectAllElementGenerated(answer, introspectedTable)) {
            parentElement.addElement(answer);
        }
    }
}
