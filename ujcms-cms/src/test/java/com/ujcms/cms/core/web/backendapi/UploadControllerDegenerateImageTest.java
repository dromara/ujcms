package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.web.support.ExceptionResolver;
import com.ujcms.common.image.ThumbnailatorHandler;
import com.ujcms.common.web.PathResolver;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 退化图片上传回归（docs/spec/upload-degenerate-image/plan.md）：签名完好但数据截断的图片曾在
 * 缩放解码时抛出带 EOFException 因由的异常，被 Spring 断连客户端静默机制吞成空 200；须显式 400。
 * standalone MockMvc 使用真实控制器 + 生产异常解析链（默认解析器在前，{@link ExceptionResolver}
 * 兜底），复现完整分发路径。
 */
class UploadControllerDegenerateImageTest {
    private MockMvc mvc;
    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() throws Exception {
        attachmentService = mock(AttachmentService.class);
        ConfigService configService = mock(ConfigService.class);
        Config config = new Config();
        String storeDir = Files.createTempDirectory("upload-store").toFile().getAbsolutePath();
        config.setUploadStorage(new Config.Storage(Config.Storage.TYPE_LOCAL, "file:" + storeDir, "/uploads"));
        when(configService.getUnique()).thenReturn(config);
        @SuppressWarnings("unchecked")
        ObjectProvider<ServletContext> servletContextProvider = mock(ObjectProvider.class);
        PathResolver pathResolver = new PathResolver(servletContextProvider);
        UploadController controller = new UploadController(attachmentService, pathResolver,
                new ThumbnailatorHandler(), configService, new Props());
        ExceptionHandlerExceptionResolver handlerResolver = new ExceptionHandlerExceptionResolver();
        handlerResolver.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setHandlerExceptionResolvers(Arrays.asList(handlerResolver,
                        new DefaultHandlerExceptionResolver(), new ExceptionResolver(configService)))
                .build();
        Site site = new Site();
        site.setId(1L);
        site.setConfig(config);
        Contexts.setCurrentSite(site);
        User user = new User();
        user.setId(1L);
        Contexts.setCurrentUser(user);
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentSite();
        Contexts.clearCurrentUser();
    }

    private static byte[] tinyPng() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private MvcResult upload(byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/backend/image-upload")
                        .file(new MockMultipartFile("file", "tiny.png", "image/png", bytes)))
                .andReturn();
    }

    @Test
    void truncatedPngIsRejectedWith400NotSilent200() throws Exception {
        // PNG 签名完好、数据截断：getFormatName 嗅探通过，ImageIO 解码必失败
        MvcResult result = upload(Arrays.copyOf(tinyPng(), 16));
        assertEquals(400, result.getResponse().getStatus());
        String message = result.getResponse().getErrorMessage();
        assertNotNull(message);
        assertTrue(message.contains("corrupt or unreadable"), message);
        verify(attachmentService, never()).insert(any());
    }

    @Test
    void tinyButValidPngStillUploads() throws Exception {
        // 退化 ≠ 尺寸小：合法 4×4 PNG 正常入库
        MvcResult result = upload(tinyPng());
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"url\""));
        verify(attachmentService).insert(any());
    }

    @Test
    void unreadableFileKeeps400FormatMessage() throws Exception {
        // 空文件在格式嗅探处即 400（既有路径不受解码校验影响）
        MvcResult result = upload(new byte[0]);
        assertEquals(400, result.getResponse().getStatus());
        assertEquals("Image format type not supported.", result.getResponse().getErrorMessage());
        verify(attachmentService, never()).insert(any());
    }
}
