package com.ujcms.cms.core.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.mapper.BlockItemMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;

/**
 * 区块绑定的区块项模型须存在、是区块项模型，且对区块可见（docs/spec/model-management/plan.md M11）：本站区块可用本站或
 * 全局的模型，全局区块只可用全局的模型。与专题、表单类型同一规则；以前不校验，篡改请求即可绑定其它站点的模型
 */
class BlockModelValidationTest {
    private static final long SITE = 1L;
    private ModelService modelService;
    private BlockService service;

    @BeforeEach
    void setUp() {
        modelService = mock(ModelService.class);
        service = new BlockService(mock(BlockItemMapper.class), mock(BlockMapper.class), modelService,
                mock(SnowflakeSequence.class));
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
    void aSiteBlockTakesAModelOfItsSiteOrAGlobalOne() {
        when(modelService.select(10L)).thenReturn(model(10L, SITE, EntityConstants.SCOPE_PRIVATE, Block.MODEL_TYPE));
        when(modelService.select(11L)).thenReturn(model(11L, null, EntityConstants.SCOPE_GLOBAL, Block.MODEL_TYPE));
        assertDoesNotThrow(() -> service.validateModel(SITE, 10L));
        assertDoesNotThrow(() -> service.validateModel(SITE, 11L));
    }

    @Test
    void aGlobalBlockTakesOnlyAGlobalModel() {
        when(modelService.select(10L)).thenReturn(model(10L, SITE, EntityConstants.SCOPE_PRIVATE, Block.MODEL_TYPE));
        when(modelService.select(11L)).thenReturn(model(11L, null, EntityConstants.SCOPE_GLOBAL, Block.MODEL_TYPE));
        assertDoesNotThrow(() -> service.validateModel(null, 11L));
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateModel(null, 10L));
        assertEquals("error.block.modelInvalid", e.getCode());
    }

    @Test
    void rejectsAnotherSitesModelANonBlockItemModelAndAnUnknownId() {
        when(modelService.select(12L)).thenReturn(model(12L, 2L, EntityConstants.SCOPE_PRIVATE, Block.MODEL_TYPE));
        when(modelService.select(13L)).thenReturn(model(13L, SITE, EntityConstants.SCOPE_PRIVATE, "article"));
        when(modelService.select(14L)).thenReturn(null);
        for (long id : new long[]{12L, 13L, 14L}) {
            Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateModel(SITE, id));
            assertEquals("error.block.modelInvalid", e.getCode());
        }
    }
}
