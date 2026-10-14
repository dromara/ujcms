package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4 散列自库行重导出的<b>往返稳定性</b>证明（theme-marketplace A3 前置）：A3 是第一个从活行
 * 重导出散列的消费者（syncUnmodified / SeedPlan 的 operatorModified 判定），必须先证明
 * <ul>
 *   <li>customs 经 {@code Model.sanitizeMap} →（导入时刻散列）→ {@code disassembleMap} 落 JSON 列
 *       → {@code assembleMap} 重装配（重导出散列）后投影散列不变；</li>
 *   <li>标签以名称列表入散列、顺序敏感（行上重导出按关联行序，即导入时的种子声明序）；</li>
 *   <li>HTML 清洗幂等（种子侧散列以单次清洗模拟保存期处理；操作员未动的行重存不再改写）。</li>
 * </ul>
 * 带尾零的小数（如 {@code 19.50}）同样稳定：{@code Constants.MAPPER} 开启
 * {@code USE_BIG_DECIMAL_FOR_FLOATS}，JSON 列重解析保留 BigDecimal 标度——以测试钉死。
 */
class SeedRoundTripTest {

    private final HtmlSanitizeService sanitizeService = new HtmlSanitizeService("");

    /**
     * 覆盖 number（整数/小数）、date、switch、checkbox（含 Key 列表）、select、text、富文本 的模型。
     */
    private static Model model() {
        Model model = new Model();
        model.setCustoms("""
                [
                  { "code": "count",   "type": "number" },
                  { "code": "price",   "type": "number", "precision": 2 },
                  { "code": "since",   "type": "date" },
                  { "code": "enabled", "type": "switch" },
                  { "code": "colors",  "type": "checkbox", "dataType": "number" },
                  { "code": "kind",    "type": "select" },
                  { "code": "note",    "type": "text" },
                  { "code": "body",    "type": "richEditor", "clob": true }
                ]
                """);
        return model;
    }

    /**
     * 种子形 customs（Jackson 解析种子 JSON 的原生类型：Integer/String/Boolean/List）。
     */
    private static Map<String, Object> seedCustoms() {
        Map<String, Object> customs = new LinkedHashMap<>();
        customs.put("count", 5);
        customs.put("price", "19.5");
        customs.put("since", "2026-07-01T08:00:00+08:00");
        customs.put("enabled", true);
        customs.put("colors", List.of("red", "blue"));
        customs.put("colorsKey", List.of(1, 2));
        customs.put("kind", "demo");
        customs.put("note", "示例");
        customs.put("body", "<p>正文 <img src=\"/uploads/a.svg\" /></p>");
        return customs;
    }

    /**
     * 模拟 JSON 列往返：sanitizeMap（导入时服务层保存）→ disassembleMap 落 mains/clobs JSON →
     * assembleMap 重装配（自库行重导出）。
     */
    private static Map<String, Object> roundTrip(Model model, Map<String, Object> sanitized) {
        AtomicReference<String> mainsJson = new AtomicReference<>();
        AtomicReference<String> clobsJson = new AtomicReference<>();
        model.disassembleMap(sanitized, mainsJson::set, clobsJson::set);
        Map<String, Object> reassembled = new HashMap<>(16);
        reassembled.putAll(model.assembleMap(mainsJson.get()));
        reassembled.putAll(model.assembleMap(clobsJson.get()));
        return reassembled;
    }

    @Test
    void channelCustomsHashSurvivesJsonColumnRoundTrip() {
        Model model = model();
        Map<String, Object> sanitized = model.sanitizeMap(seedCustoms(), sanitizeService);

        Channel imported = new Channel();
        imported.setName("演示");
        imported.setCustoms(sanitized);
        String importTimeHash = SeedHash.channelHash(imported);

        Channel reloaded = new Channel();
        reloaded.setName("演示");
        reloaded.setCustoms(roundTrip(model, sanitized));
        assertEquals(importTimeHash, SeedHash.channelHash(reloaded),
                "customs 经 mains/clobs JSON 列往返后投影散列必须复现（syncUnmodified 的判定基础）");
    }

    @Test
    void decimalTrailingZeroKeepsScaleThroughJsonColumns() {
        // Constants.MAPPER 开启 USE_BIG_DECIMAL_FOR_FLOATS：JSON 列重解析回 BigDecimal 且保留标度，
        // 尾零小数（19.50）的投影散列同样复现——若此断言失败说明 MAPPER 配置变了，须同步更新规格记录
        Model model = model();
        Map<String, Object> customs = seedCustoms();
        customs.put("price", "19.50");
        Map<String, Object> sanitized = model.sanitizeMap(customs, sanitizeService);

        Channel imported = new Channel();
        imported.setCustoms(sanitized);
        Channel reloaded = new Channel();
        reloaded.setCustoms(roundTrip(model, sanitized));
        assertEquals(SeedHash.channelHash(imported), SeedHash.channelHash(reloaded));
    }

