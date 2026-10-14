package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleTopic;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ArticleTopicMapper;
import com.ujcms.cms.core.mapper.BlockItemMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.TopicService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.domain.Link;
import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.cms.ext.domain.Survey;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.mapper.FormTypeMapper;
import com.ujcms.cms.ext.mapper.LinkGroupMapper;
import com.ujcms.cms.ext.mapper.SurveyMapper;
import com.ujcms.cms.ext.mapper.VoteMapper;
import com.ujcms.cms.ext.service.LinkService;
import com.ujcms.cms.ext.service.args.LinkArgs;
import com.ujcms.common.query.QueryParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 主题内容种子导出器（theme-seed-export §3/§4/§7.1）——{@link ThemeContentSeedImporter} 的镜像。
 * 选择 + 依赖闭包（绝非整站）：缺省选择 = 本主题来源行 + 构成引用实体 + 闭包（文章→栏目祖先、
 * 专题绑定、区块条目）；每栏目文章上限缺省 20（构成引用与来源行不受限，D6）；仅经闭包触达的
 * 操作员/他源栏目与专题按 D7 跳过（linked——引用以别名迟绑，不断链）。
 *
 * <p><b>分类与铸造</b>（§4）：每个入选行按来源三元组归入四类（{@link SeedOriginClass}）；
 * 第 1/2 类保留 originId，第 3/4 类铸造（{@link SeedOriginIds}，纯函数不写库，D2/D3）。
 *
 * <p><b>确定性发射</b>（§7.1）：{@code channels.channels.json} 单文件树序；文章按栏目别名分组
 * {@code <alias>.articles.json}、组内按 originId 排序；专题/区块条目各一个文件；投影值一律取自
 * {@link SeedHash} 的规范投影（D1——散列与导出同源），序列化经
 * {@link SeedExportBundle#canonicalJsonBytes}。发布日期导出为
 * {@code publishOffsetDays = min(0, publishDate − exportTime)} 整天（§7.2）。
 *
 * <p><b>硬排除</b>（§7.2）：processKey/权限/用户数据从不入种子（种子文法本就无此形——发射仅按
 * 种子架构键集构造）；回收站文章恒不导出；浏览计数等运行时字段不在投影内。全程只读（D2）。
 *
 * @author PONY
 */
@Component
public class ThemeContentSeedExporter {
    private static final Logger logger = LoggerFactory.getLogger(ThemeContentSeedExporter.class);

    private static final String SCHEMA_PREFIX = "https://www.ujcms.com/schema/composition/";
    /**
     * 可种子化的模型类型（model-seeding D2 + theme-seed-export E4e / D37：内容绑定四类 +
     * 表单模型——form 型随表单类型闭包入包）
     */
    private static final Set<String> SEEDABLE_MODEL_TYPES =
            Set.of(Article.MODEL_TYPE, Channel.MODEL_TYPE, Topic.MODEL_TYPE, Block.MODEL_TYPE,
                    FormType.MODEL_TYPE);
    private static final String CATEGORY_CHANNEL = "channel";
    private static final String CATEGORY_ARTICLE = "article";
    private static final String CATEGORY_TOPIC = "topic";
    private static final String CATEGORY_BLOCK_ITEM = "blockItem";
    private static final String STATUS_DRAFT = "draft";
    private static final String TYPE_NORMAL = "normal";
    private static final String TYPE_SINGLE = "single";
    private static final String TYPE_LINK = "link";
    private static final String CHANNELS_FILE = "channels.channels.json";

    private final com.ujcms.cms.core.service.LayoutVariantService layoutVariantService;
    private final ArticleService articleService;
    private final TopicService topicService;
    private final ModelService modelService;
    private final BlockService blockService;
    private final ArticleMapper articleMapper;
    private final TopicMapper topicMapper;
    private final ArticleTopicMapper articleTopicMapper;
    private final BlockMapper blockMapper;
    private final BlockItemMapper blockItemMapper;
    private final VoteMapper voteMapper;
    private final SurveyMapper surveyMapper;
    private final FormTypeMapper formTypeMapper;
    private final LinkGroupMapper linkGroupMapper;
    private final LinkService linkService;
    private final com.ujcms.cms.core.mapper.ModelMapper modelMapper;
    private final com.ujcms.cms.core.mapper.DictTypeMapper dictTypeMapper;
    private final com.ujcms.cms.core.service.DictService dictService;

    public ThemeContentSeedExporter(ArticleService articleService, TopicService topicService,
                                    ModelService modelService, BlockService blockService,
                                    ArticleMapper articleMapper, TopicMapper topicMapper,
                                    ArticleTopicMapper articleTopicMapper, BlockMapper blockMapper,
                                    BlockItemMapper blockItemMapper, VoteMapper voteMapper,
                                    SurveyMapper surveyMapper, FormTypeMapper formTypeMapper,
                                    LinkGroupMapper linkGroupMapper, LinkService linkService,
                                    com.ujcms.cms.core.mapper.ModelMapper modelMapper,
                                    com.ujcms.cms.core.mapper.DictTypeMapper dictTypeMapper,
                                    com.ujcms.cms.core.service.DictService dictService,
                                    com.ujcms.cms.core.service.LayoutVariantService layoutVariantService) {
        this.layoutVariantService = layoutVariantService;
        this.articleService = articleService;
        this.topicService = topicService;
        this.modelService = modelService;
        this.blockService = blockService;
        this.articleMapper = articleMapper;
        this.topicMapper = topicMapper;
        this.articleTopicMapper = articleTopicMapper;
        this.blockMapper = blockMapper;
        this.blockItemMapper = blockItemMapper;
        this.voteMapper = voteMapper;
        this.surveyMapper = surveyMapper;
        this.formTypeMapper = formTypeMapper;
        this.linkGroupMapper = linkGroupMapper;
        this.linkService = linkService;
        this.modelMapper = modelMapper;
        this.dictTypeMapper = dictTypeMapper;
        this.dictService = dictService;
    }

    /**
     * 导出内容种子（栏目 → 文章 → 专题 → 区块定义 + 条目 → 投票定义；文件写入 {@code ctx.files}，
     * originId 映射写入 {@code ctx}——构成发射的反向令牌化消费之）。
     */
    void export(SeedExportContext ctx) {
        List<Channel> channels = selectChannels(ctx);
        classifyChannels(ctx, channels);
        List<Article> articles = selectArticles(ctx);
        classifyArticles(ctx, articles);
        List<Topic> topics = selectTopics(ctx);
        Map<Long, String> topicOriginById = classifyTopics(ctx, topics);
        List<Block> blocks = selectBlocks(ctx);
        classifyBlocks(ctx, blocks);
        List<Vote> votes = selectVotes(ctx);
        classifyVotes(ctx, votes);
        List<Survey> surveys = selectSurveys(ctx);
        classifySurveys(ctx, surveys);
        List<FormType> formTypes = selectFormTypes(ctx);
        classifyFormTypes(ctx, formTypes);
        List<LinkGroup> linkGroups = selectLinkGroups(ctx);
        Map<Long, List<Link>> linksByGroup = classifyLinkGroups(ctx, linkGroups);
        // 模型/字典（model-seeding D6 + E4e 闭包边）：绑定闭包取自已选定的栏目/专题/区块 + 表单类型——
        // 须在其选择之后（表单类型的 modelId 汇入模型闭包，令绑定的 form 模型随包）
        List<Model> models = selectModels(ctx, channels, topics, blocks, formTypes);
        List<DictType> dictTypes = selectDicts(ctx, models);
        classifyDicts(ctx, dictTypes);
        classifyModels(ctx, models);

        emitChannels(ctx, channels);
        emitArticles(ctx, articles);
        emitTopics(ctx, topics, topicOriginById);
        emitBlocks(ctx, blocks, articles);
        emitVotes(ctx, votes);
        emitSurveys(ctx, surveys);
        emitFormTypes(ctx, formTypes);
        emitLinkGroups(ctx, linkGroups, linksByGroup);
        emitModels(ctx, models);
        emitDicts(ctx, dictTypes);
    }

    // ----- 栏目：选择 + 祖先闭包（§3）-----

    private List<Channel> selectChannels(SeedExportContext ctx) {
        Set<Long> selected = new LinkedHashSet<>();
        for (Channel channel : ctx.channelsInTreeOrder) {
            boolean wanted = ctx.request.channelIds() != null
                    ? ctx.request.channelIds().contains(channel.getId())
                    : ctx.origin.equals(channel.getOrigin());
            if (wanted) {
                selected.add(channel.getId());
            }
        }
        // 祖先闭包：本主题来源祖先自动补足；操作员/他源祖先按 D7 跳过（子树导入后挂到顶级并告警）
        for (Long id : new ArrayList<>(selected)) {
            Channel current = ctx.channelById.get(id);
            while (current != null && current.getParentId() != null) {
                Channel parent = ctx.channelById.get(current.getParentId());
                if (parent == null) {
                    break;
                }
                if (!selected.contains(parent.getId())) {
                    if (ctx.origin.equals(parent.getOrigin())) {
                        selected.add(parent.getId());
                    } else if (ctx.linkedSkippedChannelIds.add(parent.getId())) {
                        ctx.issues.add(new ExportPlan.RefIssue(CATEGORY_CHANNEL, parent.getSlug(),
                                "父栏目为操作员/他主题行，缺省不导出（D7）——其下导出子树将以顶级导入；"
                                        + "显式勾选可放宽"));
                    }
                }
                current = parent;
            }
        }
        // 「链接到第一个子栏目」型不可种子化（目标是站内栏目ID，无种子文法）；转向链接型自 D45 起可种子化
        List<Channel> exported = new ArrayList<>();
        for (Channel channel : ctx.channelsInTreeOrder) {
            if (!selected.contains(channel.getId())) {
                continue;
            }
            if (channel.getType() == Channel.TYPE_LINK_CHILD) {
                ctx.issues.add(new ExportPlan.RefIssue(CATEGORY_CHANNEL, channel.getSlug(),
                        "「链接到第一个子栏目」型栏目不可种子化，跳过"));
                continue;
            }
            exported.add(channel);
            ctx.exportedChannelIds.add(channel.getId());
        }
        return exported;
    }

    private void classifyChannels(SeedExportContext ctx, List<Channel> channels) {
        Set<String> used = new HashSet<>();
        Map<Channel, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (Channel channel : channels) {
            String currentHash = SeedHash.channelHash(channel);
            SeedOriginClass cls = SeedOriginClass.classify(channel.getOrigin(), ctx.origin,
                    channel.getOriginHash(), currentHash);
            ctx.channels.count(cls);
            if (cls.keepsOriginId() && channel.getOriginId() != null) {
                used.add(channel.getOriginId());
                ctx.channelOriginById.put(channel.getId(), channel.getOriginId());
            } else {
                pendingHash.put(channel, currentHash);
                if (cls == SeedOriginClass.OPERATOR_NEW) {
                    adoptable.add(channel.getId());
                }
            }
        }
        // 保留ID先占位，铸造后进行（树序确定 → 冲突后缀确定）
        pendingHash.forEach((channel, hash) -> {
            String originId = SeedOriginIds.mintAliased(channel.getSlug(), "c-", hash, used);
            ctx.channelOriginById.put(channel.getId(), originId);
            // 第 3 类进认领候选（§4.2）；第 4 类只铸不认领（D3）
            if (adoptable.contains(channel.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.CHANNEL, channel.getId(),
                        originId, hash));
            }
        });
    }

    // ----- 文章：上限 + 来源行 + 构成引用闭包（§3 / D6）-----

    private List<Article> selectArticles(SeedExportContext ctx) {
        Map<Long, Article> byId = new LinkedHashMap<>();
        List<Short> statuses = ctx.request.includeDrafts()
                ? List.of(Article.STATUS_PUBLISHED, Article.STATUS_DRAFT)
                : List.of(Article.STATUS_PUBLISHED);
        int cap = ctx.request.articleCapPerChannel();
        for (Long channelId : ctx.exportedChannelIds) {
            if (cap <= 0) {
                break;
            }
            ArticleArgs args = ArticleArgs.of().channelId(channelId).status(statuses);
            args.getQueryMap().put(QueryParser.ORDER_BY, "publishDate_desc,id_desc");
            for (Article article : articleService.selectList(args, 0, cap)) {
                byId.putIfAbsent(article.getId(), article);
            }
        }
        // 本主题来源文章不受上限约束（缺省选择第 1 项：来源行恒入选）
        for (Article article : articleMapper.listByOrigin(ctx.siteId, ctx.origin)) {
            if (article.getStatus() == Article.STATUS_DELETED
                    || (article.getStatus() == Article.STATUS_DRAFT && !ctx.request.includeDrafts())
                    || !ctx.exportedChannelIds.contains(article.getChannelId())) {
                continue;
            }
            byId.putIfAbsent(article.getId(), article);
        }
        // 构成引用的文章恒入选（D6：卡片指向缺失文章即坏安装——闭包优先于上限）
        for (Long id : ctx.referencedArticleIds) {
            if (byId.containsKey(id)) {
                continue;
            }
            Article article = articleService.select(id);
            if (article == null) {
                ctx.issues.add(new ExportPlan.RefIssue("panel", String.valueOf(id),
                        "articleRef 指向不存在的文章，令牌无法生成"));
                continue;
            }
            if (article.getStatus() == Article.STATUS_DELETED) {
                ctx.issues.add(new ExportPlan.RefIssue("panel", String.valueOf(id),
                        "articleRef 指向回收站文章，不可导出——恢复后重试"));
                continue;
            }
            byId.put(id, article);
        }
        // 铸造与发射的确定性遍历序：按行ID升序
        return byId.values().stream().sorted(Comparator.comparing(Article::getId)).toList();
    }

    private void classifyArticles(SeedExportContext ctx, List<Article> articles) {
        Set<String> used = new HashSet<>();
        Map<Article, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (Article article : articles) {
            String currentHash = SeedHash.articleHash(article, tagNames(article));
            SeedOriginClass cls = SeedOriginClass.classify(article.getOrigin(), ctx.origin,
                    article.getOriginHash(), currentHash);
            ctx.articles.count(cls);
            if (cls.keepsOriginId() && article.getOriginId() != null) {
                used.add(article.getOriginId());
                ctx.articleOriginById.put(article.getId(), article.getOriginId());
            } else {
                pendingHash.put(article, currentHash);
                if (cls == SeedOriginClass.OPERATOR_NEW) {
                    adoptable.add(article.getId());
                }
            }
        }
        pendingHash.forEach((article, hash) -> {
            String originId = SeedOriginIds.mintArticle(article.hasCustomSlug() ? article.getSlug() : null, article.getTitle(), hash, used);
            ctx.articleOriginById.put(article.getId(), originId);
            if (adoptable.contains(article.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.ARTICLE, article.getId(),
                        originId, hash));
            }
        });
    }

    // ----- 专题（§3：缺省 = 本主题来源；显式选择可放宽）-----

    private List<Topic> selectTopics(SeedExportContext ctx) {
        List<Topic> topics = new ArrayList<>();
        if (ctx.request.topicIds() != null) {
            for (Long id : ctx.request.topicIds()) {
                Topic topic = topicService.select(id);
                if (topic == null || !ctx.siteId.equals(topic.getSiteId())) {
                    ctx.issues.add(new ExportPlan.RefIssue(CATEGORY_TOPIC, String.valueOf(id),
                            "选择的专题不存在或不属于本站点，跳过"));
                    continue;
                }
                topics.add(topic);
            }
        } else {
            topics.addAll(topicMapper.listByOrigin(ctx.siteId, ctx.origin));
        }
        topics.sort(Comparator.comparing(Topic::getId));
        return topics;
    }

    private Map<Long, String> classifyTopics(SeedExportContext ctx, List<Topic> topics) {
        Map<Long, String> originById = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        Map<Topic, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (Topic topic : topics) {
            String currentHash = SeedHash.topicHash(topic);
            SeedOriginClass cls = SeedOriginClass.classify(topic.getOrigin(), ctx.origin,
                    topic.getOriginHash(), currentHash);
            ctx.topics.count(cls);
            if (cls.keepsOriginId() && topic.getOriginId() != null) {
                used.add(topic.getOriginId());
                originById.put(topic.getId(), topic.getOriginId());
            } else {
                pendingHash.put(topic, currentHash);
                if (cls == SeedOriginClass.OPERATOR_NEW) {
                    adoptable.add(topic.getId());
                }
            }
        }
        pendingHash.forEach((topic, hash) -> {
            String originId = SeedOriginIds.mintAliased(topic.getSlug(), "t-", hash, used);
            originById.put(topic.getId(), originId);
            if (adoptable.contains(topic.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.TOPIC, topic.getId(),
                        originId, hash));
            }
        });
        return originById;
    }

    // ----- 区块定义（E4a / D27：本主题来源 + blockRef 引用闭包，无操作员子选择）-----

    private List<Block> selectBlocks(SeedExportContext ctx) {
        Map<Long, Block> byId = new LinkedHashMap<>();
        for (Block block : blockMapper.listByOrigin(ctx.siteId, ctx.origin)) {
            byId.putIfAbsent(block.getId(), block);
        }
        // blockRef 引用的定义恒随包（D6 闭包类比——令牌在空白站不可解析即整次安装回滚，D27）
        for (Long id : ctx.referencedBlockIds) {
            Block block = ctx.blockById.get(id);
            if (block == null) {
                ctx.issues.add(new ExportPlan.RefIssue("panel", String.valueOf(id),
                        "blockRef 指向不存在的区块，令牌无法生成"));
                continue;
            }
            byId.putIfAbsent(id, block);
        }
        return byId.values().stream().sorted(Comparator.comparing(Block::getId)).toList();
    }

    private void classifyBlocks(SeedExportContext ctx, List<Block> blocks) {
        Set<String> used = new HashSet<>();
        Map<Block, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (Block block : blocks) {
            String currentHash = SeedHash.blockHash(block);
            SeedOriginClass cls = SeedOriginClass.classify(block.getOrigin(), ctx.origin,
                    block.getOriginHash(), currentHash);
            ctx.blocks.count(cls);
            ctx.exportedBlockIds.add(block.getId());
            if (cls.keepsOriginId() && block.getOriginId() != null) {
                used.add(block.getOriginId());
                ctx.blockOriginById.put(block.getId(), block.getOriginId());
            } else {
                pendingHash.put(block, currentHash);
                // 全局区块服务全站，绝不认领（D26）；他源行只铸不认领（D3）
                if (cls == SeedOriginClass.OPERATOR_NEW && ctx.siteId.equals(block.getSiteId())) {
                    adoptable.add(block.getId());
                }
            }
        }
        pendingHash.forEach((block, hash) -> {
            String originId = SeedOriginIds.mintAliased(block.getAlias(), "b-", hash, used);
            ctx.blockOriginById.put(block.getId(), originId);
            if (adoptable.contains(block.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.BLOCK, block.getId(),
                        originId, hash));
            }
        });
    }

    // ----- 投票定义（E4b / D30：本主题来源 + voteRef 引用闭包，无操作员子选择）-----

    private List<Vote> selectVotes(SeedExportContext ctx) {
        Map<Long, Vote> byId = new LinkedHashMap<>();
        for (Vote vote : voteMapper.listByOrigin(ctx.siteId, ctx.origin)) {
            byId.putIfAbsent(vote.getId(), vote);
        }
        // voteRef 引用的定义恒随包（D6 闭包类比——来源绑定令牌在空白站不可解析即整次安装回滚）
        for (Long id : ctx.referencedVoteIds) {
            Vote vote = ctx.voteById.get(id);
            if (vote == null) {
                ctx.issues.add(new ExportPlan.RefIssue("panel", String.valueOf(id),
                        "voteRef 指向不存在的投票，令牌无法生成"));
                continue;
            }
            byId.putIfAbsent(id, vote);
        }
        return byId.values().stream().sorted(Comparator.comparing(Vote::getId)).toList();
    }

    private void classifyVotes(SeedExportContext ctx, List<Vote> votes) {
        Set<String> used = new HashSet<>();
        Map<Vote, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (Vote vote : votes) {
            String currentHash = SeedHash.voteHash(vote);
            SeedOriginClass cls = SeedOriginClass.classify(vote.getOrigin(), ctx.origin,
                    vote.getOriginHash(), currentHash);
            ctx.votes.count(cls);
            if (cls.keepsOriginId() && vote.getOriginId() != null) {
                used.add(vote.getOriginId());
                ctx.voteOriginById.put(vote.getId(), vote.getOriginId());
            } else {
                pendingHash.put(vote, currentHash);
                // 投票恒本站私有——第 3 类均可认领；他源行只铸不认领（D3）
                if (cls == SeedOriginClass.OPERATOR_NEW) {
                    adoptable.add(vote.getId());
                }
            }
        }
        pendingHash.forEach((vote, hash) -> {
            String originId = SeedOriginIds.mintVote(vote.getTitle(), hash, used);
            ctx.voteOriginById.put(vote.getId(), originId);
            if (adoptable.contains(vote.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.VOTE, vote.getId(),
                        originId, hash));
            }
        });
    }

    // ----- 问卷定义（E4d / D35：本主题来源 + 向导显式多选——问卷无引用面，无闭包）-----

    private List<Survey> selectSurveys(SeedExportContext ctx) {
        Map<Long, Survey> byId = new LinkedHashMap<>();
        for (Survey survey : surveyMapper.listByOrigin(ctx.siteId, ctx.origin)) {
            byId.putIfAbsent(survey.getId(), survey);
        }
        // 显式多选（向导「指定」——操作员问卷入包的唯一通道；越站/不存在的ID给出诊断）
        Set<Long> selected = ctx.request.surveyIds();
        if (selected != null) {
            for (Long id : selected) {
                if (byId.containsKey(id)) {
                    continue;
                }
                Survey survey = surveyMapper.select(id);
                if (survey == null || !ctx.siteId.equals(survey.getSiteId())) {
                    ctx.issues.add(new ExportPlan.RefIssue("survey", String.valueOf(id),
                            "问卷不存在或不属本站，忽略该选择"));
                    continue;
                }
                byId.put(id, survey);
            }
        }
        return byId.values().stream().sorted(Comparator.comparing(Survey::getId)).toList();
    }

    private void classifySurveys(SeedExportContext ctx, List<Survey> surveys) {
        Set<String> used = new HashSet<>();
        Map<Survey, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (Survey survey : surveys) {
            String currentHash = SeedHash.surveyHash(survey);
            SeedOriginClass cls = SeedOriginClass.classify(survey.getOrigin(), ctx.origin,
                    survey.getOriginHash(), currentHash);
            ctx.surveys.count(cls);
            if (cls.keepsOriginId() && survey.getOriginId() != null) {
                used.add(survey.getOriginId());
                ctx.surveyOriginById.put(survey.getId(), survey.getOriginId());
            } else {
                pendingHash.put(survey, currentHash);
                // 问卷恒本站私有——第 3 类均可认领；他源行只铸不认领（D3）
                if (cls == SeedOriginClass.OPERATOR_NEW) {
                    adoptable.add(survey.getId());
                }
            }
        }
        pendingHash.forEach((survey, hash) -> {
            String originId = SeedOriginIds.mintSurvey(survey.getTitle(), hash, used);
            ctx.surveyOriginById.put(survey.getId(), originId);
            if (adoptable.contains(survey.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.SURVEY, survey.getId(),
                        originId, hash));
            }
        });
    }

    // ----- 表单类型定义（E4e / D40：本主题来源 + 向导显式多选——表单无引用面，无闭包）-----

    private List<FormType> selectFormTypes(SeedExportContext ctx) {
        Map<Long, FormType> byId = new LinkedHashMap<>();
        for (FormType formType : formTypeMapper.listByOrigin(ctx.siteId, ctx.origin)) {
            byId.putIfAbsent(formType.getId(), formType);
        }
        // 显式多选（向导「指定」——操作员表单入包的唯一通道；越站/不存在的ID给出诊断）
        Set<Long> selected = ctx.request.formTypeIds();
        if (selected != null) {
            for (Long id : selected) {
                if (byId.containsKey(id)) {
                    continue;
                }
                FormType formType = formTypeMapper.select(id);
                if (formType == null || !ctx.siteId.equals(formType.getSiteId())) {
                    ctx.issues.add(new ExportPlan.RefIssue("form", String.valueOf(id),
                            "表单类型不存在或不属本站，忽略该选择"));
                    continue;
                }
                byId.put(id, formType);
            }
        }
        return byId.values().stream().sorted(Comparator.comparing(FormType::getId)).toList();
    }

    private void classifyFormTypes(SeedExportContext ctx, List<FormType> formTypes) {
        Set<String> used = new HashSet<>();
        Map<FormType, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (FormType formType : formTypes) {
            String currentHash = SeedHash.formTypeHash(formType, modelName(ctx, formType.getModelId()));
            SeedOriginClass cls = SeedOriginClass.classify(formType.getOrigin(), ctx.origin,
                    formType.getOriginHash(), currentHash);
            ctx.forms.count(cls);
            if (cls.keepsOriginId() && formType.getOriginId() != null) {
                used.add(formType.getOriginId());
                ctx.formTypeOriginById.put(formType.getId(), formType.getOriginId());
            } else {
                pendingHash.put(formType, currentHash);
                // 表单恒本站私有——第 3 类均可认领；他源行只铸不认领（D3）
                if (cls == SeedOriginClass.OPERATOR_NEW) {
                    adoptable.add(formType.getId());
                }
            }
        }
        pendingHash.forEach((formType, hash) -> {
            String originId = SeedOriginIds.mintFormType(formType.getName(), hash, used);
            ctx.formTypeOriginById.put(formType.getId(), originId);
            if (adoptable.contains(formType.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.FORM_TYPE, formType.getId(),
                        originId, hash));
            }
        });
    }

    /**
     * 表单类型绑定模型的名称（投影/散列用）：按 id 查站点可见模型解析域取名；缺失返回 {@code null}
     * （投影随之无 model 值——与导入侧 {@link SeedHash#formTypeProjection} 同形）。
     */
    @Nullable
    private String modelName(SeedExportContext ctx, @Nullable Long modelId) {
        if (modelId == null) {
            return null;
        }
        Model model = ctx.modelById.get(modelId);
        return model != null ? model.getName() : null;
    }

    // ----- 发射（§7.1：投影值取自 SeedHash 规范投影，D1）-----

    /**
     * 种子发射的变体值（template-track-variants D8）：显式 {@code *_template_} 在导出主题声明目录中的
     * 反查值（操作员通过模板下拉表达页面类型）；无命中不发射。
     */
    @org.jspecify.annotations.Nullable
    private String seedVariantValue(SeedExportContext ctx, String surface,
                                    @org.jspecify.annotations.Nullable String explicitTemplate) {
        return layoutVariantService.themeVariantValueByTemplate(
                ThemeSettingsService.themeKey(ctx.siteId, ctx.themeName), surface, explicitTemplate);
    }

    private void emitChannels(SeedExportContext ctx, List<Channel> channels) {
        if (channels.isEmpty()) {
            return;
        }
        Map<Long, Model> modelCache = new HashMap<>(8);
        List<Map<String, Object>> nodes = new ArrayList<>(channels.size());
        for (Channel channel : channels) {
            TreeMap<String, Object> p = SeedHash.channelProjection(channel);
            String originId = ctx.channelOriginById.get(channel.getId());
            String where = CATEGORY_CHANNEL + ":" + originId;
            Map<String, Object> node = new LinkedHashMap<>(16);
            node.put("originId", originId);
            putNonBlank(node, "alias", (String) p.get("alias"));
            if (channel.getParentId() != null) {
                String parentOrigin = ctx.channelOriginById.get(channel.getParentId());
                if (parentOrigin != null) {
                    node.put("parent", parentOrigin);
                }
            }
            node.put("name", p.get("name"));
            node.put("nav", p.get("nav"));
            node.put("type", channelTypeName(channel.getType()));
            // 转向链接原样发射（与链接组 url 同法：导航目标非媒体，导入亦不展开令牌）；与投影同谓词条件入键
            putNonBlank(node, "linkUrl", (String) p.get("linkUrl"));
            if (Boolean.TRUE.equals(p.get("targetBlank"))) {
                node.put("targetBlank", Boolean.TRUE);
            }
            putNonBlank(node, "channelModel", modelName(modelCache, channel.getChannelModelId()));
            putNonBlank(node, "articleModel", modelName(modelCache, channel.getArticleModelId()));
            // 变体值是页面类型意图（template-track-variants D8）：显式模板列的反查结果
            putNonBlank(node, "channelLayout", seedVariantValue(ctx, LayoutVariantCatalog.SURFACE_CHANNEL,
                    channel.getChannelTemplate()));
            putNonBlank(node, "articleLayout", seedVariantValue(ctx, LayoutVariantCatalog.SURFACE_ARTICLE,
                    channel.getArticleTemplate()));
            node.put("pageSize", p.get("pageSize"));
            putNonBlank(node, "image", ctx.assets.rewriteUrl((String) p.get("image"), where + ".image"));
            Map<String, Object> ext = new LinkedHashMap<>(4);
            putNonBlank(ext, "seoTitle", (String) p.get("seoTitle"));
            putNonBlank(ext, "seoKeywords", (String) p.get("seoKeywords"));
            putNonBlank(ext, "seoDescription", (String) p.get("seoDescription"));
            putNonBlank(ext, "text", ctx.assets.rewriteInline((String) p.get("text"), where + ".text"));
            if (!ext.isEmpty()) {
                node.put("ext", ext);
            }
            putCustoms(ctx, node, castMap(p.get("customs")), modelCache.get(channel.getChannelModelId()), where);
            nodes.add(node);
        }
        ctx.files.put("content/" + CHANNELS_FILE,
                SeedExportBundle.canonicalJsonBytes(Map.of(
                        "$schema", SCHEMA_PREFIX + "seed-channels.schema.json", "channels", nodes)));
    }

    private void emitArticles(SeedExportContext ctx, List<Article> articles) {
        if (articles.isEmpty()) {
            return;
        }
        Map<Long, Model> modelCache = new HashMap<>(8);
        Map<String, List<Map<String, Object>>> byGroup = new TreeMap<>();
        for (Article article : articles) {
            Channel channel = ctx.channelById.get(article.getChannelId());
            String channelRef = channel != null && !channel.getSlug().isBlank()
                    ? channel.getSlug()
                    : ctx.channelOriginById.getOrDefault(article.getChannelId(), "articles");
            byGroup.computeIfAbsent(safeGroupName(channelRef), key -> new ArrayList<>())
                    .add(articleNode(ctx, article, channel, channelRef, modelCache));
        }
        byGroup.forEach((group, nodes) -> {
            nodes.sort(Comparator.comparing(node -> (String) node.get("originId")));
            ctx.files.put("content/articles/" + group + ".articles.json",
                    SeedExportBundle.canonicalJsonBytes(Map.of(
                            "$schema", SCHEMA_PREFIX + "seed-articles.schema.json", "articles", nodes)));
        });
    }

    private Map<String, Object> articleNode(SeedExportContext ctx, Article article, @Nullable Channel channel,
                                            String channelRef, Map<Long, Model> modelCache) {
        List<String> tagNames = tagNames(article);
        TreeMap<String, Object> p = SeedHash.articleProjection(article, tagNames);
        String originId = ctx.articleOriginById.get(article.getId());
        String where = CATEGORY_ARTICLE + ":" + originId;
        Map<String, Object> node = new LinkedHashMap<>(16);
        node.put("originId", originId);
        node.put("channel", channelRef);
        node.put("title", p.get("title"));
        putNonBlank(node, "subtitle", (String) p.get("subtitle"));
        putNonBlank(node, "image", ctx.assets.rewriteUrl((String) p.get("image"), where + ".image"));
        putNonBlank(node, "text", ctx.assets.rewriteInline((String) p.get("text"), where + ".text"));
        long offsetDays = Math.min(0, ChronoUnit.DAYS.between(ctx.exportTime, article.getPublishDate()));
        if (offsetDays != 0) {
            node.put("publishOffsetDays", offsetDays);
        }
        if (article.getStatus() != Article.STATUS_PUBLISHED) {
            // 种子状态词汇仅 published|draft：非发布态一律导出为草稿（绝不替操作员发布下线内容）
            node.put("status", STATUS_DRAFT);
            if (article.getStatus() != Article.STATUS_DRAFT) {
                ctx.issues.add(new ExportPlan.RefIssue(CATEGORY_ARTICLE, originId,
                        "文章状态 " + article.getStatus() + " 超出种子词汇（published|draft），按草稿导出"));
            }
        }
        if (!tagNames.isEmpty()) {
            node.put("tags", tagNames);
        }
        List<Article.ArticleImage> imageList = castImageList(p.get("imageList"));
        if (!imageList.isEmpty()) {
            List<Map<String, Object>> images = new ArrayList<>(imageList.size());
            for (Article.ArticleImage image : imageList) {
                Map<String, Object> imageNode = new LinkedHashMap<>(4);
                imageNode.put("url", ctx.assets.rewriteUrl(image.getUrl(), where + ".imageList"));
                putNonBlank(imageNode, "name", image.getName());
                putNonBlank(imageNode, "description", image.getDescription());
                images.add(imageNode);
            }
            node.put("imageList", images);
        }
        List<Article.ArticleFile> fileList = castFileList(p.get("fileList"));
        if (!fileList.isEmpty()) {
            List<Map<String, Object>> files = new ArrayList<>(fileList.size());
            for (Article.ArticleFile file : fileList) {
                Map<String, Object> fileNode = new LinkedHashMap<>(4);
                fileNode.put("url", ctx.assets.rewriteUrl(file.getUrl(), where + ".fileList"));
                putNonBlank(fileNode, "name", file.getName());
                putIfNotNull(fileNode, "length", file.getLength());
                files.add(fileNode);
            }
            node.put("fileList", files);
        }
        Map<String, Object> ext = new LinkedHashMap<>(8);
        putNonBlank(ext, "author", (String) p.get("author"));
        putNonBlank(ext, "editor", (String) p.get("editor"));
        putNonBlank(ext, "source", (String) p.get("source"));
        putNonBlank(ext, "seoKeywords", (String) p.get("seoKeywords"));
        putNonBlank(ext, "seoDescription", (String) p.get("seoDescription"));
        putNonBlank(ext, "video", ctx.assets.rewriteUrl((String) p.get("video"), where + ".video"));
        putIfNotNull(ext, "videoDuration", p.get("videoDuration"));
        putNonBlank(ext, "file", ctx.assets.rewriteUrl((String) p.get("file"), where + ".file"));
        putNonBlank(ext, "fileName", (String) p.get("fileName"));
        putIfNotNull(ext, "fileLength", p.get("fileLength"));
        putNonBlank(ext, "doc", ctx.assets.rewriteUrl((String) p.get("doc"), where + ".doc"));
        putNonBlank(ext, "docName", (String) p.get("docName"));
        putIfNotNull(ext, "docLength", p.get("docLength"));
        if (!ext.isEmpty()) {
            node.put("ext", ext);
        }
        Model articleModel = channel != null ? model(modelCache, channel.getArticleModelId()) : null;
        putCustoms(ctx, node, castMap(p.get("customs")), articleModel, where);
        return node;
    }

    private void emitTopics(SeedExportContext ctx, List<Topic> topics, Map<Long, String> topicOriginById) {
        if (topics.isEmpty()) {
            return;
        }
        Map<Long, Model> modelCache = new HashMap<>(4);
        List<Topic> ordered = new ArrayList<>(topics);
        ordered.sort(Comparator.comparing(topic -> topicOriginById.get(topic.getId())));
        List<Map<String, Object>> nodes = new ArrayList<>(ordered.size());
        for (Topic topic : ordered) {
            TreeMap<String, Object> p = SeedHash.topicProjection(topic);
            String originId = topicOriginById.get(topic.getId());
            String where = CATEGORY_TOPIC + ":" + originId;
            Map<String, Object> node = new LinkedHashMap<>(8);
            node.put("originId", originId);
            putNonBlank(node, "alias", (String) p.get("alias"));
            node.put("title", p.get("title"));
            putNonBlank(node, "image", ctx.assets.rewriteUrl((String) p.get("image"), where + ".image"));
            putNonBlank(node, "text", ctx.assets.rewriteInline((String) p.get("text"), where + ".text"));
            Map<String, Object> ext = new LinkedHashMap<>(4);
            putNonBlank(ext, "seoTitle", (String) p.get("seoTitle"));
            putNonBlank(ext, "seoKeywords", (String) p.get("seoKeywords"));
            putNonBlank(ext, "seoDescription", (String) p.get("seoDescription"));
            if (!ext.isEmpty()) {
                node.put("ext", ext);
            }
            putCustoms(ctx, node, castMap(p.get("customs")), model(modelCache, topic.getTopicModelId()), where);
            List<String> bindings = topicArticleOriginIds(ctx, topic, originId);
            if (!bindings.isEmpty()) {
                node.put("articles", bindings);
            }
            nodes.add(node);
        }
        ctx.files.put("content/topics/topics.topics.json",
                SeedExportBundle.canonicalJsonBytes(Map.of(
                        "$schema", SCHEMA_PREFIX + "seed-topics.schema.json", "topics", nodes)));
    }

    /**
     * 专题文章绑定（读回序 = {@code order_ asc, 文章ID desc}，与前台读取一致）；
     * 绑定指向导出集之外的文章 → 省略 + 闭包诊断（§3 widen-or-warn）。
     */
    private List<String> topicArticleOriginIds(SeedExportContext ctx, Topic topic, String originId) {
        List<ArticleTopic> bindings = new ArrayList<>(articleTopicMapper.listByTopicId(topic.getId()));
        bindings.sort(Comparator
                .comparing(ArticleTopic::getOrder, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ArticleTopic::getArticleId, Comparator.reverseOrder()));
        List<String> originIds = new ArrayList<>(bindings.size());
        for (ArticleTopic binding : bindings) {
            String articleOrigin = ctx.articleOriginById.get(binding.getArticleId());
            if (articleOrigin != null) {
                originIds.add(articleOrigin);
            } else {
                ctx.issues.add(new ExportPlan.RefIssue(CATEGORY_TOPIC, originId,
                        "绑定文章（ID " + binding.getArticleId() + "）不在导出集内，绑定省略——放宽选择可保留"));
            }
        }
        return originIds;
    }

    /**
     * 区块文件发射（E4a / D25）：导出集内的区块<b>定义</b>（投影取自 {@link SeedHash#blockProjection}，
     * D1）+ 区块<b>条目</b>（A6 权宜形保留：只随导出文章走、启用条目按所属区块聚合、无来源三元组、
     * 只计条数；linkUrl/mobileImage/video 等超出权宜文法的字段不导出 + 诊断）。条目嵌于其定义组；
     * 所属区块不在导出集内的条目以 A6 条目组形发射（{@code {block: 别名, items}}——操作员区块的
     * 可移植形）。组按别名字典序。
     */
    private void emitBlocks(SeedExportContext ctx, List<Block> blocks, List<Article> articles) {
        Map<Long, Block> blockCache = new HashMap<>(4);
        blocks.forEach(block -> blockCache.put(block.getId(), block));
        Map<String, List<BlockItem>> itemsByAlias = new TreeMap<>();
        for (Article article : articles) {
            for (BlockItem item : blockItemMapper.listByArticleId(article.getId())) {
                if (!Boolean.TRUE.equals(item.getEnabled())) {
                    continue;
                }
                Block block = blockCache.computeIfAbsent(item.getBlockId(), blockService::select);
                if (block == null) {
                    logger.warn("Export block item without block, skipped: item={}", item.getId());
                    continue;
                }
                itemsByAlias.computeIfAbsent(block.getAlias(), key -> new ArrayList<>()).add(item);
            }
        }
        if (blocks.isEmpty() && itemsByAlias.isEmpty()) {
            return;
        }
        // 组按别名字典序：定义组（含其条目）与集外条目组同池排序
        Map<Long, Model> modelCache = new HashMap<>(4);
        Map<String, Map<String, Object>> groupsByAlias = new TreeMap<>();
        for (Block block : blocks) {
            TreeMap<String, Object> p = SeedHash.blockProjection(block);
            String originId = ctx.blockOriginById.get(block.getId());
            Map<String, Object> node = new LinkedHashMap<>(12);
            node.put("originId", originId);
            putNonBlank(node, "alias", (String) p.get("alias"));
            node.put("name", p.get("name"));
            putNonBlank(node, "category", (String) p.get("category"));
            putNonBlank(node, "title", (String) p.get("title"));
            putNonBlank(node, "moreUrl", ctx.rewriter().themeOnly((String) p.get("moreUrl")));
            putNonBlank(node, "blockItemModel", modelName(modelCache, block.getBlockItemModelId()));
            node.put("recommendable", p.get("recommendable"));
            node.put("enabled", p.get("enabled"));
            groupsByAlias.put(block.getAlias(), node);
        }
        itemsByAlias.forEach((alias, items) -> {
            items.sort(Comparator.comparing(BlockItem::getOrder,
                    Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(BlockItem::getId));
            List<Map<String, Object>> itemNodes = new ArrayList<>(items.size());
            for (BlockItem item : items) {
                String articleOrigin = ctx.articleOriginById.get(item.getArticleId());
                String where = CATEGORY_BLOCK_ITEM + ":" + alias + "/" + articleOrigin;
                Map<String, Object> node = new LinkedHashMap<>(8);
                node.put("article", articleOrigin);
                node.put("title", item.getTitle());
                putNonBlank(node, "subtitle", item.getSubtitle());
                putNonBlank(node, "description", item.getDescription());
                putNonBlank(node, "image", ctx.assets.rewriteUrl(item.getImage(), where + ".image"));
                if (Boolean.TRUE.equals(item.getTargetBlank())) {
                    node.put("targetBlank", true);
                }
                if (item.getLinkUrl() != null || item.getMobileImage() != null || item.getVideo() != null) {
                    ctx.issues.add(new ExportPlan.RefIssue(CATEGORY_BLOCK_ITEM, where,
                            "条目的 linkUrl/mobileImage/video 超出权宜种子文法（A6），未导出"));
                }
                itemNodes.add(node);
                ctx.blockItems++;
            }
            Map<String, Object> defNode = groupsByAlias.get(alias);
            if (defNode != null) {
                defNode.put("items", itemNodes);
            } else {
                Map<String, Object> itemsOnly = new LinkedHashMap<>(4);
                itemsOnly.put("block", alias);
                itemsOnly.put("items", itemNodes);
                groupsByAlias.put(alias, itemsOnly);
            }
        });
        ctx.files.put("content/blocks/blocks.blocks.json",
                SeedExportBundle.canonicalJsonBytes(Map.of(
                        "$schema", SCHEMA_PREFIX + "seed-blocks.schema.json", "blocks",
                        new ArrayList<>(groupsByAlias.values()))));
    }

    /**
     * 投票文件发射（E4b / D29）：导出集内的投票定义（投影取自 {@link SeedHash#voteProjection}，
     * D1；选项即定义、随组内嵌为标题列表；times / 选项计票 / 起止档期恒不出包，§7.2）。
     * 组按 originId 字典序（投票无别名）。
     */
    private void emitVotes(SeedExportContext ctx, List<Vote> votes) {
        if (votes.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> groupsByOriginId = new TreeMap<>();
        for (Vote vote : votes) {
            TreeMap<String, Object> p = SeedHash.voteProjection(vote);
            Map<String, Object> node = new LinkedHashMap<>(8);
            node.put("originId", ctx.voteOriginById.get(vote.getId()));
            node.put("title", p.get("title"));
            putNonBlank(node, "description", (String) p.get("description"));
            node.put("interval", p.get("interval"));
            node.put("mode", p.get("mode"));
            node.put("multiple", p.get("multiple"));
            node.put("enabled", p.get("enabled"));
            node.put("options", p.get("options"));
            groupsByOriginId.put(String.valueOf(node.get("originId")), node);
        }
        ctx.files.put("content/votes/votes.votes.json",
                SeedExportBundle.canonicalJsonBytes(Map.of(
                        "$schema", SCHEMA_PREFIX + "seed-votes.schema.json", "votes",
                        new ArrayList<>(groupsByOriginId.values()))));
    }

    /**
     * 问卷文件发射（E4d / D35）：导出集内的问卷定义（投影取自 {@link SeedHash#surveyProjection}，
     * D1；条目/选项即定义、随组内嵌；times / 反馈 / 起止档期恒不出包，§7.2）。
     * 组按 originId 字典序（问卷无别名）。
     */
    @SuppressWarnings("unchecked")
    private void emitSurveys(SeedExportContext ctx, List<Survey> surveys) {
        if (surveys.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> groupsByOriginId = new TreeMap<>();
        for (Survey survey : surveys) {
            TreeMap<String, Object> p = SeedHash.surveyProjection(survey);
            Map<String, Object> node = new LinkedHashMap<>(8);
            node.put("originId", ctx.surveyOriginById.get(survey.getId()));
            node.put("title", p.get("title"));
            putNonBlank(node, "description", (String) p.get("description"));
            node.put("mode", p.get("mode"));
            node.put("interval", p.get("interval"));
            node.put("enabled", p.get("enabled"));
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object rawItem : (List<Object>) p.get("items")) {
                Map<String, Object> itemProjection = (Map<String, Object>) rawItem;
                Map<String, Object> item = new LinkedHashMap<>(4);
                item.put("title", itemProjection.get("title"));
                item.put("multiple", itemProjection.get("multiple"));
                item.put("essay", itemProjection.get("essay"));
                List<String> options = (List<String>) itemProjection.get("options");
                if (!options.isEmpty()) {
                    item.put("options", options);
                }
                items.add(item);
            }
            node.put("items", items);
            groupsByOriginId.put(String.valueOf(node.get("originId")), node);
        }
        ctx.files.put("content/surveys/surveys.surveys.json",
                SeedExportBundle.canonicalJsonBytes(Map.of(
                        "$schema", SCHEMA_PREFIX + "seed-surveys.schema.json", "surveys",
                        new ArrayList<>(groupsByOriginId.values()))));
    }

    /**
     * 表单类型文件发射（E4e / D38）：导出集内的表单类型定义（投影取自
     * {@link SeedHash#formTypeProjection}，D1；绑定模型以名称随行、按名迟绑）。processKey /
     * 提交恒不出包（§7.2）。组按 originId 字典序（表单无别名）。
     */
    private void emitFormTypes(SeedExportContext ctx, List<FormType> formTypes) {
        if (formTypes.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> groupsByOriginId = new TreeMap<>();
        for (FormType formType : formTypes) {
            TreeMap<String, Object> p = SeedHash.formTypeProjection(formType,
                    modelName(ctx, formType.getModelId()));
            Map<String, Object> node = new LinkedHashMap<>(8);
            node.put("originId", ctx.formTypeOriginById.get(formType.getId()));
            node.put("name", p.get("name"));
            node.put("model", p.get("model"));
            putNonBlank(node, "listTemplate", (String) p.get("listTemplate"));
            putNonBlank(node, "itemTemplate", (String) p.get("itemTemplate"));
            node.put("mode", p.get("mode"));
            node.put("viewable", p.get("viewable"));
            node.put("enabled", p.get("enabled"));
            groupsByOriginId.put(String.valueOf(node.get("originId")), node);
        }
        ctx.files.put("content/forms/forms.forms.json",
                SeedExportBundle.canonicalJsonBytes(Map.of(
                        "$schema", SCHEMA_PREFIX + "seed-forms.schema.json", "forms",
                        new ArrayList<>(groupsByOriginId.values()))));
    }

    /**
     * 链接组文件发射（E4f / D43）：导出集内的分组定义（投影取自
     * {@link SeedHash#linkGroupProjection}，D1；链接即定义、随组内嵌，序即排列；
     * 两级 order 恒不出包）。logo 经反向资源遍历令牌化（§5——散列恒为展开后投影，D9 口径）。
     * 链接三选一目标（link-groups D5）：栏目引用恒发别名（{@code channelAliasById} 全站覆盖）；
     * 文章引用发导出集 originId，导出集之外冻结为当前 URL 发射 + 计划诊断（软降级不丢链——
     * 散列仍为 DB 形投影，同 logo 的 D9 口径）。组按 originId 字典序。
     */
    @SuppressWarnings("unchecked")
    private void emitLinkGroups(SeedExportContext ctx, List<LinkGroup> linkGroups,
                                     Map<Long, List<Link>> linksByGroup) {
        if (linkGroups.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> groupsByOriginId = new TreeMap<>();
        for (LinkGroup group : linkGroups) {
            List<Link> links = linksByGroup.getOrDefault(group.getId(), List.of());
            TreeMap<String, Object> p = SeedHash.linkGroupProjection(group, links);
            String originId = ctx.linkGroupOriginById.get(group.getId());
            Map<String, Object> node = new LinkedHashMap<>(12);
            node.put("originId", originId);
            putNonBlank(node, "alias", (String) p.get("alias"));
            node.put("name", p.get("name"));
            node.put("type", p.get("type"));
            node.put("imageWidth", p.get("imageWidth"));
            node.put("imageHeight", p.get("imageHeight"));
            putNonBlank(node, "description", (String) p.get("description"));
            putNonBlank(node, "slot", (String) p.get("slot"));
            List<Map<String, Object>> linkNodes = new ArrayList<>(links.size());
            List<Object> linkProjections = (List<Object>) p.get("links");
            for (int i = 0; i < linkProjections.size(); i++) {
                Map<String, Object> lp = (Map<String, Object>) linkProjections.get(i);
                Link link = links.get(i);
                Map<String, Object> linkNode = new LinkedHashMap<>(8);
                linkNode.put("name", lp.get("name"));
                String target;
                if (link.getChannelId() != null) {
                    target = "channel:" + lp.get("channel");
                    linkNode.put("channel", lp.get("channel"));
                } else if (link.getArticleId() != null) {
                    String articleOriginId = ctx.articleOriginById.get(link.getArticleId());
                    if (articleOriginId != null) {
                        target = "article:" + articleOriginId;
                        linkNode.put("article", articleOriginId);
                    } else {
                        String frozenUrl = link.getUrl();
                        target = frozenUrl;
                        linkNode.put("url", frozenUrl);
                        ctx.issues.add(new ExportPlan.RefIssue("link", originId + "/" + lp.get("name"),
                                "链接指向导出集之外的文章，冻结为URL导出；放宽文章选择可保留迟绑引用"));
                    }
                } else {
                    target = (String) lp.get("url");
                    linkNode.put("url", lp.get("url"));
                }
                String where = "link:" + originId + "/" + target;
                putNonBlank(linkNode, "logo", ctx.assets.rewriteUrl((String) lp.get("logo"), where + ".logo"));
                putNonBlank(linkNode, "description", (String) lp.get("description"));
                linkNode.put("targetBlank", lp.get("targetBlank"));
                linkNode.put("enabled", lp.get("enabled"));
                linkNodes.add(linkNode);
                ctx.linkItems++;
            }
            node.put("links", linkNodes);
            groupsByOriginId.put(String.valueOf(originId), node);
        }
        ctx.files.put("content/link-groups/link-groups.link-groups.json",
                SeedExportBundle.canonicalJsonBytes(Map.of(
                        "$schema", SCHEMA_PREFIX + "seed-link-groups.schema.json", "linkGroups",
                        new ArrayList<>(groupsByOriginId.values()))));
    }

    // ----- 链接组（E4f / D43：本主题来源 + 向导显式多选——链接组无引用面，无闭包）-----

    private List<LinkGroup> selectLinkGroups(SeedExportContext ctx) {
        Map<Long, LinkGroup> byId = new LinkedHashMap<>();
        for (LinkGroup type : linkGroupMapper.listByOrigin(ctx.siteId, ctx.origin)) {
            byId.putIfAbsent(type.getId(), type);
        }
        // 显式多选（向导「指定」——操作员分组入包的唯一通道；越站/不存在的ID给出诊断）
        Set<Long> selected = ctx.request.linkGroupIds();
        if (selected != null) {
            for (Long id : selected) {
                if (byId.containsKey(id)) {
                    continue;
                }
                LinkGroup type = linkGroupMapper.select(id);
                if (type == null || !ctx.siteId.equals(type.getSiteId())) {
                    ctx.issues.add(new ExportPlan.RefIssue("linkGroup", String.valueOf(id),
                            "链接组不存在或不属本站，忽略该选择"));
                    continue;
                }
                byId.put(id, type);
            }
        }
        return byId.values().stream().sorted(Comparator.comparing(LinkGroup::getId)).toList();
    }

    /**
     * 分类 + 铸造（区块式：originId 缺省取别名，{@code flt-} 散列回退）。链接随定义入投影——
     * 分类散列与发射共用同一次链接查询（{@code (order_, id_)} 升序）。
     *
     * @return 分组ID → 链接列表（发射复用，避免二次查询）
     */
    private Map<Long, List<Link>> classifyLinkGroups(SeedExportContext ctx,
                                                                List<LinkGroup> linkGroups) {
        Map<Long, List<Link>> linksByGroup = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        Map<LinkGroup, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (LinkGroup type : linkGroups) {
            List<Link> links = linkService.selectList(LinkArgs.of().groupId(type.getId()));
            linksByGroup.put(type.getId(), links);
            String currentHash = SeedHash.linkGroupHash(type, links);
            SeedOriginClass cls = SeedOriginClass.classify(type.getOrigin(), ctx.origin,
                    type.getOriginHash(), currentHash);
            ctx.linkGroups.count(cls);
            if (cls.keepsOriginId() && type.getOriginId() != null) {
                used.add(type.getOriginId());
                ctx.linkGroupOriginById.put(type.getId(), type.getOriginId());
            } else {
                pendingHash.put(type, currentHash);
                // 分组恒本站私有——第 3 类均可认领；他源行只铸不认领（D3）
                if (cls == SeedOriginClass.OPERATOR_NEW) {
                    adoptable.add(type.getId());
                }
            }
        }
        pendingHash.forEach((type, hash) -> {
            String originId = SeedOriginIds.mintAliased(type.getAlias(), "lg-", hash, used);
            ctx.linkGroupOriginById.put(type.getId(), originId);
            if (adoptable.contains(type.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.LINK_GROUP, type.getId(),
                        originId, hash));
            }
        });
        return linksByGroup;
    }

    // ----- 模型定义（model-seeding D6：本主题来源 + 绑定闭包，恒不 linked 跳过）-----

    /**
     * 模型选择：本主题来源行 + 导出行的实际绑定闭包（栏目 channelModelId/articleModelId、
     * 专题 topicModelId、区块 blockItemModelId）。缺失的模型是断装（topicModelMissing /
     * customs 清洗丢失），闭包胜于 D7——绑定触达的操作员/全局模型恒随包（D6）。
     * 仅四类可种子化 type 入包（D2）；悬挂绑定给出诊断。
     */
    private List<Model> selectModels(SeedExportContext ctx, List<Channel> channels,
                                     List<Topic> topics, List<Block> blocks, List<FormType> formTypes) {
        Map<Long, Model> byId = new LinkedHashMap<>();
        for (Model model : modelMapper.listByOrigin(ctx.siteId, ctx.origin)) {
            byId.putIfAbsent(model.getId(), model);
        }
        Set<Long> bound = new LinkedHashSet<>();
        for (Channel channel : channels) {
            bound.add(channel.getChannelModelId());
            bound.add(channel.getArticleModelId());
        }
        for (Topic topic : topics) {
            bound.add(topic.getTopicModelId());
        }
        for (Block block : blocks) {
            if (block.getBlockItemModelId() != null) {
                bound.add(block.getBlockItemModelId());
            }
        }
        // 表单类型的绑定模型（E4e 闭包边，D37）：form 型模型随表单类型入包
        for (FormType formType : formTypes) {
            bound.add(formType.getModelId());
        }
        bound.remove(null);
        for (Long id : bound) {
            if (byId.containsKey(id)) {
                continue;
            }
            Model model = ctx.modelById.get(id);
            if (model == null) {
                ctx.issues.add(new ExportPlan.RefIssue("model", String.valueOf(id),
                        "内容绑定指向不可见的模型，定义无法随包"));
                continue;
            }
            if (!SEEDABLE_MODEL_TYPES.contains(model.getType())) {
                ctx.issues.add(new ExportPlan.RefIssue("model", model.getName(),
                        "模型类型 '" + model.getType() + "' 不可种子化（D2），定义不随包"));
                continue;
            }
            byId.put(id, model);
        }
        return byId.values().stream().sorted(Comparator.comparing(Model::getId)).toList();
    }

    private void classifyModels(SeedExportContext ctx, List<Model> models) {
        Set<String> used = new HashSet<>();
        Map<Model, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (Model model : models) {
            String currentHash = SeedHash.modelHash(model, ctx.dictAliasById);
            SeedOriginClass cls = SeedOriginClass.classify(model.getOrigin(), ctx.origin,
                    model.getOriginHash(), currentHash);
            ctx.models.count(cls);
            if (cls.keepsOriginId() && model.getOriginId() != null) {
                used.add(model.getOriginId());
                ctx.modelOriginById.put(model.getId(), model.getOriginId());
            } else {
                pendingHash.put(model, currentHash);
                // 全局模型恒不认领（D1——共享基建不受单站移除支配）；他源行只铸不认领（D3）
                if (cls == SeedOriginClass.OPERATOR_NEW && model.getSiteId() != null) {
                    adoptable.add(model.getId());
                }
            }
        }
        pendingHash.forEach((model, hash) -> {
            String originId = SeedOriginIds.mintModel(model.getType(), model.getName(), hash, used);
            ctx.modelOriginById.put(model.getId(), originId);
            if (adoptable.contains(model.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.MODEL, model.getId(),
                        originId, hash));
            }
        });
    }

    /**
     * 模型文件发射（D1/D11）：投影取自 {@link SeedHash#modelProjection}（字段集为解析树、
     * dictTypeId 携带字典类型别名、悬挂绑定已丢弃——投影即发射）。组按 originId 字典序。
     */
    private void emitModels(SeedExportContext ctx, List<Model> models) {
        if (models.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> groupsByOriginId = new TreeMap<>();
        for (Model model : models) {
            TreeMap<String, Object> p = SeedHash.modelProjection(model, ctx.dictAliasById);
            Map<String, Object> node = new LinkedHashMap<>(8);
            node.put("originId", ctx.modelOriginById.get(model.getId()));
            node.put("type", p.get("type"));
            node.put("name", p.get("name"));
            putIfNotNull(node, "mains", p.get("mains"));
            putIfNotNull(node, "asides", p.get("asides"));
            putIfNotNull(node, "customs", p.get("customs"));
            groupsByOriginId.put(String.valueOf(node.get("originId")), node);
        }
        ctx.files.put("content/models/models.models.json",
                SeedExportBundle.canonicalJsonBytes(Map.of(
                        "$schema", SCHEMA_PREFIX + "seed-models.schema.json", "models",
                        new ArrayList<>(groupsByOriginId.values()))));
    }

    // ----- 字典类型定义（model-seeding D8：本主题来源 + 导出模型的 dictTypeId 绑定闭包）-----

    /**
     * 字典类型选择：本主题来源行 + 导出模型字段集里的 dictTypeId 绑定闭包（数字取值——
     * 别名取值是尚未导入的种子形，活行不存在）。悬挂绑定与投影同步丢弃（D11），此处仅诊断。
     */
    private List<DictType> selectDicts(SeedExportContext ctx, List<Model> models) {
        Map<Long, DictType> byId = new LinkedHashMap<>();
        for (DictType dictType : dictTypeMapper.listByOrigin(ctx.siteId, ctx.origin)) {
            byId.putIfAbsent(dictType.getId(), dictType);
        }
        Set<Long> bound = new LinkedHashSet<>();
        for (Model model : models) {
            SeedHash.collectDictBindings(model.getMains(), bound);
            SeedHash.collectDictBindings(model.getAsides(), bound);
            SeedHash.collectDictBindings(model.getCustoms(), bound);
        }
        for (Long id : bound) {
            if (byId.containsKey(id)) {
                continue;
            }
            DictType dictType = ctx.dictTypeById.get(id);
            if (dictType == null) {
                ctx.issues.add(new ExportPlan.RefIssue("dict", String.valueOf(id),
                        "模型字段绑定指向不可见的字典类型，绑定已从发射投影丢弃"));
                continue;
            }
            byId.put(id, dictType);
        }
        return byId.values().stream().sorted(Comparator.comparing(DictType::getId)).toList();
    }

    private void classifyDicts(SeedExportContext ctx, List<DictType> dictTypes) {
        Set<String> used = new HashSet<>();
        Map<DictType, String> pendingHash = new LinkedHashMap<>();
        Set<Long> adoptable = new HashSet<>();
        for (DictType dictType : dictTypes) {
            String currentHash = SeedHash.dictTypeHash(dictType, dictService.listByTypeId(dictType.getId()));
            SeedOriginClass cls = SeedOriginClass.classify(dictType.getOrigin(), ctx.origin,
                    dictType.getOriginHash(), currentHash);
            ctx.dicts.count(cls);
            if (cls.keepsOriginId() && dictType.getOriginId() != null) {
                used.add(dictType.getOriginId());
                ctx.dictTypeOriginById.put(dictType.getId(), dictType.getOriginId());
            } else {
                pendingHash.put(dictType, currentHash);
                // 全局/sys 字典恒不认领（D8）；他源行只铸不认领（D3）
                if (cls == SeedOriginClass.OPERATOR_NEW && dictType.getSiteId() != null
                        && !Boolean.TRUE.equals(dictType.getSys())) {
                    adoptable.add(dictType.getId());
                }
            }
        }
        pendingHash.forEach((dictType, hash) -> {
            String originId = SeedOriginIds.mintDictType(dictType.getAlias(), hash, used);
            ctx.dictTypeOriginById.put(dictType.getId(), originId);
            if (adoptable.contains(dictType.getId())) {
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.DICT, dictType.getId(),
                        originId, hash));
            }
        });
    }

    /**
     * 字典文件发射（D8）：投影取自 {@link SeedHash#dictTypeProjection}（条目随类型，平铺；
     * scope/order/sys 恒不出包）。组按 originId 字典序。
     */
    @SuppressWarnings("unchecked")
    private void emitDicts(SeedExportContext ctx, List<DictType> dictTypes) {
        if (dictTypes.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> groupsByOriginId = new TreeMap<>();
        for (DictType dictType : dictTypes) {
            TreeMap<String, Object> p = SeedHash.dictTypeProjection(dictType,
                    dictService.listByTypeId(dictType.getId()));
            Map<String, Object> node = new LinkedHashMap<>(8);
            node.put("originId", ctx.dictTypeOriginById.get(dictType.getId()));
            node.put("alias", p.get("alias"));
            node.put("name", p.get("name"));
            putNonBlank(node, "remark", (String) p.get("remark"));
            node.put("dataType", p.get("dataType"));
            node.put("entries", emitDictEntries((List<Object>) p.get("entries")));
            groupsByOriginId.put(String.valueOf(node.get("originId")), node);
        }
        ctx.files.put("content/dicts/dicts.dicts.json",
                SeedExportBundle.canonicalJsonBytes(Map.of(
                        "$schema", SCHEMA_PREFIX + "seed-dicts.schema.json", "dictTypes",
                        new ArrayList<>(groupsByOriginId.values()))));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> emitDictEntries(List<Object> entryNodes) {
        List<Map<String, Object>> out = new ArrayList<>(entryNodes.size());
        for (Object raw : entryNodes) {
            Map<String, Object> projection = (Map<String, Object>) raw;
            Map<String, Object> entry = new LinkedHashMap<>(4);
            entry.put("name", projection.get("name"));
            entry.put("value", projection.get("value"));
            putNonBlank(entry, "remark", (String) projection.get("remark"));
            entry.put("enabled", projection.get("enabled"));
            out.add(entry);
        }
        return out;
    }

    // ----- helpers -----

    private static List<String> tagNames(Article article) {
        return article.getTags().stream().map(Tag::getName).toList();
    }

    @Nullable
    private Model model(Map<Long, Model> cache, @Nullable Long modelId) {
        if (modelId == null) {
            return null;
        }
        return cache.computeIfAbsent(modelId, modelService::select);
    }

    @Nullable
    private String modelName(Map<Long, Model> cache, @Nullable Long modelId) {
        Model model = model(cache, modelId);
        return model != null ? model.getName() : null;
    }

    private static void putCustoms(SeedExportContext ctx, Map<String, Object> node,
                                   Map<String, Object> customs, @Nullable Model model, String where) {
        if (customs.isEmpty()) {
            return;
        }
        node.put("customs", ctx.rewriter().rewriteCustoms(customs, model, where + ".customs"));
    }

    private static void putNonBlank(Map<String, Object> node, String key, @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            node.put(key, value);
        }
    }

    private static void putIfNotNull(Map<String, Object> node, String key, @Nullable Object value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(@Nullable Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Article.ArticleImage> castImageList(@Nullable Object value) {
        return value instanceof List<?> list ? (List<Article.ArticleImage>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Article.ArticleFile> castFileList(@Nullable Object value) {
        return value instanceof List<?> list ? (List<Article.ArticleFile>) list : List.of();
    }

    private static String channelTypeName(short type) {
        if (type == Channel.TYPE_SINGLE) {
            return TYPE_SINGLE;
        }
        if (type == Channel.TYPE_LINK) {
            return TYPE_LINK;
        }
        return TYPE_NORMAL;
    }

    /**
     * 文章分组文件名（栏目别名本就限于 {@code [\w-]+}；防御性过滤）。
     */
    private static String safeGroupName(String group) {
        String safe = group.replaceAll("[^\\w-]", "-");
        return safe.isEmpty() ? "articles" : safe;
    }
}
