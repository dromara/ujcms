package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.composition.PreviewUrlResolver;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.security.PreviewTokenService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.web.support.BackendInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static com.ujcms.cms.core.support.Constants.DEMO_USER_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 预览令牌端点契约：取 GET——演示站只读账号（{@code ujcms.demo=true} + 固定 ID 10，{@link BackendInterceptor}
 * 仅放行 GET）也要能签发令牌，否则构建器预览画布与主题试看对演示账号一律 403。
 * 请求经真实拦截器（standalone MockMvc 同线程执行，登录身份经 {@link SecurityContextHolder} 注入）。
 */
class CompositionControllerTest {
    private static final String USERNAME = "demo";
    private static final Long ORG = 1L;

    private PreviewTokenService previewTokenService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        previewTokenService = mock(PreviewTokenService.class);
        User demo = new User();
        demo.setId((long) DEMO_USER_ID);
        demo.setUsername(USERNAME);
        demo.setOrgId(ORG);
        UserService userService = mock(UserService.class);
        when(userService.selectByUsername(USERNAME)).thenReturn(demo);
        SiteService siteService = mock(SiteService.class);
        when(siteService.findFirstByUserIdAndOrgId(demo.getId(), ORG)).thenReturn(new Site());
        Props props = new Props();
        props.setDemo(true);
        mvc = MockMvcBuilders
                .standaloneSetup(new CompositionController(previewTokenService, mock(PreviewUrlResolver.class)))
                .addInterceptors(new BackendInterceptor(userService, siteService, mock(OrgService.class), props))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USERNAME, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readOnlyDemoUserCanMintPreviewToken() throws Exception {
        when(previewTokenService.mint(USERNAME)).thenReturn(new PreviewTokenService.Token("ptoken", 300));

        mvc.perform(get("/api/backend/core/composition/preview-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("ptoken"))
                .andExpect(jsonPath("$.expiresIn").value(300));
    }
}
