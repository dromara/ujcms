package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 演示站两套种子的跨站键对齐测试（install-flow D50）。商业演示站是同一语言组里的两个站点：
 * 中文站播种 {@code default} 主题、英文站播种 {@code journal} 主题。两棵种子树的<b>内容清单</b>
 * 各自独立（Journal 是杂志站，没有视频/下载/文库栏目），但<b>跨站键</b>必须一致，两站才互通：
 * <ul>
 * <li>栏目按别名配对（{@code LangAlternateService}）——对应栏目别名相同；</li>
 * <li>文章按种子来源键配对（{@code ArticleI18nService.linkSeedPeers}，安装时建链）——
 * 同一篇内容的两个语言版本来源键相同。</li>
 * </ul>
 * 配对集合在此钉死：任何一侧改名都会让切换器悄悄退回首页、译文链接悄悄消失，测试先于演示站发现。
 * 发行版仓库不携带 Journal 主题（仅随演示叠加库分发）时跳过。
 */
class DemoSeedLangAlignmentTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 两站对应的栏目（别名相同）
     */
    private static final Set<String> SHARED_CHANNELS = Set.of("news", "world", "photo", "product", "about-us");
    /**
     * Journal 独有、对方既无同别名栏目也无同别名上级的栏目（切换器回退对方首页）
     */
    private static final Set<String> JOURNAL_ONLY_TOP_CHANNELS = Set.of("sport", "life");
    /**
     * 两站互为译文的文章（来源键相同）
     */
    private static final Set<String> SHARED_ARTICLES = Set.of(
            "a-standard-zoom-lens", "a-wide-macro-prime-lens", "a-fast-standard-prime-lens", "a-compact-standard-prime-lens",
            "a-telephoto-zoom-lens", "a-carbon-road-bike", "a-hardtail-mountain-bike", "a-business-laptop",
            "a-large-tablet",
            "a-island-climate-threat", "a-wetland-egrets-return", "a-library-reading-hall",
            "a-shopping-festival-warehouse", "a-ai-phone-memory", "a-semiconductor-investors",
            "a-quantum-computing-advance", "a-mountain-reserve-new-species", "a-supermoon-viewing-map",
            "a-vintage-elegance", "a-fresh-outfit",
            "a-long-jump-gold", "a-sailing-regatta",
            "a-sunrise-sunset-scenery");

    private static Path defaultContent;
    private static Path journalContent;

    @BeforeAll
    static void locateRoots() {
        // 从模块目录（surefire 工作目录）向上查找项目根
        Path dir = Paths.get("").toAbsolutePath();
        Path themes = null;
        for (int i = 0; i < 3 && dir != null; i++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("ujcms-starter/src/main/webapp/themes/1/default/seed"))) {
                themes = dir.resolve("ujcms-starter/src/main/webapp/themes/1");
                break;
            }
        }
        Assumptions.assumeTrue(themes != null, "未找到参考主题种子树（发行版仓库不携带），跳过校验");
        defaultContent = themes.resolve("default/seed/content");
        journalContent = themes.resolve("journal/seed/content");
        Assumptions.assumeTrue(Files.isDirectory(journalContent), "未找到 Journal 演示主题（发行版仓库不携带），跳过校验");
    }

    /**
     * 栏目别名 → 父栏目别名（顶级为 {@code null}）
     */
    private static Map<String, String> channelParents(Path content) throws IOException {
        Map<String, String> parents = new HashMap<>();
        for (JsonNode channel : MAPPER.readTree(content.resolve("channels.channels.json").toFile()).path("channels")) {
            parents.put(channel.path("alias").asText(), channel.path("parent").asText(null));
        }
        return parents;
    }

    private static List<String> articleOriginIds(Path content) throws IOException {
        List<String> ids = new ArrayList<>();
        try (Stream<Path> stream = Files.list(content.resolve("articles"))) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".articles.json")).sorted().toList()) {
                for (JsonNode article : MAPPER.readTree(file.toFile()).path("articles")) {
                    ids.add(article.path("originId").asText());
                }
            }
        }
        return ids;
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> shared = new TreeSet<>(left);
        shared.retainAll(right);
        return shared;
    }

    /**
     * 来源键在一棵种子树内必须唯一：重复键的第二篇会被导入器当作已存在而静默跳过，
     * 建链时同键多篇也无从配对
     */
    @Test
    void articleOriginIdsAreUniquePerTheme() throws IOException {
        for (Path content : List.of(defaultContent, journalContent)) {
            List<String> ids = articleOriginIds(content);
            assertEquals(ids.size(), new HashSet<>(ids).size(), content + " 存在重复的文章来源键：" + ids);
        }
    }

    @Test
    void sharedArticleOriginIdsArePinned() throws IOException {
        Set<String> shared = intersection(new HashSet<>(articleOriginIds(defaultContent)),
                new HashSet<>(articleOriginIds(journalContent)));
        assertEquals(new TreeSet<>(SHARED_ARTICLES), shared,
                "两套种子共有的文章来源键变了：来源键相同即安装时链为译文，改动须是有意的（同步更新本测试与 install-flow D50）");
    }

    @Test
    void sharedChannelAliasesArePinned() throws IOException {
        Set<String> shared = intersection(channelParents(defaultContent).keySet(),
                channelParents(journalContent).keySet());
        assertEquals(new TreeSet<>(SHARED_CHANNELS), shared,
                "两套种子共有的栏目别名变了：别名相同即语言切换器的对应栏目，改动须是有意的");
    }

    /**
     * Journal 的每个栏目要么在中文站有同别名栏目，要么有一个这样的上级（切换器沿父栏目上溯），
     * 要么在明示的“仅英文站”清单里——新增栏目不会不知不觉地落回对方首页
     */
    @Test
    void everyJournalChannelReachesAPeerOrIsListedAsJournalOnly() throws IOException {
        Set<String> defaultAliases = channelParents(defaultContent).keySet();
        Map<String, String> journal = channelParents(journalContent);
        for (String alias : journal.keySet()) {
            boolean reachable = false;
            for (String current = alias; current != null; current = journal.get(current)) {
                if (defaultAliases.contains(current)) {
                    reachable = true;
                    break;
                }
            }
            assertTrue(reachable || JOURNAL_ONLY_TOP_CHANNELS.contains(alias),
                    "Journal 栏目 " + alias + " 在中文站没有同别名栏目或上级，也不在仅英文站清单里");
        }
    }
}
