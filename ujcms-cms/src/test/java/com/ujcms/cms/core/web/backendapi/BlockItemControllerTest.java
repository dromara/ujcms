package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 区块项后台接口契约（docs/spec/console-master-detail R6）：{@code order} 只经排序接口改写——列表的行内开关回传整行，
 * 带着旧 order 会把刚拖好的顺序改回去；站点归属以已存储的数据为准，请求体里的 siteId 既不用于校验，也不写入。
 * 站点上下文经 {@link Contexts} ThreadLocal 注入（standalone MockMvc 同线程执行）。
 */
class BlockItemControllerTest {
    private static final Long SITE = 1L;
    private static final Long OTHER_SITE = 2L;
    private static final String URL = "/api/backend/core/block-item";

    private BlockItemService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(BlockItemService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BlockItemController(service)).build();
        Site site = new Site();
        site.setId(SITE);
        Contexts.setCurrentSite(site);
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentSite();
    }

    private BlockItem stored(long id, Long siteId) {
        BlockItem bean = new BlockItem();
        bean.setId(id);
        bean.setSiteId(siteId);
        bean.setBlockId(7L);
        bean.setTitle("原标题");
        bean.setOrder(5L);
        bean.setEnabled(true);
        when(service.select(id)).thenReturn(bean);
        return bean;
    }

    /** standalone MockMvc 无项目异常解析器：控制器异常以 ServletException 包裹抛出。 */
    private static void assertCause(Class<?> type, Executable executable) {
        Throwable thrown = assertThrows(Throwable.class, executable);
        Throwable cause = thrown;
        while (cause.getCause() != null && !type.isInstance(cause)) {
            cause = cause.getCause();
        }
        assertInstanceOf(type, cause);
    }

    @Test
    void updateKeepsStoredOrderAndSite() throws Exception {
        stored(10L, SITE);
        // 行内开关回传整行：order 是拖拽之前的旧值，siteId 被改成了别的站点
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":10,\"siteId\":2,\"blockId\":7,\"title\":\"新标题\",\"order\":99,\"enabled\":false}"))
                .andExpect(status().isOk());

        ArgumentCaptor<BlockItem> saved = ArgumentCaptor.forClass(BlockItem.class);
        verify(service).update(saved.capture());
        assertEquals(5L, saved.getValue().getOrder());
        assertEquals(SITE, saved.getValue().getSiteId());
        assertEquals("新标题", saved.getValue().getTitle());
        assertFalse(saved.getValue().getEnabled());
    }

    @Test
    void updateOfAnotherSitesItemIsRefusedEvenWhenTheRequestClaimsThisSite() {
        stored(11L, OTHER_SITE);
        assertCause(Http400Exception.class, () -> mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":11,\"siteId\":1,\"blockId\":7,\"title\":\"篡改\"}")));
        verify(service, never()).update(any());
    }
}
