package com.ujcms.generator.mybatis.xml;

import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.AbstractXmlElementGenerator;

public class XmlUpdateOrderElement extends AbstractXmlElementGenerator {
    @Override
    public void addElements(XmlElement parentElement) {
        XmlElement answer = new XmlElement("update");

        answer.addAttribute(new Attribute("id", "updateOrder"));

        StringBuilder sb = new StringBuilder();
        sb.append("update ");
        sb.append(introspectedTable.getFullyQualifiedTableNameAtRuntime());
        answer.addElement(new TextElement(sb.toString()));

        sb.setLength(0);
        sb.append("set order_ = #{order}");
        answer.addElement(new TextElement(sb.toString()));

        sb.setLength(0);
        sb.append("where id_ = #{id}");
        answer.addElement(new TextElement(sb.toString()));

        parentElement.addElement(answer);
    }
}
