package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.cms.ext.service.LinkGroupService;
import com.ujcms.cms.ext.service.args.LinkGroupArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 链接组后台接口契约（docs/spec/console-master-detail）：后台列表限定本站并统计各组链接数，供“链接管理”左侧列表显示；
 * {@code linkCount} 未统计时不输出——链接组也经公开接口返回，不应多出一个恒为 null 的字段。
 * 站点上下文经 {@link Contexts} ThreadLocal 注入（standalone MockMvc 同线程执行）。
 */
class LinkGroupControllerTest {
    private static final Long SITE = 1L;
    private static final String URL = "/api/backend/ext/link-group";

    private LinkGroupService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(LinkGroupService.class);
        mvc = MockMvcBuilders.standaloneSetup(new LinkGroupController(service, mock(ManifestService.class))).build();
        Site site = new Site();
        site.setId(SITE);
        Contexts.setCurrentSite(site);
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentSite();
    }

    private static LinkGroup group(long id, Integer linkCount) {
        LinkGroup bean = new LinkGroup();
        bean.setId(id);
        bean.setSiteId(SITE);
        bean.setName("友情链接");
        bean.setAlias("friend-" + id);
        bean.setLinkCount(linkCount);
        return bean;
    }

    @Test
    void listIsSiteScopedAndCountsLinks() throws Exception {
        when(service.selectList(any(LinkGroupArgs.class))).thenReturn(List.of(group(10L, 3)));

        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$[0].linkCount").value(3));

        ArgumentCaptor<LinkGroupArgs> args = ArgumentCaptor.forClass(LinkGroupArgs.class);
        verify(service).selectList(args.capture());
        assertTrue(args.getValue().isQueryLinkCount());
        assertEquals(SITE, args.getValue().getQueryMap().get("EQ_siteId_Long"));
    }

    @Test
    void linkCountIsOmittedWhenNotCounted() throws Exception {
        when(service.selectList(any(LinkGroupArgs.class))).thenReturn(List.of(group(10L, null)));

        mvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alias").value("friend-10"))
                .andExpect(jsonPath("$[0].linkCount").doesNotExist());
    }
}