    @Test
    void articleTagsHashIsOrderSensitiveNameList() {
        Article article = new Article();
        article.setTitle("标题");
        String hash = SeedHash.articleHash(article, List.of("甲", "乙"));
        assertEquals(hash, SeedHash.articleHash(article, List.of("甲", "乙")));
        // 行上重导出按 ujcms_article_tag.order_（即导入时种子声明序）——顺序参与散列
        assertNotEquals(hash, SeedHash.articleHash(article, List.of("乙", "甲")));
    }

    @Test
    void articleImageListHashSurvivesJsonColumnRoundTrip() {
        // A6：图片集经 image_list_json_ 列往返（setImageList 序列化 → setImageListJson 重解析）散列复现
        Article imported = new Article();
        imported.setTitle("标题");
        Article.ArticleImage first = new Article.ArticleImage();
        first.setUrl("/uploads/theme-seed/1/default/img/p1.svg");
        first.setName("图1");
        first.setDescription("演示相册");
        Article.ArticleImage second = new Article.ArticleImage();
        second.setUrl("/uploads/theme-seed/1/default/img/p2.svg");
        imported.setImageList(List.of(first, second));
        String importTimeHash = SeedHash.articleHash(imported, List.of());

        Article reloaded = new Article();
        reloaded.setTitle("标题");
        reloaded.setImageListJson(imported.getImageListJson());
        assertEquals(importTimeHash, SeedHash.articleHash(reloaded, List.of()),
                "图片集经 JSON 列往返后投影散列必须复现（syncUnmodified 的判定基础）");
    }

    @Test
    void articleFileListHashSurvivesJsonColumnRoundTrip() {
        // D45：附件列表经 file_list_json_ 列往返（setFileList 序列化 → setFileListJson 重解析）散列复现
        Article imported = new Article();
        imported.setTitle("标题");
        Article.ArticleFile first = new Article.ArticleFile();
        first.setUrl("/uploads/theme-seed/1/default/media/a.zip");
        first.setName("附件甲.zip");
        first.setLength(158L);
        Article.ArticleFile second = new Article.ArticleFile();
        second.setUrl("/uploads/theme-seed/1/default/media/b.zip");
        imported.setFileList(List.of(first, second));
        String importTimeHash = SeedHash.articleHash(imported, List.of());

        Article reloaded = new Article();
        reloaded.setTitle("标题");
        reloaded.setFileListJson(imported.getFileListJson());
        assertEquals(importTimeHash, SeedHash.articleHash(reloaded, List.of()),
                "附件列表经 JSON 列往返后投影散列必须复现（syncUnmodified 的判定基础）");
    }

    @Test
    void richSanitizeIsIdempotentOnRepresentativeSeedHtml() {
        String seedHtml = """
                <h2>标题</h2>
                <p>段落 <a href="https://example.com">链接</a> <strong>加粗</strong></p>
                <ul><li>项目一</li><li>项目二</li></ul>
                <p><img src="/uploads/theme-seed/1/default/img/a.svg" alt="图"></p>
                """;
        String once = sanitizeService.sanitize(seedHtml, PolicyTier.RICH);
        assertEquals(once, sanitizeService.sanitize(once, PolicyTier.RICH),
                "RICH 清洗必须幂等：种子侧散列以单次清洗模拟保存期处理，重存不得再改写");
    }

    @Test
    void richEditorCustomsFieldSanitizeIsIdempotent() {
        Model model = model();
        Map<String, Object> once = model.sanitizeMap(seedCustoms(), sanitizeService);
        Map<String, Object> twice = model.sanitizeMap(once, sanitizeService);
        assertEquals(SeedHash.sha256Canonical(once), SeedHash.sha256Canonical(twice),
                "customs 富文本字段的清洗必须幂等（同上）");
    }

    // =====================================================================
    // theme-seed-export E1 硬验收（D4）：导出 → 擦除 → 导入 → 重导出 字节一致；
    // 导出 JSON 通过已发布种子架构校验；内容未变的重导出字节一致。
    // 世界为内存态（Mockito 有状态桩 + 真实 SeedAssetCopier/SeedAssetExtractor/file: 存储），
    // 导入走真实导入器管线——与 ThemeContentSeedImporterTest / ThemeSeedImporterTest 同构。
    // =====================================================================

    private static final Long SITE = 1L;
    private static final String THEME = "demo";

