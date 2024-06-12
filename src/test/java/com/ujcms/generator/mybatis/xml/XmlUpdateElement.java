package com.ujcms.generator.mybatis.xml;

import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.dom.OutputUtilities;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.ListUtilities;
import org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.elements.AbstractXmlElementGenerator;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * update 不包含 order_ 字段的更新
 */
public class XmlUpdateElement extends AbstractXmlElementGenerator {

    private final boolean isSimple;

    public XmlUpdateElement(boolean isSimple) {
        super();
        this.isSimple = isSimple;
    }

    @Override
    public void addElements(XmlElement parentElement) {
        XmlElement answer = new XmlElement("update"); //$NON-NLS-1$

        answer.addAttribute(new Attribute(
                "id", introspectedTable.getUpdateByPrimaryKeyStatementId())); //$NON-NLS-1$
        answer.addAttribute(new Attribute("parameterType", //$NON-NLS-1$
                introspectedTable.getBaseRecordType()));

        context.getCommentGenerator().addComment(answer);

        StringBuilder sb = new StringBuilder();
        sb.append("update "); //$NON-NLS-1$
        sb.append(introspectedTable.getFullyQualifiedTableNameAtRuntime());
        answer.addElement(new TextElement(sb.toString()));

        // set up for first column
        sb.setLength(0);
        sb.append("set "); //$NON-NLS-1$

        List<IntrospectedColumn> columnList;
        if (isSimple) {
            columnList = ListUtilities.removeGeneratedAlwaysColumns(introspectedTable.getNonPrimaryKeyColumns());
        } else {
            columnList = ListUtilities.removeGeneratedAlwaysColumns(introspectedTable.getBaseColumns());
        }

        String updateExcludes = introspectedTable.getTableConfigurationProperty("updateExcludes");
        List<String> updateExcludeList = Optional.ofNullable(updateExcludes)
                .map(it -> it.split(",")).map(Arrays::stream).orElse(Stream.empty())
                .map(String::trim).collect(Collectors.toList());

        // 修改 begin
        columnList = columnList.stream().filter(introspectedColumn -> {
            String columnName = MyBatis3FormattingUtilities.getEscapedColumnName(introspectedColumn);
            String orderColumn = "order_";
            List<String> orderTypes = Collections.singletonList("Long");
            String columnJavaType = introspectedColumn.getFullyQualifiedJavaType().getShortName();
            boolean order = orderColumn.equals(columnName) && orderTypes.contains(columnJavaType);
            return !order && !updateExcludeList.contains(columnName);
        }).collect(Collectors.toList());
        // 修改 end

        for (Iterator<IntrospectedColumn> iter = columnList.iterator(); iter.hasNext(); ) {
            IntrospectedColumn introspectedColumn = iter.next();

            String columnName = MyBatis3FormattingUtilities.getEscapedColumnName(introspectedColumn);
            sb.append(columnName);
            sb.append(" = "); //$NON-NLS-1$
            String parameterClause = MyBatis3FormattingUtilities.getParameterClause(introspectedColumn);
            if (columnName.endsWith("_json_") &&
                    (parameterClause.contains("LONGVARCHAR") || parameterClause.contains("CLOB"))) {
                parameterClause = parameterClause.replace("LONGVARCHAR", "OTHER");
                parameterClause = parameterClause.replace("CLOB", "OTHER");
            }
            sb.append(parameterClause);

            if (iter.hasNext()) {
                sb.append(',');
            }

            answer.addElement(new TextElement(sb.toString()));

            // set up for the next column
            if (iter.hasNext()) {
                sb.setLength(0);
                OutputUtilities.xmlIndent(sb, 1);
            }
        }

        boolean and = false;
        for (IntrospectedColumn introspectedColumn : introspectedTable
                .getPrimaryKeyColumns()) {
            sb.setLength(0);
            if (and) {
                sb.append("  and "); //$NON-NLS-1$
            } else {
                sb.append("where "); //$NON-NLS-1$
                and = true;
            }

            sb.append(MyBatis3FormattingUtilities
                    .getEscapedColumnName(introspectedColumn));
            sb.append(" = "); //$NON-NLS-1$
            sb.append(MyBatis3FormattingUtilities
                    .getParameterClause(introspectedColumn));
            answer.addElement(new TextElement(sb.toString()));
        }

        if (context.getPlugins()
                .sqlMapUpdateByPrimaryKeyWithoutBLOBsElementGenerated(answer,
                        introspectedTable)) {
            parentElement.addElement(answer);
        }
    }
}
