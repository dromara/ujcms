package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.RegistryReload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主题文件监听测试：构成制品变更触发防抖重扫（来源 watcher）。
 * macOS 的 WatchService 为轮询实现（HIGH 灵敏度 2 秒），等待窗口给足余量；Linux inotify 即时。
 */
class ThemeFileWatcherTest {
    /**
     * 轮询实现 + 防抖的等待余量
     */
    private static final long AWAIT_MILLIS = 20_000;

    @TempDir
    Path templatesRoot;

    private ThemeFileWatcher watcher;

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    @Timeout(40)
    void compositionFileChangeTriggersWatcherRescan() throws IOException {
        Path pagesDir = templatesRoot.resolve("1/liquid-default/pages");
        Files.createDirectories(pagesDir);

        ManifestService manifestService = mock(ManifestService.class);
        ThemeDirScanner scanner = mock(ThemeDirScanner.class);
        when(scanner.resolveTemplatesRoot()).thenReturn(templatesRoot.toFile());

        watcher = new ThemeFileWatcher(manifestService, scanner, new ThemeLifecycleLock(), true);
        watcher.start();

        Files.writeString(pagesDir.resolve("home.manifest.json"),
                "{ \"name\": \"home\", \"template\": \"index\", \"slots\": {} }");

        verify(manifestService, timeout(AWAIT_MILLIS))
                .scanAll(eq(RegistryReload.SOURCE_WATCHER), isNull());
    }

    @Test
    @Timeout(10)
    void disabledWatcherNeverStarts() {
        ManifestService manifestService = mock(ManifestService.class);
        ThemeDirScanner scanner = mock(ThemeDirScanner.class);
        watcher = new ThemeFileWatcher(manifestService, scanner, new ThemeLifecycleLock(), false);
        watcher.start();
        org.junit.jupiter.api.Assertions.assertFalse(watcher.isRunning());
    }
}