    @org.junit.jupiter.api.Test
    void exportWipeImportReExportIsByteIdentical(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp)
            throws java.io.IOException {
        // 世界 1：作者站点——导入夹具种子（来源行），再加操作员内容（第 3 类）
        ExportWorld first = new ExportWorld(tmp.resolve("w1"));
        first.importTheme(writeFixtureTheme(tmp.resolve("authoring")));
        first.addOperatorArticle();
        SeedExportBundle exported = first.export();

        // 发出的每个 JSON 都必须通过已发布种子架构（D4 第二腿）
        validateBundleAgainstSchemas(exported);
        // 分类计数抽查：夹具行为第 1 类（散列复现），操作员文章为第 3 类（铸造）
        // D45：转向链接栏目 message-board 随三元组导入为第 1 类（linkUrl/targetBlank 条件入投影）
        assertEquals(3, exported.plan().channels().originUnmodified());
        assertEquals(1, exported.plan().articles().originUnmodified());
        assertEquals(1, exported.plan().articles().operatorNew());
        assertEquals(1, exported.plan().topics().originUnmodified());
        assertEquals(2, exported.plan().panels().originUnmodified());
        assertEquals(1, exported.plan().regions().originUnmodified());
        // E4a：promo 定义随三元组导入为第 1 类；focus 条目组沿用全局区块（不入定义导出集）；
        // 条目 2 条（focus 1 + promo 1，均骑乘 a-welcome）
        assertEquals(1, exported.plan().blocks().originUnmodified());
        assertEquals(0, exported.plan().blocks().operatorNew());
        // E4b：v-poll 定义随三元组导入为第 1 类（投票无别名，恒不 linked）
        assertEquals(1, exported.plan().votes().originUnmodified());
        assertEquals(0, exported.plan().votes().operatorNew());
        // E4d：s-exp 问卷定义经端口导入为第 1 类（缺省选择 = 本主题来源，无引用面）
        assertEquals(1, exported.plan().surveys().originUnmodified());
        // E4e：f-signup 表单类型定义经端口导入为第 1 类（缺省选择 = 本主题来源，无引用面）
        assertEquals(1, exported.plan().forms().originUnmodified());
        assertEquals(0, exported.plan().forms().linkedSkipped());
        // E4f：gov 友链分组定义随三元组导入为第 1 类（链接随定义、logo 资源令牌展开后散列复现）
        assertEquals(1, exported.plan().linkGroups().originUnmodified());
        assertEquals(0, exported.plan().linkGroups().operatorNew());
        assertEquals(2, exported.plan().linkItems());
        // model-seeding D6 + E4e：种子模型「视频」（article）与「报名表」（form）为第 1 类；绑定闭包
        // 拉入出厂 常规/新闻（全局）与专题模型（本站私有）——均第 3 类铸造、恒不 linked 跳过。
        // 表单模型经表单类型的 modelId 闭包边入包（D37）
        assertEquals(2, exported.plan().models().originUnmodified());
        assertEquals(3, exported.plan().models().operatorNew());
        assertEquals(0, exported.plan().models().linkedSkipped());
        // D8：种子字典类型为第 1 类（视频模型的 dictTypeId 绑定闭包同样命中它）
        assertEquals(1, exported.plan().dicts().originUnmodified());
        assertEquals(2, exported.plan().blockItems());
        assertTrue(exported.plan().assetBytes() > 0);
        org.junit.jupiter.api.Assertions.assertFalse(exported.plan().oversize());
        // 操作员文章的题图以内容散列铸造 relpath 进入资源束
        assertTrue(exported.files().keySet().stream()
                .anyMatch(path -> path.matches("content/assets/img/[0-9a-f]{8}-photo\\.svg")));

        // 擦除：全新世界 2（空库、空上传存储）——安装导出的种子包
        java.nio.file.Path exportedTheme = writeBundleAsTheme(tmp.resolve("exported-theme"), exported);
        ExportWorld second = new ExportWorld(tmp.resolve("w2"));
        second.importTheme(exportedTheme);
        SeedExportBundle reExported = second.export();

        assertBundlesByteIdentical(exported, reExported);
        // 擦除后重导入的行全部携带来源三元组：重导出全为第 1 类，originId 稳定（§4 往返）
        assertEquals(2, reExported.plan().articles().originUnmodified());
        assertEquals(0, reExported.plan().articles().operatorNew());
        assertEquals(1, reExported.plan().blocks().originUnmodified());
        assertEquals(1, reExported.plan().votes().originUnmodified());
        assertEquals(1, reExported.plan().surveys().originUnmodified());
        assertEquals(1, reExported.plan().forms().originUnmodified());
        assertEquals(1, reExported.plan().linkGroups().originUnmodified());
        assertEquals(2, reExported.plan().linkItems());
        // 世界 2 预置同名出厂模型：种子 常规/新闻/专题 定义 linked 沿用（恒不收编，D1）——
        // 重导出闭包仍拉入它们（第 3 类确定性重铸，发射字节不变）；「视频」「报名表」携三元组归第 1 类
        assertEquals(2, reExported.plan().models().originUnmodified());
        assertEquals(3, reExported.plan().models().operatorNew());
        assertEquals(1, reExported.plan().dicts().originUnmodified());
        assertEquals(2, reExported.plan().blockItems());
    }

