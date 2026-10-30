package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldTypeRegistry;
import com.ujcms.cms.core.composition.fields.types.BlockRefFieldType;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleTopic;
import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.ChannelExt;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ArticleTopicMapper;
import com.ujcms.cms.core.mapper.BlockItemMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.mapper.ChannelExtMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.DictTypeMapper;
import com.ujcms.cms.core.mapper.ModelMapper;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.LayoutVariantService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.TopicService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.domain.VoteOption;
import com.ujcms.cms.ext.mapper.VoteMapper;
import com.ujcms.cms.ext.service.VoteService;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.PathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 导出往返测试的内存世界（theme-seed-export D4；{@code SeedRoundTripTest} 专用）：
 * Mockito 有状态桩模拟持久层（服务插入分配ID并入内存表、mapper 查询扫内存表、
 * {@code updateOriginHash} 落到 bean），资源走真实 {@link SeedAssetCopier}/
 * {@link SeedAssetExtractor} + {@code file:} 临时存储——导入/导出管线全为真实实现。
 */
final class ExportWorld {
    static final Long SITE = 1L;
    static final String THEME = "demo";

    private final AtomicLong idSeq = new AtomicLong(1000);
    private final Map<Long, Channel> channels = new LinkedHashMap<>();
    private final Map<Long, Article> articles = new LinkedHashMap<>();
    private final Map<Long, Topic> topics = new LinkedHashMap<>();
    private final Map<Long, List<ArticleTopic>> topicBindings = new LinkedHashMap<>();
    private final Map<Long, Block> blocks = new LinkedHashMap<>();
    private final List<BlockItem> blockItems = new ArrayList<>();
    private final Map<Long, Model> models = new LinkedHashMap<>();
    private final Map<Long, DictType> dictTypes = new LinkedHashMap<>();
    private final Map<Long, Dict> dictEntries = new LinkedHashMap<>();
    private final Map<Long, Vote> votes = new LinkedHashMap<>();
    private final Map<Long, com.ujcms.cms.ext.domain.LinkGroup> linkGroups = new LinkedHashMap<>();
    private final Map<Long, com.ujcms.cms.ext.domain.Link> links = new LinkedHashMap<>();
    private final Map<Long, com.ujcms.cms.ext.domain.Survey> surveys = new LinkedHashMap<>();
    /**
     * 问卷定义写入桩（E4d / D46：核心 SurveyService 语义的内存替身——所有版本同一路径）
     */
    final com.ujcms.cms.ext.service.SurveyService surveyService =
            mock(com.ujcms.cms.ext.service.SurveyService.class);
    private final Map<Long, com.ujcms.cms.ext.domain.FormType> formTypes = new LinkedHashMap<>();
    /**
     * 表单类型定义写入桩（E4e / D46：核心 FormTypeService 语义的内存替身——所有版本同一路径）
     */
    final com.ujcms.cms.ext.service.FormTypeService formTypeService =
            mock(com.ujcms.cms.ext.service.FormTypeService.class);
    private final Map<Long, Placement> placements = new LinkedHashMap<>();
    private final List<PageRegion> regions = new ArrayList<>();
    private final Map<String, Attachment> attachmentsByUrl = new LinkedHashMap<>();
    private final Block focusBlock = new Block();

    private final Path uploads;
    private final Path templates;
    private final ThemeContentSeedImporter contentImporter;
    private final ThemeSeedImporter regionImporter;
    final ThemeSeedExporter exporter;
    final SeedOriginAdopter adopter;
    /**
     * 设置服务桩（导入器与 E2 导出编排共用；快照测试按需 stub {@code findState}，§7.4）
     */
    final ThemeSettingsService settingsService;
    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final SiteService siteService;

