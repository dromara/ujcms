package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.ujcms.cms.core.support.Constants.MAPPER;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 随仓库提交的主题种子自洽性测试。种子的毛病只在安装时暴露——Schema 不过，导入在该文件处抛
 * {@link CompositionLoadException} 中止；资源缺失、引用悬空则逐条降级跳过——而导入器自身的测试只用夹具。
 * 本测试按导入器的口径校验仓库里真实的种子树：参考主题（default），以及存在时的 Journal 演示主题
 * 与 SSR 参考纯数据包（{@code ujcms-theme-e2e/demo-data/}）。逐棵校验：
 * <ul>
 * <li>每个种子 JSON 都在导入器读取的位置，并通过导入器所用的同一套 {@link CompositionSchemas}
 * 校验（按文件后缀选 Schema；栏目文件按全名）；</li>
 * <li>{@code {{asset:…}}} 令牌集合与 {@code seed/content/assets/} 下的文件集合相等；</li>
 * <li>文章来源键在一棵种子树内唯一；</li>
 * <li>文章的栏目、区块条目的文章、专题成员、链接的栏目/文章目标，以及区域种子里的
 * {@code {{block:}}}/{@code {{channel:}}}/{@code {{vote:}}}/{@code {{article:}}} 令牌，
 * 都能在同一棵种子树内解析；</li>
 * <li>{@code ext.docLength}/{@code ext.fileLength}/{@code fileList[].length} 等于所引用资源文件的字节数。</li>
 * </ul>
 * 测试只认结构、不含任何内容字面量（标题、来源键、文件名），随各发行版分发；发行版仓库不携带
 * Journal 主题与 SSR 仓库时只校验 default。
 */
class ThemeSeedIntegrityTest {
    private static final CompositionSchemas SCHEMAS = new CompositionSchemas();
    /**
     * 资源令牌（与 {@code SeedAssetCopier} 的私有同名常量同式）
     */
    private static final Pattern ASSET_TOKEN = Pattern.compile("\\{\\{asset:([^}]+)}}");
    private static final String ASSETS_DIR = "content/assets";
    private static final String DS_STORE = ".DS_Store";

    /**
     * 一类种子文件：导入器读取的目录（相对 {@code seed/}，不递归）、文件名与对应 Schema。
     * {@code exact} 为真时 {@code name} 是完整文件名，否则是文件名后缀
     */
    private record SeedKind(String dir, String name, boolean exact, String schema) {
        /**
         * 按后缀取文件的一类（导入器 {@code listFiles(dir, suffix)}）
         */
        SeedKind(String dir, String suffix, String schema) {
            this(dir, suffix, false, schema);
        }

        boolean matches(String fileName) {
            return exact ? fileName.equals(name) : fileName.endsWith(name);
        }
    }

    /**
     * 栏目文件全主题恰好一个：导入器只读 {@code content/channels.channels.json}，按全名而非后缀匹配——
     * 同目录下其它以 {@code .channels.json} 结尾的文件不会被导入
     */
    private static final SeedKind CHANNELS =
            new SeedKind("content", "channels.channels.json", true, CompositionSchemas.SEED_CHANNELS);
    private static final SeedKind ARTICLES =
            new SeedKind("content/articles", ".articles.json", CompositionSchemas.SEED_ARTICLES);
    private static final SeedKind TOPICS =
            new SeedKind("content/topics", ".topics.json", CompositionSchemas.SEED_TOPICS);
    private static final SeedKind BLOCKS =
            new SeedKind("content/blocks", ".blocks.json", CompositionSchemas.SEED_BLOCKS);
    private static final SeedKind VOTES =
            new SeedKind("content/votes", ".votes.json", CompositionSchemas.SEED_VOTES);
    private static final SeedKind LINK_GROUPS =
            new SeedKind("content/link-groups", ".link-groups.json", CompositionSchemas.SEED_LINK_GROUPS);
    private static final SeedKind REGIONS =
            new SeedKind("regions", ".regions.json", CompositionSchemas.SEED_REGIONS);
    private static final List<SeedKind> KINDS = List.of(CHANNELS, ARTICLES, TOPICS, BLOCKS, VOTES,
            new SeedKind("content/surveys", ".surveys.json", CompositionSchemas.SEED_SURVEYS),
            new SeedKind("content/models", ".models.json", CompositionSchemas.SEED_MODELS),
            new SeedKind("content/dicts", ".dicts.json", CompositionSchemas.SEED_DICTS),
            new SeedKind("content/forms", ".forms.json", CompositionSchemas.SEED_FORMS),
            LINK_GROUPS, REGIONS);

