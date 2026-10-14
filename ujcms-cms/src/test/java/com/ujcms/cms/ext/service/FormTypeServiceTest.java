package com.ujcms.cms.ext.service;

import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.ProcessDefinitionPort;
import com.ujcms.cms.ext.domain.Form;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.mapper.FormMapper;
import com.ujcms.cms.ext.mapper.FormTypeMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.LogicException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表单类型删除契约（docs/spec/console-master-detail R4）：表单是访客提交的数据，不随类型级联删除——类型下还有表单
 * （不论状态）即拒绝。提示用专属的 {@code error.formType.hasForms}：类型就在“表单管理”里删，通用的
 * {@code error.refer.form} 会把用户指回他此刻所在的那一屏。
 * <p>
 * 模型校验契约（docs/spec/article-data-permission/plan.md D16）：绑定的模型须存在、是表单模型、属于本站或全局。
 */
class FormTypeServiceTest {
    private static final long SITE = 1L;
    private static final long OTHER_SITE = 2L;
    private FormMapper formMapper;
    private FormTypeMapper mapper;
    private ModelService modelService;
    private ObjectProvider<ProcessDefinitionPort> processDefinitionPort;
    private ProcessDefinitionPort port;
    private FormTypeService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        formMapper = mock(FormMapper.class);
        mapper = mock(FormTypeMapper.class);
        modelService = mock(ModelService.class);
        processDefinitionPort = mock(ObjectProvider.class);
        port = mock(ProcessDefinitionPort.class);
        when(processDefinitionPort.getIfAvailable()).thenReturn(port);
        service = new FormTypeService(formMapper, mapper, modelService, mock(SnowflakeSequence.class),
                processDefinitionPort);
    }

    @Test
    void deleteIsRefusedWhileFormsExist() {
        when(formMapper.existsByTypeId(7L)).thenReturn(1);
        LogicException e = assertThrows(LogicException.class, () -> service.delete(7L));
        assertEquals("error.formType.hasForms", e.getCode());
        verify(mapper, never()).delete(7L);
    }

    @Test
    void deleteOfAnEmptyTypeGoesThrough() {
        when(formMapper.existsByTypeId(7L)).thenReturn(0);
        when(mapper.delete(7L)).thenReturn(1);
        assertEquals(1, service.delete(7L));
    }

    private Model model(long id, Long siteId, short scope, String type) {
        Model m = new Model();
        m.setId(id);
        m.setSiteId(siteId);
        m.setScope(scope);
        m.setType(type);
        return m;
    }

    @Test
    void validateModelAcceptsAFormModelOfThisSite() {
        when(modelService.select(10L)).thenReturn(model(10L, SITE, EntityConstants.SCOPE_PRIVATE, FormType.MODEL_TYPE));
        assertDoesNotThrow(() -> service.validateModel(SITE, 10L));
    }

    @Test
    void validateModelAcceptsAGlobalFormModel() {
        when(modelService.select(11L)).thenReturn(model(11L, null, EntityConstants.SCOPE_GLOBAL, FormType.MODEL_TYPE));
        assertDoesNotThrow(() -> service.validateModel(SITE, 11L));
    }

    @Test
    void validateModelRejectsAnotherSitesModel() {
        when(modelService.select(12L)).thenReturn(model(12L, OTHER_SITE, EntityConstants.SCOPE_PRIVATE, FormType.MODEL_TYPE));
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateModel(SITE, 12L));
        assertEquals("error.formType.modelInvalid", e.getCode());
    }

    @Test
    void validateModelRejectsANonFormModel() {
        when(modelService.select(13L)).thenReturn(model(13L, SITE, EntityConstants.SCOPE_PRIVATE, "article"));
        assertThrows(Http400Exception.class, () -> service.validateModel(SITE, 13L));
    }

    @Test
    void validateModelRejectsAnUnknownModel() {
        when(modelService.select(14L)).thenReturn(null);
        assertThrows(Http400Exception.class, () -> service.validateModel(SITE, 14L));
    }

    // ---- 审核流程（D23）：留空即无流程；否则须是本站已部署的表单流程

    @Test
    void validateProcessKeyAcceptsBlank() {
        assertDoesNotThrow(() -> service.validateProcessKey(SITE, null));
        assertDoesNotThrow(() -> service.validateProcessKey(SITE, " "));
        verify(port, never()).isDeployed(anyLong(), any(), any());
    }

    @Test
    void validateProcessKeyAcceptsADeployedFormProcessOfThisSite() {
        when(port.isDeployed(SITE, Form.PROCESS_CATEGORY, "form-review")).thenReturn(true);
        assertDoesNotThrow(() -> service.validateProcessKey(SITE, "form-review"));
    }

    @Test
    void validateProcessKeyRejectsAProcessNotDeployedForThisSite() {
        when(port.isDeployed(SITE, Form.PROCESS_CATEGORY, "elsewhere")).thenReturn(false);
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateProcessKey(SITE, "elsewhere"));
        assertEquals("error.formType.processInvalid", e.getCode());
    }

    @Test
    void validateProcessKeyRejectsAnyKeyWithoutThePort() {
        when(processDefinitionPort.getIfAvailable()).thenReturn(null);
        assertThrows(Http400Exception.class, () -> service.validateProcessKey(SITE, "form-review"));
        assertDoesNotThrow(() -> service.validateProcessKey(SITE, ""));
    }
}
