package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.SeedOverwrite;
import com.ujcms.cms.core.composition.ThemeImpactReport;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.service.ThemeInstallService;
import com.ujcms.cms.core.service.ThemeInstallService.ActivateTarget;
import com.ujcms.cms.core.service.ThemeInstallService.SeedScope;
import com.ujcms.cms.core.service.ThemePackageService;
import com.ujcms.cms.core.service.ThemeSeedExportService;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 激活目标线值绑定（theme-marketplace §3 R8）：激活 / 影响预览体的 {@code target}（小写
 * {@code desktop|mobile|both}，缺省 desktop）与上传安装表单的 {@code activate}（缺省只安装，
 * 未知值 400）。standalone MockMvc，服务层全 mock。
 */
class ThemeControllerActivateTargetTest {
    private ThemeInstallService service;
    private ThemePackageService packageService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ThemeInstallService.class);
        packageService = mock(ThemePackageService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ThemeController(service, mock(ThemeSettingsService.class),
                mock(ManifestService.class), mock(ThemeSeedExportService.class), packageService)).build();
        when(service.previewActivate(any(), any(), any())).thenReturn(
                new ThemeImpactReport(List.of(), List.of(), 0, 0, false, false, null, false, 0, 0, 0));
    }

    @Test
    void activateBindsLowercaseTarget() throws Exception {
        mvc.perform(put("/api/backend/core/theme/1/active").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeName\":\"mobile\",\"target\":\"mobile\"}"))
                .andExpect(status().isOk());
        verify(service).activate(1L, "mobile", ActivateTarget.MOBILE, SeedScope.NONE,
                SeedOverwrite.FILL_ONLY);
    }

    @Test
    void activateDefaultsToDesktopWhenTargetAbsent() throws Exception {
        mvc.perform(put("/api/backend/core/theme/1/active").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeName\":\"default\"}"))
                .andExpect(status().isOk());
        verify(service).activate(1L, "default", ActivateTarget.DESKTOP, SeedScope.NONE,
                SeedOverwrite.FILL_ONLY);
    }

    @Test
    void previewActivateBindsBothAndRejectsUnknownTarget() throws Exception {
        mvc.perform(post("/api/backend/core/theme/1/preview-activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeName\":\"default\",\"target\":\"both\"}"))
                .andExpect(status().isOk());
        verify(service).previewActivate(1L, "default", ActivateTarget.BOTH);

        mvc.perform(post("/api/backend/core/theme/1/preview-activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeName\":\"default\",\"target\":\"bogus\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void installFormActivateParamParsesOrIsAbsent() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "t.zip", "application/zip", new byte[]{1});
        mvc.perform(multipart("/api/backend/core/theme/install").file(file)
                        .param("siteId", "1").param("themeName", "t").param("activate", "mobile"))
                .andExpect(status().isOk());
        verify(packageService).installFromUpload(any(), eq(1L), eq("t"), eq(ActivateTarget.MOBILE),
                eq(SeedScope.NONE));

        mvc.perform(multipart("/api/backend/core/theme/install").file(file)
                        .param("siteId", "1").param("themeName", "t"))
                .andExpect(status().isOk());
        verify(packageService).installFromUpload(any(), eq(1L), eq("t"), isNull(), eq(SeedScope.NONE));
    }

    @Test
    void installFormRejectsUnknownActivateValue() {
        MockMultipartFile file = new MockMultipartFile("file", "t.zip", "application/zip", new byte[]{1});
        // standalone MockMvc 无全局异常映射：Http400Exception 以原样冒出即为「400 路径」
        Exception ex = assertThrows(Exception.class, () -> mvc.perform(
                multipart("/api/backend/core/theme/install").file(file)
                        .param("siteId", "1").param("themeName", "t").param("activate", "bogus")));
        assertInstanceOf(Http400Exception.class, ex.getCause() != null ? ex.getCause() : ex);
        verify(packageService, never()).installFromUpload(any(), any(), any(), any(), any());
    }
}
