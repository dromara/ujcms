package com.ujcms.cms.core.web.backendapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http400Exception;

/**
 * 站点设置 › 基础设置改换自定义设置模型时先校验绑定（docs/spec/model-management/plan.md M11）：改了才校验，校验在写入之前，
 * 拒绝时当前站点对象不被改写
 */
class SiteSettingsControllerModelTest {
    private SiteService service;
    private SiteSettingsController controller;
    private Site current;

    @BeforeEach
    void setUp() {
        service = mock(SiteService.class);
        controller = new SiteSettingsController(service, mock(ConfigService.class));
        current = new Site();
        current.setId(1L);
        current.setModelId(5L);
        current.setConfig(new Config());
        Contexts.setCurrentSite(current);
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentSite();
    }

    private static Site bean(long modelId) {
        Site bean = new Site();
        bean.setModelId(modelId);
        return bean;
    }

    @Test
    void aChangedModelIsValidatedBeforeTheWrite() {
        controller.updateBase(bean(9L));
        InOrder inOrder = inOrder(service);
        inOrder.verify(service).validateModel(1L, 9L);
        inOrder.verify(service).update(current);
        assertEquals(9L, current.getModelId());
    }

    @Test
    void anUnchangedModelIsNotRevalidated() {
        controller.updateBase(bean(5L));
        verify(service, never()).validateModel(any(), any());
        verify(service).update(current);
    }

    @Test
    void aRefusedModelWritesNothing() {
        doThrow(new Http400Exception("error.site.modelInvalid", "9")).when(service).validateModel(1L, 9L);
        assertThrows(Http400Exception.class, () -> controller.updateBase(bean(9L)));
        verify(service, never()).update(any());
        assertEquals(5L, current.getModelId(), "the current site object is untouched");
    }
}
