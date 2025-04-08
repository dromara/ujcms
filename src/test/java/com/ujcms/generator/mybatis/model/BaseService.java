package com.ujcms.generator.mybatis.model;

import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.codegen.AbstractJavaGenerator;

import java.util.ArrayList;
import java.util.List;

import static org.mybatis.generator.internal.util.messages.Messages.getString;

public class BaseService extends AbstractJavaGenerator {

    public BaseService(String project) {
        super(project);
    }

    protected String getJavaType() {
        String recordType = introspectedTable.getBaseRecordType();
        int end = recordType.lastIndexOf(".");
        int begin = recordType.lastIndexOf(".", end - 1);
        return recordType.substring(0, begin) + ".service" + recordType.substring(end) + "Service";
    }

    protected String getModelType() {
        return new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()).getShortName();
    }

    protected String getModelBaseType() {
        String recordType = introspectedTable.getBaseRecordType();
        int index = recordType.lastIndexOf(".");
        return recordType.substring(0, index) + ".base" + recordType.substring(index) + "Base";
    }

    protected String getArgsType() {
        String recordType = introspectedTable.getBaseRecordType();
        int end = recordType.lastIndexOf(".");
        int begin = recordType.lastIndexOf(".", end - 1);
        return recordType.substring(0, begin) + ".service.args" + recordType.substring(end) + "Args";
    }

    @Override
    public List<CompilationUnit> getCompilationUnits() {
        FullyQualifiedTable table = introspectedTable.getFullyQualifiedTable();
        progressCallback.startTask(getString("Progress.8", table.toString()));
        FullyQualifiedJavaType type = new FullyQualifiedJavaType(getJavaType());
        TopLevelClass topLevelClass = new TopLevelClass(type);
        topLevelClass.setVisibility(JavaVisibility.PUBLIC);
        // 是否分页
        boolean pageable = !"false".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("pageable"));
        // 公共import
        topLevelClass.addImportedType(introspectedTable.getBaseRecordType());
        topLevelClass.addImportedType(getModelBaseType());
        topLevelClass.addImportedType(introspectedTable.getMyBatis3JavaMapperType());
        topLevelClass.addImportedType(getArgsType());
        topLevelClass.addImportedType("org.springframework.lang.Nullable");
        topLevelClass.addImportedType("org.springframework.stereotype.Service");
        topLevelClass.addImportedType("org.springframework.transaction.annotation.Transactional");
        if (pageable) {
            topLevelClass.addImportedType("com.github.pagehelper.Page");
        }
        topLevelClass.addImportedType("com.github.pagehelper.page.PageMethod");
        topLevelClass.addImportedType("java.util.List");
        topLevelClass.addImportedType("java.util.Objects");
        topLevelClass.addImportedType("com.ujcms.commons.query.QueryInfo");
        topLevelClass.addImportedType("com.ujcms.commons.query.QueryParser");
        // topLevelClass.addImportedType("com.ujcms.cms.core.service.SeqService");
        topLevelClass.addImportedType("com.ujcms.commons.db.identifier.SnowflakeSequence");
        topLevelClass.addImportedType("org.springframework.stereotype.Service");
        // @Service 注解
        topLevelClass.addAnnotation("@Service");
        // Mapper 属性
        Field mapper = new Field("mapper", new FullyQualifiedJavaType(introspectedTable.getMyBatis3JavaMapperType()));
        mapper.setVisibility(JavaVisibility.PRIVATE);
        mapper.setFinal(true);
        topLevelClass.addField(mapper);
        // SeqService 属性
        // Field seqService = new Field("seqService", new FullyQualifiedJavaType("SeqService"));
        // seqService.setVisibility(JavaVisibility.PRIVATE);
        // seqService.setFinal(true);
        // topLevelClass.addField(seqService);
        // SnowflakeSequence 属性
        Field snowflakeSequence = new Field("snowflakeSequence", new FullyQualifiedJavaType("SnowflakeSequence"));
        snowflakeSequence.setVisibility(JavaVisibility.PRIVATE);
        snowflakeSequence.setFinal(true);
        topLevelClass.addField(snowflakeSequence);
        // 构造器
        Method constructor = new Method(topLevelClass.getType().getShortName());
        constructor.setConstructor(true);
        constructor.setVisibility(JavaVisibility.PUBLIC);
        constructor.addParameter(new Parameter(new FullyQualifiedJavaType(introspectedTable.getMyBatis3JavaMapperType()), "mapper"));
        // constructor.addParameter(new Parameter(new FullyQualifiedJavaType("SeqService"), "seqService"));
        constructor.addParameter(new Parameter(new FullyQualifiedJavaType("SnowflakeSequence"), "snowflakeSequence"));
        constructor.addBodyLine("this.mapper = mapper;");
        // constructor.addBodyLine("this.seqService = seqService;");
        constructor.addBodyLine("this.snowflakeSequence = snowflakeSequence;");
        topLevelClass.addMethod(constructor);
        // insert 方法
        Method insert = new Method("insert");
        insert.setVisibility(JavaVisibility.PUBLIC);
        insert.addAnnotation("@Transactional(rollbackFor = Exception.class)");
        insert.addParameter(new Parameter(new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()), "bean"));
        insert.addBodyLine("bean.setId(snowflakeSequence.nextId());");
        if ("true".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("order"))) {
            insert.addBodyLine("bean.setOrder(bean.getId());");
        }
        insert.addBodyLine("mapper.insert(bean);");
        topLevelClass.addMethod(insert);
        // update 方法
        Method update = new Method("update");
        update.setVisibility(JavaVisibility.PUBLIC);
        update.addAnnotation("@Transactional(rollbackFor = Exception.class)");
        update.addParameter(new Parameter(new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()), "bean"));
        update.addBodyLine("mapper.update(bean);");
        topLevelClass.addMethod(update);
        // moveOrder 方法
        if ("true".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("order"))) {
            topLevelClass.addImportedType("com.ujcms.commons.db.order.OrderEntityUtils");
            Method moveOrder = new Method("moveOrder");
            moveOrder.setVisibility(JavaVisibility.PUBLIC);
            moveOrder.addAnnotation("@Transactional(rollbackFor = Exception.class)");
            moveOrder.addParameter(new Parameter(new FullyQualifiedJavaType("Long"), "fromId"));
            moveOrder.addParameter(new Parameter(new FullyQualifiedJavaType("Long"), "toId"));
            moveOrder.addBodyLine("OrderEntityUtils.move(mapper, fromId, toId);");
            topLevelClass.addMethod(moveOrder);
        }
        // delete 方法
        Method delete = new Method("delete");
        delete.setVisibility(JavaVisibility.PUBLIC);
        delete.setReturnType(new FullyQualifiedJavaType("int"));
        delete.addAnnotation("@Transactional(rollbackFor = Exception.class)");
        delete.addParameter(new Parameter(new FullyQualifiedJavaType("Long"), "id"));
        delete.addBodyLine("return mapper.delete(id);");
        topLevelClass.addMethod(delete);
        // deleteAll 方法
        Method deleteAll = new Method("delete");
        deleteAll.setVisibility(JavaVisibility.PUBLIC);
        deleteAll.setReturnType(new FullyQualifiedJavaType("int"));
        deleteAll.addAnnotation("@Transactional(rollbackFor = Exception.class)");
        FullyQualifiedJavaType idsType = new FullyQualifiedJavaType("List");
        idsType.addTypeArgument(new FullyQualifiedJavaType("Long"));
        deleteAll.addParameter(new Parameter(idsType, "ids"));
        deleteAll.addBodyLine("return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();");
        topLevelClass.addMethod(deleteAll);
        // select 方法
        Method select = new Method("select");
        select.setVisibility(JavaVisibility.PUBLIC);
        FullyQualifiedJavaType returnType = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
        select.setReturnType(returnType);
        select.addAnnotation("@Nullable");
        select.addParameter(new Parameter(new FullyQualifiedJavaType("Long"), "id"));
        select.addBodyLine("return mapper.select(id);");
        topLevelClass.addMethod(select);
        // selectList(Map<String, Object> queryMap) 方法
        selectList(topLevelClass);
        selectListOffsetLimit(topLevelClass);
        if (pageable) {
            selectPage(topLevelClass);
        }

        List<CompilationUnit> answer = new ArrayList<>();
        if (!"false".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("service"))) {
            answer.add(topLevelClass);
        }
        return answer;
    }

    private void selectList(TopLevelClass topLevelClass) {
        Method method = new Method("selectList");
        method.setVisibility(JavaVisibility.PUBLIC);
        FullyQualifiedJavaType returnType = new FullyQualifiedJavaType("java.util.List");
        returnType.addTypeArgument(new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()));
        method.setReturnType(returnType);

        Parameter args = new Parameter(new FullyQualifiedJavaType(getModelType() + "Args"), "args");
        method.addParameter(args);
        String orderBy = "id_desc";
        if ("true".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("order"))) {
            orderBy = "order_,id_";
        }
        method.addBodyLine("QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), "
                + getModelType() + "Base.TABLE_NAME, \"" + orderBy + "\");");
        method.addBodyLine("return mapper.selectAll(queryInfo);");
        topLevelClass.addMethod(method);
    }

    private void selectListOffsetLimit(TopLevelClass topLevelClass) {
        Method method = new Method("selectList");
        method.setVisibility(JavaVisibility.PUBLIC);
        FullyQualifiedJavaType returnType = new FullyQualifiedJavaType("java.util.List");
        returnType.addTypeArgument(new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()));
        method.setReturnType(returnType);

        Parameter args = new Parameter(new FullyQualifiedJavaType(getModelType() + "Args"), "args");
        method.addParameter(args);
        Parameter offset = new Parameter(new FullyQualifiedJavaType("int"), "offset");
        method.addParameter(offset);
        Parameter limit = new Parameter(new FullyQualifiedJavaType("int"), "limit");
        method.addParameter(limit);
        method.addBodyLine("return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));");
        topLevelClass.addMethod(method);
    }

    private void selectPage(TopLevelClass topLevelClass) {
        Method method = new Method("selectPage");
        method.setVisibility(JavaVisibility.PUBLIC);
        FullyQualifiedJavaType returnType = new FullyQualifiedJavaType("Page");
        returnType.addTypeArgument(new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()));
        method.setReturnType(returnType);

        Parameter args = new Parameter(new FullyQualifiedJavaType(getModelType() + "Args"), "args");
        method.addParameter(args);
        method.addParameter(new Parameter(new FullyQualifiedJavaType("int"), "page"));
        method.addParameter(new Parameter(new FullyQualifiedJavaType("int"), "pageSize"));
        method.addBodyLine("return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));");
        topLevelClass.addMethod(method);
    }
}
