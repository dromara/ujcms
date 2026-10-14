package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 沙箱站点成员 seam 缺省行为测试（sandbox-preview-site §5 / S3）：无商业装配（社区/基础版）时
 * 回落 {@link SandboxSiteRegistry#NONE}——任何站点都不是沙箱、失效为空操作，前台渲染零感知。
 */
class SandboxSiteRegistryTest {

    @Test
    void noneNeverMatchesAndInvalidateIsNoop() {
        assertFalse(SandboxSiteRegistry.NONE.isSandboxSite(1L));
        assertFalse(SandboxSiteRegistry.NONE.isSandboxSite(999L));
        // 缺省 invalidate 空操作：不抛异常、行为不变
        SandboxSiteRegistry.NONE.invalidate();
        assertFalse(SandboxSiteRegistry.NONE.isSandboxSite(1L));
    }
}
