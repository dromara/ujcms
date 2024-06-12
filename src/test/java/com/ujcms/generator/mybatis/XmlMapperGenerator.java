package com.ujcms.generator.mybatis;

import com.ujcms.generator.mybatis.xml.*;
import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.Document;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.AbstractXmlGenerator;
import org.mybatis.generator.codegen.XmlConstants;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.AbstractXmlElementGenerator;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.DeleteByPrimaryKeyElementGenerator;

import static org.mybatis.generator.internal.util.messages.Messages.getString;

public class XmlMapperGenerator extends AbstractXmlGenerator {

    public XmlMapperGenerator() {
        super();
    }

    protected XmlElement getSqlMapElement() {
        FullyQualifiedTable table = introspectedTable.getFullyQualifiedTable();
        progressCallback.startTask(getString("Progress.12", table.toString())); //$NON-NLS-1$
        XmlElement answer = new XmlElement("mapper");
        String namespace = introspectedTable.getMyBatis3SqlMapNamespace();
        answer.addAttribute(new Attribute("namespace", namespace));

        context.getCommentGenerator().addRootComment(answer);

        addResultMapWithoutBLOBsElement(answer);
        addResultMapWithBLOBsElement(answer);
        addResultMapElement(answer);

        if("true".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("order"))) {
            AbstractXmlElementGenerator moveUpGenerator = new XmlMoveUpElement();
            initializeAndExecuteGenerator(moveUpGenerator, answer);

            AbstractXmlElementGenerator moveDownGenerator = new XmlMoveDownElement();
            initializeAndExecuteGenerator(moveDownGenerator, answer);

            AbstractXmlElementGenerator updateOrderGenerator = new XmlUpdateOrderElement();
            initializeAndExecuteGenerator(updateOrderGenerator, answer);
        }

        addSelectAllSelectElement(answer);
        addSelectAllJoinElement(answer);
        addSelectAllWhereElement(answer);
        addSelectAllElement(answer);
        addSelectByPrimaryKeyElement(answer);
        addDeleteByPrimaryKeyElement(answer);
        addUpdateElement(answer);
        addInsertElement(answer);
        addColumnListElement(answer);

        return answer;
    }

    protected void addResultMapWithoutBLOBsElement(XmlElement parentElement) {
        if (introspectedTable.getRules().generateBaseResultMap()) {
            AbstractXmlElementGenerator elementGenerator = new XmlBaseResultMapElement();
            initializeAndExecuteGenerator(elementGenerator, parentElement);
        }
    }

    protected void addResultMapWithBLOBsElement(XmlElement parentElement) {
        if (introspectedTable.getRules().generateBaseResultMap()) {
            AbstractXmlElementGenerator elementGenerator = new XmlBlobResultMapElement();
            initializeAndExecuteGenerator(elementGenerator, parentElement);
        }
    }

    protected void addResultMapElement(XmlElement parentElement) {
        if (introspectedTable.getRules().generateBaseResultMap()) {
            AbstractXmlElementGenerator elementGenerator = new XmlResultMapElement();
            initializeAndExecuteGenerator(elementGenerator, parentElement);
        }
    }

    protected void addSelectByPrimaryKeyElement(XmlElement parentElement) {
        if (introspectedTable.getRules().generateSelectByPrimaryKey()) {
            AbstractXmlElementGenerator elementGenerator = new XmlSelectElement();
            initializeAndExecuteGenerator(elementGenerator, parentElement);
        }
    }

    protected void addSelectAllSelectElement(XmlElement parentElement) {
        AbstractXmlElementGenerator elementGenerator = new XmlSelectAllSelectElement();
        initializeAndExecuteGenerator(elementGenerator, parentElement);
    }

    protected void addSelectAllJoinElement(XmlElement parentElement) {
        AbstractXmlElementGenerator elementGenerator = new XmlSelectAllJoinElement();
        initializeAndExecuteGenerator(elementGenerator, parentElement);
    }

    protected void addSelectAllWhereElement(XmlElement parentElement) {
        AbstractXmlElementGenerator elementGenerator = new XmlSelectAllWhereElement();
        initializeAndExecuteGenerator(elementGenerator, parentElement);
    }

    protected void addSelectAllElement(XmlElement parentElement) {
        AbstractXmlElementGenerator elementGenerator = new XmlSelectAllElement();
        initializeAndExecuteGenerator(elementGenerator, parentElement);
    }

    protected void addDeleteByPrimaryKeyElement(XmlElement parentElement) {
        if (introspectedTable.getRules().generateDeleteByPrimaryKey()) {
            AbstractXmlElementGenerator elementGenerator = new DeleteByPrimaryKeyElementGenerator(true);
            initializeAndExecuteGenerator(elementGenerator, parentElement);
        }
    }

    protected void addInsertElement(XmlElement parentElement) {
        if (introspectedTable.getRules().generateInsert()) {
            AbstractXmlElementGenerator elementGenerator = new XmlInsertElement(true);
            initializeAndExecuteGenerator(elementGenerator, parentElement);
        }
    }

    protected void addUpdateElement(XmlElement parentElement) {
        if (introspectedTable.getRules().generateUpdateByPrimaryKeySelective()) {
            AbstractXmlElementGenerator elementGenerator = new XmlUpdateElement(true);
            initializeAndExecuteGenerator(elementGenerator, parentElement);
        }
    }

    protected void addColumnListElement(XmlElement parentElement) {
        if (introspectedTable.getRules().generateBaseColumnList()) {
            AbstractXmlElementGenerator elementGenerator = new XmlColumnListElement();
            initializeAndExecuteGenerator(elementGenerator, parentElement);
        }
    }

    protected void initializeAndExecuteGenerator(AbstractXmlElementGenerator elementGenerator, XmlElement parentElement) {
        elementGenerator.setContext(context);
        elementGenerator.setIntrospectedTable(introspectedTable);
        elementGenerator.setProgressCallback(progressCallback);
        elementGenerator.setWarnings(warnings);
        elementGenerator.addElements(parentElement);
    }

    @Override
    public Document getDocument() {
        Document document = new Document(XmlConstants.MYBATIS3_MAPPER_PUBLIC_ID, XmlConstants.MYBATIS3_MAPPER_SYSTEM_ID);
        document.setRootElement(getSqlMapElement());

        if (!context.getPlugins().sqlMapDocumentGenerated(document, introspectedTable)) {
            document = null;
        }

        return document;
    }
}
