package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.ThemeContentSeedImporter.ContentSeedResult;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LayoutVariantService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.TopicService;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.common.web.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容种子导入器测试（theme-marketplace §5.4）：树序栏目导入、skipProcess 文章插入、专题绑定、
 * 来源三元组与散列、{@code {{asset:}}} 令牌展开与确定性上传路径、幂等跳过、别名冲突 linked、
 * 变体值物化模板列、缺专题模型的整类失败。
 */
class ThemeContentSeedImporterTest {
    private static final Long SITE = 1L;
    private static final String THEME = "mytheme";
    private static final String ORIGIN = "theme:mytheme";

    private ChannelService channelService;
    private ArticleService articleService;
    private TopicService topicService;
    private ModelService modelService;
    private SiteService siteService;
    private BlockService blockService;
    private BlockItemService blockItemService;
    private LayoutVariantService layoutVariantService;
    private ChannelMapper channelMapper;
    private ArticleMapper articleMapper;
    private TopicMapper topicMapper;
    private AttachmentService attachmentService;
    private com.ujcms.cms.core.mapper.BlockMapper blockMapper;
    private com.ujcms.cms.ext.service.VoteService voteService;
    private com.ujcms.cms.ext.mapper.VoteMapper voteMapper;
    private com.ujcms.cms.ext.mapper.SurveyMapper surveyMapper;
    private com.ujcms.cms.ext.mapper.FormTypeMapper formTypeMapper;
    private com.ujcms.cms.ext.service.LinkGroupService linkGroupService;
    private com.ujcms.cms.ext.service.LinkService linkService;
    private com.ujcms.cms.ext.mapper.LinkGroupMapper linkGroupMapper;
    private com.ujcms.cms.core.mapper.ModelMapper modelMapper;
    private com.ujcms.cms.core.service.DictTypeService dictTypeService;
    private com.ujcms.cms.core.service.DictService dictService;
    private com.ujcms.cms.core.mapper.DictTypeMapper dictTypeMapper;
    private com.ujcms.cms.ext.service.SurveyService surveyService;
    private com.ujcms.cms.ext.service.FormTypeService formTypeService;
    private ThemeContentSeedImporter importer;
    private SeedAssetCopier assetCopier;
    private final AtomicLong idSeq = new AtomicLong(100);

    @TempDir
    Path uploads;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        articleService = mock(ArticleService.class);
        topicService = mock(TopicService.class);
        modelService = mock(ModelService.class);
        siteService = mock(SiteService.class);
        layoutVariantService = mock(LayoutVariantService.class);
        channelMapper = mock(ChannelMapper.class);
        articleMapper = mock(ArticleMapper.class);
        topicMapper = mock(TopicMapper.class);
        attachmentService = mock(AttachmentService.class);

        // 真实 SeedAssetCopier：本地存储指向 @TempDir（file: 前缀无需 ServletContext）
        ConfigService configService = mock(ConfigService.class);
        Config config = new Config();
        config.setUploadStorage(new Config.Storage(0, "file:" + uploads, "/uploads"));
        when(configService.getUnique()).thenReturn(config);
        assetCopier = new SeedAssetCopier(configService, new PathResolver(null), attachmentService);

        Site site = new Site();
        site.setId(SITE);
        site.setOrgId(3L);
        when(siteService.select(SITE)).thenReturn(site);
        // 站点可见模型：栏目「常规」/ 文章「新闻」；专题模型由各用例自行声明
        when(modelService.selectList(any(ModelArgs.class))).thenAnswer(inv -> {
            ModelArgs args = inv.getArgument(0);
            String type = (String) args.getQueryMap().get("EQ_type");
            return switch (type) {
                case "channel" -> List.of(model(12L, "常规", "channel"));
                case "article" -> List.of(model(11L, "新闻", "article"));
                case "form" -> List.of(model(60L, "报名表", "form"));
                default -> List.of();
            };
        });
        // 服务层插入：分配ID（模拟持久化）
        doAnswer(inv -> {
            Channel c = inv.getArgument(0);
            c.setId(idSeq.incrementAndGet());
            return null;
        }).when(channelService).insert(any(Channel.class), anyList(), anyList(), anyList());
        doAnswer(inv -> {
            Article a = inv.getArgument(0);
            a.setId(idSeq.incrementAndGet());
            return null;
        }).when(articleService).insert(any(Article.class), anyLong(), anyList(), anyBoolean());
        doAnswer(inv -> {
            Topic t = inv.getArgument(0);
            t.setId(idSeq.incrementAndGet());
            return null;
        }).when(topicService).insert(any(Topic.class));

        // 站点可见区块：全局「焦点」（区块定义不可种子化，导入按别名解析既有行）
        blockService = mock(BlockService.class);
        blockItemService = mock(BlockItemService.class);
        Block focus = new Block();
        focus.setId(40L);
        focus.setAlias("focus");
        when(blockService.selectList(any(BlockArgs.class))).thenReturn(List.of(focus));
        doAnswer(inv -> {
            BlockItem item = inv.getArgument(0);
            item.setId(idSeq.incrementAndGet());
            return null;
        }).when(blockItemService).insert(any(BlockItem.class));

