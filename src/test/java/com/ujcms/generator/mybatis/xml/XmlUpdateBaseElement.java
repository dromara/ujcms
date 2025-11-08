package com.ujcms.generator.mybatis.xml;

import org.mybatis.generator.api.dom.xml.XmlElement;

/**
 * update 不包含 order_ 字段、JSON 字段、BLOB 字段的更新
 */
public class XmlUpdateBaseElement extends XmlUpdateElement {
    public XmlUpdateBaseElement(boolean isSimple) {
        super(isSimple);
    }

    @Override
    public void addElements(XmlElement parentElement) {
        XmlElement answer = buildUpdateElement(introspectedTable.getUpdateByPrimaryKeySelectiveStatementId(), false);
        if (context.getPlugins().sqlMapUpdateByPrimaryKeyWithoutBLOBsElementGenerated(answer, introspectedTable)) {
            parentElement.addElement(answer);
        }
    }
}
