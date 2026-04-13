package com.ujcms.generator.mybatis;

import com.ujcms.generator.mybatis.model.*;
import org.mybatis.generator.api.ProgressCallback;
import org.mybatis.generator.codegen.AbstractJavaClientGenerator;
import org.mybatis.generator.codegen.AbstractJavaGenerator;
import org.mybatis.generator.codegen.mybatis3.IntrospectedTableMyBatis3Impl;
import org.mybatis.generator.codegen.mybatis3.javamapper.SimpleAnnotatedClientGenerator;
import org.mybatis.generator.internal.ObjectFactory;

import java.util.List;

public class IntrospectedTableSimpleImpl extends IntrospectedTableMyBatis3Impl {
    public IntrospectedTableSimpleImpl() {
        super();
    }

    @Override
    protected void calculateXmlMapperGenerator(AbstractJavaClientGenerator javaClientGenerator, List<String> warnings, ProgressCallback progressCallback) {
        if (javaClientGenerator == null) {
            if (context.getSqlMapGeneratorConfiguration() != null) {
                xmlMapperGenerator = new XmlMapperGenerator();
            }
        } else {
            xmlMapperGenerator = javaClientGenerator.getMatchedXMLGenerator();
        }
        initializeAbstractGenerator(xmlMapperGenerator, warnings, progressCallback);
    }

    @Override
    protected AbstractJavaClientGenerator createJavaClientGenerator() {
        if (context.getJavaClientGeneratorConfiguration() == null) return null;
        String type = context.getJavaClientGeneratorConfiguration().getConfigurationType();
        AbstractJavaClientGenerator javaGenerator;
        if ("XMLMAPPER".equalsIgnoreCase(type)) { //$NON-NLS-1$
            javaGenerator = new JavaMapperGenerator(getClientProject());
        } else if ("ANNOTATEDMAPPER".equalsIgnoreCase(type)) { //$NON-NLS-1$
            javaGenerator = new SimpleAnnotatedClientGenerator(getClientProject());
        } else if ("MAPPER".equalsIgnoreCase(type)) { //$NON-NLS-1$
            javaGenerator = new JavaMapperGenerator(getClientProject());
        } else {
            javaGenerator = (AbstractJavaClientGenerator) ObjectFactory.createInternalObject(type);
        }
        return javaGenerator;
    }

    @Override
    protected void calculateJavaModelGenerators(List<String> warnings, ProgressCallback progressCallback) {
        AbstractJavaGenerator superBaseModelGenerator = new SuperBaseModel(getModelProject());
        initializeAbstractGenerator(superBaseModelGenerator, warnings, progressCallback);
        javaGenerators.add(superBaseModelGenerator);

        AbstractJavaGenerator baseModelGenerator = new BaseModel(getModelProject());
        initializeAbstractGenerator(baseModelGenerator, warnings, progressCallback);
        javaGenerators.add(baseModelGenerator);

        AbstractJavaGenerator baseServiceGenerator = new BaseService(getModelProject());
        initializeAbstractGenerator(baseServiceGenerator, warnings, progressCallback);
        javaGenerators.add(baseServiceGenerator);

        AbstractJavaGenerator baseServiceArgsGenerator = new BaseServiceArgs(getModelProject());
        initializeAbstractGenerator(baseServiceArgsGenerator, warnings, progressCallback);
        javaGenerators.add(baseServiceArgsGenerator);

        AbstractJavaGenerator baseControllerGenerator = new BaseController(getModelProject());
        initializeAbstractGenerator(baseControllerGenerator, warnings, progressCallback);
        javaGenerators.add(baseControllerGenerator);
    }
}