    /**
     * 待校验的种子树：名称 → {@code seed} 目录
     */
    private static final Map<String, Path> SEEDS = new LinkedHashMap<>();

    @BeforeAll
    static void locateRoots() {
        // 从模块目录（surefire 工作目录）向上查找项目根
        Path dir = Paths.get("").toAbsolutePath();
        Path root = null;
        for (int i = 0; i < 3 && dir != null; i++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("ujcms-starter/src/main/webapp/themes/1/default/seed"))) {
                root = dir;
                break;
            }
        }
        Assumptions.assumeTrue(root != null, "未找到参考主题种子树（发行版仓库不携带），跳过校验");
        Path themes = root.resolve("ujcms-starter/src/main/webapp/themes/1");
        SEEDS.put("default", themes.resolve("default/seed"));
        // Journal 演示主题与 SSR 参考纯数据包不随发行版仓库分发：存在才校验
        Path journal = themes.resolve("journal/seed");
        if (Files.isDirectory(journal)) {
            SEEDS.put("journal", journal);
        }
        Path demoData = root.resolve("ujcms-theme-e2e/demo-data/seed");
        if (Files.isDirectory(demoData)) {
            SEEDS.put("demo-data", demoData);
        }
    }

    static Stream<Arguments> seeds() {
        return SEEDS.entrySet().stream().map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    // ----- 读取 -----

    /**
     * 与导入器同一读法（{@code ThemeContentSeedImporter.readAndValidate}）
     */
    private static JsonNode read(Path file) throws IOException {
        return MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * 某类种子文件，按导入器的口径列出：目录的直接子文件，按后缀（栏目文件按全名）过滤
     */
    private static List<Path> files(Path seed, SeedKind kind) throws IOException {
        Path dir = seed.resolve(kind.dir());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(file -> kind.matches(file.getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 种子树内的全部 JSON 文件（资源目录除外）
     */
    private static List<Path> jsonFiles(Path seed) throws IOException {
        Path assets = seed.resolve(ASSETS_DIR);
        try (Stream<Path> stream = Files.walk(seed)) {
            return stream.filter(Files::isRegularFile)
                    .filter(file -> !file.startsWith(assets))
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 资源目录下全部文件的相对路径（即令牌里的 relpath，分隔符恒为 {@code /}）
     */
    private static Set<String> assetFiles(Path seed) throws IOException {
        Path assets = seed.resolve(ASSETS_DIR);
        if (!Files.isDirectory(assets)) {
            return Set.of();
        }
        try (Stream<Path> stream = Files.walk(assets)) {
            Set<String> files = new TreeSet<>();
            stream.filter(Files::isRegularFile)
                    .filter(file -> !DS_STORE.equals(file.getFileName().toString()))
                    .forEach(file -> files.add(slashed(assets.relativize(file))));
            return files;
        }
    }

    private static String slashed(Path relative) {
        List<String> names = new ArrayList<>();
        relative.forEach(name -> names.add(name.toString()));
        return String.join("/", names);
    }

    /**
     * 某类种子文件里的全部实体：各文件根对象的 {@code field} 数组依次相接
     */
    private static List<JsonNode> entries(Path seed, SeedKind kind, String field) throws IOException {
        List<JsonNode> entries = new ArrayList<>();
        for (Path file : files(seed, kind)) {
            read(file).path(field).forEach(entries::add);
        }
        return entries;
    }

    private static Set<String> keys(List<JsonNode> entries, Function<JsonNode, String> key) {
        Set<String> keys = new HashSet<>();
        entries.forEach(entry -> keys.add(key.apply(entry)));
        return keys;
    }

    private static String originId(JsonNode entry) {
        return entry.path("originId").asText();
    }

    /**
     * 别名缺省取来源键（与导入器的 {@code aliasOf} 一致）
     */
    private static String alias(JsonNode entry) {
        return entry.hasNonNull("alias") ? entry.get("alias").asText() : originId(entry);
    }

    /**
     * 递归遍历节点内的全部字符串值
     */
    private static void strings(JsonNode node, Consumer<String> consumer) {
        if (node.isTextual()) {
            consumer.accept(node.asText());
        } else if (node.isContainerNode()) {
            node.forEach(child -> strings(child, consumer));
        }
    }

    // ----- 校验 -----

    /**
     * 导入器按「目录 + 后缀」（栏目文件按「目录 + 全名」）取文件并逐个过 Schema：校验不过即抛异常中止导入，
     * 位置或文件名不对的文件则被静默忽略——两者都不允许出现在提交的种子里
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("seeds")
    void everySeedFileValidatesAgainstItsSchema(String name, Path seed) throws IOException {
        List<String> problems = new ArrayList<>();
        Set<Path> recognized = new HashSet<>();
        for (SeedKind kind : KINDS) {
            for (Path file : files(seed, kind)) {
                recognized.add(file);
                for (String error : SCHEMAS.validate(kind.schema(), read(file))) {
                    problems.add(slashed(seed.relativize(file)) + "（" + kind.schema() + "）：" + error);
                }
            }
        }
        for (Path file : jsonFiles(seed)) {
            if (!recognized.contains(file)) {
                problems.add(slashed(seed.relativize(file)) + "：位置或文件名不在导入器的读取范围内");
            }
        }
        assertEquals(List.of(), problems, name + " 的种子文件未通过校验");
    }

    /**
     * 令牌指向的文件缺失时导入器仍展开为确定性 URL（页面出现坏图/坏链）；没有令牌引用的文件
     * 则是随包分发的死重
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("seeds")
    void assetTokensEqualAssetFiles(String name, Path seed) throws IOException {
        Set<String> tokens = new TreeSet<>();
        for (Path file : jsonFiles(seed)) {
            strings(read(file), value -> {
                Matcher matcher = ASSET_TOKEN.matcher(value);
                while (matcher.find()) {
                    tokens.add(matcher.group(1).trim());
                }
            });
        }
        Set<String> files = assetFiles(seed);
        Set<String> missing = new TreeSet<>(tokens);
        missing.removeAll(files);
        Set<String> unreferenced = new TreeSet<>(files);
        unreferenced.removeAll(tokens);
        assertAll(name + " 的资源令牌与 seed/" + ASSETS_DIR + " 下的文件须一一对应",
                () -> assertEquals(Set.of(), missing, "令牌指向的资源文件不存在"),
                () -> assertEquals(Set.of(), unreferenced, "资源文件未被任何令牌引用"));
    }

    /**
     * 重复键的第二篇会被导入器当作已存在而静默跳过
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("seeds")
    void articleOriginIdsAreUnique(String name, Path seed) throws IOException {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new TreeSet<>();
        for (JsonNode article : entries(seed, ARTICLES, "articles")) {
            if (!seen.add(originId(article))) {
                duplicates.add(originId(article));
            }
        }
        assertEquals(Set.of(), duplicates, name + " 存在重复的文章来源键");
    }

    /**
     * 内容种子之间的引用：解析失败时导入器逐条计失败或告警跳过（文章不入库，条目、成员、链接丢失），
     * 安装照常完成——改名漏改一处只能在这里发现
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("seeds")
    void contentReferencesResolveWithinSeed(String name, Path seed) throws IOException {
        List<JsonNode> channels = entries(seed, CHANNELS, "channels");
        Set<String> channelOriginIds = keys(channels, ThemeSeedIntegrityTest::originId);
        Set<String> channelAliases = keys(channels, ThemeSeedIntegrityTest::alias);
        List<JsonNode> articles = entries(seed, ARTICLES, "articles");
        Set<String> articleOriginIds = keys(articles, ThemeSeedIntegrityTest::originId);

        List<String> problems = new ArrayList<>();
        for (JsonNode article : articles) {
            // 文章的栏目先按栏目来源键、再按别名解析（导入器 resolveChannel）
            String channel = article.path("channel").asText();
            if (!channelOriginIds.contains(channel) && !channelAliases.contains(channel)) {
                problems.add("文章 " + originId(article) + " 的栏目 " + channel);
            }
        }
        for (JsonNode block : entries(seed, BLOCKS, "blocks")) {
            // 定义组以别名标识，条目组以目标区块别名标识
            String label = block.hasNonNull("originId") ? alias(block) : block.path("block").asText();
            for (JsonNode item : block.path("items")) {
                String article = item.path("article").asText();
                if (!articleOriginIds.contains(article)) {
                    problems.add("区块 " + label + " 的条目文章 " + article);
                }
            }
        }
        for (JsonNode topic : entries(seed, TOPICS, "topics")) {
            for (JsonNode member : topic.path("articles")) {
                if (!articleOriginIds.contains(member.asText())) {
                    problems.add("专题 " + originId(topic) + " 的成员文章 " + member.asText());
                }
            }
        }
        for (JsonNode group : entries(seed, LINK_GROUPS, "linkGroups")) {
            for (JsonNode link : group.path("links")) {
                // 链接的栏目目标只按别名解析（导入器 applyLinkSeed）
                if (link.hasNonNull("channel") && !channelAliases.contains(link.get("channel").asText())) {
                    problems.add("链接组 " + originId(group) + " 的栏目目标 " + link.get("channel").asText());
                }
                if (link.hasNonNull("article") && !articleOriginIds.contains(link.get("article").asText())) {
                    problems.add("链接组 " + originId(group) + " 的文章目标 " + link.get("article").asText());
                }
            }
        }
        assertEquals(List.of(), problems, name + " 的内容种子存在无法在本种子内解析的引用");
    }

    /**
     * 区域种子的引用令牌（{@link SeedTokenExpander#REF_TOKEN}）：栏目、区块按别名，文章、投票按来源键。
     * 不可解析时导入器把所在区域整块跳过
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("seeds")
    void regionTokensResolveWithinSeed(String name, Path seed) throws IOException {
        List<JsonNode> blockDefs = entries(seed, BLOCKS, "blocks").stream()
                .filter(block -> block.hasNonNull("originId")).toList();
        Map<String, Set<String>> targets = Map.of(
                "channel", keys(entries(seed, CHANNELS, "channels"), ThemeSeedIntegrityTest::alias),
                "block", keys(blockDefs, ThemeSeedIntegrityTest::alias),
                "article", keys(entries(seed, ARTICLES, "articles"), ThemeSeedIntegrityTest::originId),
                "vote", keys(entries(seed, VOTES, "votes"), ThemeSeedIntegrityTest::originId));

        List<String> problems = new ArrayList<>();
        for (Path file : files(seed, REGIONS)) {
            strings(read(file), value -> {
                Matcher matcher = SeedTokenExpander.REF_TOKEN.matcher(value);
                while (matcher.find()) {
                    if (!targets.get(matcher.group(1)).contains(matcher.group(2).trim())) {
                        problems.add(slashed(seed.relativize(file)) + "：" + matcher.group());
                    }
                }
            });
        }
        assertEquals(List.of(), problems, name + " 的区域种子存在无法在本种子内解析的引用令牌");
    }

    /**
     * 大小字段原样落库、页面据此显示文件大小，导入器不对着文件核对
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("seeds")
    void sizeFieldsEqualAssetFileSizes(String name, Path seed) throws IOException {
        List<String> problems = new ArrayList<>();
        for (JsonNode article : entries(seed, ARTICLES, "articles")) {
            String id = originId(article);
            JsonNode ext = article.path("ext");
            checkSize(seed, ext.path("doc"), ext.path("docLength"), id + " 的 ext.docLength", problems);
            checkSize(seed, ext.path("file"), ext.path("fileLength"), id + " 的 ext.fileLength", problems);
            int index = 0;
            for (JsonNode item : article.path("fileList")) {
                checkSize(seed, item.path("url"), item.path("length"),
                        id + " 的 fileList[" + index++ + "].length", problems);
            }
        }
        assertEquals(List.of(), problems, name + " 的文件大小字段与资源文件的实际字节数不一致");
    }

    /**
     * 只核对可核对的情形：大小字段已声明，且其 URL 整体是一个资源令牌（外部地址的大小无从核对）
     */
    private static void checkSize(Path seed, JsonNode url, JsonNode length, String label,
                                  List<String> problems) throws IOException {
        Matcher matcher = ASSET_TOKEN.matcher(url.asText());
        if (length.isMissingNode() || length.isNull() || !matcher.matches()) {
            return;
        }
        String relpath = matcher.group(1).trim();
        Path file = seed.resolve(ASSETS_DIR).resolve(relpath);
        if (!Files.isRegularFile(file)) {
            problems.add(label + "：资源文件 " + relpath + " 不存在");
        } else if (length.asLong() != Files.size(file)) {
            problems.add(label + " = " + length.asLong() + "，而 " + relpath + " 实为 " + Files.size(file) + " 字节");
        }
    }
}
