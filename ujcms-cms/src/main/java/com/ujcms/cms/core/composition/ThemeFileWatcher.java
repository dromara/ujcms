package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.RegistryReload;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.stream.Stream;

/**
 * 主题构成文件监听器（§9.5 检测路径 B）。监听模板存储根目录下各主题的
 * {@code pages/}、{@code shapes/} 目录与 {@code theme.manifest.json}，250ms 防抖后全量重扫。
 * 重扫失败保留旧快照并记录审计（§9.3）。
 *
 * <p>注册整棵主题目录树（深度受限）并在目录新建时补注册；非构成路径的事件被过滤掉。
 *
 * @author PONY
 */
@Component
public class ThemeFileWatcher implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(ThemeFileWatcher.class);

    /**
     * 防抖间隔（§9.5）
     */
    private static final long DEBOUNCE_MILLIS = 250;
    /**
     * 注册深度：根(0)/站点(1)/主题(2)/pages|shapes(3)
     */
    private static final int MAX_REGISTER_DEPTH = 3;

    private final ManifestService manifestService;
    private final ThemeDirScanner themeDirScanner;
    private final ThemeLifecycleLock lifecycleLock;
    private final boolean enabled;

    @Nullable
    private WatchService watchService;
    @Nullable
    private Thread watchThread;
    private volatile boolean running;
    /**
     * 本轮防抖窗口内变更涉及的站点ID（单一监听线程访问，无需同步）。重扫时锁住这些站点，与安装互斥（§12.5）。
     */
    private final java.util.Set<Long> dirtySites = new java.util.LinkedHashSet<>();

    public ThemeFileWatcher(ManifestService manifestService, ThemeDirScanner themeDirScanner,
                            ThemeLifecycleLock lifecycleLock,
                            @Value("${ujcms.composition.watch-enabled:true}") boolean enabled) {
        this.manifestService = manifestService;
        this.themeDirScanner = themeDirScanner;
        this.lifecycleLock = lifecycleLock;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled || running) {
            return;
        }
        File root = themeDirScanner.resolveTemplatesRoot();
        if (root == null || !root.isDirectory()) {
            logger.info("Theme file watcher disabled: templates root not present");
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerTree(root.toPath(), 0);
        } catch (IOException e) {
            logger.warn("Cannot start theme file watcher: {}", e.getMessage());
            return;
        }
        running = true;
        watchThread = new Thread(this::watchLoop, "composition-theme-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
        logger.info("Theme file watcher started on {}", root);
    }

    @Override
    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.debug("Closing watch service: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 启动扫描（ApplicationRunner）之后再开始监听
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void watchLoop() {
        WatchService service = this.watchService;
        if (service == null) {
            return;
        }
        boolean dirty = false;
        while (running) {
            WatchKey key;
            try {
                // 防抖：有未处理变更时短轮询，静默 250ms 后才触发重扫
                key = dirty ? service.poll(DEBOUNCE_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
                        : service.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.nio.file.ClosedWatchServiceException e) {
                return;
            }
            if (key == null) {
                // 防抖窗口内无新事件 → 触发重扫
                if (dirty) {
                    dirty = false;
                    rescan();
                }
                continue;
            }
            Path dir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                if (handleEvent(dir, event)) {
                    dirty = true;
                }
            }
            key.reset();
        }
    }

    /**
     * @return 该事件是否与构成制品相关（需要触发重扫）
     */
    private boolean handleEvent(Path dir, WatchEvent<?> event) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            return true;
        }
        Object context = event.context();
        if (!(context instanceof Path name)) {
            return false;
        }
        Path path = dir.resolve(name);
        // 新建目录补注册（主题安装、pages/shapes 目录新建）
        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(path)) {
            try {
                registerTree(path, depthOf(path));
            } catch (IOException e) {
                logger.debug("Cannot register new dir {}: {}", path, e.getMessage());
            }
            if (isCompositionDir(path)) {
                recordDirtySite(path);
                return true;
            }
            return false;
        }
        if (isCompositionArtifact(path)) {
            recordDirtySite(path);
            return true;
        }
        return false;
    }

    /**
     * 由变更路径推断站点ID（{@code templates/{siteId}/...} 的首段），登记到本轮脏站点集。
     */
    private void recordDirtySite(Path path) {
        File root = themeDirScanner.resolveTemplatesRoot();
        if (root == null) {
            return;
        }
        Path relative = root.toPath().relativize(path);
        if (relative.getNameCount() == 0) {
            return;
        }
        try {
            dirtySites.add(Long.parseLong(relative.getName(0).toString()));
        } catch (NumberFormatException e) {
            logger.debug("Non-numeric site dir in composition path: {}", path);
        }
    }

    /**
     * 构成制品判定：{@code pages/}、{@code shapes/} 下的 .json 文件，或主题根的 {@code theme.manifest.json}
     */
    private boolean isCompositionArtifact(Path path) {
        String fileName = path.getFileName().toString();
        if (ThemeDirScanner.THEME_MANIFEST_FILE.equals(fileName)) {
            return true;
        }
        Path parent = path.getParent();
        if (parent == null) {
            return false;
        }
        String parentName = parent.getFileName().toString();
        return fileName.endsWith(ThemeDirScanner.JSON_SUFFIX)
                && (ThemeDirScanner.PAGES_DIR.equals(parentName) || ThemeDirScanner.SHAPES_DIR.equals(parentName));
    }

    private boolean isCompositionDir(Path path) {
        String name = path.getFileName().toString();
        return ThemeDirScanner.PAGES_DIR.equals(name) || ThemeDirScanner.SHAPES_DIR.equals(name);
    }

    private int depthOf(Path path) {
        File root = themeDirScanner.resolveTemplatesRoot();
        if (root == null) {
            return MAX_REGISTER_DEPTH;
        }
        return root.toPath().relativize(path).getNameCount();
    }

    /**
     * macOS 无原生 WatchService（轮询实现，默认 10 秒）。反射取
     * {@code SensitivityWatchEventModifier.HIGH}（2 秒）以降低轮询延迟；
     * 该类已标记废弃、未来可能移除，反射缺失时安全退化为默认灵敏度（Linux inotify 不受影响）。
     */
    private static final WatchEvent.Modifier[] SENSITIVITY = sensitivityModifiers();

    private static WatchEvent.Modifier[] sensitivityModifiers() {
        try {
            Class<?> clazz = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier");
            return new WatchEvent.Modifier[]{(WatchEvent.Modifier) clazz.getField("HIGH").get(null)};
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new WatchEvent.Modifier[0];
        }
    }

    private void registerTree(Path start, int startDepth) throws IOException {
        WatchService service = this.watchService;
        if (service == null) {
            return;
        }
        try (Stream<Path> stream = Files.walk(start, MAX_REGISTER_DEPTH - startDepth)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    dir.register(service, new WatchEvent.Kind<?>[]{StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE}, SENSITIVITY);
                } catch (IOException e) {
                    logger.debug("Cannot watch dir {}: {}", dir, e.getMessage());
                }
            });
        }
    }

    /**
     * 重扫失败保留旧快照（{@code applySnapshot} 校验失败即拒绝），仅记录日志与审计。
     * 与各受影响站点的安装互斥（§12.5）：安装期间的监听变更被锁挡住、合并到安装后的重扫。
     */
    private void rescan() {
        java.util.Set<Long> sites = new java.util.LinkedHashSet<>(dirtySites);
        dirtySites.clear();
        try {
            lifecycleLock.withSiteLocks(sites,
                    () -> manifestService.scanAll(RegistryReload.SOURCE_WATCHER, null));
        } catch (RuntimeException e) {
            logger.warn("Theme watcher rescan failed, old snapshot stays: {}", e.getMessage());
        }
    }
}
