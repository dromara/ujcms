package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.service.ConfigService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 主题目录扫描器（§9.5 检测路径 A/B/C 共用）。遍历模板存储根目录下的
 * {@code {siteId}/{themeName}/} 主题目录，将 {@code shapes/*.json} 与
 * {@code pages/*.manifest.json}（均为纯数据 JSON，§8.1）装入快照构建器，
 * 命名空间为规范化主题键 {@code {siteId}/{themeName}}。
 *
 * <p>主题渲染器（templates/、*.html）与引擎检测不在此处理——引擎缓存失效仍由安装器独占负责（§9.5）。
 *
 * @author PONY
 */
@Component
public class ThemeDirScanner {
    private static final Logger logger = LoggerFactory.getLogger(ThemeDirScanner.class);

    /**
     * 主题清单文件名（原 {@code template.json}，§8.2 更名）
     */
    public static final String THEME_MANIFEST_FILE = "theme.manifest.json";
    /**
     * 主题内 Shape 目录名
     */
    public static final String SHAPES_DIR = "shapes";
    /**
     * 主题内页面清单目录名
     */
    public static final String PAGES_DIR = "pages";
    /**
     * 页面清单文件后缀
     */
    public static final String MANIFEST_SUFFIX = ".manifest.json";
    /**
     * Shape 文件后缀
     */
    public static final String JSON_SUFFIX = ".json";

    private final ShapeLoader shapeLoader;
    private final ManifestLoader manifestLoader;
    private final ThemeManifestLoader themeManifestLoader;
    private final ConfigService configService;
    private final ResourceLoader resourceLoader;

    public ThemeDirScanner(ShapeLoader shapeLoader, ManifestLoader manifestLoader,
                           ThemeManifestLoader themeManifestLoader,
                           ConfigService configService, ResourceLoader resourceLoader) {
        this.shapeLoader = shapeLoader;
        this.manifestLoader = manifestLoader;
        this.themeManifestLoader = themeManifestLoader;
        this.configService = configService;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 扫描全部已安装主题目录并装入构建器。模板存储根目录不存在时静默跳过（无主题可装载）。
     */
    public void scanInto(RegistrySnapshot.Builder builder) {
        File root = resolveTemplatesRoot();
        if (root == null || !root.isDirectory()) {
            logger.debug("Templates root not present, skip theme scan");
            return;
        }
        scan(root, builder);
    }

    /**
     * 扫描指定模板存储根目录（测试可直接传入临时目录）。
     *
     * @param templatesRoot 模板存储根目录（其下为 {@code {siteId}/{themeName}/}）
     * @param builder       快照构建器
     */
    public void scan(File templatesRoot, RegistrySnapshot.Builder builder) {
        for (File siteDir : listDirs(templatesRoot)) {
            for (File themeDir : listDirs(siteDir)) {
                // 跳过安装/更新的瞬态目录（{@code <theme>.staging-…} / {@code <theme>.backup-…}）——
                // 它们在替换窗口内短暂存在于站点目录下，被当作主题扫描会读到半写状态或旧副本（§12.5）。
                if (isTransientDir(themeDir.getName())) {
                    continue;
                }
                String themeKey = siteDir.getName() + "/" + themeDir.getName();
                loadTheme(themeKey, themeDir, builder);
            }
        }
    }

    /**
     * 装载单个主题目录的构成制品。
     *
     * @param themeKey 规范化主题键（{@code {siteId}/{themeName}}）
     * @param themeDir 主题目录
     * @param builder  快照构建器
     */
    public void loadTheme(String themeKey, File themeDir, RegistrySnapshot.Builder builder) {
        for (File file : listFiles(new File(themeDir, SHAPES_DIR), JSON_SUFFIX)) {
            String json = readFile(file);
            Shape shape = shapeLoader.load(json, Shape.SOURCE_THEME_PREFIX + themeKey, file.getPath());
            builder.addThemeShape(themeKey, shape, json, file.getPath());
        }
        for (File file : listFiles(new File(themeDir, PAGES_DIR), MANIFEST_SUFFIX)) {
            String json = readFile(file);
            PageManifest manifest = manifestLoader.load(json, file.getPath());
            builder.addThemeManifest(themeKey, manifest, json, file.getPath());
        }
        // 主题清单的 settings / layoutVariants 块（§8.2 / manifest 2.0 §4.1）：结构是文件事实、随清单热重载
        // （ThemeFileWatcher 监视整个主题目录，清单一改即重扫）。无清单或无对应块的主题不登记。
        File manifestFile = new File(themeDir, THEME_MANIFEST_FILE);
        if (manifestFile.isFile() && manifestFile.length() > 0) {
            ThemeManifestLoader.ParsedManifest parsed = themeManifestLoader.load(readFile(manifestFile),
                    manifestFile.getPath());
            builder.addThemeSettings(themeKey, parsed.settings(), parsed.settingsJson());
            builder.addThemeLayoutVariants(themeKey, parsed.layoutVariants(), parsed.layoutVariantsJson());
            builder.addThemeLinkSlots(themeKey, parsed.linkSlots(), parsed.linkSlotsJson());
        }
    }

    /**
     * 模板存储根目录（{@code Config.templateStorage.path} 经 ResourceLoader 解析）。
     * 无法解析为文件系统目录时返回 {@code null}。
     *
     * <p>不以 {@code resource.exists()} 判存在：Servlet 资源查询走 Tomcat 资源缓存——裸 JAR
     * 首装（install-flow P3 D6）时 {@code /themes} 在启动期尚不存在，内置包解包后的注册表
     * 重扫会命中陈旧的否定缓存（TTL 内 exists() 仍为 false），构成种子被整批误判为未声明。
     * {@code getFile()} 只做路径解析（getRealPath），{@code isDirectory()} 直查文件系统，不经缓存。
     */
    @Nullable
    public File resolveTemplatesRoot() {
        try {
            String storagePath = configService.getUnique().getTemplateStorage().getPath();
            Resource resource = resourceLoader.getResource(storagePath);
            File file = resource.getFile();
            return file.isDirectory() ? file : null;
        } catch (IOException | RuntimeException e) {
            logger.warn("Cannot resolve templates root: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 是否为安装/更新的瞬态目录名（{@code .staging-} / {@code .backup-} 中缀）。
     */
    public static boolean isTransientDir(String name) {
        return name.contains(".staging-") || name.contains(".backup-");
    }

    private static File[] listDirs(File dir) {
        File[] dirs = dir.listFiles(File::isDirectory);
        if (dirs == null) {
            return new File[0];
        }
        Arrays.sort(dirs, Comparator.comparing(File::getName));
        return dirs;
    }

    private static File[] listFiles(File dir, String suffix) {
        File[] files = dir.listFiles(file -> file.isFile() && file.getName().endsWith(suffix));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    private static String readFile(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CompositionLoadException(file.getPath(), e);
        }
    }
}
