package com.ujcms.cms.core.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.composition.SeedOverwrite;
import com.ujcms.cms.core.domain.ThemePackage;
import com.ujcms.cms.core.marketplace.MarketplaceClient.DownloadGrant;
import com.ujcms.cms.core.marketplace.MarketplaceClient.DownloadedPackage;
import com.ujcms.cms.core.service.ThemeInstallService;
import com.ujcms.cms.core.service.ThemeInstallService.ActivateTarget;
import com.ujcms.cms.core.service.ThemeInstallService.SeedScope;
import com.ujcms.cms.core.service.ThemeInstallService.UpdateReport;
import com.ujcms.cms.core.service.ThemePackageService;

/**
 * {@link MarketplaceInstallService} 编排回归（install-flow P3 起写穿主题包库）：
 * sha256 不合即拒（D-K7，可疑包绝不入库）、下载即入库并从库内副本委派既有安装/更新管线
 * （D-C1-5 及其更新面）、库命中零重传（slug+版本 / 授权散列两条命中路径）、下载临时文件必清、
 * 不可达转译为可诉 {@code marketplace.error.*}；C2：已连接时下载授权带令牌
 * （经 withAccessToken），连接失效退回匿名。
 */
class MarketplaceInstallServiceTest {
    private MarketplaceClient client;
    private MarketplaceConnectionService connectionService;
    private ThemeInstallService themeInstallService;
    private ThemePackageService packageService;
    private MarketplaceInstallService service;

