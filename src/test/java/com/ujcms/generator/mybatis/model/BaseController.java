package com.ujcms.generator.mybatis.model;

import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.codegen.AbstractJavaGenerator;

import java.util.ArrayList;
import java.util.List;

import static org.mybatis.generator.internal.util.messages.Messages.getString;

public class BaseController extends AbstractJavaGenerator {

    public BaseController(String project) {
        super(project);
    }

    protected String getJavaType() {
        String recordType = introspectedTable.getBaseRecordType();
        int end = recordType.lastIndexOf(".");
        int begin = recordType.lastIndexOf(".", end - 1);
        return recordType.substring(0, begin) + ".web.backendapi" + recordType.substring(end) + "Controller";
    }

    protected String getModelType() {
        return new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()).getShortName();
    }

    protected String getServiceType() {
        String recordType = introspectedTable.getBaseRecordType();
        int end = recordType.lastIndexOf(".");
        int begin = recordType.lastIndexOf(".", end - 1);
        return recordType.substring(0, begin) + ".service" + recordType.substring(end) + "Service";
    }

    protected String getArgsType() {
        String recordType = introspectedTable.getBaseRecordType();
        int end = recordType.lastIndexOf(".");
        int begin = recordType.lastIndexOf(".", end - 1);
        return recordType.substring(0, begin) + ".service.args" + recordType.substring(end) + "Args";
    }

    protected String getSubSystem() {
        String recordType = introspectedTable.getBaseRecordType();
        int end = recordType.lastIndexOf(".");
        end = recordType.lastIndexOf(".", end - 1);
        int begin = recordType.lastIndexOf(".", end - 1);
        return recordType.substring(begin + 1, end);
    }

    protected String getModelTypeLower() {
        String modelType = getModelType();
        StringBuilder sb = new StringBuilder();
        for (char c : modelType.toCharArray()) {
            if (Character.isUpperCase(c) && sb.length() > 0) sb.append('-');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    protected String getModelTypeLowerCamel() {
        String modelType = getModelType();
        return Character.toLowerCase(modelType.charAt(0)) + modelType.substring(1);
    }

    protected String getRequestMapping() {
        return "/" + getSubSystem() + "/" + getModelTypeLower();
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
        topLevelClass.addImportedType(getServiceType());
        topLevelClass.addImportedType(getArgsType());
        topLevelClass.addImportedType("com.ujcms.cms.core.aop.annotations.OperationLog");
        topLevelClass.addImportedType("com.ujcms.cms.core.aop.enums.OperationType");
        topLevelClass.addImportedType("com.ujcms.commons.web.Entities");
        topLevelClass.addImportedType("com.ujcms.commons.web.Responses");
        topLevelClass.addImportedType("com.ujcms.commons.web.Responses.Body");
        topLevelClass.addImportedType("com.ujcms.commons.web.exception.Http404Exception");
        topLevelClass.addImportedType("com.ujcms.commons.web.exception.Http400Exception");
        if (pageable) {
            topLevelClass.addImportedType("org.springframework.data.domain.Page");
            topLevelClass.addStaticImport("com.ujcms.commons.db.MyBatis.springPage");
            topLevelClass.addStaticImport("com.ujcms.cms.core.support.Constants.validPage");
            topLevelClass.addStaticImport("com.ujcms.cms.core.support.Constants.validPageSize");
        }
        topLevelClass.addImportedType("org.springframework.http.ResponseEntity");
        topLevelClass.addImportedType("org.springframework.web.bind.annotation.*");
        topLevelClass.addImportedType("org.springframework.security.access.prepost.PreAuthorize");
        topLevelClass.addImportedType("javax.servlet.http.HttpServletRequest");
        topLevelClass.addImportedType("javax.validation.Valid");
        topLevelClass.addImportedType("java.util.List");
        topLevelClass.addImportedType("java.util.Optional");

        topLevelClass.addStaticImport("com.ujcms.cms.core.support.UrlConstants.BACKEND_API");
        topLevelClass.addStaticImport("com.ujcms.commons.query.QueryUtils.getQueryMap");
        // @Service 注解
        topLevelClass.addAnnotation("@RestController(\"backend" + getModelType() + "Controller" + "\")");
        topLevelClass.addAnnotation("@RequestMapping(BACKEND_API + \"" + getRequestMapping() + "\")");
        // Mapper 属性
        Field service = new Field("service", new FullyQualifiedJavaType(getServiceType()));
        service.setVisibility(JavaVisibility.PRIVATE);
        service.setFinal(true);
        topLevelClass.addField(service);
        // 构造器
        Method constructor = new Method(topLevelClass.getType().getShortName());
        constructor.setConstructor(true);
        constructor.setVisibility(JavaVisibility.PUBLIC);
        constructor.addParameter(new Parameter(new FullyQualifiedJavaType(getServiceType()), "service"));
        constructor.addBodyLine("this.service = service;");
        topLevelClass.addMethod(constructor);
        // 公用参数
        Parameter requestParam = new Parameter(new FullyQualifiedJavaType("HttpServletRequest"), "request");
        Parameter beanParam = new Parameter(new FullyQualifiedJavaType(introspectedTable.getBaseRecordType()), "bean");
        beanParam.addAnnotation("@RequestBody");
        beanParam.addAnnotation("@Valid");
        // list 方法
        Method list = new Method("list");
        list.setVisibility(JavaVisibility.PUBLIC);
        list.addAnnotation("@GetMapping");
        list.addAnnotation("@PreAuthorize(\"hasAnyAuthority('" + getModelTypeLowerCamel() + ":list','*')\")");
        if (pageable) {
            Parameter pageParameter = new Parameter(new FullyQualifiedJavaType("Integer"), "page");
            pageParameter.addAnnotation("@RequestParam(\"page\")");
            Parameter pageSizeParameter = new Parameter(new FullyQualifiedJavaType("Integer"), "pageSize");
            pageSizeParameter.addAnnotation("@RequestParam(\"pageSize\")");
            list.addParameter(pageParameter);
            list.addParameter(pageSizeParameter);
        }
        list.addParameter(requestParam);
        // 返回类型 ResponseEntity<Body>
        FullyQualifiedJavaType listReturnType;
        listReturnType = new FullyQualifiedJavaType(pageable ? "Page" : "List");
        listReturnType.addTypeArgument(new FullyQualifiedJavaType(getModelType()));
        list.setReturnType(listReturnType);
        list.addBodyLine(String.format("%sArgs args = %sArgs.of(getQueryMap(request.getQueryString()));",
                getModelType(), getModelType()));
        if (pageable) {
            list.addBodyLine("return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));");
        } else {
            list.addBodyLine("return service.selectList(args);");
        }
        topLevelClass.addMethod(list);
        // show 方法
        Method show = new Method("show");
        show.setVisibility(JavaVisibility.PUBLIC);
        show.addAnnotation("@GetMapping(\"{id}\")");
        show.addAnnotation("@PreAuthorize(\"hasAnyAuthority('" + getModelTypeLowerCamel() + ":show','*')\")");
        show.setReturnType(new FullyQualifiedJavaType(getModelType()));
        Parameter idParam = new Parameter(new FullyQualifiedJavaType("Long"), "id");
        idParam.addAnnotation("@PathVariable(\"id\")");
        show.addParameter(idParam);
        show.addBodyLine("return Optional.ofNullable(service.select(id))");
        show.addBodyLine(".orElseThrow(() -> new Http404Exception(" + getModelType() + ".NOT_FOUND + id));");
        topLevelClass.addMethod(show);

        // 返回类型 ResponseEntity<Body>
        FullyQualifiedJavaType returnType = new FullyQualifiedJavaType("ResponseEntity");
        returnType.addTypeArgument(new FullyQualifiedJavaType("Body"));
        // create 方法
        Method create = new Method("create");
        create.setVisibility(JavaVisibility.PUBLIC);
        create.addAnnotation("@PostMapping");
        create.addAnnotation("@PreAuthorize(\"hasAnyAuthority('" + getModelTypeLowerCamel() + ":create','*')\")");
        create.addAnnotation("@OperationLog(module = \"" + getModelTypeLowerCamel() +
                "\", operation = \"create\", type = OperationType.CREATE)");
        create.setReturnType(returnType);
        create.addParameter(beanParam);
        // create.addParameter(requestParam);
        create.addBodyLine(getModelType() + " " + getModelTypeLowerCamel() + " = new " + getModelType() + "();");
        create.addBodyLine("Entities.copy(bean, " + getModelTypeLowerCamel() + ");");
        create.addBodyLine("service.insert(" + getModelTypeLowerCamel() + ");");
        create.addBodyLine("return Responses.ok();");
        topLevelClass.addMethod(create);
        // update 方法
        Method update = new Method("update");
        update.setVisibility(JavaVisibility.PUBLIC);
        update.addAnnotation("@PutMapping");
        update.addAnnotation("@PreAuthorize(\"hasAnyAuthority('" + getModelTypeLowerCamel() + ":update','*')\")");
        update.addAnnotation("@OperationLog(module = \"" + getModelTypeLowerCamel() +
                "\", operation = \"update\", type = OperationType.UPDATE)");
        update.setReturnType(returnType);
        update.addParameter(beanParam);
        update.addBodyLine(getModelType() + " " + getModelTypeLowerCamel() + " = Optional.ofNullable(service.select(bean.getId()))");
        update.addBodyLine(".orElseThrow(() -> new Http400Exception(" + getModelType() + ".NOT_FOUND + bean.getId()));");
        update.addBodyLine("Entities.copy(bean, " + getModelTypeLowerCamel() + ");");
        update.addBodyLine("service.update(" + getModelTypeLowerCamel() + ");");
        update.addBodyLine("return Responses.ok();");
        topLevelClass.addMethod(update);
        // updateOrder 方法
        if ("true".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("order"))) {
            Method updateOrder = new Method("updateOrder");
            updateOrder.setVisibility(JavaVisibility.PUBLIC);
            updateOrder.addAnnotation("@PostMapping(\"update-order\")");
            updateOrder.addAnnotation("@PreAuthorize(\"hasAnyAuthority('" + getModelTypeLowerCamel() + ":update','*')\")");
            updateOrder.addAnnotation("@OperationLog(module = \"" + getModelTypeLowerCamel() +
                    "\", operation = \"updateOrder\", type = OperationType.UPDATE)");
            updateOrder.setReturnType(returnType);

            topLevelClass.addImportedType("com.ujcms.commons.db.order.MoveOrderParams");
            Parameter moveOrderParam = new Parameter(new FullyQualifiedJavaType("MoveOrderParams"), "params");
            moveOrderParam.addAnnotation("@RequestBody");
            moveOrderParam.addAnnotation("@Valid");
            updateOrder.addParameter(moveOrderParam);

            updateOrder.addBodyLine(getModelType() + " fromBean = Optional.ofNullable(service.select(params.getFromId()))");
            updateOrder.addBodyLine(".orElseThrow(() -> new Http400Exception(" + getModelType() + ".NOT_FOUND + params.getFromId()));");
            updateOrder.addBodyLine(getModelType() + " toBean = Optional.ofNullable(service.select(params.getToId()))");
            updateOrder.addBodyLine(".orElseThrow(() -> new Http400Exception(" + getModelType() + ".NOT_FOUND + params.getToId()));");

            updateOrder.addBodyLine("service.moveOrder(fromBean.getId(), toBean.getId());");
            updateOrder.addBodyLine("return Responses.ok();");
            topLevelClass.addMethod(updateOrder);
        }
        // delete 方法
        Method delete = new Method("delete");
        delete.setVisibility(JavaVisibility.PUBLIC);
        delete.addAnnotation("@DeleteMapping");
        delete.addAnnotation("@PreAuthorize(\"hasAnyAuthority('" + getModelTypeLowerCamel() + ":delete','*')\")");
        delete.addAnnotation("@OperationLog(module = \"" + getModelTypeLowerCamel() +
                "\", operation = \"delete\", type = OperationType.DELETE)");
        delete.setReturnType(returnType);
        FullyQualifiedJavaType idsType = new FullyQualifiedJavaType("List");
        idsType.addTypeArgument(new FullyQualifiedJavaType("Long"));
        Parameter idsParam = new Parameter(idsType, "ids");
        idsParam.addAnnotation("@RequestBody");
        delete.addParameter(idsParam);
        delete.addBodyLine("for (Long id : ids) {");
        delete.addBodyLine(getModelType() + " bean = Optional.ofNullable(service.select(id))");
        delete.addBodyLine(".orElseThrow(() -> new Http400Exception(" + getModelType() + ".NOT_FOUND + id));");
        delete.addBodyLine("service.delete(bean.getId());");
        delete.addBodyLine("}");
        delete.addBodyLine("return Responses.ok();");
        topLevelClass.addMethod(delete);

        List<CompilationUnit> answer = new ArrayList<>();
        if (!"false".equalsIgnoreCase(introspectedTable.getTableConfigurationProperty("controller"))) {
            answer.add(topLevelClass);
        }
        return answer;
    }
}