    @org.junit.jupiter.api.Test
    void reExportOverUnchangedContentIsByteIdentical(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws java.io.IOException {
        ExportWorld world = new ExportWorld(tmp.resolve("w"));
        world.importTheme(writeFixtureTheme(tmp.resolve("authoring")));
        world.addOperatorArticle();
        assertBundlesByteIdentical(world.export(), world.export());
    }

    /**
     * E2 §4.2 认领转换：第 3 类行（操作员文章 / 面板 / 区域）认领后，下次导出归第 1 类，
     * 发射字节不变（认领只写 provenance，不动内容投影）。
     */
    @org.junit.jupiter.api.Test
    void adoptTransitionsOperatorRowsToClass1(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp)
            throws java.io.IOException {
        ExportWorld world = new ExportWorld(tmp.resolve("w"));
        world.importTheme(writeFixtureTheme(tmp.resolve("authoring")));
        world.addOperatorArticle();
        world.addOperatorRegion();
        SeedExportBundle first = world.export();
        assertEquals(1, first.plan().articles().operatorNew());
        assertEquals(1, first.plan().panels().operatorNew());
        assertEquals(1, first.plan().regions().operatorNew());
        // D26/D27：操作员面板 blockRef 引用全局 focus——随闭包入导出集（第 3 类铸造）但绝不入认领候选
        assertEquals(1, first.plan().blocks().operatorNew());
        assertTrue(first.adoptions().stream().noneMatch(adoption ->
                adoption.category() == SeedAdoption.Category.BLOCK));
        // E4b / D31：操作员投票经 voteRef 闭包入导出集（第 3 类铸造）且入认领候选（投票恒本站私有）
        assertEquals(1, first.plan().votes().operatorNew());
        assertTrue(first.adoptions().stream().anyMatch(adoption ->
                adoption.category() == SeedAdoption.Category.VOTE));
        // model-seeding D1：绑定闭包拉入的本站私有 专题 模型（第 3 类）恰一枚入认领候选；
        // 全局 常规/新闻 恒不入列；第 1 类种子模型/字典不入列
        assertEquals(1, first.adoptions().stream()
                .filter(adoption -> adoption.category() == SeedAdoption.Category.MODEL).count());
        assertTrue(first.adoptions().stream().noneMatch(adoption ->
                adoption.category() == SeedAdoption.Category.DICT));
        // 认领候选：文章 + 面板（导入侧位置方案的 originId）+ 区域（无 originId）+ 投票 + 专题模型
        // ——他源/本源/全局行不入列
        assertEquals(5, first.adoptions().size());
        assertTrue(first.adoptions().stream().anyMatch(adoption ->
                adoption.category() == SeedAdoption.Category.PANEL
                        && "home#aside#r1#0#0".equals(adoption.originId())));
        assertTrue(first.adoptions().stream().anyMatch(adoption ->
                adoption.category() == SeedAdoption.Category.REGION && adoption.originId() == null));

        assertEquals(5, world.adopter.adopt("theme:" + THEME, first.adoptions()));

        SeedExportBundle second = world.export();
        assertEquals(0, second.plan().articles().operatorNew());
        assertEquals(2, second.plan().articles().originUnmodified());
        assertEquals(0, second.plan().panels().operatorNew());
        assertEquals(3, second.plan().panels().originUnmodified());
        assertEquals(0, second.plan().regions().operatorNew());
        assertEquals(2, second.plan().regions().originUnmodified());
        // 全局 focus 恒不被认领：重导出仍为第 3 类（铸造确定性保证发射字节不变，D26）
        assertEquals(1, second.plan().blocks().operatorNew());
        // 认领后的操作员投票转第 1 类（E4b）
        assertEquals(0, second.plan().votes().operatorNew());
        assertEquals(2, second.plan().votes().originUnmodified());
        // 认领后的本站私有 专题 模型转第 1 类；全局 常规/新闻 恒不认领、重导出仍第 3 类（D1）
        assertEquals(2, second.plan().models().operatorNew());
        // 视频（种子 article）+ 报名表（种子 form，E4e）+ 认领后的 专题 = 3 枚第 1 类
        assertEquals(3, second.plan().models().originUnmodified());
        assertTrue(second.adoptions().isEmpty(), "认领后不应再有认领候选");
        assertBundlesByteIdentical(first, second);

        // 幂等 / D3 防御：已带来源标记的行再认领恒为 0 行（SQL 条件在桩里同样生效）
        assertEquals(0, world.adopter.adopt("theme:" + THEME, first.adoptions()));
    }

    /**
     * E2 硬验收：导出的 dataOnlyZip 通过 {@code ThemeInstallValidator}，经既有种子管线导入
     * 后重导出字节一致；ZIP 本身确定性（同内容两次打包字节一致）。
     */
    @org.junit.jupiter.api.Test
    void exportedDataOnlyZipPassesValidatorAndImports(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws java.io.IOException {
        ExportWorld world = new ExportWorld(tmp.resolve("w1"));
        world.importTheme(writeFixtureTheme(tmp.resolve("authoring")));
        world.addOperatorArticle();
        com.ujcms.cms.core.service.ThemeSeedExportService service = world.exportService();
        com.ujcms.cms.core.service.ThemeSeedExportService.ZipPackage zip = service.exportZip(
                SITE, THEME, SeedExportRequest.defaults(), true, false, false, "9.9.9");
        assertEquals(THEME + "-9.9.9.zip", zip.filename());
        // 确定性（D4）：同内容重打包字节一致
        org.junit.jupiter.api.Assertions.assertArrayEquals(zip.bytes(), service.exportZip(
                SITE, THEME, SeedExportRequest.defaults(), true, false, false, "9.9.9").bytes());
        // 安装门槛：通过校验器且判定为纯数据包（§8）
        com.ujcms.cms.core.component.ThemeInstallReport report =
                new com.ujcms.cms.core.component.ThemeInstallValidator(
                        com.ujcms.cms.core.support.Constants.MAPPER)
                        .validate(new java.io.ByteArrayInputStream(zip.bytes()), zip.bytes().length,
                                50L * 1024 * 1024);
        org.junit.jupiter.api.Assertions.assertNotNull(report.getMetadata());
        assertTrue(report.getMetadata().isDataOnly());

        // 解包 → 全新世界导入 → 重导出字节一致（往返闭环走 ZIP 而非目录复制）
        java.nio.file.Path unzipped = tmp.resolve("unzipped");
        unzip(zip.bytes(), unzipped);
        ExportWorld second = new ExportWorld(tmp.resolve("w2"));
        second.importTheme(unzipped);
        assertBundlesByteIdentical(world.export(), second.export());
    }

    /**
     * §7.4 快照往返（Q3 验收的内存形）：快照开启导出 themeZip → 包内清单默认值 = 已存设置值 →
     * 解包装入全新世界（无任何已存设置值）→ 快照开启重导出字节一致（清单已烘焙 + 无存值零写入，
     * D4/D21——「配置的外观」随包呈现而无需复制任何设置）。
     */
    @org.junit.jupiter.api.Test
    void settingsSnapshotThemeZipRoundTripIsByteIdentical(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws java.io.IOException {
        ExportWorld world = new ExportWorld(tmp.resolve("w1"));
        world.importTheme(writeFixtureTheme(tmp.resolve("authoring")));
        java.nio.file.Files.createDirectories(world.themeDir());
        java.nio.file.Files.writeString(world.themeDir().resolve("theme.manifest.json"), """
                {
                  "name": "Demo",
                  "themeName": "demo",
                  "version": "1.0.0",
                  "settings": { "groups": [
                    { "name": "brand", "fields": {
                      "slogan": { "type": "text", "default": "作者默认标语" }
                    } }
                  ] }
                }
                """);
        com.ujcms.cms.core.domain.ThemeState state = new com.ujcms.cms.core.domain.ThemeState();
        state.setId(1L);
        state.setSiteId(SITE);
        state.setThemeName(THEME);
        java.util.Map<String, java.util.Map<String, Object>> global = new java.util.LinkedHashMap<>();
        global.put("brand", new java.util.LinkedHashMap<>(Map.of("slogan", "配置的标语")));
        state.getSettings().setGlobal(global);
        org.mockito.Mockito.when(world.settingsService.findState(SITE, THEME)).thenReturn(state);

        com.ujcms.cms.core.service.ThemeSeedExportService.ZipPackage zip = world.exportService()
                .exportZip(SITE, THEME, SeedExportRequest.defaults(), false, false, true, "9.9.9");
        java.nio.file.Path unzipped = tmp.resolve("unzipped");
        unzip(zip.bytes(), unzipped);
        com.fasterxml.jackson.databind.JsonNode manifest = com.ujcms.cms.core.support.Constants.MAPPER
                .readTree(unzipped.resolve("theme.manifest.json").toFile());
        assertEquals("配置的标语", manifest.path("settings").path("groups").path(0)
                .path("fields").path("slogan").path("default").asText(), "包内清单默认值 = 已存设置值");

        // 全新世界：主题目录 = 解包产物（清单已烘焙），种子经真实管线导入，无任何已存设置值
        ExportWorld second = new ExportWorld(tmp.resolve("w2"));
        copyDir(unzipped, second.themeDir());
        second.importTheme(unzipped);
        org.junit.jupiter.api.Assertions.assertArrayEquals(zip.bytes(), second.exportService()
                        .exportZip(SITE, THEME, SeedExportRequest.defaults(), false, false, true, "9.9.9")
                        .bytes(),
                "新站无存值 + 已烘焙清单：快照重导出字节一致（D4）");
    }

    private static void copyDir(java.nio.file.Path src, java.nio.file.Path dest) throws java.io.IOException {
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(src)) {
            for (java.nio.file.Path path : walk.toList()) {
                java.nio.file.Path target = dest.resolve(src.relativize(path).toString());
                if (java.nio.file.Files.isDirectory(path)) {
                    java.nio.file.Files.createDirectories(target);
                } else {
                    java.nio.file.Files.createDirectories(target.getParent());
                    java.nio.file.Files.copy(path, target);
                }
            }
        }
    }

    private static void unzip(byte[] zip, java.nio.file.Path dest) throws java.io.IOException {
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(zip), java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                java.nio.file.Path target = dest.resolve(entry.getName());
                java.nio.file.Files.createDirectories(target.getParent());
                java.nio.file.Files.write(target, in.readAllBytes());
                in.closeEntry();
            }
        }
    }