    /**
     * 库内副本的物化路径（withPackageFile 应用动作时使用的稳定路径）
     */
    private Path libraryZip;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(MarketplaceClient.class);
        connectionService = mock(MarketplaceConnectionService.class);
        themeInstallService = mock(ThemeInstallService.class);
        packageService = mock(ThemePackageService.class);
        when(client.isEnabled()).thenReturn(true);
        when(connectionService.isConnected()).thenReturn(false);
        libraryZip = Files.createTempFile("market-test-library-", ".zip");
        // 缺省：库无命中；入库返回行；withPackageFile 以库内路径应用动作
        when(packageService.findBySlugAndVersion(anyString(), anyString())).thenReturn(null);
        when(packageService.findBySlugAndSha256(anyString(), anyString())).thenReturn(null);
        when(packageService.save(any(Path.class), anyString(), anyString(), anyString()))
                .thenAnswer((InvocationOnMock inv) -> packageRow("corpus-theme", "1.0.0"));
        when(packageService.withPackageFile(any(), any())).thenAnswer((InvocationOnMock inv) ->
                inv.<Function<Path, Object>>getArgument(1).apply(libraryZip));
        service = new MarketplaceInstallService(client, connectionService, themeInstallService, packageService);
    }

    private static ThemePackage packageRow(String name, String version) {
        ThemePackage pkg = new ThemePackage();
        pkg.setId(1L);
        pkg.setName(name);
        pkg.setVersion(version);
        pkg.setSource(ThemePackage.SOURCE_MARKETPLACE);
        return pkg;
    }

    private Path tempZip() throws Exception {
        return Files.createTempFile("market-test-", ".zip");
    }

    @Test
    void disabledRefusesInstall() {
        when(client.isEnabled()).thenReturn(false);
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> service.install(
                "corpus-theme", null, 1L, "corpus-theme", null, SeedScope.NONE));
        assertEquals("marketplace.error.disabled", e.getMessage());
    }

    @Test
    void sha256MismatchRefusesInstallAndCleansUp() throws Exception {
        Path zip = tempZip();
        when(client.requestDownload("corpus-theme", null, null))
                .thenReturn(new DownloadGrant("/files/packages/x.zip", "aa".repeat(32), 10, null));
        when(client.downloadPackage(any())).thenReturn(new DownloadedPackage(zip, "bb".repeat(32), 10));

        MarketplaceException e = assertThrows(MarketplaceException.class, () -> service.install(
                "corpus-theme", null, 1L, "corpus-theme", null, SeedScope.NONE));
        assertEquals("marketplace.error.sha256Mismatch", e.getMessage());
        // 可疑包绝不入库、绝不进安装管线
        verify(packageService, never()).save(any(Path.class), anyString(), any(), any());
        verify(themeInstallService, never()).install(any(Path.class), anyLong(), anyString(),
                any(), any());
        assertFalse(Files.exists(zip), "downloaded temp file must be cleaned up after refusal");
    }

    @Test
    void matchingShaSavesToLibraryAndDelegatesToInstallPipeline() throws Exception {
        Path zip = tempZip();
        String sha = "ab".repeat(32);
        ThemeInstallReport report = mock(ThemeInstallReport.class);
        when(client.requestDownload("corpus-theme", "1.2.3", null))
                .thenReturn(new DownloadGrant("/files/packages/x.zip", sha, 10, null));
        when(client.downloadPackage(any())).thenReturn(new DownloadedPackage(zip, sha, 10));
        when(themeInstallService.install(eq(libraryZip), eq(2L), eq("my-theme"), eq(ActivateTarget.DESKTOP),
                eq(SeedScope.ALL))).thenReturn(report);

        ThemeInstallReport result = service.install("corpus-theme", "1.2.3", 2L, "my-theme",
                ActivateTarget.DESKTOP, SeedScope.ALL);
        assertSame(report, result);
        // 写穿：下载即入库（来源 marketplace + slug 归档），随后从库内副本安装
        verify(packageService).save(eq(zip), eq(ThemePackage.SOURCE_MARKETPLACE),
                eq("corpus-theme"), eq("corpus-theme"));
        assertFalse(Files.exists(zip), "downloaded temp file must be cleaned up after install");
    }

    @Test
    void libraryHitByVersionSkipsMarketplaceEntirely() throws Exception {
        // 零重传（§6）：slug+版本命中库（文件在场）→ 不申请授权、不下载，市场禁用也可安装
        when(client.isEnabled()).thenReturn(false);
        ThemePackage hit = packageRow("corpus-theme", "1.2.3");
        when(packageService.findBySlugAndVersion("corpus-theme", "1.2.3")).thenReturn(hit);
        when(packageService.packageFileExists(hit)).thenReturn(true);
        ThemeInstallReport report = mock(ThemeInstallReport.class);
        when(themeInstallService.install(eq(libraryZip), eq(2L), eq("my-theme"), isNull(),
                eq(SeedScope.ALL))).thenReturn(report);

        assertSame(report, service.install("corpus-theme", "1.2.3", 2L, "my-theme",
                null, SeedScope.ALL));
        verify(client, never()).requestDownload(anyString(), any(), any());
        verify(client, never()).downloadPackage(any());
        verify(packageService, never()).save(any(Path.class), anyString(), any(), any());
    }

    @Test
    void grantShaHitSkipsDownload() throws Exception {
        // 零重传（§6）：未指定版本时授权散列命中库 → 免下载
        String sha = "ab".repeat(32);
        when(client.requestDownload("corpus-theme", null, null))
                .thenReturn(new DownloadGrant("/files/packages/x.zip", sha, 10, null));
        ThemePackage hit = packageRow("corpus-theme", "1.2.3");
        when(packageService.findBySlugAndSha256("corpus-theme", sha)).thenReturn(hit);
        when(packageService.packageFileExists(hit)).thenReturn(true);
        ThemeInstallReport report = mock(ThemeInstallReport.class);
        when(themeInstallService.install(eq(libraryZip), eq(1L), eq("corpus-theme"), isNull(),
                eq(SeedScope.NONE))).thenReturn(report);

        assertSame(report, service.install("corpus-theme", null, 1L, "corpus-theme",
                null, SeedScope.NONE));
        verify(client, never()).downloadPackage(any());
        verify(packageService, never()).save(any(Path.class), anyString(), any(), any());
    }

    @Test
    void updateSavesToLibraryAndDelegatesToUpdatePipeline() throws Exception {
        Path zip = tempZip();
        String sha = "cd".repeat(32);
        UpdateReport report = mock(UpdateReport.class);
        when(client.requestDownload("edu-campus", "2.1.0", null))
                .thenReturn(new DownloadGrant("/files/packages/y.zip", sha, 10, null));
        when(client.downloadPackage(any())).thenReturn(new DownloadedPackage(zip, sha, 10));
        when(themeInstallService.update(eq(libraryZip), eq(3L), eq("edu-campus"), eq(SeedScope.NONE),
                eq(SeedOverwrite.FILL_ONLY))).thenReturn(report);

        UpdateReport result = service.update("edu-campus", "2.1.0", 3L, "edu-campus",
                SeedScope.NONE, SeedOverwrite.FILL_ONLY);
        assertSame(report, result);
        verify(packageService).save(eq(zip), eq(ThemePackage.SOURCE_MARKETPLACE),
                eq("edu-campus"), eq("edu-campus"));
        assertFalse(Files.exists(zip), "downloaded temp file must be cleaned up after update");
    }

    @Test
    void updateSha256MismatchRefusesAndNeverTouchesPipeline() throws Exception {
        Path zip = tempZip();
        when(client.requestDownload("edu-campus", null, null))
                .thenReturn(new DownloadGrant("/files/packages/y.zip", "aa".repeat(32), 10, null));
        when(client.downloadPackage(any())).thenReturn(new DownloadedPackage(zip, "bb".repeat(32), 10));

        MarketplaceException e = assertThrows(MarketplaceException.class, () -> service.update(
                "edu-campus", null, 3L, "edu-campus", SeedScope.NONE, SeedOverwrite.FILL_ONLY));
        assertEquals("marketplace.error.sha256Mismatch", e.getMessage());
        verify(packageService, never()).save(any(Path.class), anyString(), any(), any());
        verify(themeInstallService, never()).update(any(Path.class), anyLong(), anyString(), any(), any());
        assertFalse(Files.exists(zip));
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectedGrantCarriesAccessToken() throws Exception {
        Path zip = tempZip();
        String sha = "ef".repeat(32);
        ThemeInstallReport report = mock(ThemeInstallReport.class);
        when(connectionService.isConnected()).thenReturn(true);
        // withAccessToken 把令牌交给回调（刷新/401 重试都在连接服务内部）
        when(connectionService.withAccessToken(any())).thenAnswer((InvocationOnMock inv) ->
                ((Function<String, Object>) inv.getArgument(0)).apply("token-1"));
        when(client.requestDownload("edu-campus", null, "token-1"))
                .thenReturn(new DownloadGrant("/files/packages/y.zip", sha, 10, null));
        when(client.downloadPackage(any())).thenReturn(new DownloadedPackage(zip, sha, 10));
        when(themeInstallService.install(eq(libraryZip), eq(1L), eq("edu-campus"), isNull(),
                eq(SeedScope.NONE))).thenReturn(report);

        assertSame(report, service.install("edu-campus", null, 1L, "edu-campus", null, SeedScope.NONE));
        verify(client, never()).requestDownload(anyString(), any(), isNull());
    }

    @Test
    void unusableConnectionFallsBackToAnonymousGrant() throws Exception {
        Path zip = tempZip();
        String sha = "ab".repeat(32);
        ThemeInstallReport report = mock(ThemeInstallReport.class);
        when(connectionService.isConnected()).thenReturn(true);
        when(connectionService.withAccessToken(any())).thenThrow(new MarketplaceException(
                "marketplace.error.reconnectRequired", "stale connection"));
        when(client.requestDownload("corpus-theme", null, null))
                .thenReturn(new DownloadGrant("/files/packages/x.zip", sha, 10, null));
        when(client.downloadPackage(any())).thenReturn(new DownloadedPackage(zip, sha, 10));
        when(themeInstallService.install(eq(libraryZip), eq(1L), eq("corpus-theme"), isNull(),
                eq(SeedScope.NONE))).thenReturn(report);

        assertSame(report, service.install("corpus-theme", null, 1L, "corpus-theme", null, SeedScope.NONE));
    }

    @Test
    void unavailableStoreBecomesActionableOfflineError() {
        when(client.requestDownload(anyString(), any(), any())).thenThrow(new MarketplaceUnavailableException(
                MarketplaceUnavailableException.Reason.OFFLINE, "unreachable", (Throwable) null));
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> service.install(
                "corpus-theme", null, 1L, "corpus-theme", null, SeedScope.NONE));
        assertEquals("marketplace.error.offline", e.getMessage());
    }

    @Test
    void rateLimitedStoreBecomesActionableError() {
        when(client.requestDownload(anyString(), any(), any())).thenThrow(new MarketplaceUnavailableException(
                MarketplaceUnavailableException.Reason.RATE_LIMITED, "429", 30));
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> service.install(
                "corpus-theme", null, 1L, "corpus-theme", null, SeedScope.NONE));
        assertEquals("marketplace.error.rateLimited", e.getMessage());
    }
}
