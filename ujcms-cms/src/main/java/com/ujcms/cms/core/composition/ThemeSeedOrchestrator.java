package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.ThemeContentSeedImporter.ContentSeedPlan;
import com.ujcms.cms.core.composition.ThemeContentSeedImporter.ContentSeedResult;
import com.ujcms.cms.core.composition.ThemeSeedImporter.CompositionSeedPlan;
import com.ujcms.cms.core.composition.ThemeSeedImporter.SeedImportResult;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 主题种子编排器（theme-marketplace §5.4）：按固定顺序
 * <b>内容（栏目 → 文章 → 专题）→ 构成（区域 + 面板）</b>运行两个导入器——
 * 内容先行，构成种子里的 {@code {{channel:<alias>}}} / {@code {{article:<originId>}}}
 * 令牌才有解析目标（§5.2）。
 *
 * <p>刻意无事务边界：内容导入逐实体提交（断点续传），构成导入保留其既有单事务。
 *
 * <p>A3 起同时编排只读的<b>干跑计划</b>（{@link #plan}，§6.2）：两个导入器的逐类别对比 +
 * 内容资源总字节数，供安装/激活/更新/重置的确认对话框使用。
 *
 * @author PONY
 */
@Component
public class ThemeSeedOrchestrator {
    private final ThemeContentSeedImporter contentSeedImporter;
    private final ThemeSeedImporter seedImporter;

    public ThemeSeedOrchestrator(ThemeContentSeedImporter contentSeedImporter, ThemeSeedImporter seedImporter) {
        this.contentSeedImporter = contentSeedImporter;
        this.seedImporter = seedImporter;
    }

    /**
     * 运行种子导入（只填空，A2 缺省行为）。
     */
    public SeedRunResult importSeed(Long siteId, String themeName, File themeDir, String templateStoreUrl,
                                    @Nullable Long userId, boolean includeContent) {
        return importSeed(siteId, themeName, themeDir, templateStoreUrl, userId, includeContent,
                SeedOverwrite.FILL_ONLY);
    }

    /**
     * 运行种子导入。
     *
     * @param siteId           站点ID
     * @param themeName        主题名（目录名）
     * @param themeDir         主题目录
     * @param templateStoreUrl 模板存储 URL 前缀（{@code {{theme}}} 展开用）
     * @param userId           操作员ID
     * @param includeContent   是否包含内容种子（seedScope=all，R5）
     * @param overwrite        既有本源行的覆盖策略（§6.1，A3）
     * @return 内容 + 构成的合并结果
     */
    public SeedRunResult importSeed(Long siteId, String themeName, File themeDir, String templateStoreUrl,
                                    @Nullable Long userId, boolean includeContent, SeedOverwrite overwrite) {
        ContentSeedResult content = includeContent
                ? contentSeedImporter.importContent(siteId, themeName, themeDir, userId, overwrite)
                : ContentSeedResult.EMPTY;
        SeedImportResult composition = seedImporter.importSeed(siteId, themeName, themeDir, templateStoreUrl,
                userId, overwrite);
        return new SeedRunResult(content, composition);
    }

    /**
     * 种子导入干跑计划（只读，§6.2 {@code seed/preview}）：内容 + 构成的逐类别对比 +
     * 内容资源总字节数 + 别名冲突明细。
     */
    public SeedPlan plan(Long siteId, String themeName, File themeDir, String templateStoreUrl) {
        ContentSeedPlan content = contentSeedImporter.plan(siteId, themeName, themeDir);
        CompositionSeedPlan composition = seedImporter.plan(siteId, themeName, themeDir, templateStoreUrl);
        List<SeedPlan.Collision> collisions = new ArrayList<>(content.collisions());
        collisions.addAll(composition.collisions());
        return new SeedPlan(content.channels(), content.articles(), content.topics(), content.blocks(),
                content.votes(), content.surveys(), content.forms(), content.linkGroups(),
                content.models(), content.dicts(),
                composition.panels(), composition.regions(), assetBytes(themeDir), collisions);
    }

    /**
     * 种子声明的内容条目数（影响报告 {@code seedContent}，§6.2——只数种子文件，不查库）。
     */
    public ThemeContentSeedImporter.DeclaredContent declaredContent(File themeDir) {
        return contentSeedImporter.countDeclared(themeDir);
    }

    /**
     * 内容种子资源总字节数（{@code seed/content/assets/**}，导入时复制进上传存储）。
     */
    private static long assetBytes(File themeDir) {
        File assetsDir = new File(new File(new File(themeDir, "seed"), "content"), "assets");
        return sumBytes(assetsDir);
    }

    private static long sumBytes(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        long total = 0;
        for (File file : files) {
            total += file.isDirectory() ? sumBytes(file) : file.length();
        }
        return total;
    }

    /**
     * 种子运行合并结果（§5.4 SeedImportResult v2：内容按类别计数 + 构成计数）
     */
    public record SeedRunResult(ContentSeedResult content, SeedImportResult composition) {
    }
}