    private static void assertBundlesByteIdentical(SeedExportBundle expected, SeedExportBundle actual) {
        assertEquals(expected.files().keySet(), actual.files().keySet(), "文件清单必须一致");
        expected.files().forEach((path, bytes) -> org.junit.jupiter.api.Assertions.assertArrayEquals(
                bytes, actual.files().get(path), "文件字节必须一致（D4）: " + path));
    }

    private static void validateBundleAgainstSchemas(SeedExportBundle bundle) throws java.io.IOException {
        CompositionSchemas schemas = new CompositionSchemas();
        for (Map.Entry<String, byte[]> entry : bundle.files().entrySet()) {
            String path = entry.getKey();
            if (!path.endsWith(".json")) {
                continue;
            }
            String schemaName;
            if (path.endsWith(".channels.json")) {
                schemaName = CompositionSchemas.SEED_CHANNELS;
            } else if (path.endsWith(".articles.json")) {
                schemaName = CompositionSchemas.SEED_ARTICLES;
            } else if (path.endsWith(".topics.json")) {
                schemaName = CompositionSchemas.SEED_TOPICS;
            } else if (path.endsWith(".blocks.json")) {
                schemaName = CompositionSchemas.SEED_BLOCKS;
            } else if (path.endsWith(".votes.json")) {
                schemaName = CompositionSchemas.SEED_VOTES;
            } else if (path.endsWith(".surveys.json")) {
                schemaName = CompositionSchemas.SEED_SURVEYS;
            } else if (path.endsWith(".models.json")) {
                schemaName = CompositionSchemas.SEED_MODELS;
            } else if (path.endsWith(".dicts.json")) {
                schemaName = CompositionSchemas.SEED_DICTS;
            } else if (path.endsWith(".forms.json")) {
                schemaName = CompositionSchemas.SEED_FORMS;
            } else if (path.endsWith(".link-groups.json")) {
                schemaName = CompositionSchemas.SEED_LINK_GROUPS;
            } else if (path.endsWith(".regions.json")) {
                schemaName = CompositionSchemas.SEED_REGIONS;
            } else {
                throw new IllegalStateException("Unexpected JSON in bundle: " + path);
            }
            com.fasterxml.jackson.databind.JsonNode node = com.ujcms.cms.core.support.Constants.MAPPER
                    .readTree(new String(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(List.of(), schemas.validate(schemaName, node),
                    "导出 JSON 必须通过已发布架构: " + path);
        }
    }

    /**
     * D46 版本无关：问卷/表单类型/专题的功能面属 T2，但定义与 schema 同在核心——导入器不看版本级别，
     * 一次导入即完整落库（无 failed 行），升级后无需补导；导出回读与 T2 完全一致。
     */
    @org.junit.jupiter.api.Test
    void tierGatedFamiliesImportOnEveryEdition(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp)
            throws java.io.IOException {
        ExportWorld world = new ExportWorld(tmp.resolve("w"));
        java.nio.file.Path theme = writeFixtureTheme(tmp.resolve("authoring"));
        ThemeContentSeedImporter.ContentSeedResult result = world.importTheme(theme);
        assertEquals(1, result.surveys().imported());
        assertEquals(0, result.surveys().failed());
        assertEquals(1, result.forms().imported());
        assertEquals(0, result.forms().failed());
        assertEquals(2, result.models().imported());
        assertTrue(result.failures().isEmpty(), "no tier failures: " + result.failures());
        // 重导幂等：全部已存在
        ThemeContentSeedImporter.ContentSeedResult again = world.importTheme(theme);
        assertEquals(0, again.surveys().imported());
        assertEquals(0, again.forms().imported());
        SeedExportBundle exported = world.export();
        assertEquals(1, exported.plan().surveys().originUnmodified());
        assertEquals(1, exported.plan().forms().originUnmodified());
    }

    /**
     * 作者种子夹具：栏目树 + 文章（媒体/标签/customs 资源令牌）+ 专题绑定 + 区块条目 +
     * 区域（媒体/富文本/引用令牌面板、enabled/visibility 非缺省面板）。
     */
    private static java.nio.file.Path writeFixtureTheme(java.nio.file.Path dir) throws java.io.IOException {
        java.nio.file.Path content = dir.resolve("seed/content");
        java.nio.file.Files.createDirectories(content.resolve("articles"));
        java.nio.file.Files.createDirectories(content.resolve("topics"));
        java.nio.file.Files.createDirectories(content.resolve("blocks"));
        java.nio.file.Files.createDirectories(content.resolve("votes"));
        java.nio.file.Files.createDirectories(content.resolve("surveys"));
        java.nio.file.Files.createDirectories(content.resolve("forms"));
        java.nio.file.Files.createDirectories(content.resolve("link-groups"));
        java.nio.file.Files.createDirectories(content.resolve("models"));
        java.nio.file.Files.createDirectories(content.resolve("dicts"));
        java.nio.file.Files.createDirectories(content.resolve("assets/img"));
        java.nio.file.Files.createDirectories(dir.resolve("seed/regions"));
        java.nio.file.Files.writeString(content.resolve("assets/img/a.svg"), "<svg/>");
        java.nio.file.Files.writeString(content.resolve("channels.channels.json"), """
                { "channels": [
                  { "originId": "news", "alias": "news", "name": "新闻", "nav": true,
                    "channelModel": "常规", "articleModel": "新闻",
                    "image": "{{asset:img/a.svg}}",
                    "ext": { "seoTitle": "新闻中心",
                             "text": "<p>单页正文 <img src=\\"{{asset:img/a.svg}}\\"></p>" } },
                  { "originId": "news-sub", "alias": "news-sub", "name": "子栏目", "parent": "news",
                    "articleModel": "视频", "nav": false },
                  { "originId": "message-board", "alias": "message-board", "name": "留言",
                    "type": "link", "linkUrl": "/message-board", "targetBlank": true }
                ] }
                """);
        java.nio.file.Files.writeString(content.resolve("articles/news.articles.json"), """
                { "articles": [
                  { "originId": "a-welcome", "channel": "news", "title": "欢迎使用全新主题",
                    "image": "{{asset:img/a.svg}}",
                    "text": "<p>正文 <img src=\\"{{asset:img/a.svg}}\\"></p>",
                    "publishOffsetDays": -3, "tags": ["示例", "主题"],
                    "imageList": [ { "url": "{{asset:img/a.svg}}", "name": "图1" } ],
                    "fileList": [ { "url": "{{asset:img/a.svg}}", "name": "附件.svg", "length": 6 } ],
                    "ext": { "author": "作者", "video": "{{asset:img/a.svg}}", "videoDuration": 4 },
                    "customs": { "cover": "{{asset:img/a.svg}}" } }
                ] }
                """);
        java.nio.file.Files.writeString(content.resolve("topics/demo.topics.json"), """
                { "topics": [
                  { "originId": "t-featured", "alias": "featured", "title": "精选专题",
                    "articles": ["a-welcome"] }
                ] }
                """);
        // E4a / D25：定义组（promo——三元组 upsert，条目内嵌）+ A6 条目组（focus——沿用既有全局区块）
        java.nio.file.Files.writeString(content.resolve("blocks/focus.blocks.json"), """
                { "blocks": [
                  { "originId": "promo", "alias": "promo", "name": "推广位", "title": "推广",
                    "items": [ { "article": "a-welcome", "title": "推广标题" } ] },
                  { "block": "focus", "items": [
                    { "article": "a-welcome", "title": "焦点标题", "image": "{{asset:img/a.svg}}" }
                  ] }
                ] }
                """);
        // E4b / D29：投票定义组（文章式三元组 upsert；选项即定义、无别名恒不 linked）
        java.nio.file.Files.writeString(content.resolve("votes/votes.votes.json"), """
                { "votes": [
                  { "originId": "v-poll", "title": "您从哪里知道本主题的", "description": "示例投票",
                    "multiple": false, "enabled": true,
                    "options": [ "朋友推荐", "搜索引擎", "其它" ] }
                ] }
                """);
        // E4d / D35：问卷定义组（文章式三元组 upsert，写入经 T2 端口；条目/选项随定义）
        java.nio.file.Files.writeString(content.resolve("surveys/surveys.surveys.json"), """
                { "surveys": [
                  { "originId": "s-exp", "title": "体验调查",
                    "items": [
                      { "title": "渠道来源", "options": [ "朋友推荐", "搜索引擎" ] },
                      { "title": "建议", "essay": true }
                    ] }
                ] }
                """);
        // model-seeding D1/D8：字典类型（平铺条目）+ 字典绑定的文章模型（news-sub 绑定其为文章模型，
        // 闭包与按名解析双路覆盖；字典先于模型导入，别名绑定写回站内ID）
        java.nio.file.Files.writeString(content.resolve("dicts/dicts.dicts.json"), """
                { "dictTypes": [
                  { "originId": "video_area", "alias": "video_area", "name": "视频_地区",
                    "entries": [
                      { "name": "华语", "value": "1" },
                      { "name": "欧美", "value": "2" }
                    ] }
                ] }
                """);
        // E4e / D37：表单模型（type=form）随表单类型闭包入包；同样绑定 video_area 字典，
        // 覆盖「表单模型 → 字典」闭包边（collectDictBindings 对 form 模型字段集同法遍历）
        java.nio.file.Files.writeString(content.resolve("models/models.models.json"), """
                { "models": [
                  { "originId": "m-article-video", "type": "article", "name": "视频",
                    "customs": [ { "code": "area", "type": "select", "name": "地区",
                                   "dictTypeId": "video_area" } ] },
                  { "originId": "m-form-signup", "type": "form", "name": "报名表",
                    "customs": [ { "code": "region", "type": "select", "name": "地区",
                                   "dictTypeId": "video_area" } ] }
                ] }
                """);
        // E4e / D38：表单类型定义组（文章式三元组 upsert；绑定模型按名随行、写入经 T2 端口）
        java.nio.file.Files.writeString(content.resolve("forms/forms.forms.json"), """
                { "forms": [
                  { "originId": "f-signup", "name": "在线报名", "model": "报名表",
                    "listTemplate": "form_list_enter", "itemTemplate": "form_item_enter",
                    "mode": 0, "viewable": true }
                ] }
                """);
        // E4f / D42：友情链接分组定义组（区块式三元组 upsert；链接随定义、logo 资源令牌走 §5 遍历）
        java.nio.file.Files.writeString(content.resolve("link-groups/link-groups.link-groups.json"), """
                { "linkGroups": [
                  { "originId": "gov", "alias": "gov", "name": "常用链接", "type": 1,
                    "links": [
                      { "name": "示例站点甲", "url": "https://example.com/a/",
                        "logo": "{{asset:img/a.svg}}" },
                      { "name": "示例站点乙", "url": "https://example.com/b/", "enabled": true }
                    ] }
                ] }
                """);
        java.nio.file.Files.writeString(dir.resolve("seed/regions/home.regions.json"), """
                { "page": "home", "regions": {
                  "body": { "rows": [
                    { "id": "r1", "layout": "full", "columns": [ { "panels": [
                        { "component": "RichHtml",
                          "settings": { "title": "Welcome",
                                        "body": "<p><img src=\\"{{asset:img/a.svg}}\\"></p>",
                                        "cover": { "url": "{{asset:img/a.svg}}", "alt": "图" },
                                        "channel": "{{channel:news}}",
                                        "article": "{{article:a-welcome}}",
                                        "block": "{{block:promo}}",
                                        "vote": "{{vote:v-poll}}",
                                        "deco": "{{theme}}/assets/deco.png" } },
                        { "component": "RichHtml", "settings": { "title": "第二" },
                          "enabled": false, "visibility": "mobile" } ] } ] }
                  ] } } }
                """);
        return dir;
    }

    /**
     * 把导出文件束落成可安装主题目录（{@code seed/} 下逐文件写盘——E2 打包器的最小替身）。
     */
    private static java.nio.file.Path writeBundleAsTheme(java.nio.file.Path dir, SeedExportBundle bundle)
            throws java.io.IOException {
        for (Map.Entry<String, byte[]> entry : bundle.files().entrySet()) {
            java.nio.file.Path target = dir.resolve("seed").resolve(entry.getKey());
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.write(target, entry.getValue());
        }
        return dir;
    }
}
