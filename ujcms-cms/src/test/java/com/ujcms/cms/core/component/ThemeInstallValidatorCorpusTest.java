package com.ujcms.cms.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 共享夹具语料库回归（remote-marketplace D-C1-7，履约 marketplace 侧 D-M2-1）：
 * {@code src/test/resources/marketplace/fixtures/packages/} 下的包必须与 marketplace 仓库的
 * {@code PackageValidator}（{@link ThemeInstallValidator} 的逐规则移植）判定一致——期望值
 * 以语料库 README（{@code ../ujcms-marketplace/contracts/fixtures/packages/README.md}）为准。
 *
 * <p><b>锁步义务：</b>本校验器行为变更时，必须在同一变更窗口更新 marketplace 侧移植与语料库
 * （其 {@code build-fixtures.mjs} 重新生成 ZIP），再运行
 * {@code scripts/sync-marketplace-contracts.mjs} 回同步至本仓库。
 * 语料漂移会在任一侧构建中失败。
 *
 * <p><b>范围注记：</b>语料库只含两侧判定必然一致的包。清单（theme.manifest.json）在 CMS 安装
 * 路径是可选的、在 marketplace 摄取侧是必填的——清单缺失类规则刻意不入语料库，由各自本地
 * 测试覆盖（本侧见 {@link ThemeInstallValidatorTest}）。
 */
class ThemeInstallValidatorCorpusTest {
    /** 语料库假定的预算：两侧默认 50 MB（CMS {@code ujcms.theme.install.maxSizeMb} 默认值） */
    private static final long MAX_BYTES = 50L * 1024 * 1024;

    private final ThemeInstallValidator validator = new ThemeInstallValidator(new ObjectMapper());

    private static byte[] corpusBytes(String name) {
        try (InputStream in = ThemeInstallValidatorCorpusTest.class
                .getResourceAsStream("/marketplace/fixtures/packages/" + name)) {
            assertNotNull(in, "corpus fixture missing from test classpath: " + name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ThemeInstallReport validate(byte[] zip) {
        return validator.validate(new ByteArrayInputStream(zip), zip.length, MAX_BYTES);
    }

    private void assertRejected(String fixture, String expectedCode) {
        byte[] zip = corpusBytes(fixture);
        ThemeInstallException e = assertThrows(ThemeInstallException.class, () -> validate(zip));
        assertEquals(expectedCode, e.getMessage(), fixture);
    }

    @Test
    void corpusValidThemeIsAccepted() {
        ThemeInstallReport report = validate(corpusBytes("valid-theme.zip"));
        assertEquals(2, report.getTemplateFileCount());
        assertTrue(report.isHasManifest());
        assertNotNull(report.getMetadata());
        assertEquals("corpus-theme", report.getMetadata().getThemeName());
        assertFalse(report.getMetadata().isDataOnly());
        assertTrue(report.getWarnings().isEmpty());
    }

    @Test
    void corpusValidDataOnlyIsAccepted() {
        ThemeInstallReport report = validate(corpusBytes("valid-data-only.zip"));
        assertEquals(0, report.getTemplateFileCount());
        assertNotNull(report.getMetadata());
        assertTrue(report.getMetadata().isDataOnly());
        assertTrue(report.getWarnings().isEmpty());
    }

    @Test
    void corpusZipSlipIsRejected() {
        assertRejected("reject-zip-slip.zip", "theme.install.error.zipSlip");
    }

    @Test
    void corpusOversizeIsRejected() {
        assertRejected("reject-oversize.zip", "theme.install.error.tooLarge");
    }

    @Test
    void corpusInvalidManifestIsRejected() {
        assertRejected("reject-invalid-manifest.zip", "theme.install.error.unknownKind");
    }

    @Test
    void corpusCompositionNonJsonIsRejected() {
        assertRejected("reject-composition-non-json.zip", "theme.install.error.compositionNonJson");
    }
}
