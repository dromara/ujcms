package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.composition.PlacementService;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * core 保留的面板反向查询端点（主题详情“来源面板”所用）。编辑端点用例随控制器移入 T3
 * （{@code PageRegionT3ControllerTest}）。站点上下文经 {@link Contexts} ThreadLocal 注入（standalone MockMvc 同线程执行）。
 */
class PageRegionControllerTest {
    private static final Long SITE = 1L;

    private PlacementService placementService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        placementService = mock(PlacementService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PageRegionController(placementService)).build();
        Site site = new Site();
        site.setId(SITE);
        site.setTheme("default");
        site.setMobileTheme("default");
        Contexts.setCurrentSite(site);
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentSite();
    }

    @Test
    void panelEndpointFiltersBySeedOriginAndReturnsPanelRows() throws Exception {
        // 面板反向查询（theme-marketplace §7.2 来源可见性）：按来源标记过滤本站面板投放
        Placement seeded = new Placement();
        seeded.setId(9001L);
        seeded.setSiteId(SITE);
        seeded.setPageRef("home");
        seeded.setSlotName("body");
        seeded.setPanelShape("RichHtml");
        seeded.setOrigin("theme:default");
        seeded.setOriginId("home#body#r1#0#0");
        Placement operator = new Placement();
        operator.setId(9002L);
        operator.setSiteId(SITE);
        operator.setPageRef("home");
        operator.setSlotName("body");
        operator.setPanelShape("RichHtml");
        when(placementService.listBySiteId(SITE)).thenReturn(List.of(seeded, operator));

        mvc.perform(get("/api/backend/core/page-region/panel").param("origin", "theme:default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("9001"))
                .andExpect(jsonPath("$[0].region").value("body"))
                .andExpect(jsonPath("$[0].component").value("RichHtml"))
                .andExpect(jsonPath("$[0].originId").value("home#body#r1#0#0"))
                .andExpect(jsonPath("$[0].archived").value(false));
    }
}