        blockMapper = mock(com.ujcms.cms.core.mapper.BlockMapper.class);
        voteService = mock(com.ujcms.cms.ext.service.VoteService.class);
        voteMapper = mock(com.ujcms.cms.ext.mapper.VoteMapper.class);
        surveyMapper = mock(com.ujcms.cms.ext.mapper.SurveyMapper.class);
        surveyService = mock(com.ujcms.cms.ext.service.SurveyService.class);
        formTypeMapper = mock(com.ujcms.cms.ext.mapper.FormTypeMapper.class);
        formTypeService = mock(com.ujcms.cms.ext.service.FormTypeService.class);
        modelMapper = mock(com.ujcms.cms.core.mapper.ModelMapper.class);
        dictTypeService = mock(com.ujcms.cms.core.service.DictTypeService.class);
        dictService = mock(com.ujcms.cms.core.service.DictService.class);
        dictTypeMapper = mock(com.ujcms.cms.core.mapper.DictTypeMapper.class);
        when(dictTypeService.selectList(any(com.ujcms.cms.core.service.args.DictTypeArgs.class)))
                .thenReturn(List.of());
        linkGroupService = mock(com.ujcms.cms.ext.service.LinkGroupService.class);
        linkService = mock(com.ujcms.cms.ext.service.LinkService.class);
        linkGroupMapper = mock(com.ujcms.cms.ext.mapper.LinkGroupMapper.class);
        when(linkService.selectList(any(com.ujcms.cms.ext.service.args.LinkArgs.class)))
                .thenReturn(List.of());
        importer = new ThemeContentSeedImporter(channelService, articleService, topicService, modelService,
                siteService, blockService, blockItemService,
                layoutVariantService, mock(com.ujcms.cms.core.security.HtmlSanitizeService.class),
                channelMapper, articleMapper, topicMapper, blockMapper, voteService, voteMapper,
                surveyMapper, surveyService, formTypeMapper, formTypeService,
                linkGroupService, linkService, linkGroupMapper,
                modelMapper, dictTypeService, dictService, dictTypeMapper,
                new CompositionSchemas(), assetCopier);
    }

    private static Model model(Long id, String name, String type) {
        Model model = new Model();
        model.setId(id);
        model.setName(name);
        model.setType(type);
        return model;
    }

    private Path themeDir(Path tmp) throws IOException {
        Path dir = tmp.resolve("theme");
        Path content = dir.resolve("seed/content");
        Files.createDirectories(content.resolve("articles"));
        Files.createDirectories(content.resolve("topics"));
        Files.createDirectories(content.resolve("assets/img"));
        Files.writeString(content.resolve("assets/img/a.svg"), "<svg/>");
        Files.writeString(content.resolve("channels.channels.json"), """
                { "channels": [
                  { "originId": "news", "alias": "news", "name": "新闻", "nav": true,
                    "articleModel": "新闻", "ext": { "seoTitle": "新闻" } },
                  { "originId": "news-sub", "name": "子栏目", "parent": "news",
                    "channelLayout": "channel_video" }
                ] }
                """);
        Files.writeString(content.resolve("articles/demo.articles.json"), """
                { "articles": [
                  { "originId": "a-1", "channel": "news", "title": "欢迎",
                    "image": "{{asset:img/a.svg}}",
                    "text": "<p>hi <img src=\\"{{asset:img/a.svg}}\\"></p>",
                    "publishOffsetDays": -3, "tags": ["示例"],
                    "imageList": [ { "url": "{{asset:img/a.svg}}", "name": "图1" } ],
                    "fileList": [ { "url": "{{asset:img/a.svg}}", "name": "附件.svg", "length": 6 },
                                  { "url": "{{asset:img/a.svg}}" } ],
                    "ext": { "author": "作者", "video": "{{asset:img/a.svg}}", "videoDuration": 4,
                             "file": "{{asset:img/a.svg}}", "fileName": "演示.zip", "fileLength": 215 } }
                ] }
                """);
        Files.writeString(content.resolve("topics/demo.topics.json"), """
                { "topics": [
                  { "originId": "t-1", "title": "专题", "articles": ["a-1", "a-unknown"] }
                ] }
                """);
        Files.createDirectories(content.resolve("blocks"));
        Files.writeString(content.resolve("blocks/demo.blocks.json"), """
                { "blocks": [
                  { "block": "focus", "items": [
                    { "article": "a-1", "title": "焦点标题", "image": "{{asset:img/a.svg}}" }
                  ] }
                ] }
                """);
        return dir;
    }

    /** 导入器装配无版本级别参数（D46）：专题定义在任何版本都经 TopicService 入库，栏目/文章/区块条目同行。 */
    @Test
    void importsTreeArticlesTopicsWithOriginHashAndAssets(@TempDir Path tmp) throws IOException {
        when(modelService.selectList(any(ModelArgs.class))).thenAnswer(inv -> {
            ModelArgs args = inv.getArgument(0);
            String type = (String) args.getQueryMap().get("EQ_type");
            return switch (type) {
                case "channel" -> List.of(model(12L, "常规", "channel"));
                case "article" -> List.of(model(11L, "新闻", "article"));
                case "topic" -> List.of(model(31L, "专题", "topic"));
                default -> List.of();
            };
        });

        ContentSeedResult result = importer.importContent(SITE, THEME, themeDir(tmp).toFile(), 5L);

        assertEquals(2, result.channels().imported());
        assertEquals(1, result.articles().imported());
        assertEquals(1, result.topics().imported());
        assertEquals(1, result.blockItems().imported());
        assertEquals(1, result.assetsCopied());

        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelService, org.mockito.Mockito.times(2))
                .insert(channelCaptor.capture(), anyList(), anyList(), anyList());
        Channel news = channelCaptor.getAllValues().get(0);
        assertEquals(ORIGIN, news.getOrigin());
        assertEquals("news", news.getOriginId());
        assertEquals("news", news.getSlug());
        assertEquals(11L, news.getArticleModelId());
        assertEquals("新闻", news.getSeoTitle());
        Channel sub = channelCaptor.getAllValues().get(1);
        // 树序：parent originId 解析为已分配的父栏目ID；alias 缺省取 originId
        assertEquals(news.getId(), sub.getParentId());
        assertEquals("news-sub", sub.getSlug());
        verify(channelMapper).updateOriginHash(news.getId(), SeedHash.channelHash(news));

        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        // skipProcess 恒为 true（演示数据绝不生成审批任务）
        verify(articleService).insert(articleCaptor.capture(), eq(5L), eq(List.of("示例")), eq(true));
        Article article = articleCaptor.getValue();
        assertEquals(news.getId(), article.getChannelId());
        assertEquals(3L, article.getOrgId());
        assertEquals(ORIGIN, article.getOrigin());
        assertEquals("a-1", article.getOriginId());
        assertEquals(Article.STATUS_PUBLISHED, article.getStatus());
        // publishOffsetDays：相对导入时刻回移
        assertTrue(article.getPublishDate().isBefore(java.time.OffsetDateTime.now().minusDays(2)));
        // {{asset:}} 展开为上传存储 URL（确定性路径 /theme-seed/<siteId>/<themeName>/…）
        assertEquals("/uploads/theme-seed/1/mytheme/img/a.svg", article.getImage());
        assertTrue(article.getText().contains("src=\"/uploads/theme-seed/1/mytheme/img/a.svg\""));
        // 媒体扩展字段（A6）：video/file 令牌展开，标量原样，图片集 url 展开
        assertEquals("/uploads/theme-seed/1/mytheme/img/a.svg", article.getVideo());
        assertEquals(4, article.getVideoDuration());
        assertEquals("/uploads/theme-seed/1/mytheme/img/a.svg", article.getFile());
        assertEquals("演示.zip", article.getFileName());
        assertEquals(215L, article.getFileLength());
        assertEquals(1, article.getImageList().size());
        assertEquals("/uploads/theme-seed/1/mytheme/img/a.svg", article.getImageList().get(0).getUrl());
        // 附件列表（D45）：url 展开，name/length 缺省为空串/0
        assertEquals(2, article.getFileList().size());
        assertEquals("/uploads/theme-seed/1/mytheme/img/a.svg", article.getFileList().get(0).getUrl());
        assertEquals("附件.svg", article.getFileList().get(0).getName());
        assertEquals(6L, article.getFileList().get(0).getLength());
        assertEquals("", article.getFileList().get(1).getName());
        assertEquals(0L, article.getFileList().get(1).getLength());
        verify(articleMapper).updateOriginHash(article.getId(),
                SeedHash.articleHash(article, List.of("示例")));
        // 资源实际落盘 + 附件登记
        assertTrue(Files.isRegularFile(uploads.resolve("theme-seed/1/mytheme/img/a.svg")));
        verify(attachmentService).insert(any());

        ArgumentCaptor<Topic> topicCaptor = ArgumentCaptor.forClass(Topic.class);
        verify(topicService).insert(topicCaptor.capture());
        Topic topic = topicCaptor.getValue();
        assertEquals(ORIGIN, topic.getOrigin());
        assertEquals(31L, topic.getTopicModelId());
        assertEquals("t-1", topic.getSlug());
        verify(topicMapper).updateOriginHash(topic.getId(), SeedHash.topicHash(topic));
        // 专题文章绑定：可解析的按序绑定，未知 originId 告警跳过
        verify(articleService).assignArticlesToTopic(eq(topic.getId()), eq(List.of(article.getId())), eq(null));

        // 区块条目（A6）：按别名解析既有区块，文章 originId → 已分配ID，图片令牌展开；无来源标记
        ArgumentCaptor<BlockItem> itemCaptor = ArgumentCaptor.forClass(BlockItem.class);
        verify(blockItemService).insert(itemCaptor.capture());
        BlockItem item = itemCaptor.getValue();
        assertEquals(40L, item.getBlockId());
        assertEquals(article.getId(), item.getArticleId());
        assertEquals("焦点标题", item.getTitle());
        assertEquals("/uploads/theme-seed/1/mytheme/img/a.svg", item.getImage());
    }

    @Test
    void reimportSkipsByOriginWithoutReassigningTopicArticles(@TempDir Path tmp) throws IOException {
        Channel existingChannel = new Channel();
        existingChannel.setId(500L);
        existingChannel.setSlug("news");
        when(channelMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(existingChannel);
        Article existingArticle = new Article();
        existingArticle.setId(600L);
        when(articleMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(existingArticle);
        Topic existingTopic = new Topic();
        existingTopic.setId(700L);
        when(topicMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(existingTopic);
        // 区块条目按 (blockId, articleId) 幂等：已存在即跳过
        when(blockItemService.countByBlockIdAndArticleId(anyLong(), anyLong())).thenReturn(true);

        ContentSeedResult result = importer.importContent(SITE, THEME, themeDir(tmp).toFile(), null);

        assertEquals(2, result.channels().skipped());
        assertEquals(1, result.articles().skipped());
        assertEquals(1, result.topics().skipped());
        assertEquals(1, result.blockItems().skipped());
        verify(channelService, never()).insert(any(), anyList(), anyList(), anyList());
        verify(articleService, never()).insert(any(), anyLong(), anyList(), anyBoolean());
        verify(topicService, never()).insert(any());
        verify(blockItemService, never()).insert(any());
        // 跳过的专题不重置操作员的文章编排
        verify(articleService, never()).assignArticlesToTopic(any(), anyList(), any());
    }

    @Test
    void aliasCollisionLinksWithoutAdopting(@TempDir Path tmp) throws IOException {
        Channel operatorChannel = new Channel();
        operatorChannel.setId(900L);
        operatorChannel.setSlug("news");
        when(channelService.findBySiteIdAndSlug(SITE, "news")).thenReturn(operatorChannel);

        ContentSeedResult result = importer.importContent(SITE, THEME, themeDir(tmp).toFile(), 5L);

        assertEquals(1, result.channels().linked());
        assertEquals(1, result.channels().imported());
        // linked 栏目不被收编：不插入、不打来源标记
        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelService).insert(channelCaptor.capture(), anyList(), anyList(), anyList());
        assertEquals("news-sub", channelCaptor.getValue().getOriginId());
        // 子栏目/文章的引用解析指向既有操作员栏目
        assertEquals(900L, channelCaptor.getValue().getParentId());
        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleService).insert(articleCaptor.capture(), anyLong(), anyList(), eq(true));
        assertEquals(900L, articleCaptor.getValue().getChannelId());
    }

    @Test
    void seedVariantValueMaterializesTemplateColumn(@TempDir Path tmp) throws IOException {
        // 模板轨道物化（template-track-variants D8）：种子变体值经安装主题绑定落到显式模板列
        when(layoutVariantService.themeBoundTemplate(SITE + "/" + THEME,
                LayoutVariantCatalog.SURFACE_CHANNEL, "channel_video")).thenReturn("channel_video");

        importer.importContent(SITE, THEME, themeDir(tmp).toFile(), 5L);

        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelService, org.mockito.Mockito.times(2))
                .insert(channelCaptor.capture(), anyList(), anyList(), anyList());
        Channel seeded = channelCaptor.getAllValues().get(1);
        assertEquals("channel_video", seeded.getChannelTemplate());
    }

    @Test
    void linkChannelImportsLinkUrlAndTargetBlank(@TempDir Path tmp) throws IOException {
        // D45：转向链接栏目可种子化——type=link 落 TYPE_LINK，linkUrl 原样写入（不展开令牌），targetBlank 随种子
        Path dir = tmp.resolve("theme");
        Path content = dir.resolve("seed/content");
        Files.createDirectories(content);
        Files.writeString(content.resolve("channels.channels.json"), """
                { "channels": [
                  { "originId": "message-board", "name": "留言", "type": "link",
                    "linkUrl": "/message-board", "targetBlank": true },
                  { "originId": "about", "name": "关于", "type": "single" },
                  { "originId": "news", "name": "新闻" }
                ] }
                """);

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L);

        assertEquals(3, result.channels().imported());
        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelService, org.mockito.Mockito.times(3))
                .insert(channelCaptor.capture(), anyList(), anyList(), anyList());
        Channel link = channelCaptor.getAllValues().get(0);
        assertEquals(Channel.TYPE_LINK, link.getType());
        assertEquals("/message-board", link.getLinkUrl());
        assertTrue(link.getTargetBlank());
        verify(channelMapper).updateOriginHash(link.getId(), SeedHash.channelHash(link));
        Channel single = channelCaptor.getAllValues().get(1);
        assertEquals(Channel.TYPE_SINGLE, single.getType());
        assertNull(single.getLinkUrl());
        assertFalse(single.getTargetBlank());
        assertEquals(Channel.TYPE_NORMAL, channelCaptor.getAllValues().get(2).getType());
    }

    @Test
    void missingTopicModelFailsTopicsOnly(@TempDir Path tmp) throws IOException {
        // setUp 默认无 topic 模型
        ContentSeedResult result = importer.importContent(SITE, THEME, themeDir(tmp).toFile(), 5L);

        assertEquals(1, result.topics().failed());
        assertEquals(0, result.topics().imported());
        verify(topicService, never()).insert(any());
        // 其余类别不受影响
        assertEquals(2, result.channels().imported());
        assertEquals(1, result.articles().imported());
        // 失败明细随结果出仓（§8.1 / D22）：可译原因码 + originId——控制台据此给出建模补齐指引
        assertEquals(1, result.failures().size());
        ThemeContentSeedImporter.Failure failure = result.failures().get(0);
        assertEquals(ThemeContentSeedImporter.Failure.CAT_TOPICS, failure.category());
        assertEquals("t-1", failure.ref());
        assertEquals(ThemeContentSeedImporter.Failure.REASON_TOPIC_MODEL_MISSING, failure.reason());
        assertTrue(failure.detail().contains("create one and re-import"));
    }

    @Test
    void unknownFailureRecordsExceptionDetail(@TempDir Path tmp) throws IOException {
        doAnswer(inv -> {
            throw new IllegalStateException("boom on insert");
        }).when(articleService).insert(any(Article.class), anyLong(), anyList(), anyBoolean());

        ContentSeedResult result = importer.importContent(SITE, THEME, themeDir(tmp).toFile(), 5L);

        assertEquals(1, result.articles().failed());
        // 未登记原因码的异常按 unknown 记录，详情为异常消息（控制台回退展示原文）
        ThemeContentSeedImporter.Failure failure = result.failures().stream()
                .filter(f -> ThemeContentSeedImporter.Failure.CAT_ARTICLES.equals(f.category()))
                .findFirst().orElseThrow();
        assertEquals("a-1", failure.ref());
        assertEquals(ThemeContentSeedImporter.Failure.REASON_UNKNOWN, failure.reason());
        assertEquals("boom on insert", failure.detail());
    }

    @Test
    void noContentDirYieldsEmptyResult(@TempDir Path tmp) throws IOException {
        Path bare = tmp.resolve("bare");
        Files.createDirectories(bare);
        ContentSeedResult result = importer.importContent(SITE, THEME, bare.toFile(), null);
        assertEquals(ContentSeedResult.EMPTY, result);
    }

    // ----- 区块定义（E4a / D23/D25） -----

    /** 定义组：promo 新建（本站私有 + 三元组 + 散列）；focus 别名撞上既有全局区块 → linked，条目照常填充。 */
    @Test
    void blockDefInsertsWithTripleAndLinksOnAliasCollision(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.writeString(dir.resolve("seed/content/blocks/demo.blocks.json"), """
                { "blocks": [
                  { "originId": "promo", "name": "推广位", "title": "推广",
                    "items": [ { "article": "a-1", "title": "推广条目" } ] },
                  { "originId": "focus", "alias": "focus", "name": "首页焦点", "items": [
                    { "article": "a-1", "title": "焦点标题" }
                  ] }
                ] }
                """);
        doAnswer(inv -> {
            Block bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            return null;
        }).when(blockService).insert(any(Block.class), anyLong());

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L);

        assertEquals(1, result.blocks().imported());
        assertEquals(1, result.blocks().linked());
        assertEquals(2, result.blockItems().imported());
        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
        verify(blockService).insert(blockCaptor.capture(), eq(SITE));
        Block promo = blockCaptor.getValue();
        assertEquals(ORIGIN, promo.getOrigin());
        assertEquals("promo", promo.getOriginId());
        // 别名缺省取 originId；新建恒为本站私有（D23）
        assertEquals("promo", promo.getAlias());
        assertEquals((short) 0, promo.getScope());
        verify(blockMapper).updateOriginHash(promo.getId(), SeedHash.blockHash(promo));
        // linked 的 focus 不插入、不认领（D7 类比），其条目挂到既有全局区块（ID 40）
        ArgumentCaptor<BlockItem> itemCaptor = ArgumentCaptor.forClass(BlockItem.class);
        verify(blockItemService, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        assertTrue(itemCaptor.getAllValues().stream().anyMatch(item -> item.getBlockId().equals(40L)));
        assertTrue(itemCaptor.getAllValues().stream().anyMatch(item -> item.getBlockId().equals(promo.getId())));
    }

    /** 定义刷新（§6.1）：syncUnmodified 只写投影字段，条目模型绑定/scope/排序不动，散列重落。 */
    @Test
    void blockDefSyncUnmodifiedRefreshesProjectionOnly(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.writeString(dir.resolve("seed/content/blocks/demo.blocks.json"), """
                { "blocks": [ { "originId": "promo", "name": "推广位（新）", "title": "推广" } ] }
                """);
        Block existing = new Block();
        existing.setId(70L);
        existing.setSiteId(SITE);
        existing.setAlias("promo");
        existing.setName("推广位");
        existing.setBlockItemModelId(88L);
        existing.setOrigin(ORIGIN);
        existing.setOriginId("promo");
        existing.setOriginHash(SeedHash.blockHash(existing));
        when(blockMapper.selectByOrigin(SITE, ORIGIN, "promo")).thenReturn(existing);

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L,
                SeedOverwrite.SYNC_UNMODIFIED);

        assertEquals(1, result.blocks().updated());
        verify(blockService).update(eq(existing), eq(SITE));
        assertEquals("推广位（新）", existing.getName());
        assertEquals("推广", existing.getTitle());
        // 条目模型绑定保持操作员所有（D23）；散列按最终状态重落
        assertEquals(88L, existing.getBlockItemModelId());
        verify(blockMapper).updateOriginHash(70L, SeedHash.blockHash(existing));
    }

    // ----- 投票定义（E4b / D29） -----

    /** 定义组：新建携带三元组（本站私有）、选项随定义（序即排列）、散列按最终状态落库。 */
    @Test
    void voteDefInsertsWithTripleAndOptions(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/votes"));
        Files.writeString(dir.resolve("seed/content/votes/demo.votes.json"), """
                { "votes": [
                  { "originId": "v-1", "title": "满意度", "options": [ "满意", "不满意" ] }
                ] }
                """);
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.Vote bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            return null;
        }).when(voteService).insert(any(com.ujcms.cms.ext.domain.Vote.class), anyList());

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L);

        assertEquals(1, result.votes().imported());
        assertEquals(0, result.votes().linked());
        ArgumentCaptor<com.ujcms.cms.ext.domain.Vote> voteCaptor =
                ArgumentCaptor.forClass(com.ujcms.cms.ext.domain.Vote.class);
        ArgumentCaptor<List<com.ujcms.cms.ext.domain.VoteOption>> optionsCaptor = ArgumentCaptor.captor();
        verify(voteService).insert(voteCaptor.capture(), optionsCaptor.capture());
        com.ujcms.cms.ext.domain.Vote vote = voteCaptor.getValue();
        assertEquals(ORIGIN, vote.getOrigin());
        assertEquals("v-1", vote.getOriginId());
        assertEquals(SITE, vote.getSiteId());
        assertEquals(List.of("满意", "不满意"), optionsCaptor.getValue().stream()
                .map(com.ujcms.cms.ext.domain.VoteOption::getTitle).toList());
        verify(voteMapper).updateOriginHash(vote.getId(), SeedHash.voteHash(vote));
    }

    /** 定义刷新（§6.1 / D29）：syncUnmodified 按标题匹配既有选项（保留 id 与计票），
     * 未命中插入 0 票、缺席删除；times 与档期不动，散列按最终状态重落。 */
    @Test
    void voteDefSyncUnmodifiedMatchesOptionsByTitle(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/votes"));
        Files.writeString(dir.resolve("seed/content/votes/demo.votes.json"), """
                { "votes": [
                  { "originId": "v-1", "title": "满意度（新）", "options": [ "满意", "一般" ] }
                ] }
                """);
        com.ujcms.cms.ext.domain.Vote existing = new com.ujcms.cms.ext.domain.Vote();
        existing.setId(90L);
        existing.setSiteId(SITE);
        existing.setTitle("满意度");
        existing.setTimes(7);
        com.ujcms.cms.ext.domain.VoteOption keep = new com.ujcms.cms.ext.domain.VoteOption();
        keep.setId(1L);
        keep.setTitle("满意");
        keep.setCount(5);
        com.ujcms.cms.ext.domain.VoteOption gone = new com.ujcms.cms.ext.domain.VoteOption();
        gone.setId(2L);
        gone.setTitle("不满意");
        gone.setCount(3);
        existing.setOptions(new java.util.ArrayList<>(List.of(keep, gone)));
        existing.setOrigin(ORIGIN);
        existing.setOriginId("v-1");
        existing.setOriginHash(SeedHash.voteHash(existing));
        when(voteMapper.selectByOrigin(SITE, ORIGIN, "v-1")).thenReturn(existing);

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L,
                SeedOverwrite.SYNC_UNMODIFIED);

        assertEquals(1, result.votes().updated());
        ArgumentCaptor<List<com.ujcms.cms.ext.domain.VoteOption>> optionsCaptor = ArgumentCaptor.captor();
        verify(voteService).update(eq(existing), optionsCaptor.capture());
        List<com.ujcms.cms.ext.domain.VoteOption> options = optionsCaptor.getValue();
        assertEquals(2, options.size());
        // 标题命中的既有选项保留 id 与计票；新标题为全新选项（0 票）；缺席的「不满意」不入列
        assertEquals(1L, options.get(0).getId());
        assertEquals(5, options.get(0).getCount());
        assertEquals("一般", options.get(1).getTitle());
        assertEquals(0, options.get(1).getCount());
        assertEquals("满意度（新）", existing.getTitle());
        assertEquals(7, existing.getTimes());
        verify(voteMapper).updateOriginHash(90L, SeedHash.voteHash(existing));
    }

    // ----- 链接组（E4f / D42） -----

    /** 定义组：新建携带三元组（本站私有）、链接随定义按种子序插入、散列按最终状态落库；
     * 别名撞既有分组 → linked 不收编、链接以 三选一目标 幂等填空。 */
    @Test
    void linkGroupDefInsertsWithTripleAndLinksAliasCollisionLinks(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/link-groups"));
        Files.writeString(dir.resolve("seed/content/link-groups/demo.link-groups.json"), """
                { "linkGroups": [
                  { "originId": "gov", "name": "常用链接",
                    "links": [
                      { "name": "示例站点甲", "url": "https://example.com/a/" },
                      { "name": "示例站点乙", "url": "https://example.com/b/" }
                    ] },
                  { "originId": "media", "name": "友情链接",
                    "links": [
                      { "name": "示例站点丙", "url": "https://example.com/c/" },
                      { "name": "既有链接", "url": "https://existing.example.com/" }
                    ] }
                ] }
                """);
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.LinkGroup bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            return null;
        }).when(linkGroupService).insert(any(com.ujcms.cms.ext.domain.LinkGroup.class));
        List<com.ujcms.cms.ext.domain.Link> inserted = new java.util.ArrayList<>();
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.Link bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            inserted.add(bean);
            return null;
        }).when(linkService).insert(any(com.ujcms.cms.ext.domain.Link.class));
        // 操作员既有分组 media：别名冲突 → linked；其既有链接命中种子 url 即跳过
        com.ujcms.cms.ext.domain.LinkGroup operatorType = new com.ujcms.cms.ext.domain.LinkGroup();
        operatorType.setId(400L);
        operatorType.setSiteId(SITE);
        operatorType.setAlias("media");
        operatorType.setName("媒体");
        when(linkGroupService.selectByAlias(SITE, "media")).thenReturn(operatorType);
        com.ujcms.cms.ext.domain.Link existingLink = new com.ujcms.cms.ext.domain.Link();
        existingLink.setId(401L);
        existingLink.setGroupId(400L);
        existingLink.setLinkUrl("https://existing.example.com/");
        existingLink.setOrder(401L);
        when(linkService.selectList(any(com.ujcms.cms.ext.service.args.LinkArgs.class)))
                .thenAnswer(inv -> {
                    Object typeId = inv.<com.ujcms.cms.ext.service.args.LinkArgs>getArgument(0)
                            .getQueryMap().get("EQ_groupId_Long");
                    return Long.valueOf(400L).equals(typeId) ? List.of(existingLink) : List.of();
                });

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L);

        assertEquals(1, result.linkGroups().imported());
        assertEquals(1, result.linkGroups().linked());
        // gov 组 2 链接 + media 组 1 新链接（既有 url 幂等跳过）
        assertEquals(3, result.linkItems().imported());
        assertEquals(1, result.linkItems().skipped());
        ArgumentCaptor<com.ujcms.cms.ext.domain.LinkGroup> typeCaptor =
                ArgumentCaptor.forClass(com.ujcms.cms.ext.domain.LinkGroup.class);
        verify(linkGroupService).insert(typeCaptor.capture());
        com.ujcms.cms.ext.domain.LinkGroup type = typeCaptor.getValue();
        assertEquals(ORIGIN, type.getOrigin());
        assertEquals("gov", type.getOriginId());
        assertEquals("gov", type.getAlias());
        assertEquals(SITE, type.getSiteId());
        // 新建组的链接按种子序落库；linked 组的新链接归其 typeId
        assertEquals(List.of("https://example.com/a/", "https://example.com/b/",
                        "https://example.com/c/"),
                inserted.stream().map(com.ujcms.cms.ext.domain.Link::getLinkUrl).toList());
        assertEquals(operatorType.getId(), inserted.get(2).getGroupId());
        List<com.ujcms.cms.ext.domain.Link> govLinks = inserted.subList(0, 2);
        verify(linkGroupMapper).updateOriginHash(type.getId(),
                SeedHash.linkGroupHash(type, govLinks));
        // linked 组绝不写散列/三元组
        verify(linkGroupMapper, org.mockito.Mockito.never()).updateOriginHash(eq(400L), any());
    }

    /** 定义刷新（§6.1 / D43）：syncUnmodified 只动散列复现的分组——链接无依赖数据，
     * 按种子序整组重建（旧链接删除、新链接插入），散列按最终状态重落。 */
    @Test
    void linkGroupSyncUnmodifiedRebuildsLinksInSeedOrder(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/link-groups"));
        Files.writeString(dir.resolve("seed/content/link-groups/demo.link-groups.json"), """
                { "linkGroups": [
                  { "originId": "gov", "name": "常用链接（新）",
                    "links": [ { "name": "示例站点甲", "url": "https://example.com/a/" } ] }
                ] }
                """);
        com.ujcms.cms.ext.domain.LinkGroup existing = new com.ujcms.cms.ext.domain.LinkGroup();
        existing.setId(90L);
        existing.setSiteId(SITE);
        existing.setAlias("gov");
        existing.setName("常用链接");
        existing.setOrigin(ORIGIN);
        existing.setOriginId("gov");
        com.ujcms.cms.ext.domain.Link old = new com.ujcms.cms.ext.domain.Link();
        old.setId(91L);
        old.setGroupId(90L);
        old.setName("旧链接");
        old.setLinkUrl("https://old.example.com/");
        old.setOrder(91L);
        existing.setOriginHash(SeedHash.linkGroupHash(existing, List.of(old)));
        when(linkGroupMapper.selectByOrigin(SITE, ORIGIN, "gov")).thenReturn(existing);
        when(linkService.selectList(any(com.ujcms.cms.ext.service.args.LinkArgs.class)))
                .thenReturn(List.of(old));
        List<com.ujcms.cms.ext.domain.Link> inserted = new java.util.ArrayList<>();
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.Link bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            inserted.add(bean);
            return null;
        }).when(linkService).insert(any(com.ujcms.cms.ext.domain.Link.class));

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L,
                SeedOverwrite.SYNC_UNMODIFIED);

        assertEquals(1, result.linkGroups().updated());
        verify(linkGroupService).update(existing);
        verify(linkService).delete(91L);
        assertEquals(List.of("https://example.com/a/"),
                inserted.stream().map(com.ujcms.cms.ext.domain.Link::getLinkUrl).toList());
        assertEquals("常用链接（新）", existing.getName());
        verify(linkGroupMapper).updateOriginHash(90L,
                SeedHash.linkGroupHash(existing, inserted));
    }

    /** 三选一目标（link-groups D4）：url 直存；channel 按站内别名迟绑、article 按本主题来源
     * originId 迟绑（id 与 association 皆置——散列可移植形）；解析失败的链接跳过计数不失败整组；
     * 分组 slot 随种子落库。 */
    @Test
    void linkGroupTypedRefsResolveAndUnresolvableSkips(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/link-groups"));
        Files.writeString(dir.resolve("seed/content/link-groups/demo.link-groups.json"), """
                { "linkGroups": [
                  { "originId": "footer-about", "name": "关于集团", "slot": "footer",
                    "links": [
                      { "name": "官网", "url": "https://www.example.com/" },
                      { "name": "集团简介", "channel": "about" },
                      { "name": "年度报告", "article": "art-report" },
                      { "name": "失联栏目", "channel": "missing" }
                    ] }
                ] }
                """);
        Channel about = new Channel();
        about.setId(800L);
        about.setSlug("about");
        when(channelMapper.findBySiteIdAndSlug(SITE, "about")).thenReturn(List.of(about));
        when(channelMapper.findBySiteIdAndSlug(SITE, "missing")).thenReturn(List.of());
        Article report = new Article();
        report.setId(801L);
        report.setOriginId("art-report");
        when(articleMapper.selectByOrigin(SITE, ORIGIN, "art-report")).thenReturn(report);
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.LinkGroup bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            return null;
        }).when(linkGroupService).insert(any(com.ujcms.cms.ext.domain.LinkGroup.class));
        List<com.ujcms.cms.ext.domain.Link> inserted = new java.util.ArrayList<>();
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.Link bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            inserted.add(bean);
            return null;
        }).when(linkService).insert(any(com.ujcms.cms.ext.domain.Link.class));

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L);

        assertEquals(1, result.linkGroups().imported());
        assertEquals(3, result.linkItems().imported());
        assertEquals(1, result.linkItems().skipped());
        ArgumentCaptor<com.ujcms.cms.ext.domain.LinkGroup> groupCaptor =
                ArgumentCaptor.forClass(com.ujcms.cms.ext.domain.LinkGroup.class);
        verify(linkGroupService).insert(groupCaptor.capture());
        assertEquals("footer", groupCaptor.getValue().getSlot());
        assertEquals("https://www.example.com/", inserted.get(0).getLinkUrl());
        assertEquals(800L, inserted.get(1).getChannelId());
        assertEquals("about", inserted.get(1).getChannel().getSlug());
        assertEquals(801L, inserted.get(2).getArticleId());
        assertEquals("art-report", inserted.get(2).getArticle().getOriginId());
        // 散列按最终三链接状态落库（可移植形：channel 别名 / article originId 入投影）
        verify(linkGroupMapper).updateOriginHash(groupCaptor.getValue().getId(),
                SeedHash.linkGroupHash(groupCaptor.getValue(), inserted));
    }

    // ----- 覆盖策略（§6.1，A3） -----

    /** 与种子 originId=news 对应的既有本源栏目行（R4：散列可控——匹配自身投影即「未改」）。 */
    private Channel existingNews(boolean unmodified) {
        Channel existing = new Channel();
        existing.setId(500L);
        existing.setSiteId(SITE);
        existing.setSlug("news");
        existing.setName("新闻");
        existing.setOrigin(ORIGIN);
        existing.setOriginId("news");
        existing.setCustoms(new java.util.HashMap<>(0));
        existing.setOriginHash(unmodified ? SeedHash.channelHash(existing) : "0".repeat(64));
        when(channelMapper.selectByOrigin(SITE, ORIGIN, "news")).thenReturn(existing);
        return existing;
    }

    @Test
    void syncUnmodifiedRefreshesUntouchedRowAndRestamps(@TempDir Path tmp) throws IOException {
        Channel existing = existingNews(true);

        ContentSeedResult result = importer.importContent(SITE, THEME, themeDir(tmp).toFile(), 5L,
                SeedOverwrite.SYNC_UNMODIFIED);

        assertEquals(1, result.channels().updated());
        verify(channelService).update(eq(existing), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
        // 刷新只写投影字段：树位置/模型绑定不动；散列按最终状态重落
        verify(channelMapper).updateOriginHash(500L, SeedHash.channelHash(existing));
        assertEquals("新闻", existing.getName());
        assertEquals("新闻", existing.getSeoTitle());
    }

    @Test
    void syncUnmodifiedSkipsOperatorEditedRow(@TempDir Path tmp) throws IOException {
        existingNews(false);

        ContentSeedResult result = importer.importContent(SITE, THEME, themeDir(tmp).toFile(), 5L,
                SeedOverwrite.SYNC_UNMODIFIED);

        assertEquals(0, result.channels().updated());
        assertEquals(1, result.channels().skipped());
        verify(channelService, never()).update(any(Channel.class), any(), any(), any());
    }

    @Test
    void resetAllOriginOverwritesEditedOriginRowButNeverLinkedRows(@TempDir Path tmp) throws IOException {
        Channel edited = existingNews(false);
        // news-sub 别名撞上操作员栏目 → linked，任何策略下都不被触碰（安全不变量 1）
        Channel operatorChannel = new Channel();
        operatorChannel.setId(900L);
        operatorChannel.setSlug("news-sub");
        when(channelService.findBySiteIdAndSlug(SITE, "news-sub")).thenReturn(operatorChannel);

        ContentSeedResult result = importer.importContent(SITE, THEME, themeDir(tmp).toFile(), 5L,
                SeedOverwrite.RESET_ALL_ORIGIN);

        assertEquals(1, result.channels().updated());
        assertEquals(1, result.channels().linked());
        verify(channelService).update(eq(edited), any(), any(), any());
        verify(channelService, never()).update(eq(operatorChannel), any(), any(), any());
    }

    @Test
    void overwriteNeverRefreshesRecycledArticles(@TempDir Path tmp) throws IOException {
        Article binned = new Article();
        binned.setId(600L);
        binned.setSiteId(SITE);
        binned.setStatus(Article.STATUS_DELETED);
        binned.setOrigin(ORIGIN);
        binned.setOriginId("a-1");
        when(articleMapper.selectByOrigin(SITE, ORIGIN, "a-1")).thenReturn(binned);

        ContentSeedResult result = importer.importContent(SITE, THEME, themeDir(tmp).toFile(), 5L,
                SeedOverwrite.RESET_ALL_ORIGIN);

        // 回收站中的行不被静默复活/改写：先恢复再刷新
        assertEquals(0, result.articles().updated());
        assertEquals(1, result.articles().skipped());
        verify(articleService, never()).update(any(Article.class), anyLong(), anyList());
    }

    // ----- models / dicts（model-seeding P1）-----

    /** 模型定义插入（D1/D8）：字典先行、别名绑定写回站内ID、三元组 + 散列落库、行本站私有。 */
    @Test
    void modelDefInsertsWithTripleAndDictBinding(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/dicts"));
        Files.writeString(dir.resolve("seed/content/dicts/demo.dicts.json"), """
                { "dictTypes": [
                  { "originId": "video_area", "name": "视频_地区", "entries": [
                    { "name": "华语", "value": "1" },
                    { "name": "欧美", "value": "2" }
                  ] }
                ] }
                """);
        Files.createDirectories(dir.resolve("seed/content/models"));
        Files.writeString(dir.resolve("seed/content/models/demo.models.json"), """
                { "models": [
                  { "originId": "m-article-video", "type": "article", "name": "视频",
                    "customs": [ { "code": "area", "type": "select", "name": "地区",
                                   "dictTypeId": "video_area" } ] }
                ] }
                """);
        java.util.Map<Long, com.ujcms.cms.core.domain.Dict> entryStore = new java.util.LinkedHashMap<>();
        doAnswer(inv -> {
            com.ujcms.cms.core.domain.DictType bean = inv.getArgument(0);
            bean.setId(500L);
            bean.setSiteId(SITE);
            return null;
        }).when(dictTypeService).insert(any(com.ujcms.cms.core.domain.DictType.class), any());
        doAnswer(inv -> {
            com.ujcms.cms.core.domain.Dict bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            entryStore.put(bean.getId(), bean);
            return null;
        }).when(dictService).insert(any(com.ujcms.cms.core.domain.Dict.class));
        when(dictService.listByTypeId(500L)).thenAnswer(inv -> entryStore.values().stream().toList());
        doAnswer(inv -> {
            Model bean = inv.getArgument(0);
            bean.setId(900L);
            bean.setSiteId(SITE);
            return null;
        }).when(modelService).insert(any(Model.class), any());

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L);

        assertEquals(1, result.dicts().imported());
        assertEquals(1, result.models().imported());
        ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
        verify(modelService).insert(modelCaptor.capture(), eq(SITE));
        Model model = modelCaptor.getValue();
        assertEquals(ORIGIN, model.getOrigin());
        assertEquals("m-article-video", model.getOriginId());
        assertEquals("article", model.getType());
        // 别名绑定写回站内ID（字符串形，存储惯例）
        assertTrue(model.getCustoms().contains("\"dictTypeId\":\"500\""));
        // 散列以别名形投影计算（导入写ID、投影回导别名——自洽，D11）
        verify(modelMapper).updateOriginHash(900L,
                SeedHash.modelHash(model, java.util.Map.of(500L, "video_area")));
        // 条目随类型（平铺）：2 条，序按种子序
        assertEquals(2, entryStore.size());
    }

    /** (type, name) 撞可见集 → linked 恒不收编（D1）：出厂同名即常态，恒不插入。 */
    @Test
    void modelNameCollisionLinksNeverAbsorbs(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/models"));
        Files.writeString(dir.resolve("seed/content/models/demo.models.json"), """
                { "models": [
                  { "originId": "m-article-news", "type": "article", "name": "新闻" }
                ] }
                """);

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L);

        assertEquals(1, result.models().linked());
        assertEquals(0, result.models().imported());
        verify(modelService, never()).insert(any(Model.class), any());
    }

    /** 三元组命中而 type 不符 → modelTypeMismatch 失败（D3：type 即身份，刷新恒不改写）。 */
    @Test
    void modelTypeMismatchFailsWithReasonCode(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/models"));
        Files.writeString(dir.resolve("seed/content/models/demo.models.json"), """
                { "models": [
                  { "originId": "m-1", "type": "article", "name": "视频" }
                ] }
                """);
        Model existing = model(910L, "视频", "channel");
        existing.setSiteId(SITE);
        existing.setOrigin(ORIGIN);
        existing.setOriginId("m-1");
        when(modelMapper.selectByOrigin(SITE, ORIGIN, "m-1")).thenReturn(existing);

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L);

        assertEquals(1, result.models().failed());
        assertEquals(1, result.failures().stream()
                .filter(f -> f.category().equals("models")
                        && f.reason().equals(ThemeContentSeedImporter.Failure.REASON_MODEL_TYPE_MISMATCH))
                .count());
        verify(modelService, never()).update(any(Model.class), any());
    }

    /** 字典刷新（D8）：syncUnmodified 按层内 value 匹配既有条目（保留 id），未命中插入、缺席删除。 */
    @Test
    void dictTypeSyncUnmodifiedMatchesEntriesByValue(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/dicts"));
        Files.writeString(dir.resolve("seed/content/dicts/demo.dicts.json"), """
                { "dictTypes": [
                  { "originId": "video_area", "name": "视频_地区（新）", "entries": [
                    { "name": "华语（新）", "value": "1" },
                    { "name": "新增", "value": "2" }
                  ] }
                ] }
                """);
        com.ujcms.cms.core.domain.DictType existing = new com.ujcms.cms.core.domain.DictType();
        existing.setId(700L);
        existing.setSiteId(SITE);
        existing.setAlias("video_area");
        existing.setName("视频_地区");
        existing.setOrigin(ORIGIN);
        existing.setOriginId("video_area");
        com.ujcms.cms.core.domain.Dict keep = new com.ujcms.cms.core.domain.Dict();
        keep.setId(71L);
        keep.setTypeId(700L);
        keep.setName("华语");
        keep.setValue("1");
        keep.setOrder(1);
        com.ujcms.cms.core.domain.Dict gone = new com.ujcms.cms.core.domain.Dict();
        gone.setId(72L);
        gone.setTypeId(700L);
        gone.setName("粤语");
        gone.setValue("9");
        gone.setOrder(2);
        java.util.Map<Long, com.ujcms.cms.core.domain.Dict> entryStore = new java.util.LinkedHashMap<>();
        entryStore.put(keep.getId(), keep);
        entryStore.put(gone.getId(), gone);
        existing.setOriginHash(SeedHash.dictTypeHash(existing, new java.util.ArrayList<>(entryStore.values())));
        when(dictTypeMapper.selectByOrigin(SITE, ORIGIN, "video_area")).thenReturn(existing);
        when(dictService.listByTypeId(700L)).thenAnswer(inv -> entryStore.values().stream().toList());
        doAnswer(inv -> {
            com.ujcms.cms.core.domain.Dict bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            entryStore.put(bean.getId(), bean);
            return null;
        }).when(dictService).insert(any(com.ujcms.cms.core.domain.Dict.class));
        doAnswer(inv -> {
            entryStore.remove(inv.<Long>getArgument(0));
            return 1;
        }).when(dictService).delete(anyLong());

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L,
                SeedOverwrite.SYNC_UNMODIFIED);

        assertEquals(1, result.dicts().updated());
        // value=1 命中：保留 id、改名；value=9 缺席：删除；value=2 新插
        assertEquals("华语（新）", keep.getName());
        verify(dictService).update(keep);
        verify(dictService).delete(72L);
        assertEquals(2, entryStore.size());
        verify(dictTypeMapper).updateOriginHash(eq(700L), any());
        assertEquals("视频_地区（新）", existing.getName());
    }

    /**
     * 别名撞本站既有类型 → linked 不收编；但该类型一条条目都没有时补上种子条目（只填空）——
     * 修复旧版「移除演示数据」留下的空类型，否则前台筛选项为空。类型行本身不改名、不认领。
     */
    @Test
    void linkedDictTypeWithoutEntriesTakesSeedEntries(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/dicts"));
        Files.writeString(dir.resolve("seed/content/dicts/demo.dicts.json"), """
                { "dictTypes": [
                  { "originId": "video_area", "name": "视频_地区", "entries": [
                    { "name": "华语", "value": "1" },
                    { "name": "欧洲", "value": "3" }
                  ] }
                ] }
                """);
        com.ujcms.cms.core.domain.DictType emptied = new com.ujcms.cms.core.domain.DictType();
        emptied.setId(700L);
        emptied.setSiteId(SITE);
        emptied.setAlias("video_area");
        emptied.setName("操作员改过的名字");
        when(dictTypeService.selectList(any(com.ujcms.cms.core.service.args.DictTypeArgs.class)))
                .thenReturn(List.of(emptied));
        List<com.ujcms.cms.core.domain.Dict> inserted = new java.util.ArrayList<>();
        when(dictService.listByTypeId(700L)).thenAnswer(inv -> List.copyOf(inserted));
        doAnswer(inv -> {
            inserted.add(inv.getArgument(0));
            return null;
        }).when(dictService).insert(any(com.ujcms.cms.core.domain.Dict.class));

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L, SeedOverwrite.FILL_ONLY);

        assertEquals(1, result.dicts().linked());
        assertEquals(0, result.dicts().imported());
        assertEquals(List.of("1", "3"), inserted.stream().map(com.ujcms.cms.core.domain.Dict::getValue).toList());
        assertEquals(List.of(700L, 700L), inserted.stream().map(com.ujcms.cms.core.domain.Dict::getTypeId).toList());
        // 类型行不被触碰：不改名、不打来源标记、不写散列
        assertEquals("操作员改过的名字", emptied.getName());
        verify(dictTypeService, never()).update(any(com.ujcms.cms.core.domain.DictType.class), any());
        verify(dictTypeService, never()).insert(any(com.ujcms.cms.core.domain.DictType.class), any());
        verify(dictTypeMapper, never()).updateOriginHash(anyLong(), any());
    }

    /** linked 类型只要有条目（哪怕一条、哪怕与种子不同）就原样不动；全局同别名类型即使为空也不补。 */
    @Test
    void linkedDictTypeWithEntriesOrGlobalIsUntouched(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/dicts"));
        Files.writeString(dir.resolve("seed/content/dicts/demo.dicts.json"), """
                { "dictTypes": [
                  { "originId": "video_area", "name": "视频_地区", "entries": [ { "name": "华语", "value": "1" } ] },
                  { "originId": "video_type", "name": "视频_类型", "entries": [ { "name": "喜剧", "value": "1" } ] }
                ] }
                """);
        com.ujcms.cms.core.domain.DictType operatorOwned = new com.ujcms.cms.core.domain.DictType();
        operatorOwned.setId(700L);
        operatorOwned.setSiteId(SITE);
        operatorOwned.setAlias("video_area");
        com.ujcms.cms.core.domain.DictType globalEmpty = new com.ujcms.cms.core.domain.DictType();
        globalEmpty.setId(701L);
        globalEmpty.setSiteId(null);
        globalEmpty.setAlias("video_type");
        when(dictTypeService.selectList(any(com.ujcms.cms.core.service.args.DictTypeArgs.class)))
                .thenReturn(List.of(operatorOwned, globalEmpty));
        com.ujcms.cms.core.domain.Dict own = new com.ujcms.cms.core.domain.Dict();
        own.setId(71L);
        own.setTypeId(700L);
        own.setName("操作员条目");
        own.setValue("9");
        when(dictService.listByTypeId(700L)).thenReturn(List.of(own));
        when(dictService.listByTypeId(701L)).thenReturn(List.of());

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L, SeedOverwrite.FILL_ONLY);

        assertEquals(2, result.dicts().linked());
        verify(dictService, never()).insert(any(com.ujcms.cms.core.domain.Dict.class));
        verify(dictService, never()).update(any(com.ujcms.cms.core.domain.Dict.class));
        verify(dictService, never()).delete(anyLong());
    }

    /** 表单类型导入（E4e / D38）：三元组插入、绑定模型按名解析（type=form）、写入经核心 FormTypeService、processKey 不入。 */
    @Test
    void formTypeDefInsertsWithTripleAndResolvesModelByName(@TempDir Path tmp) throws IOException {
        Path dir = themeDir(tmp);
        Files.createDirectories(dir.resolve("seed/content/forms"));
        Files.writeString(dir.resolve("seed/content/forms/demo.forms.json"), """
                { "forms": [
                  { "originId": "f-signup", "name": "在线报名", "model": "报名表",
                    "listTemplate": "form_list_enter", "mode": 0, "viewable": true }
                ] }
                """);
        when(formTypeMapper.selectByOrigin(SITE, ORIGIN, "f-signup")).thenReturn(null);
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.FormType bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            return null;
        }).when(formTypeService).insert(any(com.ujcms.cms.ext.domain.FormType.class));

        ContentSeedResult result = importer.importContent(SITE, THEME, dir.toFile(), 5L);

        assertEquals(1, result.forms().imported());
        assertEquals(0, result.forms().failed());
        ArgumentCaptor<com.ujcms.cms.ext.domain.FormType> captor =
                ArgumentCaptor.forClass(com.ujcms.cms.ext.domain.FormType.class);
        verify(formTypeService).insert(captor.capture());
        com.ujcms.cms.ext.domain.FormType saved = captor.getValue();
        assertEquals(ORIGIN, saved.getOrigin());
        assertEquals("f-signup", saved.getOriginId());
        assertEquals("在线报名", saved.getName());
        assertEquals(60L, saved.getModelId());              // 按名解析到 report 站点可见 form 模型
        assertEquals((short) 0, saved.getMode());
        assertTrue(saved.getViewable());
        assertNull(saved.getProcessKey());                  // §7.2：processKey 恒不入种子
        verify(formTypeMapper).updateOriginHash(anyLong(), any());
    }

}
