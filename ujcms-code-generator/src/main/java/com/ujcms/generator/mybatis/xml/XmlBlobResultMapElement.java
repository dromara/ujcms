package com.ujcms.generator.mybatis.xml;

import org.apache.ibatis.type.JdbcType;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.AbstractXmlElementGenerator;

import static org.mybatis.generator.internal.util.StringUtility.stringHasValue;

public class XmlBlobResultMapElement extends AbstractXmlElementGenerator {

    public XmlBlobResultMapElement() {
        super();
    }

    @Override
    public void addElements(XmlElement parentElement) {
        XmlElement answer = new XmlElement("resultMap"); //$NON-NLS-1$

        answer.addAttribute(new Attribute("id", //$NON-NLS-1$
                introspectedTable.getResultMapWithBLOBsId()));

        String returnType = introspectedTable.getBaseRecordType();

        answer.addAttribute(new Attribute("type", //$NON-NLS-1$
                returnType));

        if (!introspectedTable.isConstructorBased()) {
            answer.addAttribute(new Attribute("extends", //$NON-NLS-1$
                    introspectedTable.getBaseResultMapId()));
        }

        context.getCommentGenerator().addComment(answer);

        if (introspectedTable.isConstructorBased()) {
            addResultMapConstructorElements(answer);
        } else {
            addResultMapElements(answer);
        }

        if (context.getPlugins()
                .sqlMapResultMapWithBLOBsElementGenerated(answer,
                        introspectedTable)) {
            parentElement.addElement(answer);
        }
    }

    private void addResultMapElements(XmlElement answer) {
        for (IntrospectedColumn introspectedColumn : introspectedTable
                .getBLOBColumns()) {
            XmlElement resultElement = new XmlElement("result"); //$NON-NLS-1$
            String property = introspectedColumn.getJavaProperty();
            String jdbcType = introspectedColumn.getJdbcTypeName();
            if (property.endsWith("Json") &&
                    (jdbcType.equals(JdbcType.LONGVARCHAR.name()) || jdbcType.equals(JdbcType.CLOB.name()))) {
                jdbcType = JdbcType.OTHER.name();
            }
            resultElement.addAttribute(generateColumnAttribute(introspectedColumn));
            resultElement.addAttribute(new Attribute(
                    "property", property)); //$NON-NLS-1$
            resultElement.addAttribute(new Attribute(
                    "jdbcType", jdbcType)); //$NON-NLS-1$

            if (stringHasValue(introspectedColumn
                    .getTypeHandler())) {
                resultElement.addAttribute(new Attribute(
                        "typeHandler", introspectedColumn.getTypeHandler())); //$NON-NLS-1$
            }

            answer.addElement(resultElement);
        }
    }

    private void addResultMapConstructorElements(XmlElement answer) {
        XmlElement constructor = new XmlElement("constructor"); //$NON-NLS-1$

        for (IntrospectedColumn introspectedColumn : introspectedTable
                .getPrimaryKeyColumns()) {
            XmlElement resultElement = new XmlElement("idArg"); //$NON-NLS-1$

            resultElement.addAttribute(generateColumnAttribute(introspectedColumn));
            resultElement.addAttribute(new Attribute(
                    "jdbcType", introspectedColumn.getJdbcTypeName())); //$NON-NLS-1$
            resultElement.addAttribute(new Attribute("javaType", //$NON-NLS-1$
                    introspectedColumn.getFullyQualifiedJavaType().getFullyQualifiedName()));

            if (stringHasValue(introspectedColumn
                    .getTypeHandler())) {
                resultElement.addAttribute(new Attribute(
                        "typeHandler", introspectedColumn.getTypeHandler())); //$NON-NLS-1$
            }

            constructor.addElement(resultElement);
        }

        for (IntrospectedColumn introspectedColumn : introspectedTable
                .getNonPrimaryKeyColumns()) {
            XmlElement resultElement = new XmlElement("arg"); //$NON-NLS-1$

            resultElement.addAttribute(generateColumnAttribute(introspectedColumn));
            resultElement.addAttribute(new Attribute(
                    "jdbcType", introspectedColumn.getJdbcTypeName())); //$NON-NLS-1$

            if (introspectedColumn.getFullyQualifiedJavaType().isPrimitive()) {
                // need to use the MyBatis type alias for a primitive byte
                String sb = '_' + introspectedColumn.getFullyQualifiedJavaType().getShortName();
                resultElement.addAttribute(new Attribute("javaType", //$NON-NLS-1$
                        sb));

            } else if ("byte[]".equals(introspectedColumn.getFullyQualifiedJavaType() //$NON-NLS-1$
                    .getFullyQualifiedName())) {
                // need to use the MyBatis type alias for a primitive byte arry
                resultElement.addAttribute(new Attribute("javaType", //$NON-NLS-1$
                        "_byte[]")); //$NON-NLS-1$
            } else {
                resultElement.addAttribute(new Attribute("javaType", //$NON-NLS-1$
                        introspectedColumn.getFullyQualifiedJavaType().getFullyQualifiedName()));
            }

            if (stringHasValue(introspectedColumn
                    .getTypeHandler())) {
                resultElement.addAttribute(new Attribute(
                        "typeHandler", introspectedColumn.getTypeHandler())); //$NON-NLS-1$
            }

            constructor.addElement(resultElement);
        }

        answer.addElement(constructor);
    }

    private Attribute generateColumnAttribute(IntrospectedColumn introspectedColumn) {
        return new Attribute("column", //$NON-NLS-1$
                MyBatis3FormattingUtilities.getRenamedColumnNameForResultMap(introspectedColumn));
    }
}
