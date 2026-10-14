package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.mapper.ArticleTopicMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.mapper.TopicTreeMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 专题绑定的模型须存在、是专题模型、属于本站或全局（docs/spec/article-data-permission/plan.md §13.5）：
 * 与栏目、表单类型同一规则，以前是 500
 */
class TopicModelValidationTest {
    private static final long SITE = 1L;
    private ModelService modelService;
    private TopicService service;

    @BeforeEach
    void setUp() {
        modelService = mock(ModelService.class);
        service = new TopicService(mock(TopicMapper.class), mock(TopicTreeMapper.class), mock(ArticleTopicMapper.class),
                modelService, mock(HtmlSanitizeService.class), mock(SnowflakeSequence.class), mock(SlugGenerator.class));
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
    void acceptsATopicModelOfThisSiteOrAGlobalOne() {
        when(modelService.select(10L)).thenReturn(model(10L, SITE, EntityConstants.SCOPE_PRIVATE, Topic.MODEL_TYPE));
        when(modelService.select(11L)).thenReturn(model(11L, null, EntityConstants.SCOPE_GLOBAL, Topic.MODEL_TYPE));
        assertDoesNotThrow(() -> service.validateModel(SITE, 10L));
        assertDoesNotThrow(() -> service.validateModel(SITE, 11L));
    }

    @Test
    void rejectsAnotherSitesModelANonTopicModelAndAnUnknownId() {
        when(modelService.select(12L)).thenReturn(model(12L, 2L, EntityConstants.SCOPE_PRIVATE, Topic.MODEL_TYPE));
        when(modelService.select(13L)).thenReturn(model(13L, SITE, EntityConstants.SCOPE_PRIVATE, "article"));
        when(modelService.select(14L)).thenReturn(null);
        for (long id : new long[]{12L, 13L, 14L}) {
            Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateModel(SITE, id));
            assertEquals("error.topic.modelInvalid", e.getCode());
        }
    }
}