    ExportWorld(Path root) throws IOException {
        this.uploads = root.resolve("uploads");
        this.templates = root.resolve("templates");
        Files.createDirectories(uploads);

        // ----- 存储与资源（真实组件 + file: 临时目录）-----
        configService = mock(ConfigService.class);
        Config config = new Config();
        config.setUploadStorage(new Config.Storage(0, "file:" + uploads, "/uploads"));
        config.setTemplateStorage(new Config.Storage(0, "file:" + templates, "/themes"));
        when(configService.getUnique()).thenReturn(config);
        pathResolver = new PathResolver(null);
        AttachmentService attachmentService = mock(AttachmentService.class);
        doAnswer(inv -> {
            Attachment attachment = inv.getArgument(0);
            attachmentsByUrl.putIfAbsent(attachment.getUrl(), attachment);
            return null;
        }).when(attachmentService).insert(any(Attachment.class));
        when(attachmentService.findByUrl(any())).thenAnswer(inv ->
                attachmentsByUrl.get(inv.<String>getArgument(0)));
        SeedAssetCopier assetCopier = new SeedAssetCopier(configService, pathResolver, attachmentService);
        SeedAssetExtractor assetExtractor = new SeedAssetExtractor(configService, pathResolver,
                attachmentService);

        // ----- 站点 / 模型 / 布局变体 -----
        siteService = mock(SiteService.class);
        Site site = new Site();
        site.setId(SITE);
        site.setOrgId(3L);
        when(siteService.select(SITE)).thenReturn(site);
        LayoutVariantService layoutVariantService = mock(LayoutVariantService.class);
        // 出厂形（生产镜像）：常规/新闻为全局行（scope=2、siteId=null——恒不认领），专题模型本站私有
        Model channelModel = model(12L, "常规", Channel.MODEL_TYPE, null);
        channelModel.setScope((short) 2);
        Model articleModel = model(11L, "新闻", Article.MODEL_TYPE,
                "[{ \"code\": \"cover\", \"type\": \"imageUpload\" }]");
        articleModel.setScope((short) 2);
        Model topicModel = model(31L, "专题", Topic.MODEL_TYPE, null);
        topicModel.setSiteId(SITE);
        models.put(12L, channelModel);
        models.put(11L, articleModel);
        models.put(31L, topicModel);
        ModelService modelService = mock(ModelService.class);
        when(modelService.selectList(any(ModelArgs.class))).thenAnswer(inv -> {
            ModelArgs args = inv.getArgument(0);
            String type = (String) args.getQueryMap().get("EQ_type");
            return models.values().stream()
                    .filter(model -> type == null || model.getType().equals(type)).toList();
        });
        when(modelService.select(anyLong())).thenAnswer(inv -> models.get(inv.<Long>getArgument(0)));
        doAnswer(inv -> {
            Model bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setSiteId(bean.isGlobal() ? null : inv.getArgument(1));
            models.put(bean.getId(), bean);
            return null;
        }).when(modelService).insert(any(Model.class), any());
        doAnswer(inv -> {
            Model bean = inv.getArgument(0);
            bean.setSiteId(bean.isGlobal() ? null : inv.getArgument(1));
            models.put(bean.getId(), bean);
            return null;
        }).when(modelService).update(any(Model.class), any());
        doAnswer(inv -> models.remove(inv.<Long>getArgument(0)) != null ? 1 : 0)
                .when(modelService).delete(anyLong());
        ModelMapper modelMapper = mock(ModelMapper.class);
        when(modelMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                models.values().stream()
                        .filter(model -> Objects.equals(model.getOrigin(), inv.getArgument(1))
                                && Objects.equals(model.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(modelMapper.listByOrigin(any(), any())).thenAnswer(inv ->
                models.values().stream()
                        .filter(model -> Objects.equals(model.getOrigin(), inv.getArgument(1)))
                        .toList());
        when(modelMapper.countByOrigin(any(), any())).thenAnswer(inv ->
                (int) models.values().stream()
                        .filter(model -> Objects.equals(model.getOrigin(), inv.getArgument(1))).count());
        doAnswer(inv -> {
            models.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return 1;
        }).when(modelMapper).updateOriginHash(anyLong(), any());
        when(modelMapper.clearOrigin(any(), any())).thenAnswer(inv -> {
            int cleared = 0;
            for (Model bean : models.values()) {
                if (Objects.equals(bean.getOrigin(), inv.getArgument(1))) {
                    bean.setOrigin(null);
                    bean.setOriginId(null);
                    bean.setOriginHash(null);
                    cleared++;
                }
            }
            return cleared;
        });
        when(modelMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Model bean = models.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())
                    || bean.getSiteId() == null) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });

        // ----- 字典（model-seeding D8：类型 + 条目内存驻留）-----
        DictTypeService dictTypeService = mock(DictTypeService.class);
        DictService dictService = mock(DictService.class);
        DictTypeMapper dictTypeMapper = mock(DictTypeMapper.class);
        when(dictTypeService.selectList(any(com.ujcms.cms.core.service.args.DictTypeArgs.class)))
                .thenAnswer(inv -> new ArrayList<>(dictTypes.values()));
        doAnswer(inv -> {
            DictType bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setSiteId(bean.isGlobal() ? null : inv.getArgument(1));
            dictTypes.put(bean.getId(), bean);
            return null;
        }).when(dictTypeService).insert(any(DictType.class), any());
        doAnswer(inv -> {
            DictType bean = inv.getArgument(0);
            bean.setSiteId(bean.isGlobal() ? null : inv.getArgument(1));
            dictTypes.put(bean.getId(), bean);
            return null;
        }).when(dictTypeService).update(any(DictType.class), any());
        doAnswer(inv -> dictTypes.remove(inv.<Long>getArgument(0)) != null ? 1 : 0)
                .when(dictTypeService).delete(anyLong());
        doAnswer(inv -> {
            Dict bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            dictEntries.put(bean.getId(), bean);
            return null;
        }).when(dictService).insert(any(Dict.class));
        doAnswer(inv -> {
            Dict bean = inv.getArgument(0);
            dictEntries.put(bean.getId(), bean);
            return null;
        }).when(dictService).update(any(Dict.class));
        doAnswer(inv -> dictEntries.remove(inv.<Long>getArgument(0)) != null ? 1 : 0)
                .when(dictService).delete(anyLong());
        when(dictService.listByTypeId(anyLong())).thenAnswer(inv ->
                dictEntries.values().stream()
                        .filter(entry -> entry.getTypeId().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(Dict::getOrder).thenComparing(Dict::getId))
                        .toList());
        when(dictTypeMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                dictTypes.values().stream()
                        .filter(type -> Objects.equals(type.getOrigin(), inv.getArgument(1))
                                && Objects.equals(type.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(dictTypeMapper.listByOrigin(any(), any())).thenAnswer(inv ->
                dictTypes.values().stream()
                        .filter(type -> Objects.equals(type.getOrigin(), inv.getArgument(1)))
                        .toList());
        when(dictTypeMapper.countByOrigin(any(), any())).thenAnswer(inv ->
                (int) dictTypes.values().stream()
                        .filter(type -> Objects.equals(type.getOrigin(), inv.getArgument(1))).count());
        doAnswer(inv -> {
            dictTypes.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return 1;
        }).when(dictTypeMapper).updateOriginHash(anyLong(), any());
        when(dictTypeMapper.clearOrigin(any(), any())).thenAnswer(inv -> {
            int cleared = 0;
            for (DictType bean : dictTypes.values()) {
                if (Objects.equals(bean.getOrigin(), inv.getArgument(1))) {
                    bean.setOrigin(null);
                    bean.setOriginId(null);
                    bean.setOriginHash(null);
                    cleared++;
                }
            }
            return cleared;
        });
        when(dictTypeMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            DictType bean = dictTypes.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())
                    || bean.getSiteId() == null || Boolean.TRUE.equals(bean.getSys())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });

        // ----- 栏目 -----
        ChannelService channelService = mock(ChannelService.class);
        ChannelMapper channelMapper = mock(ChannelMapper.class);
        ChannelExtMapper channelExtMapper = mock(ChannelExtMapper.class);
        doAnswer(inv -> {
            Channel bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(Math.toIntExact(bean.getId()));
            channels.put(bean.getId(), bean);
            return null;
        }).when(channelService).insert(any(Channel.class), any(ChannelService.Grants.class));
        when(channelService.findBySiteIdAndSlug(any(), any())).thenAnswer(inv ->
                channels.values().stream()
                        .filter(channel -> channel.getSlug().equals(inv.getArgument(1)))
                        .findFirst().orElse(null));
        // 仿真产线列表查询形：列表 resultMap 无 ext 关联（ext 为缺省空实例），ext 由 listBySiteId 批量补挂
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenAnswer(inv -> {
                    List<Channel> list = new ArrayList<>(channels.size());
                    for (Channel src : channels.values()) {
                        Channel copy = new Channel();
                        com.ujcms.common.web.Entities.copy(src, copy);
                        copy.setCustoms(src.getCustoms());
                        copy.setExt(new ChannelExt());
                        list.add(copy);
                    }
                    return list;
                });
        when(channelExtMapper.listBySiteId(any())).thenAnswer(inv -> {
            List<ChannelExt> exts = new ArrayList<>(channels.size());
            for (Channel channel : channels.values()) {
                ChannelExt ext = channel.getExt();
                ext.setId(channel.getId());
                exts.add(ext);
            }
            return exts;
        });
        when(channelMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                channels.values().stream()
                        .filter(channel -> Objects.equals(channel.getOrigin(), inv.getArgument(1))
                                && Objects.equals(channel.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(channelMapper.findBySiteIdAndSlug(any(), any())).thenAnswer(inv ->
                channels.values().stream()
                        .filter(channel -> channel.getSlug().equals(inv.getArgument(1)))
                        .toList());
        doAnswer(inv -> {
            channels.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return null;
        }).when(channelMapper).updateOriginHash(anyLong(), any());
        when(channelMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Channel bean = channels.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });

        // ----- 文章 -----
        ArticleService articleService = mock(ArticleService.class);
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        doAnswer(inv -> {
            Article bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            List<String> tags = inv.getArgument(2);
            List<Tag> tagBeans = new ArrayList<>(tags.size());
            for (String name : tags) {
                tagBeans.add(new Tag(SITE, inv.getArgument(1), name));
            }
            bean.setTags(tagBeans);
            articles.put(bean.getId(), bean);
            return null;
        }).when(articleService).insert(any(Article.class), anyLong(), anyList(), anyBoolean());
        when(articleService.select(anyLong())).thenAnswer(inv -> articles.get(inv.<Long>getArgument(0)));
        when(articleService.selectList(any(ArticleArgs.class), anyInt(), anyInt())).thenAnswer(inv -> {
            ArticleArgs args = inv.getArgument(0);
            Long channelId = (Long) args.getQueryMap().get("EQ_channelId_Long");
            @SuppressWarnings("unchecked")
            Collection<Short> statuses = (Collection<Short>) args.getQueryMap().get("In_status_Short");
            int offset = inv.getArgument(1);
            int limit = inv.getArgument(2);
            return articles.values().stream()
                    .filter(article -> channelId == null || channelId.equals(article.getChannelId()))
                    .filter(article -> statuses == null || statuses.contains(article.getStatus()))
                    .sorted(Comparator.comparing(Article::getPublishDate).reversed()
                            .thenComparing(Article::getId, Comparator.reverseOrder()))
                    .skip(offset).limit(limit).toList();
        });
        doAnswer(inv -> {
            Long topicId = inv.getArgument(0);
            List<Long> ids = inv.getArgument(1);
            List<ArticleTopic> bindings = new ArrayList<>(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                bindings.add(new ArticleTopic(ids.get(i), topicId, i));
            }
            topicBindings.put(topicId, bindings);
            return null;
        }).when(articleService).assignArticlesToTopic(anyLong(), anyList(), any());
        when(articleMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                articles.values().stream()
                        .filter(article -> Objects.equals(article.getOrigin(), inv.getArgument(1))
                                && Objects.equals(article.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(articleMapper.listByOrigin(any(), any())).thenAnswer(inv ->
                articles.values().stream()
                        .filter(article -> Objects.equals(article.getOrigin(), inv.getArgument(1)))
                        .toList());
        doAnswer(inv -> {
            articles.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return null;
        }).when(articleMapper).updateOriginHash(anyLong(), any());
        when(articleMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Article bean = articles.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });

        // ----- 专题 -----
        TopicService topicService = mock(TopicService.class);
        TopicMapper topicMapper = mock(TopicMapper.class);
        ArticleTopicMapper articleTopicMapper = mock(ArticleTopicMapper.class);
        doAnswer(inv -> {
            Topic bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            topics.put(bean.getId(), bean);
            return null;
        }).when(topicService).insert(any(Topic.class));
        when(topicService.findBySiteIdAndSlug(any(), any())).thenAnswer(inv ->
                topics.values().stream()
                        .filter(topic -> topic.getSlug().equals(inv.getArgument(1)))
                        .findFirst().orElse(null));
        when(topicService.select(anyLong())).thenAnswer(inv -> topics.get(inv.<Long>getArgument(0)));
        when(topicMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                topics.values().stream()
                        .filter(topic -> Objects.equals(topic.getOrigin(), inv.getArgument(1))
                                && Objects.equals(topic.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(topicMapper.listByOrigin(any(), any())).thenAnswer(inv ->
                topics.values().stream()
                        .filter(topic -> Objects.equals(topic.getOrigin(), inv.getArgument(1)))
                        .toList());
        doAnswer(inv -> {
            topics.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return null;
        }).when(topicMapper).updateOriginHash(anyLong(), any());
        when(topicMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Topic bean = topics.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });
        when(articleTopicMapper.listByTopicId(anyLong())).thenAnswer(inv ->
                new ArrayList<>(topicBindings.getOrDefault(inv.<Long>getArgument(0), List.of())));

        // ----- 区块（focus 为引导骨架行——全局共享、无来源标记，两个世界同样预置；E4a 定义可种子化）-----
        focusBlock.setId(40L);
        focusBlock.setAlias("focus");
        focusBlock.setName("焦点");
        focusBlock.setScope((short) 2);
        focusBlock.setSiteId(null);
        blocks.put(focusBlock.getId(), focusBlock);
        BlockService blockService = mock(BlockService.class);
        BlockItemService blockItemService = mock(BlockItemService.class);
        BlockItemMapper blockItemMapper = mock(BlockItemMapper.class);
        BlockMapper blockMapper = mock(BlockMapper.class);
        when(blockService.selectList(any(com.ujcms.cms.core.service.args.BlockArgs.class)))
                .thenAnswer(inv -> new ArrayList<>(blocks.values()));
        when(blockService.select(anyLong())).thenAnswer(inv -> blocks.get(inv.<Long>getArgument(0)));
        doAnswer(inv -> {
            Block bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setSiteId(bean.isGlobal() ? null : inv.getArgument(1));
            blocks.put(bean.getId(), bean);
            return null;
        }).when(blockService).insert(any(Block.class), anyLong());
        doAnswer(inv -> {
            Block bean = inv.getArgument(0);
            blocks.put(bean.getId(), bean);
            return null;
        }).when(blockService).update(any(Block.class), anyLong());
        doAnswer(inv -> {
            Long id = inv.getArgument(0);
            blockItems.removeIf(item -> item.getBlockId().equals(id));
            blocks.remove(id);
            return 1;
        }).when(blockService).delete(anyLong());
        when(blockMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                blocks.values().stream()
                        .filter(block -> Objects.equals(block.getOrigin(), inv.getArgument(1))
                                && Objects.equals(block.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(blockMapper.listByOrigin(any(), any())).thenAnswer(inv ->
                blocks.values().stream()
                        .filter(block -> Objects.equals(block.getOrigin(), inv.getArgument(1)))
                        .toList());
        when(blockMapper.listByScopeSiteIdAndAlias(any(), any())).thenAnswer(inv ->
                blocks.values().stream()
                        .filter(block -> block.getAlias().equals(inv.getArgument(1)))
                        .sorted(Comparator.comparing((Block block) -> block.getSiteId() == null ? 1 : 0)
                                .thenComparing(Block::getId))
                        .toList());
        doAnswer(inv -> {
            blocks.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return 1;
        }).when(blockMapper).updateOriginHash(anyLong(), any());
        when(blockMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Block bean = blocks.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())
                    || bean.getSiteId() == null) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });
        when(blockItemService.countByBlockIdAndArticleId(anyLong(), anyLong())).thenAnswer(inv ->
                blockItems.stream().anyMatch(item -> item.getBlockId().equals(inv.getArgument(0))
                        && Objects.equals(item.getArticleId(), inv.getArgument(1))));
        doAnswer(inv -> {
            BlockItem bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            blockItems.add(bean);
            return null;
        }).when(blockItemService).insert(any(BlockItem.class));
        when(blockItemMapper.listByArticleId(anyLong())).thenAnswer(inv ->
                blockItems.stream()
                        .filter(item -> Objects.equals(item.getArticleId(), inv.getArgument(0)))
                        .toList());
        when(blockItemMapper.countByBlockId(anyLong())).thenAnswer(inv ->
                (int) blockItems.stream()
                        .filter(item -> item.getBlockId().equals(inv.getArgument(0))).count());

        // ----- 投票（E4b：定义 + 选项随 bean 内存驻留）-----
        VoteService voteService = mock(VoteService.class);
        VoteMapper voteMapper = mock(VoteMapper.class);
        when(voteService.selectList(any(com.ujcms.cms.ext.service.args.VoteArgs.class)))
                .thenAnswer(inv -> new ArrayList<>(votes.values()));
        doAnswer(inv -> {
            Vote bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            applyVoteOptions(bean, inv.getArgument(1));
            votes.put(bean.getId(), bean);
            return null;
        }).when(voteService).insert(any(Vote.class), any());
        doAnswer(inv -> {
            Vote bean = inv.getArgument(0);
            applyVoteOptions(bean, inv.getArgument(1));
            votes.put(bean.getId(), bean);
            return null;
        }).when(voteService).update(any(Vote.class), any());
        doAnswer(inv -> {
            votes.remove(inv.<Long>getArgument(0));
            return 1;
        }).when(voteService).delete(anyLong());
        when(voteMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                votes.values().stream()
                        .filter(vote -> Objects.equals(vote.getOrigin(), inv.getArgument(1))
                                && Objects.equals(vote.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(voteMapper.listByOrigin(any(), any())).thenAnswer(inv ->
                votes.values().stream()
                        .filter(vote -> Objects.equals(vote.getOrigin(), inv.getArgument(1)))
                        .toList());
        doAnswer(inv -> {
            votes.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return 1;
        }).when(voteMapper).updateOriginHash(anyLong(), any());
        when(voteMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Vote bean = votes.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });

        // ----- 问卷（E4d：定义 + 条目/选项随 bean 内存驻留；写入经端口桩）-----
        com.ujcms.cms.ext.mapper.SurveyMapper surveyMapper =
                mock(com.ujcms.cms.ext.mapper.SurveyMapper.class);
        when(surveyMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                surveys.values().stream()
                        .filter(survey -> Objects.equals(survey.getOrigin(), inv.getArgument(1))
                                && Objects.equals(survey.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(surveyMapper.listByOrigin(any(), any())).thenAnswer(inv ->
                surveys.values().stream()
                        .filter(survey -> Objects.equals(survey.getOrigin(), inv.getArgument(1)))
                        .toList());
        when(surveyMapper.select(anyLong())).thenAnswer(inv ->
                surveys.get(inv.<Long>getArgument(0)));
        doAnswer(inv -> {
            surveys.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return 1;
        }).when(surveyMapper).updateOriginHash(anyLong(), any());
        when(surveyMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            com.ujcms.cms.ext.domain.Survey bean = surveys.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.Survey bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            applySurveyItems(bean, inv.getArgument(1));
            surveys.put(bean.getId(), bean);
            return null;
        }).when(surveyService).insert(any(com.ujcms.cms.ext.domain.Survey.class), any());
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.Survey bean = inv.getArgument(0);
            applySurveyItems(bean, inv.getArgument(1));
            surveys.put(bean.getId(), bean);
            return null;
        }).when(surveyService).update(any(com.ujcms.cms.ext.domain.Survey.class), any());
        when(surveyService.delete(anyLong())).thenAnswer(inv ->
                surveys.remove(inv.<Long>getArgument(0)) != null ? 1 : 0);

        // ----- 表单类型（E4e：定义随 bean 内存驻留；写入经核心服务桩；绑定模型按名解析 modelById）-----
        com.ujcms.cms.ext.mapper.FormTypeMapper formTypeMapper =
                mock(com.ujcms.cms.ext.mapper.FormTypeMapper.class);
        when(formTypeMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                formTypes.values().stream()
                        .filter(formType -> Objects.equals(formType.getOrigin(), inv.getArgument(1))
                                && Objects.equals(formType.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(formTypeMapper.listByOrigin(any(), any())).thenAnswer(inv ->
                formTypes.values().stream()
                        .filter(formType -> Objects.equals(formType.getOrigin(), inv.getArgument(1)))
                        .toList());
        when(formTypeMapper.select(anyLong())).thenAnswer(inv ->
                formTypes.get(inv.<Long>getArgument(0)));
        doAnswer(inv -> {
            formTypes.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return 1;
        }).when(formTypeMapper).updateOriginHash(anyLong(), any());
        when(formTypeMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            com.ujcms.cms.ext.domain.FormType bean = formTypes.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.FormType bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            formTypes.put(bean.getId(), bean);
            return null;
        }).when(formTypeService).insert(any(com.ujcms.cms.ext.domain.FormType.class));
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.FormType bean = inv.getArgument(0);
            formTypes.put(bean.getId(), bean);
            return null;
        }).when(formTypeService).update(any(com.ujcms.cms.ext.domain.FormType.class));
        when(formTypeService.delete(anyLong())).thenAnswer(inv ->
                formTypes.remove(inv.<Long>getArgument(0)) != null ? 1 : 0);

        // ----- 友情链接（E4f：分组定义 + 链接内存驻留；链接随定义、无独立三元组）-----
        com.ujcms.cms.ext.service.LinkGroupService linkGroupService =
                mock(com.ujcms.cms.ext.service.LinkGroupService.class);
        com.ujcms.cms.ext.service.LinkService linkService =
                mock(com.ujcms.cms.ext.service.LinkService.class);
        com.ujcms.cms.ext.mapper.LinkGroupMapper linkGroupMapper =
                mock(com.ujcms.cms.ext.mapper.LinkGroupMapper.class);
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.LinkGroup bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            linkGroups.put(bean.getId(), bean);
            return null;
        }).when(linkGroupService).insert(any(com.ujcms.cms.ext.domain.LinkGroup.class));
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.LinkGroup bean = inv.getArgument(0);
            linkGroups.put(bean.getId(), bean);
            return null;
        }).when(linkGroupService).update(any(com.ujcms.cms.ext.domain.LinkGroup.class));
        doAnswer(inv -> {
            linkGroups.remove(inv.<Long>getArgument(0));
            return 1;
        }).when(linkGroupService).delete(anyLong());
        when(linkGroupService.selectByAlias(any(), any())).thenAnswer(inv ->
                linkGroups.values().stream()
                        .filter(type -> Objects.equals(type.getSiteId(), inv.getArgument(0))
                                && Objects.equals(type.getAlias(), inv.getArgument(1)))
                        .findFirst().orElse(null));
        doAnswer(inv -> {
            com.ujcms.cms.ext.domain.Link bean = inv.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            bean.setOrder(bean.getId());
            links.put(bean.getId(), bean);
            return null;
        }).when(linkService).insert(any(com.ujcms.cms.ext.domain.Link.class));
        doAnswer(inv -> {
            links.remove(inv.<Long>getArgument(0));
            return 1;
        }).when(linkService).delete(anyLong());
        when(linkService.selectList(any(com.ujcms.cms.ext.service.args.LinkArgs.class)))
                .thenAnswer(inv -> {
                    Object groupId = inv.<com.ujcms.cms.ext.service.args.LinkArgs>getArgument(0)
                            .getQueryMap().get("EQ_groupId_Long");
                    return links.values().stream()
                            .filter(link -> Objects.equals(link.getGroupId(), groupId))
                            .sorted(java.util.Comparator
                                    .comparing(com.ujcms.cms.ext.domain.Link::getOrder)
                                    .thenComparing(com.ujcms.cms.ext.domain.Link::getId))
                            .toList();
                });
        when(linkGroupMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                linkGroups.values().stream()
                        .filter(type -> Objects.equals(type.getOrigin(), inv.getArgument(1))
                                && Objects.equals(type.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(linkGroupMapper.listByOrigin(any(), any())).thenAnswer(inv ->
                linkGroups.values().stream()
                        .filter(type -> Objects.equals(type.getOrigin(), inv.getArgument(1)))
                        .toList());
        when(linkGroupMapper.select(anyLong())).thenAnswer(inv ->
                linkGroups.get(inv.<Long>getArgument(0)));
        doAnswer(inv -> {
            linkGroups.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return 1;
        }).when(linkGroupMapper).updateOriginHash(anyLong(), any());
        when(linkGroupMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            com.ujcms.cms.ext.domain.LinkGroup bean = linkGroups.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });

        // ----- 构成（区域/面板/清单快照）-----
        PlacementService placementService = mock(PlacementService.class);
        PlacementMapper placementMapper = mock(PlacementMapper.class);
        PageRegionMapper pageRegionMapper = mock(PageRegionMapper.class);
        ManifestService manifestService = mock(ManifestService.class);
        when(manifestService.getSnapshot()).thenReturn(snapshot());
        when(placementService.savePanel(any(Placement.class), any(PageManifest.SlotDef.class)))
                .thenAnswer(inv -> {
                    Placement bean = inv.getArgument(0);
                    if (bean.getId() == null || bean.getId() == 0L) {
                        bean.setId(idSeq.incrementAndGet());
                    }
                    placements.put(bean.getId(), bean);
                    return bean.getId();
                });
        when(placementMapper.selectByOrigin(any(), any(), any())).thenAnswer(inv ->
                placements.values().stream()
                        .filter(placement -> Objects.equals(placement.getOrigin(), inv.getArgument(1))
                                && Objects.equals(placement.getOriginId(), inv.getArgument(2)))
                        .findFirst().orElse(null));
        when(placementMapper.listBySiteId(any())).thenAnswer(inv -> new ArrayList<>(placements.values()));
        doAnswer(inv -> {
            placements.get(inv.<Long>getArgument(0)).setOriginHash(inv.getArgument(1));
            return null;
        }).when(placementMapper).updateOriginHash(any(), any());
        when(placementMapper.adoptOrigin(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Placement bean = placements.get(inv.<Long>getArgument(0));
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginId(inv.getArgument(2));
            bean.setOriginHash(inv.getArgument(3));
            return 1;
        });
        when(pageRegionMapper.selectByUnique(any(), any(), any(), any())).thenAnswer(inv ->
                regions.stream()
                        .filter(region -> region.getPageRef().equals(inv.getArgument(1))
                                && region.getRegionName().equals(inv.getArgument(2))
                                && region.getScope().equals(inv.getArgument(3)))
                        .findFirst().orElse(null));
        doAnswer(inv -> {
            regions.add(inv.getArgument(0));
            return null;
        }).when(pageRegionMapper).insert(any(PageRegion.class));
        when(pageRegionMapper.listBySiteId(any())).thenAnswer(inv -> new ArrayList<>(regions));
        when(pageRegionMapper.adoptOrigin(anyLong(), any(), any())).thenAnswer(inv -> {
            PageRegion bean = regions.stream()
                    .filter(region -> region.getId().equals(inv.getArgument(0)))
                    .findFirst().orElse(null);
            if (bean == null || (bean.getOrigin() != null && !bean.getOrigin().isEmpty())) {
                return 0;
            }
            bean.setOrigin(inv.getArgument(1));
            bean.setOriginHash(inv.getArgument(2));
            return 1;
        });
        settingsService = mock(ThemeSettingsService.class);

        // ----- 管线装配（真实导入器/导出器）-----
        CompositionSchemas schemas = new CompositionSchemas();
        contentImporter = new ThemeContentSeedImporter(channelService, articleService, topicService,
                modelService, siteService, blockService, blockItemService, layoutVariantService,
                mock(HtmlSanitizeService.class), channelMapper, articleMapper, topicMapper, blockMapper,
                voteService, voteMapper, surveyMapper, surveyService,
                formTypeMapper, formTypeService,
                linkGroupService, linkService, linkGroupMapper,
                modelMapper, dictTypeService, dictService, dictTypeMapper, schemas, assetCopier);
        regionImporter = new ThemeSeedImporter(placementMapper, pageRegionMapper, channelMapper,
                articleMapper, blockMapper, voteMapper, placementService, mock(RegionService.class),
                settingsService, manifestService, mock(SettingsResolver.class), schemas,
                new SnowflakeSequence(0, 0), assetCopier);
        ThemeContentSeedExporter contentExporter = new ThemeContentSeedExporter(articleService,
                topicService, modelService, blockService, articleMapper, topicMapper, articleTopicMapper,
                blockMapper, blockItemMapper, voteMapper, surveyMapper, formTypeMapper,
                linkGroupMapper, linkService,
                modelMapper, dictTypeMapper, dictService, layoutVariantService);
        ThemeRegionSeedExporter regionExporter = new ThemeRegionSeedExporter(pageRegionMapper,
                placementMapper, manifestService);
        exporter = new ThemeSeedExporter(contentExporter, regionExporter, assetExtractor, channelService,
                channelExtMapper, blockService, voteService, modelService, dictTypeService, configService,
                fieldTypeRegistry());
        adopter = new SeedOriginAdopter(channelMapper, articleMapper, topicMapper, blockMapper, voteMapper,
                surveyMapper, formTypeMapper, linkGroupMapper, modelMapper, dictTypeMapper,
                placementMapper, pageRegionMapper);
    }

    /**
     * 选项列表落到 bean（真实 VoteService 的删/改/插等价内存形：赋ID/voteId/order，选项随定义驻留）。
     */
    private void applyVoteOptions(Vote bean, List<VoteOption> options) {
        int order = 1;
        for (VoteOption option : options) {
            if (option.getId() == null || option.getId() == 0L) {
                option.setId(idSeq.incrementAndGet());
            }
            option.setVoteId(bean.getId());
            option.setOrder(order++);
        }
        bean.setOptions(options);
    }

    /**
     * 条目/选项列表落到问卷 bean（核心 SurveyService 的内存等价形）。
     */
    private void applySurveyItems(com.ujcms.cms.ext.domain.Survey bean,
                                  List<com.ujcms.cms.ext.domain.SurveyItem> items) {
        int order = 1;
        for (com.ujcms.cms.ext.domain.SurveyItem item : items) {
            if (item.getId() == null || item.getId() == 0L) {
                item.setId(idSeq.incrementAndGet());
            }
            item.setSiteId(bean.getSiteId());
            item.setSurveyId(bean.getId());
            item.setOrder(order++);
            int optionOrder = 1;
            for (com.ujcms.cms.ext.domain.SurveyOption option : item.getOptions()) {
                if (option.getId() == null || option.getId() == 0L) {
                    option.setId(idSeq.incrementAndGet());
                }
                option.setSiteId(bean.getSiteId());
                option.setSurveyId(bean.getId());
                option.setItemId(item.getId());
                option.setOrder(optionOrder++);
            }
        }
        bean.setItems(items);
    }

    /**
     * 活主题目录（{@code templates/{SITE}/{THEME}}；themeZip 模式与 §7.4 快照测试用）。
     */
    java.nio.file.Path themeDir() {
        return templates.resolve(String.valueOf(SITE)).resolve(THEME);
    }

    /**
     * 真实 E2 导出编排（{@code ThemeSeedExportService}）——安装服务为桩（主题目录指向
     * {@code templates/{siteId}/{themeName}}），其余组件与本世界共享。
     */
    com.ujcms.cms.core.service.ThemeSeedExportService exportService() {
        com.ujcms.cms.core.service.ThemeInstallService installService =
                mock(com.ujcms.cms.core.service.ThemeInstallService.class);
        when(installService.themeDir(any(), any())).thenAnswer(inv ->
                templates.resolve(String.valueOf(inv.<Long>getArgument(0)))
                        .resolve(inv.<String>getArgument(1)).toFile());
        return new com.ujcms.cms.core.service.ThemeSeedExportService(exporter, adopter, installService,
                new ThemeLifecycleLock(), siteService, configService, settingsService, pathResolver,
                com.ujcms.cms.core.support.Constants.MAPPER);
    }

    /**
     * 主题清单（home/body 区域）+ RichHtml Shape（text/html/media/channelRef/articleRef 设置 +
     * {@code where.channel = $settings.channel} 数据模板——覆盖反向令牌化与引用收集的全部路径）。
     */
    private static RegistrySnapshot snapshot() {
        CompositionSchemas schemas = new CompositionSchemas();
        ManifestLoader manifestLoader = new ManifestLoader(schemas);
        ShapeLoader shapeLoader = new ShapeLoader(schemas);
        String manifestJson = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "allowedRowLayouts": ["full"],
                              "constraints": { "maxRows": 5, "maxPanelsPerColumn": 3 } },
                    "aside": { "kind": "region", "allowedShapes": ["RichHtml"],
                               "allowedRowLayouts": ["full"],
                               "constraints": { "maxRows": 5, "maxPanelsPerColumn": 3 } } } }
                """;
        String shapeJson = """
                { "name": "RichHtml", "version": "1.0.0",
                  "settings": {
                    "title":   { "type": "text" },
                    "body":    { "type": "html" },
                    "cover":   { "type": "media" },
                    "channel": { "type": "channelRef" },
                    "article": { "type": "articleRef" },
                    "block":   { "type": "blockRef" },
                    "vote":    { "type": "voteRef" },
                    "deco":    { "type": "text" }
                  },
                  "data": { "from": "article", "where": { "channel": "$settings.channel" }, "limit": 6 } }
                """;
        String themeKey = SITE + "/" + THEME;
        return RegistrySnapshot.builder()
                .addThemeManifest(themeKey, manifestLoader.load(manifestJson, "x"), "{}", "x")
                .addThemeShape(themeKey, shapeLoader.load(shapeJson, Shape.SOURCE_THEME_PREFIX + themeKey, "x"),
                        shapeJson, "x")
                .build();
    }

    private static FieldTypeRegistry fieldTypeRegistry() {
        List<com.ujcms.cms.core.composition.fields.FieldType> types =
                new ArrayList<>(List.copyOf(matrixTypes()));
        types.add(new BlockRefFieldType());
        return new FieldTypeRegistry(types);
    }

    private static Collection<com.ujcms.cms.core.composition.fields.FieldType> matrixTypes() {
        FieldTypeRegistry registry = FieldTypeMatrixTest.registry();
        List<com.ujcms.cms.core.composition.fields.FieldType> types = new ArrayList<>();
        for (String name : registry.names()) {
            types.add(Objects.requireNonNull(registry.get(name)));
        }
        return types;
    }

    private static Model model(Long id, String name, String type, String customs) {
        Model model = new Model();
        model.setId(id);
        model.setName(name);
        model.setType(type);
        model.setCustoms(customs);
        return model;
    }

    /**
     * 安装主题种子（内容 → 构成，真实导入器；镜像 {@link ThemeSeedOrchestrator} 的次序）。
     */
    ThemeContentSeedImporter.ContentSeedResult importTheme(Path themeDir) {
        ThemeContentSeedImporter.ContentSeedResult content =
                contentImporter.importContent(SITE, THEME, themeDir.toFile(), 5L);
        regionImporter.importSeed(SITE, THEME, themeDir.toFile(), "/themes", 5L);
        return content;
    }

    /**
     * 操作员内容（第 3 类）：站内上传题图（非 theme-seed 路径——走内容散列 relpath 铸造）
     * + 正文内嵌同图 + 空 customs。
     */
    void addOperatorArticle() throws IOException {
        Path photo = uploads.resolve("2026/07/photo.svg");
        Files.createDirectories(photo.getParent());
        Files.writeString(photo, "<svg op/>");
        String url = "/uploads/2026/07/photo.svg";
        attachmentsByUrl.put(url, new Attachment(SITE, 5L, "photo.svg", "/2026/07/photo.svg", url,
                Files.size(photo)));
        Channel news = channels.values().stream()
                .filter(channel -> "news".equals(channel.getSlug()))
                .findFirst().orElseThrow();
        Article bean = new Article();
        bean.setId(idSeq.incrementAndGet());
        bean.setSiteId(SITE);
        bean.setOrgId(3L);
        bean.setChannelId(news.getId());
        bean.setStatus(Article.STATUS_PUBLISHED);
        bean.setPublishDate(OffsetDateTime.now().minusDays(1));
        bean.setTitle("Operator Story");
        bean.setImage(url);
        bean.setText("<p>op <img src=\"" + url + "\"></p>");
        bean.setCustoms(new HashMap<>(0));
        articles.put(bean.getId(), bean);
    }

    /**
     * 操作员构成（第 3 类）：{@code home/aside/template} 的 default 作用域区域 + 一个 RichHtml 面板
     * （均无来源标记——认领转换测试用）。面板 blockRef 指向全局 focus 区块（ID 40）——
     * 全局行随引用闭包入导出集但绝不认领（D26），令牌化为 {@code {{block:focus}}}。
     */
    void addOperatorRegion() {
        // 操作员投票（无来源标记）：voteRef 引用使其随闭包入导出集（第 3 类铸造 + 认领候选，E4b）
        Vote poll = new Vote();
        poll.setId(90L);
        poll.setSiteId(SITE);
        poll.setTitle("满意度调查");
        VoteOption yes = new VoteOption();
        yes.setTitle("满意");
        VoteOption no = new VoteOption();
        no.setTitle("不满意");
        applyVoteOptions(poll, new ArrayList<>(List.of(yes, no)));
        votes.put(poll.getId(), poll);
        Placement panel = new Placement();
        panel.setId(idSeq.incrementAndGet());
        panel.setSiteId(SITE);
        panel.setPageRef("home");
        panel.setSlotName("aside");
        panel.setPanelShape("RichHtml");
        panel.setSettings(new LinkedHashMap<>(Map.of("title", "操作员面板", "block", "40", "vote", "90")));
        panel.setEnabled(true);
        panel.setVisibility(Placement.VISIBILITY_ALL);
        placements.put(panel.getId(), panel);
        PageRegion region = new PageRegion();
        region.setId(idSeq.incrementAndGet());
        region.setSiteId(SITE);
        region.setPageRef("home");
        region.setRegionName("aside");
        region.setLayoutJson("{\"rows\":[{\"id\":\"r1\",\"layout\":\"full\",\"columns\":"
                + "[{\"panels\":[\"" + panel.getId() + "\"]}]}]}");
        regions.add(region);
    }

    SeedExportBundle export() {
        return exporter.export(SITE, THEME, SeedExportRequest.defaults());
    }
}
