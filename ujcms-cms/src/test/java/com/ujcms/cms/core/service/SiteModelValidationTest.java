package com.ujcms.cms.core.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.mapper.OrgMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.common.web.exception.Http400Exception;

/**
 * 站点绑定的自定义设置模型须存在、是站点设置模型、属于该站点或全局（docs/spec/model-management/plan.md M11）：
 * 与专题、表单类型同一规则；以前不校验，篡改请求即可绑定其它站点的模型，不存在的ID是 500
 */
class SiteModelValidationTest {
    private static final long SITE = 1L;
    private ModelService modelService;
    private SiteService service;

    @BeforeEach
    void setUp() {
        modelService = mock(ModelService.class);
        service = new SiteService(mock(HtmlSanitizeService.class), mock(AttachmentService.class), modelService,
                mock(SiteMapper.class), mock(OrgMapper.class));
    }

    private static Model model(long id, @Nullable Long siteId, short scope, String type) {
        Model model = new Model();
        model.setId(id);
        model.setSiteId(siteId);
        model.setScope(scope);
        model.setType(type);
        return model;
    }

    @Test
    void acceptsASiteModelOfThisSiteOrAGlobalOne() {
        when(modelService.select(10L)).thenReturn(model(10L, SITE, EntityConstants.SCOPE_PRIVATE, Model.MODEL_TYPE_SITE));
        when(modelService.select(11L)).thenReturn(model(11L, null, EntityConstants.SCOPE_GLOBAL, Model.MODEL_TYPE_SITE));
        assertDoesNotThrow(() -> service.validateModel(SITE, 10L));
        assertDoesNotThrow(() -> service.validateModel(SITE, 11L));
    }

    @Test
    void rejectsAnotherSitesModelANonSiteModelAndAnUnknownId() {
        when(modelService.select(12L)).thenReturn(model(12L, 2L, EntityConstants.SCOPE_PRIVATE, Model.MODEL_TYPE_SITE));
        when(modelService.select(13L)).thenReturn(model(13L, SITE, EntityConstants.SCOPE_PRIVATE, Model.MODEL_TYPE_GLOBAL));
        when(modelService.select(14L)).thenReturn(null);
        for (long id : new long[]{12L, 13L, 14L}) {
            Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateModel(SITE, id));
            assertEquals("error.site.modelInvalid", e.getCode());
        }
    }
}
