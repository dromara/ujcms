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
