package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.ujcms.cms.core.composition.SeedPlan.CategoryPlan;
import com.ujcms.cms.core.composition.SeedPlan.Collision;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.DictTypeMapper;
import com.ujcms.cms.core.mapper.ModelMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.LayoutVariantService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.TopicService;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.cms.core.service.args.DictTypeArgs;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.domain.Link;
import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.cms.ext.domain.Survey;
import com.ujcms.cms.ext.domain.SurveyItem;
import com.ujcms.cms.ext.domain.SurveyOption;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.domain.VoteOption;
import com.ujcms.cms.ext.mapper.FormTypeMapper;
import com.ujcms.cms.ext.mapper.LinkGroupMapper;
import com.ujcms.cms.ext.mapper.SurveyMapper;
import com.ujcms.cms.ext.mapper.VoteMapper;
import com.ujcms.cms.ext.service.FormTypeService;
import com.ujcms.cms.ext.service.LinkGroupService;
import com.ujcms.cms.ext.service.LinkService;
import com.ujcms.cms.ext.service.SurveyService;
import com.ujcms.cms.ext.service.VoteService;
import com.ujcms.cms.ext.service.args.LinkArgs;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 主题内容种子导入器（theme-marketplace §5 / D1）：把主题包 {@code seed/content/} 下的
 * 栏目/文章/专题 JSON 导入为带来源三元组（{@code origin_ = theme:<name>} + {@code origin_id_} +
 * {@code origin_hash_}，R4/D5）的真实内容行。<b>全部经服务层</b>（{@link ChannelService#insert}、
 * {@link ArticleService#insert}、{@link TopicService#insert}）——清洗、Lucene、附件引用、
 * 栏目树、标签等副作用照常发生，绝不走裸 Mapper（D1）。
 *
 * <p><b>事务与恢复</b>（§5.4）：刻意不包整体事务——逐实体经服务层各自提交（ES/Lucene 写本就
 * 非事务），配合 {@code (site, origin, originId)} select-then-insert 幂等，失败后重跑即为
 * 断点续传。单实体失败计入 {@code failed} 并继续后续实体（每条失败均有日志；已知失败重跑仍失败，
 * 继续可给出完整报告——对规格「stop, report partials」的宽化，见决策日志）。失败明细随结果出仓
 * （theme-seed-export §8.1 / D22）：已知失败点抛 {@link SeedFailureException}（i18n 可译原因码），
 * 其余异常记 {@code unknown}——控制台据此给出可操作的补救指引（如专题模型缺失 → 建模后重导）。
 *
 * <p><b>覆盖策略</b>（§6.1，A3）：既有本源行按 {@link SeedOverwrite} 处理——{@code fillOnly}
 * 跳过（A2 行为）；{@code syncUnmodified} 仅刷新「操作员未改动」的行（R4 散列判定）；
 * {@code resetAllOrigin} 无条件重置。刷新只写<b>种子形投影字段</b>（内容字段 + customs），
 * 树位置 / 模型绑定 / 状态 / 发布时间 / 专题文章编排保持不动；回收站中的文章
 * （{@code STATUS_DELETED}）不刷新——移除的演示数据不被静默复活。非本源行（操作员行、linked 行）
 * 在任何策略下都不被触碰（安全不变量 1）。
 *
 * <p><b>别名冲突 → linked</b>（§5.4）：别名撞上非本源行时不插入、不认领，引用映射指向既有行——
 * 操作员行绝不被收编进主题来源。<b>审批流程</b>：种子文章恒以 {@code skipProcess} 插入——
 * 演示数据绝不生成 Flowable 审批任务。<b>布局变体</b>：站点合并目录未声明的取值丢弃并告警
 * （服务层校验会拒绝写入；主题激活后再导入即可全量生效——安装即激活的常规路径不受影响）。
 *
 * <p><b>模型解析</b>：模型无别名概念，按<b>名称</b>在站点可见模型（本站 ∪ 全局）中匹配；
 * 未命中回落首个同类模型（既有 ImportDataComponent 的「站点默认模型」约定）并告警。
 * 站点无专题模型时专题整类失败并告警（主题不可携带模型，D10；建模后重跑即导入）。
 *
 * <p><b>干跑计划</b>（§6.2，A3）：{@link #plan} 只读对比种子与站点现状，逐类别产出
 * {@code {new, linked, existing, operatorModified, seedChanged}}。种子侧散列以干跑资源会话
 * （URL 展开不落盘）+ 服务层保存期处理的模拟（正文 RICH 清洗、customs 按行上模型
 * {@code sanitizeMap}）计算，与导入后落库的投影同形可比。
 *
 * @author PONY
 */
@Component
public class ThemeContentSeedImporter {
    private static final Logger logger = LoggerFactory.getLogger(ThemeContentSeedImporter.class);

    private static final String SEED_DIR = "seed";
    private static final String CONTENT_DIR = "content";
    private static final String ASSETS_DIR = "assets";
    private static final String CHANNELS_FILE = "channels.channels.json";
    private static final String ARTICLES_DIR = "articles";
    private static final String TOPICS_DIR = "topics";
    private static final String BLOCKS_DIR = "blocks";
    private static final String ARTICLES_SUFFIX = ".articles.json";
    private static final String TOPICS_SUFFIX = ".topics.json";
    private static final String BLOCKS_SUFFIX = ".blocks.json";
    private static final String VOTES_DIR = "votes";
    private static final String VOTES_SUFFIX = ".votes.json";
    private static final String SURVEYS_DIR = "surveys";
    private static final String SURVEYS_SUFFIX = ".surveys.json";
    private static final String MODELS_DIR = "models";
    private static final String MODELS_SUFFIX = ".models.json";
    private static final String DICTS_DIR = "dicts";
    private static final String DICTS_SUFFIX = ".dicts.json";
    private static final String FORMS_DIR = "forms";
    private static final String FORMS_SUFFIX = ".forms.json";
    private static final String LINK_GROUPS_DIR = "link-groups";
    private static final String LINK_GROUPS_SUFFIX = ".link-groups.json";
    private static final String STATUS_DRAFT = "draft";
    private static final String TYPE_SINGLE = "single";
    private static final String TYPE_LINK = "link";

    private final ChannelService channelService;
    private final ArticleService articleService;
    private final TopicService topicService;
    private final ModelService modelService;
    private final SiteService siteService;
    private final BlockService blockService;
    private final BlockItemService blockItemService;
    private final LayoutVariantService layoutVariantService;
    private final HtmlSanitizeService sanitizeService;
    private final ChannelMapper channelMapper;
    private final ArticleMapper articleMapper;
    private final TopicMapper topicMapper;
    private final BlockMapper blockMapper;
    private final VoteService voteService;
    private final VoteMapper voteMapper;
    private final SurveyMapper surveyMapper;
    /**
     * 问卷定义写入路径在核心（D46：定义与 schema 同在核心，各版本数据一致——社区版同样完整落库）
     */
    private final SurveyService surveyService;
    private final FormTypeMapper formTypeMapper;
    /**
     * 表单类型定义写入路径在核心（D46，同上）
     */
    private final FormTypeService formTypeService;
    private final LinkGroupService linkGroupService;
    private final LinkService linkService;
    private final LinkGroupMapper linkGroupMapper;
    private final ModelMapper modelMapper;
    private final DictTypeService dictTypeService;
    private final DictService dictService;
    private final DictTypeMapper dictTypeMapper;
    private final CompositionSchemas schemas;
    private final SeedAssetCopier assetCopier;
    public ThemeContentSeedImporter(ChannelService channelService, ArticleService articleService,
                                    TopicService topicService, ModelService modelService, SiteService siteService,
                                    BlockService blockService, BlockItemService blockItemService,
                                    LayoutVariantService layoutVariantService, HtmlSanitizeService sanitizeService,
                                    ChannelMapper channelMapper, ArticleMapper articleMapper, TopicMapper topicMapper,
                                    BlockMapper blockMapper, VoteService voteService, VoteMapper voteMapper,
                                    SurveyMapper surveyMapper, SurveyService surveyService,
                                    FormTypeMapper formTypeMapper, FormTypeService formTypeService,
                                    LinkGroupService linkGroupService, LinkService linkService,
                                    LinkGroupMapper linkGroupMapper,
                                    ModelMapper modelMapper, DictTypeService dictTypeService,
                                    DictService dictService, DictTypeMapper dictTypeMapper,
                                    CompositionSchemas schemas, SeedAssetCopier assetCopier) {
        this.channelService = channelService;
        this.articleService = articleService;
        this.topicService = topicService;
        this.modelService = modelService;
        this.siteService = siteService;
        this.blockService = blockService;
        this.blockItemService = blockItemService;
        this.layoutVariantService = layoutVariantService;
        this.sanitizeService = sanitizeService;
        this.channelMapper = channelMapper;
        this.articleMapper = articleMapper;
        this.topicMapper = topicMapper;
        this.blockMapper = blockMapper;
        this.voteService = voteService;
        this.voteMapper = voteMapper;
        this.surveyMapper = surveyMapper;
        this.surveyService = surveyService;
        this.formTypeMapper = formTypeMapper;
        this.formTypeService = formTypeService;
        this.linkGroupService = linkGroupService;
        this.linkService = linkService;
        this.linkGroupMapper = linkGroupMapper;
        this.modelMapper = modelMapper;
        this.dictTypeService = dictTypeService;
        this.dictService = dictService;
        this.dictTypeMapper = dictTypeMapper;
        this.schemas = schemas;
        this.assetCopier = assetCopier;
    }

    /**
     * 导入主题的内容种子（只填空，A2 缺省行为）。
     */
    public ContentSeedResult importContent(Long siteId, String themeName, File themeDir, @Nullable Long userId) {
        return importContent(siteId, themeName, themeDir, userId, SeedOverwrite.FILL_ONLY);
    }

    /**
     * 导入主题的内容种子（栏目 → 文章 → 专题；内容先于构成种子导入，令牌才可解析，§5.2）。
     * 无 {@code seed/content/} 目录则为空结果。
     *
     * @param siteId    站点ID
     * @param themeName 主题名（目录名）
     * @param themeDir  主题目录
     * @param userId    操作员ID（内容作者/附件归属；空则记为匿名用户）
     * @param overwrite 既有本源行的覆盖策略（§6.1）
     */
    public ContentSeedResult importContent(Long siteId, String themeName, File themeDir, @Nullable Long userId,
                                           SeedOverwrite overwrite) {
        File contentDir = new File(new File(themeDir, SEED_DIR), CONTENT_DIR);
        if (!contentDir.isDirectory()) {
            return ContentSeedResult.EMPTY;
        }
        Site site = siteService.select(siteId);
        if (site == null) {
            throw new IllegalArgumentException("Site not found: " + siteId);
        }
        long uid = userId != null ? userId : User.ANONYMOUS_ID;
        Run run = new Run(siteId, ThemeSeedImporter.originOf(themeName),
                ThemeSettingsService.themeKey(siteId, themeName), site, uid, overwrite,
                assetCopier.open(siteId, themeName, new File(contentDir, ASSETS_DIR), uid));

        // 字典 → 模型先行（model-seeding D5）：模型字段集里的 dictTypeId 别名与
        // channelModel/articleModel/blockItemModel 的按名解析自此有种子定义可命中
        for (File file : listFiles(new File(contentDir, DICTS_DIR), DICTS_SUFFIX)) {
            importDictFile(run, file);
        }
        for (File file : listFiles(new File(contentDir, MODELS_DIR), MODELS_SUFFIX)) {
            importModelFile(run, file);
        }
        // 防御性失效：resolveModel 的按类型备忘不得早于模型类别生效（现序下本就为空，双保险）
        run.modelsByType.clear();
        importChannels(run, new File(contentDir, CHANNELS_FILE));
        for (File file : listFiles(new File(contentDir, ARTICLES_DIR), ARTICLES_SUFFIX)) {
            importArticleFile(run, file);
        }
        for (File file : listFiles(new File(contentDir, TOPICS_DIR), TOPICS_SUFFIX)) {
            importTopicFile(run, file);
        }
        for (File file : listFiles(new File(contentDir, BLOCKS_DIR), BLOCKS_SUFFIX)) {
            importBlockFile(run, file);
        }
        for (File file : listFiles(new File(contentDir, VOTES_DIR), VOTES_SUFFIX)) {
            importVoteFile(run, file);
        }
        for (File file : listFiles(new File(contentDir, SURVEYS_DIR), SURVEYS_SUFFIX)) {
            importSurveyFile(run, file);
        }
        // 表单类型（E4e）：模型/字典已先行导入，绑定模型按名解析可命中；无引用面，位置在问卷之后即可
        for (File file : listFiles(new File(contentDir, FORMS_DIR), FORMS_SUFFIX)) {
            importFormFile(run, file);
        }
        // 链接组（E4f）：无引用面、不依赖任何前序类别，收尾导入
        for (File file : listFiles(new File(contentDir, LINK_GROUPS_DIR), LINK_GROUPS_SUFFIX)) {
            importLinkGroupFile(run, file);
        }

        ContentSeedResult result = new ContentSeedResult(run.channels.toResult(), run.articles.toResult(),
                run.topics.toResult(), run.blocks.toResult(), run.votes.toResult(), run.surveys.toResult(),
                run.forms.toResult(), run.linkGroups.toResult(), run.models.toResult(), run.dicts.toResult(),
                run.blockItems.toResult(), run.linkItems.toResult(),
                run.assets.getCopied(), List.copyOf(run.failures));
        logger.info("Theme content seed imported: site={}, theme={}, overwrite={}, {}",
                siteId, themeName, overwrite, result);
        return result;
    }

    // ----- channels -----

    private void importChannels(Run run, File file) {
        if (!file.isFile()) {
            return;
        }
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_CHANNELS);
        for (JsonNode seed : node.get("channels")) {
            String originId = seed.get("originId").asText();
            try {
                importChannel(run, seed, originId);
            } catch (RuntimeException e) {
                run.fail(run.channels, Failure.CAT_CHANNELS, originId, e);
                logger.error("Seed channel import failed: originId={} ({})", originId, file, e);
            }
        }
    }

    private void importChannel(Run run, JsonNode seed, String originId) {
        Channel existing = channelMapper.selectByOrigin(run.siteId, run.origin, originId);
        String alias = aliasOf(seed, originId);
        if (existing != null) {
            run.channelIdByOrigin.put(originId, existing.getId());
            // 行上别名与种子别名都登记（刷新会把行别名重置回种子别名；文章引用两者皆可）
            run.channelIdByAlias.put(existing.getSlug(), existing.getId());
            run.channelIdByAlias.putIfAbsent(alias, existing.getId());
            if (run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(),
                    SeedHash.channelHash(existing)))) {
                syncChannel(run, seed, existing);
                run.channels.updated++;
            } else {
                run.channels.skipped++;
            }
            return;
        }
        Channel collided = channelService.findBySiteIdAndSlug(run.siteId, alias);
        if (collided != null) {
            // 别名撞上既有行：不插入、不认领（linked）——引用映射指向既有栏目（§5.4）
            run.channelIdByOrigin.put(originId, collided.getId());
            run.channelIdByAlias.put(alias, collided.getId());
            run.channels.linked++;
            logger.info("Seed channel linked to existing channel: alias={}, id={}", alias, collided.getId());
            return;
        }
        Channel bean = new Channel();
        bean.setSiteId(run.siteId);
        String parent = text(seed, "parent");
        if (parent != null) {
            Long parentId = run.channelIdByOrigin.get(parent);
            if (parentId == null) {
                throw new SeedFailureException(Failure.REASON_CHANNEL_PARENT_UNRESOLVED,
                        "Seed channel parent not resolved (tree order?): " + parent);
            }
            bean.setParentId(parentId);
        }
        Model channelModel = requireModel(run, Channel.MODEL_TYPE, text(seed, "channelModel"));
        Model articleModel = requireModel(run, Article.MODEL_TYPE, text(seed, "articleModel"));
        bean.setChannelModelId(channelModel.getId());
        bean.setArticleModelId(articleModel.getId());
        applyChannelSeed(run, seed, bean, alias);
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        channelService.insert(bean, new ChannelService.Grants(List.of(), List.of(), List.of(), List.of()));
        // R4：散列按服务层保存后的最终状态计算（customs 清洗在保存内发生）
        channelMapper.updateOriginHash(bean.getId(), SeedHash.channelHash(bean));
        run.channelIdByOrigin.put(originId, bean.getId());
        run.channelIdByAlias.put(alias, bean.getId());
        run.channels.imported++;
    }

    /**
     * 把种子的投影字段写到栏目 bean 上（新建与刷新共用）。种子缺省的可选字段取<b>全新 bean 的
     * 缺省值</b>——刷新后的行与全新导入的行投影一致；非投影列（树位置、模型绑定、processKey、
     * real 等）由调用方决定保留或设置。
     */
    private void applyChannelSeed(Run run, JsonNode seed, Channel bean, String alias) {
        Channel defaults = new Channel();
        bean.setSlug(alias);
        bean.setName(seed.get("name").asText());
        bean.setNav(seed.hasNonNull("nav") ? seed.get("nav").asBoolean() : defaults.getNav());
        bean.setType(channelType(text(seed, "type")));
        // 转向链接（D45）：原样写入，不做令牌展开——站内路径/绝对地址本身跨安装可携带
        bean.setLinkUrl(text(seed, "linkUrl"));
        bean.setTargetBlank(seed.hasNonNull("targetBlank")
                ? seed.get("targetBlank").asBoolean() : defaults.getTargetBlank());
        // 模板物化（template-track-variants D8）：种子的 channelLayout/articleLayout 是页面类型意图——
        // 显式模板列按安装主题的变体绑定物化（渲染期没有布局列中间层）。
        // 仅填空白列，不覆盖显式选择；刷新路径幂等。
        String channelLayout = text(seed, "channelLayout");
        String articleLayout = text(seed, "articleLayout");
        if (StringUtils.isBlank(bean.getChannelTemplate())) {
            bean.setChannelTemplate(layoutVariantService.themeBoundTemplate(
                    run.themeKey, LayoutVariantCatalog.SURFACE_CHANNEL, channelLayout));
        }
        if (StringUtils.isBlank(bean.getArticleTemplate())) {
            bean.setArticleTemplate(layoutVariantService.themeBoundTemplate(
                    run.themeKey, LayoutVariantCatalog.SURFACE_ARTICLE, articleLayout));
        }
        bean.setPageSize(seed.hasNonNull("pageSize") ? (short) seed.get("pageSize").asInt() : defaults.getPageSize());
        bean.setImage(run.assets.expand(text(seed, "image")));
        JsonNode ext = seed.get("ext");
        bean.setSeoTitle(ext != null ? text(ext, "seoTitle") : null);
        bean.setSeoKeywords(ext != null ? text(ext, "seoKeywords") : null);
        bean.setSeoDescription(ext != null ? text(ext, "seoDescription") : null);
        bean.setText(ext != null ? run.assets.expand(text(ext, "text")) : null);
        // customs 恒置非空映射：刷新既有行时若置 null，getCustoms() 会从行上旧 JSON 列懒装配——
        // 「重置为种子」反而复活旧值（散列归一 null ≡ 空映射，语义等价）
        Map<String, Object> customs = run.assets.expandMap(map(seed, "customs"));
        bean.setCustoms(customs != null ? customs : new HashMap<>(0));
    }

    /**
     * 以种子刷新既有本源栏目行（syncUnmodified / resetAllOrigin，§6.1）：只写投影字段，
     * 树位置与模型绑定保持不动。customs 走「先清列后落库」——旧 JSON 列先置空，
     * 服务层 {@code disassembleCustoms} 只写非空桶，否则清空后的 customs 会残留旧列值，
     * 落库行与刚落的散列自相矛盾。
     */
    private void syncChannel(Run run, JsonNode seed, Channel bean) {
        applyChannelSeed(run, seed, bean, aliasOf(seed, bean.getOriginId()));
        bean.setMainsJson(null);
        bean.setClobsJson(null);
        channelService.update(bean, null, null, null, null);
        channelMapper.updateOriginHash(bean.getId(), SeedHash.channelHash(bean));
    }

    // ----- articles -----

    private void importArticleFile(Run run, File file) {
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_ARTICLES);
        for (JsonNode seed : node.get("articles")) {
            String originId = seed.get("originId").asText();
            try {
                importArticle(run, seed, originId);
            } catch (RuntimeException e) {
                run.fail(run.articles, Failure.CAT_ARTICLES, originId, e);
                logger.error("Seed article import failed: originId={} ({})", originId, file, e);
            }
        }
    }

    private void importArticle(Run run, JsonNode seed, String originId) {
        Article existing = articleMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            run.articleIdByOrigin.put(originId, existing.getId());
            // 回收站中的行不刷新：移除的演示数据不被静默复活（先从回收站恢复再刷新）
            if (existing.getStatus() != Article.STATUS_DELETED
                    && run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(),
                    SeedHash.articleHash(existing, tagNames(existing))))) {
                syncArticle(run, seed, existing);
                run.articles.updated++;
            } else {
                run.articles.skipped++;
            }
            return;
        }
        String channelRef = seed.get("channel").asText();
        Long channelId = resolveChannel(run, channelRef);
        if (channelId == null) {
            throw new SeedFailureException(Failure.REASON_ARTICLE_CHANNEL_UNRESOLVED,
                    "Seed article channel not resolved: " + channelRef);
        }
        Article bean = new Article();
        bean.setSiteId(run.siteId);
        bean.setChannelId(channelId);
        bean.setOrgId(run.site.getOrgId());
        bean.setStatus(STATUS_DRAFT.equals(text(seed, "status")) ? Article.STATUS_DRAFT : Article.STATUS_PUBLISHED);
        // 发布日期 = 导入时刻 + 偏移（§5.4 publishOffsetDays：演示列表按 publishDate 排序仍显新鲜）
        int offsetDays = seed.hasNonNull("publishOffsetDays") ? seed.get("publishOffsetDays").asInt() : 0;
        bean.setPublishDate(OffsetDateTime.now().plusDays(offsetDays));
        applyArticleSeed(run, seed, bean);
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        List<String> tags = stringList(seed, "tags");
        // skipProcess 恒为 true：目标为带 processKey 的 linked 栏目时也直接落已发布，绝不生成审批任务（§5.4）
        articleService.insert(bean, run.userId, tags, true);
        articleMapper.updateOriginHash(bean.getId(), SeedHash.articleHash(bean, tags));
        run.articleIdByOrigin.put(originId, bean.getId());
        run.articles.imported++;
    }

    /**
     * 把种子的投影字段写到文章 bean 上（新建与刷新共用；状态/发布日期/栏目归属是生命周期字段，
     * 不在此列——刷新保持行值不动）。媒体扩展字段（A6：视频/下载/文库/图片集）与正文同享
     * {@code {{asset:}}} 令牌展开。
     */
    private void applyArticleSeed(Run run, JsonNode seed, Article bean) {
        bean.setTitle(seed.get("title").asText());
        bean.setSubtitle(text(seed, "subtitle"));
        bean.setImage(run.assets.expand(text(seed, "image")));
        bean.setText(run.assets.expand(text(seed, "text")));
        JsonNode ext = seed.get("ext");
        bean.setAuthor(ext != null ? text(ext, "author") : null);
        bean.setEditor(ext != null ? text(ext, "editor") : null);
        bean.setSource(ext != null ? text(ext, "source") : null);
        bean.setSeoKeywords(ext != null ? text(ext, "seoKeywords") : null);
        bean.setSeoDescription(ext != null ? text(ext, "seoDescription") : null);
        bean.setVideo(ext != null ? run.assets.expand(text(ext, "video")) : null);
        bean.setVideoDuration(ext != null ? integer(ext, "videoDuration") : null);
        bean.setFile(ext != null ? run.assets.expand(text(ext, "file")) : null);
        bean.setFileName(ext != null ? text(ext, "fileName") : null);
        bean.setFileLength(ext != null ? longInt(ext, "fileLength") : null);
        bean.setDoc(ext != null ? run.assets.expand(text(ext, "doc")) : null);
        bean.setDocName(ext != null ? text(ext, "docName") : null);
        bean.setDocLength(ext != null ? longInt(ext, "docLength") : null);
        bean.setImageList(imageList(run, seed));
        bean.setFileList(fileList(run, seed));
        // customs 恒置非空映射（缘由见 applyChannelSeed）
        Map<String, Object> customs = run.assets.expandMap(map(seed, "customs"));
        bean.setCustoms(customs != null ? customs : new HashMap<>(0));
    }

    private static short channelType(@Nullable String type) {
        if (TYPE_SINGLE.equals(type)) {
            return Channel.TYPE_SINGLE;
        }
        if (TYPE_LINK.equals(type)) {
            return Channel.TYPE_LINK;
        }
        return Channel.TYPE_NORMAL;
    }

    /**
     * 种子图片集 → {@link Article.ArticleImage} 列表（url 经 {@code {{asset:}}} 展开）；
     * 未声明与空列表同形（散列归一，getImageList 对空 JSON 列亦返回空列表）。
     */
    private List<Article.ArticleImage> imageList(Run run, JsonNode seed) {
        List<Article.ArticleImage> list = new ArrayList<>();
        if (seed.hasNonNull("imageList")) {
            for (JsonNode item : seed.get("imageList")) {
                Article.ArticleImage image = new Article.ArticleImage();
                image.setUrl(String.valueOf(run.assets.expand(item.get("url").asText())));
                image.setName(text(item, "name"));
                image.setDescription(text(item, "description"));
                list.add(image);
            }
        }
        return list;
    }

    /**
     * 种子附件列表 → {@link Article.ArticleFile} 列表（url 经 {@code {{asset:}}} 展开；D45）；
     * 未声明与空列表同形（投影仅非空时入键）。
     */
    private List<Article.ArticleFile> fileList(Run run, JsonNode seed) {
        List<Article.ArticleFile> list = new ArrayList<>();
        if (seed.hasNonNull("fileList")) {
            for (JsonNode item : seed.get("fileList")) {
                Article.ArticleFile file = new Article.ArticleFile();
                file.setUrl(String.valueOf(run.assets.expand(item.get("url").asText())));
                file.setName(StringUtils.defaultString(text(item, "name")));
                Long length = longInt(item, "length");
                file.setLength(length != null ? length : 0L);
                list.add(file);
            }
        }
        return list;
    }

    /**
     * 以种子刷新既有本源文章行（§6.1）：标签随种子重建（投影含标签），customs 先清列后落库
     * （同 {@link #syncChannel}）。
     */
    private void syncArticle(Run run, JsonNode seed, Article bean) {
        applyArticleSeed(run, seed, bean);
        bean.setMainsJson(null);
        bean.setClobsJson(null);
        List<String> tags = stringList(seed, "tags");
        articleService.update(bean, run.userId, tags);
        articleMapper.updateOriginHash(bean.getId(), SeedHash.articleHash(bean, tags));
    }

    /**
     * 行上标签的名称列表（R4 投影口径：导入时即种子声明的列表，重导出取关联行序）。
     */
    private static List<String> tagNames(Article article) {
        return article.getTags().stream().map(com.ujcms.cms.core.domain.Tag::getName).toList();
    }

    /**
     * 解析文章目标栏目：本次运行的 originId → 别名映射 → 站点内既有别名（种子或操作员栏目均可）。
     */
    @Nullable
    private Long resolveChannel(Run run, String ref) {
        Long id = run.channelIdByOrigin.get(ref);
        if (id == null) {
            id = run.channelIdByAlias.get(ref);
        }
        if (id == null) {
            Channel channel = channelService.findBySiteIdAndSlug(run.siteId, ref);
            if (channel != null) {
                id = channel.getId();
                run.channelIdByAlias.put(ref, id);
            }
        }
        return id;
    }

    // ----- topics -----

    private void importTopicFile(Run run, File file) {
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_TOPICS);
        // 专题的功能面为 T2，但定义与 schema 同在核心：任何版本都完整落库，升级后无需补导（D46）
        for (JsonNode seed : node.get("topics")) {
            String originId = seed.get("originId").asText();
            try {
                importTopic(run, seed, originId);
            } catch (RuntimeException e) {
                run.fail(run.topics, Failure.CAT_TOPICS, originId, e);
                logger.error("Seed topic import failed: originId={} ({})", originId, file, e);
            }
        }
    }

    private void importTopic(Run run, JsonNode seed, String originId) {
        Topic existing = topicMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            if (run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(),
                    SeedHash.topicHash(existing)))) {
                syncTopic(run, seed, existing);
                run.topics.updated++;
            } else {
                run.topics.skipped++;
            }
            return;
        }
        String alias = aliasOf(seed, originId);
        Topic collided = topicService.findBySiteIdAndSlug(run.siteId, alias);
        if (collided != null) {
            run.topics.linked++;
            logger.info("Seed topic linked to existing topic: alias={}, id={}", alias, collided.getId());
            return;
        }
        Model topicModel = resolveModel(run, Topic.MODEL_TYPE, null);
        if (topicModel == null) {
            // 站点尚无专题模型（模型不可随主题携带，D10）：失败计数，建模后重跑即导入（幂等续传）
            throw new SeedFailureException(Failure.REASON_TOPIC_MODEL_MISSING,
                    "No topic model exists for site " + run.siteId + "; create one and re-import");
        }
        Topic bean = new Topic();
        bean.setSiteId(run.siteId);
        bean.setTopicModelId(topicModel.getId());
        applyTopicSeed(run, seed, bean, alias);
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        topicService.insert(bean);
        topicMapper.updateOriginHash(bean.getId(), SeedHash.topicHash(bean));
        // 文章关联仅在新插入时绑定（跳过/linked 的专题不重置操作员编排，§5.4）
        List<Long> articleIds = resolveTopicArticles(run, stringList(seed, "articles"));
        if (!articleIds.isEmpty()) {
            articleService.assignArticlesToTopic(bean.getId(), articleIds, null);
        }
        run.topics.imported++;
    }

    /**
     * 把种子的投影字段写到专题 bean 上（新建与刷新共用；文章编排属操作员，不在此列）。
     */
    private void applyTopicSeed(Run run, JsonNode seed, Topic bean, String alias) {
        bean.setSlug(alias);
        bean.setTitle(seed.get("title").asText());
        bean.setImage(run.assets.expand(text(seed, "image")));
        bean.setText(run.assets.expand(text(seed, "text")));
        JsonNode ext = seed.get("ext");
        bean.setSeoTitle(ext != null ? text(ext, "seoTitle") : null);
        bean.setSeoKeywords(ext != null ? text(ext, "seoKeywords") : null);
        bean.setSeoDescription(ext != null ? text(ext, "seoDescription") : null);
        // customs 恒置非空映射（缘由见 applyChannelSeed）
        Map<String, Object> customs = run.assets.expandMap(map(seed, "customs"));
        bean.setCustoms(customs != null ? customs : new HashMap<>(0));
    }

    /**
     * 以种子刷新既有本源专题行（§6.1）：文章编排保持不动，customs 先清列后落库（同 {@link #syncChannel}）。
     */
    private void syncTopic(Run run, JsonNode seed, Topic bean) {
        applyTopicSeed(run, seed, bean, aliasOf(seed, bean.getOriginId()));
        bean.setMainsJson(null);
        bean.setClobsJson(null);
        topicService.update(bean);
        topicMapper.updateOriginHash(bean.getId(), SeedHash.topicHash(bean));
    }

    private List<Long> resolveTopicArticles(Run run, List<String> originIds) {
        List<Long> ids = new ArrayList<>(originIds.size());
        for (String originId : originIds) {
            Long id = resolveArticle(run, originId);
            if (id != null) {
                ids.add(id);
            } else {
                logger.warn("Seed topic references unknown article originId: {}", originId);
            }
        }
        return ids;
    }

    /**
     * 解析种子文章：本次运行的 originId 映射 → 库内 {@code (siteId, origin, originId)} 查找（断点续传）。
     */
    @Nullable
    private Long resolveArticle(Run run, String originId) {
        Long id = run.articleIdByOrigin.get(originId);
        if (id == null) {
            Article article = articleMapper.selectByOrigin(run.siteId, run.origin, originId);
            id = article != null ? article.getId() : null;
        }
        return id;
    }

    // ----- blocks（E4a / D25：定义组 upsert + A6 条目组填空）-----

    private void importBlockFile(Run run, File file) {
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_BLOCKS);
        for (JsonNode blockSeed : node.get("blocks")) {
            Block block;
            String groupRef;
            if (blockSeed.hasNonNull("originId")) {
                // 定义组（E4a / D25）：按来源三元组 upsert（别名冲突 → linked，D7 类比）
                String originId = blockSeed.get("originId").asText();
                groupRef = aliasOf(blockSeed, originId);
                try {
                    block = importBlockDef(run, blockSeed, originId);
                } catch (RuntimeException e) {
                    run.fail(run.blocks, Failure.CAT_BLOCKS, originId, e);
                    if (blockSeed.hasNonNull("items")) {
                        // 定义失败：其条目无处可挂，一并失败计数
                        run.blockItems.failed += blockSeed.get("items").size();
                    }
                    logger.error("Seed block def import failed: originId={} ({})", originId, file, e);
                    continue;
                }
            } else {
                // 条目组（A6 权宜形保留）：向既有可见区块（本站 ∪ 全局）填条目
                groupRef = blockSeed.get("block").asText();
                block = resolveBlock(run, groupRef);
                if (block == null) {
                    run.blockItems.failed += blockSeed.get("items").size();
                    run.addFailure(new Failure(Failure.CAT_BLOCK_ITEMS, groupRef,
                            Failure.REASON_BLOCK_NOT_FOUND, "Block not found by alias: " + groupRef));
                    logger.error("Seed block not found (site {}): alias={} ({})", run.siteId, groupRef, file);
                    continue;
                }
            }
            JsonNode items = blockSeed.get("items");
            if (items == null || items.isNull()) {
                continue;
            }
            for (JsonNode seed : items) {
                String articleRef = seed.get("article").asText();
                try {
                    importBlockItem(run, block, seed, articleRef);
                } catch (RuntimeException e) {
                    run.fail(run.blockItems, Failure.CAT_BLOCK_ITEMS, groupRef + "/" + articleRef, e);
                    logger.error("Seed block item import failed: block={} article={} ({})",
                            groupRef, articleRef, file, e);
                }
            }
        }
    }

    /**
     * 导入/刷新一个区块定义（E4a / D23）：{@code (siteId, origin, originId)} select-then-insert 幂等；
     * 别名撞上既有可见区块（本站 ∪ 全局）→ linked 不收编（如种子的 focus 定义在标准安装上
     * 沿用引导全局区块）；新建恒为<b>本站私有</b>（scope=0——主题是站点级实例，演示区块绝不外泄）。
     * 刷新只写投影字段（别名/名称/分类/标题/更多链接/可推荐/启用），scope/排序/条目模型绑定
     * 保持操作员所有（栏目约定）。
     *
     * @return 后续条目挂靠的区块行（既有/沿用/新建）
     */
    private Block importBlockDef(Run run, JsonNode seed, String originId) {
        Block existing = blockMapper.selectByOrigin(run.siteId, run.origin, originId);
        String alias = aliasOf(seed, originId);
        if (existing != null) {
            registerBlock(run, existing);
            if (run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(),
                    SeedHash.blockHash(existing)))) {
                applyBlockSeed(seed, existing, aliasOf(seed, existing.getOriginId()));
                blockService.update(existing, run.siteId);
                blockMapper.updateOriginHash(existing.getId(), SeedHash.blockHash(existing));
                run.blocks.updated++;
            } else {
                run.blocks.skipped++;
            }
            return existing;
        }
        Block collided = resolveBlock(run, alias);
        if (collided != null) {
            run.blocks.linked++;
            logger.info("Seed block linked to existing block: alias={}, id={}", alias, collided.getId());
            return collided;
        }
        Block bean = new Block();
        applyBlockSeed(seed, bean, alias);
        // 条目模型按名称解析（可空列——模型不可随主题携带，D10；未命中回落站点默认并告警）
        String modelName = text(seed, "blockItemModel");
        if (modelName != null) {
            Model itemModel = resolveModel(run, Block.MODEL_TYPE, modelName);
            if (itemModel != null) {
                bean.setBlockItemModelId(itemModel.getId());
            } else {
                logger.warn("Seed block item model '{}' unavailable for site {} (no blockItem models), "
                        + "binding left empty", modelName, run.siteId);
            }
        }
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        blockService.insert(bean, run.siteId);
        blockMapper.updateOriginHash(bean.getId(), SeedHash.blockHash(bean));
        registerBlock(run, bean);
        run.blocks.imported++;
        return bean;
    }

    /**
     * 把种子的投影字段写到区块 bean 上（新建与刷新共用；种子缺省的可选字段取全新 bean 的缺省值——
     * 刷新后的行与全新导入的行投影一致，栏目口径）。
     */
    private static void applyBlockSeed(JsonNode seed, Block bean, String alias) {
        Block defaults = new Block();
        bean.setAlias(alias);
        bean.setName(seed.get("name").asText());
        bean.setCategory(text(seed, "category"));
        bean.setTitle(text(seed, "title"));
        bean.setMoreUrl(text(seed, "moreUrl"));
        bean.setRecommendable(seed.hasNonNull("recommendable")
                ? seed.get("recommendable").asBoolean() : defaults.getRecommendable());
        bean.setEnabled(seed.hasNonNull("enabled")
                ? seed.get("enabled").asBoolean() : defaults.getEnabled());
    }

    /**
     * 新建/既有定义登记进别名缓存（条目组与 {@code {{block:}}} 引用同一次运行内可解析）。
     */
    private static void registerBlock(Run run, Block block) {
        if (run.blocksByAlias != null) {
            run.blocksByAlias.put(block.getAlias(), block);
        }
    }

    /**
     * 区块条目恒为只填空：无来源标记列（不参与 R4 散列/覆盖策略），以 {@code (blockId, articleId)}
     * 作幂等键——已存在即跳过，任何覆盖策略下都不刷新；物理删除随文章级联
     * （{@code ArticleService.delete} → {@code deleteByArticleId}）。
     */
    private void importBlockItem(Run run, Block block, JsonNode seed, String articleRef) {
        Long articleId = resolveArticle(run, articleRef);
        if (articleId == null) {
            throw new SeedFailureException(Failure.REASON_BLOCK_ITEM_ARTICLE_UNRESOLVED,
                    "Seed block item article not resolved: " + articleRef);
        }
        if (blockItemService.countByBlockIdAndArticleId(block.getId(), articleId)) {
            run.blockItems.skipped++;
            return;
        }
        BlockItem bean = new BlockItem();
        bean.setSiteId(run.siteId);
        bean.setBlockId(block.getId());
        bean.setArticleId(articleId);
        String title = text(seed, "title");
        if (title == null) {
            Article article = articleService.select(articleId);
            title = article != null ? article.getTitle() : articleRef;
        }
        bean.setTitle(title);
        bean.setSubtitle(text(seed, "subtitle"));
        bean.setDescription(text(seed, "description"));
        bean.setImage(run.assets.expand(text(seed, "image")));
        if (seed.hasNonNull("targetBlank")) {
            bean.setTargetBlank(seed.get("targetBlank").asBoolean());
        }
        blockItemService.insert(bean);
        run.blockItems.imported++;
    }

    /**
     * 按别名解析站点可见（本站 ∪ 全局）区块。
     */
    @Nullable
    private Block resolveBlock(Run run, String alias) {
        if (run.blocksByAlias == null) {
            run.blocksByAlias = new HashMap<>(8);
            for (Block block : blockService.selectList(BlockArgs.of().scopeSiteId(run.siteId))) {
                run.blocksByAlias.putIfAbsent(block.getAlias(), block);
            }
        }
        return run.blocksByAlias.get(alias);
    }

    // ----- votes（E4b / D29：定义组 upsert，文章式——无别名、恒不 linked）-----

    private void importVoteFile(Run run, File file) {
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_VOTES);
        for (JsonNode seed : node.get("votes")) {
            String originId = seed.get("originId").asText();
            try {
                importVoteDef(run, seed, originId);
            } catch (RuntimeException e) {
                run.fail(run.votes, Failure.CAT_VOTES, originId, e);
                logger.error("Seed vote def import failed: originId={} ({})", originId, file, e);
            }
        }
    }

    /**
     * 导入/刷新一个投票定义（E4b / D29）：{@code (siteId, origin, originId)} select-then-insert 幂等；
     * 投票无别名——无冲突可撞、恒不 linked（文章式）。选项随定义（选项即定义），刷新按<b>标题</b>
     * 匹配既有选项（保留 id 与计票），种子新增标题以 0 票插入，种子不再声明的选项删除；
     * times / 选项计票 / created / order / 起止档期恒不写（运行期与操作员状态，§7.2）。
     */
    private void importVoteDef(Run run, JsonNode seed, String originId) {
        Vote existing = voteMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            if (run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(),
                    SeedHash.voteHash(existing)))) {
                applyVoteSeed(seed, existing);
                List<VoteOption> options = buildVoteOptions(seed, existing.getOptions());
                voteService.update(existing, options);
                existing.setOptions(options);
                voteMapper.updateOriginHash(existing.getId(), SeedHash.voteHash(existing));
                run.votes.updated++;
            } else {
                run.votes.skipped++;
            }
            return;
        }
        Vote bean = new Vote();
        bean.setSiteId(run.siteId);
        applyVoteSeed(seed, bean);
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        List<VoteOption> options = buildVoteOptions(seed, List.of());
        voteService.insert(bean, options);
        bean.setOptions(options);
        voteMapper.updateOriginHash(bean.getId(), SeedHash.voteHash(bean));
        run.votes.imported++;
    }

    /**
     * 把种子的投影字段写到投票 bean 上（新建与刷新共用；种子缺省的可选字段取全新 bean 的缺省值——
     * 刷新后的行与全新导入的行投影一致，区块口径）。
     */
    private static void applyVoteSeed(JsonNode seed, Vote bean) {
        Vote defaults = new Vote();
        bean.setTitle(seed.get("title").asText());
        bean.setDescription(text(seed, "description"));
        bean.setInterval(seed.hasNonNull("interval")
                ? seed.get("interval").asInt() : defaults.getInterval());
        bean.setMode(seed.hasNonNull("mode")
                ? (short) seed.get("mode").asInt() : defaults.getMode());
        bean.setMultiple(seed.hasNonNull("multiple")
                ? seed.get("multiple").asBoolean() : defaults.getMultiple());
        bean.setEnabled(seed.hasNonNull("enabled")
                ? seed.get("enabled").asBoolean() : defaults.getEnabled());
    }

    /**
     * 按种子选项标题构建选项列表：既有选项按标题首配（保留 id 与计票，重复标题不复用），
     * 未命中即新选项（0 票）。{@code VoteService#update} 据 id 更新/插入、按缺席删除。
     */
    private static List<VoteOption> buildVoteOptions(JsonNode seed, List<VoteOption> existingOptions) {
        List<VoteOption> remaining = new ArrayList<>(existingOptions);
        List<VoteOption> options = new ArrayList<>(seed.get("options").size());
        for (JsonNode title : seed.get("options")) {
            VoteOption matched = null;
            for (VoteOption candidate : remaining) {
                if (candidate.getTitle().equals(title.asText())) {
                    matched = candidate;
                    break;
                }
            }
            if (matched != null) {
                remaining.remove(matched);
                options.add(matched);
            } else {
                VoteOption option = new VoteOption();
                option.setTitle(title.asText());
                options.add(option);
            }
        }
        return options;
    }

    // ----- surveys（E4d / D35：定义组 upsert 经核心 SurveyService，文章式——无别名、恒不 linked）-----

    private void importSurveyFile(Run run, File file) {
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_SURVEYS);
        for (JsonNode seed : node.get("surveys")) {
            String originId = seed.get("originId").asText();
            try {
                importSurveyDef(run, seed, originId);
            } catch (RuntimeException e) {
                run.fail(run.surveys, Failure.CAT_SURVEYS, originId, e);
                logger.error("Seed survey def import failed: originId={} ({})", originId, file, e);
            }
        }
    }

    /**
     * 导入/刷新一个问卷定义（E4d / D35）：{@code (siteId, origin, originId)} select-then-insert 幂等；
     * 问卷无别名——恒不 linked（文章式）。条目/选项随定义（即定义本身），刷新按<b>标题</b>匹配既有
     * 条目（保留 id——反馈绑定随行存续），条目内选项再按标题匹配（保留 id 与计数）；
     * times / 反馈 / created / order / 起止档期恒不写（运行期与用户数据，§7.2）。
     */
    private void importSurveyDef(Run run, JsonNode seed, String originId) {
        Survey existing = surveyMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            if (run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(),
                    SeedHash.surveyHash(existing)))) {
                applySurveySeed(seed, existing);
                List<SurveyItem> items = buildSurveyItems(seed, existing.getItems());
                surveyService.update(existing, items);
                existing.setItems(items);
                surveyMapper.updateOriginHash(existing.getId(), SeedHash.surveyHash(existing));
                run.surveys.updated++;
            } else {
                run.surveys.skipped++;
            }
            return;
        }
        Survey bean = new Survey();
        bean.setSiteId(run.siteId);
        applySurveySeed(seed, bean);
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        List<SurveyItem> items = buildSurveyItems(seed, List.of());
        surveyService.insert(bean, items);
        bean.setItems(items);
        surveyMapper.updateOriginHash(bean.getId(), SeedHash.surveyHash(bean));
        run.surveys.imported++;
    }

    /**
     * 把种子的投影字段写到问卷 bean 上（新建与刷新共用；种子缺省的可选字段取全新 bean 的缺省值）。
     */
    private static void applySurveySeed(JsonNode seed, Survey bean) {
        Survey defaults = new Survey();
        bean.setTitle(seed.get("title").asText());
        bean.setDescription(text(seed, "description"));
        bean.setMode(seed.hasNonNull("mode")
                ? (short) seed.get("mode").asInt() : defaults.getMode());
        bean.setInterval(seed.hasNonNull("interval")
                ? seed.get("interval").asInt() : defaults.getInterval());
        bean.setEnabled(seed.hasNonNull("enabled")
                ? seed.get("enabled").asBoolean() : defaults.getEnabled());
    }

    /**
     * 按种子构建条目列表：既有条目按标题首配（保留 id——反馈绑定存续），条目内选项再按标题首配
     * （保留 id 与计数）；未命中即新条目/新选项。{@code SurveyService#update} 据 id
     * 更新/插入、按缺席删除（含被删条目的反馈级联）。
     */
    private static List<SurveyItem> buildSurveyItems(JsonNode seed, List<SurveyItem> existingItems) {
        List<SurveyItem> remaining = new ArrayList<>(existingItems);
        List<SurveyItem> items = new ArrayList<>(seed.get("items").size());
        for (JsonNode itemSeed : seed.get("items")) {
            String title = itemSeed.get("title").asText();
            SurveyItem matched = null;
            for (SurveyItem candidate : remaining) {
                if (candidate.getTitle().equals(title)) {
                    matched = candidate;
                    break;
                }
            }
            SurveyItem item;
            List<SurveyOption> existingOptions;
            if (matched != null) {
                remaining.remove(matched);
                item = matched;
                existingOptions = matched.getOptions();
            } else {
                item = new SurveyItem();
                item.setTitle(title);
                existingOptions = List.of();
            }
            SurveyItem defaults = new SurveyItem();
            item.setMultiple(itemSeed.hasNonNull("multiple")
                    ? itemSeed.get("multiple").asBoolean() : defaults.getMultiple());
            item.setEssay(itemSeed.hasNonNull("essay")
                    ? itemSeed.get("essay").asBoolean() : defaults.getEssay());
            List<SurveyOption> remainingOptions = new ArrayList<>(existingOptions);
            List<SurveyOption> options = new ArrayList<>();
            JsonNode optionSeeds = itemSeed.get("options");
            if (optionSeeds != null && !optionSeeds.isNull()) {
                for (JsonNode optionTitle : optionSeeds) {
                    SurveyOption matchedOption = null;
                    for (SurveyOption candidate : remainingOptions) {
                        if (candidate.getTitle().equals(optionTitle.asText())) {
                            matchedOption = candidate;
                            break;
                        }
                    }
                    if (matchedOption != null) {
                        remainingOptions.remove(matchedOption);
                        options.add(matchedOption);
                    } else {
                        SurveyOption option = new SurveyOption();
                        option.setTitle(optionTitle.asText());
                        options.add(option);
                    }
                }
            }
            item.setOptions(options);
            items.add(item);
        }
        return items;
    }

    // ----- forms（E4e / D38：表单类型定义组 upsert 经核心 FormTypeService，文章式——无别名、恒不 linked。
    //         绑定模型按名解析（type=form，model-seeding D4 迟绑）；processKey / 提交恒不入种子，§7.2）-----

    private void importFormFile(Run run, File file) {
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_FORMS);
        for (JsonNode seed : node.get("forms")) {
            String originId = seed.get("originId").asText();
            try {
                importFormDef(run, seed, originId);
            } catch (RuntimeException e) {
                run.fail(run.forms, Failure.CAT_FORMS, originId, e);
                logger.error("Seed form type def import failed: originId={} ({})", originId, file, e);
            }
        }
    }

    /**
     * 导入/刷新一个表单类型定义（E4e / D38）：{@code (siteId, origin, originId)} select-then-insert 幂等；
     * 表单无别名——恒不 linked（文章式）。绑定模型按<b>名称</b>在站点可见 {@code form} 型模型中解析
     * （model-seeding D4 迟绑；{@code modelId} 非空——缺 form 模型即 {@code modelMissing} 整行失败，
     * 但缺省闭包随包携带模型，仅手写包会触发）。刷新只写投影字段（含模型重解析），
     * processKey / order / 提交恒不写（§7.2 与操作员所有）。
     */
    private void importFormDef(Run run, JsonNode seed, String originId) {
        FormType existing = formTypeMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            if (run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(),
                    SeedHash.formTypeHash(existing, modelNameById(existing.getModelId()))))) {
                Model model = requireModel(run, FormType.MODEL_TYPE, text(seed, "model"));
                applyFormSeed(seed, existing, model);
                formTypeService.update(existing);
                formTypeMapper.updateOriginHash(existing.getId(),
                        SeedHash.formTypeHash(existing, model.getName()));
                run.forms.updated++;
            } else {
                run.forms.skipped++;
            }
            return;
        }
        Model model = requireModel(run, FormType.MODEL_TYPE, text(seed, "model"));
        FormType bean = new FormType();
        bean.setSiteId(run.siteId);
        applyFormSeed(seed, bean, model);
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        formTypeService.insert(bean);
        formTypeMapper.updateOriginHash(bean.getId(), SeedHash.formTypeHash(bean, model.getName()));
        run.forms.imported++;
    }

    /**
     * 把种子的投影字段写到表单类型 bean 上（新建与刷新共用；种子缺省的可选字段取全新 bean 的缺省值）。
     * processKey 恒不设（§7.2——导入的表单在操作员重新绑定流程前无审批运行）。
     */
    private static void applyFormSeed(JsonNode seed, FormType bean, Model model) {
        FormType defaults = new FormType();
        bean.setModelId(model.getId());
        bean.setName(seed.get("name").asText());
        bean.setListTemplate(text(seed, "listTemplate"));
        bean.setItemTemplate(text(seed, "itemTemplate"));
        bean.setMode(seed.hasNonNull("mode")
                ? (short) seed.get("mode").asInt() : defaults.getMode());
        bean.setViewable(seed.hasNonNull("viewable")
                ? seed.get("viewable").asBoolean() : defaults.getViewable());
        bean.setEnabled(seed.hasNonNull("enabled")
                ? seed.get("enabled").asBoolean() : defaults.getEnabled());
    }

    /**
     * 表单类型绑定模型的名称（脏检测/刷新用）：按 id 查模型取名；缺失返回 {@code null}
     * （投影随之无 model 值——与导出侧 {@link SeedHash#formTypeProjection} 同形）。
     */
    @Nullable
    private String modelNameById(@Nullable Long modelId) {
        if (modelId == null) {
            return null;
        }
        Model model = modelMapper.select(modelId);
        return model != null ? model.getName() : null;
    }

    // ----- linkGroups（E4f / D42：分组定义组 upsert，区块式——别名撞既有分组 linked 恒不收编；
    // 链接随定义（选项式）：无独立三元组、无依赖数据，刷新即按种子序整组重建）-----

    private void importLinkGroupFile(Run run, File file) {
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_LINK_GROUPS);
        for (JsonNode seed : node.get("linkGroups")) {
            String originId = seed.get("originId").asText();
            try {
                importLinkGroupDef(run, seed, originId);
            } catch (RuntimeException e) {
                run.fail(run.linkGroups, Failure.CAT_LINK_GROUPS, originId, e);
                run.linkItems.failed += seed.get("links").size();
                logger.error("Seed link group import failed: originId={} ({})", originId, file, e);
            }
        }
    }

    /**
     * 导入/刷新一个链接组（E4f / D42）：{@code (siteId, origin, originId)} select-then-insert
     * 幂等；别名撞上本站既有分组 → linked 不收编（种子链接仍以 {@code (typeId, url)} 幂等填空——
     * 操作员分组收下演示链接，移除演示数据恒不触及）。刷新只动散列未改的分组，且链接无任何
     * 依赖数据（无计票/无引用/无子行）——直接按种子序整组重建（ID 变化无害，序即排列，D43）。
     */
    private void importLinkGroupDef(Run run, JsonNode seed, String originId) {
        LinkGroup existing = linkGroupMapper.selectByOrigin(run.siteId, run.origin, originId);
        String alias = aliasOf(seed, originId);
        if (existing != null) {
            List<Link> links = linksOf(existing.getId());
            if (run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(),
                    SeedHash.linkGroupHash(existing, links)))) {
                applyLinkGroupSeed(seed, existing, aliasOf(seed, existing.getOriginId()));
                linkGroupService.update(existing);
                for (Link link : links) {
                    linkService.delete(link.getId());
                }
                List<Link> rebuilt = insertLinks(run, seed, existing);
                linkGroupMapper.updateOriginHash(existing.getId(),
                        SeedHash.linkGroupHash(existing, rebuilt));
                run.linkGroups.updated++;
            } else {
                run.linkGroups.skipped++;
            }
            return;
        }
        LinkGroup collided = linkGroupService.selectByAlias(run.siteId, alias);
        if (collided != null) {
            run.linkGroups.linked++;
            fillLinks(run, seed, collided);
            logger.info("Seed link group linked to existing type: alias={}, id={}", alias, collided.getId());
            return;
        }
        LinkGroup bean = new LinkGroup();
        bean.setSiteId(run.siteId);
        applyLinkGroupSeed(seed, bean, alias);
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        linkGroupService.insert(bean);
        List<Link> links = insertLinks(run, seed, bean);
        linkGroupMapper.updateOriginHash(bean.getId(), SeedHash.linkGroupHash(bean, links));
        run.linkGroups.imported++;
    }

    /**
     * 把种子的投影字段写到链接组 bean 上（新建与刷新共用；种子缺省的可选字段取全新 bean 的缺省值——
     * 刷新后的行与全新导入的行投影一致，区块口径）。
     */
    private static void applyLinkGroupSeed(JsonNode seed, LinkGroup bean, String alias) {
        LinkGroup defaults = new LinkGroup();
        bean.setAlias(alias);
        bean.setName(seed.get("name").asText());
        bean.setType(seed.hasNonNull("type")
                ? (short) seed.get("type").asInt() : defaults.getType());
        bean.setImageWidth(seed.hasNonNull("imageWidth")
                ? seed.get("imageWidth").asInt() : defaults.getImageWidth());
        bean.setImageHeight(seed.hasNonNull("imageHeight")
                ? seed.get("imageHeight").asInt() : defaults.getImageHeight());
        bean.setDescription(text(seed, "description"));
        bean.setSlot(text(seed, "slot"));
    }

    /**
     * 把种子的链接字段写到链接 bean 上（logo 经资源会话展开 {@code {{asset:}}} 令牌，D9 口径）。
     * 三选一引用迟绑（link-groups D4）：{@code channel} 按站内别名解析（种子/linked/操作员栏目
     * 均可命中，与 {@code SeedTokenExpander} 同一查询）；{@code article} 按本主题来源 originId
     * 解析。解析失败返回 {@code false}——调用方跳过该链接并计数，不失败整组。
     */
    private boolean applyLinkSeed(Run run, JsonNode seed, Link bean) {
        Link defaults = new Link();
        bean.setName(seed.get("name").asText());
        if (seed.hasNonNull("channel")) {
            String channelAlias = seed.get("channel").asText();
            Channel channel = channelMapper.findBySiteIdAndSlug(run.siteId, channelAlias).stream()
                    .findFirst().orElse(null);
            if (channel == null) {
                logger.warn("Seed link skipped, channel alias not found: {} (link={})",
                        channelAlias, bean.getName());
                return false;
            }
            bean.setChannelId(channel.getId());
            bean.setChannel(channel);
        } else if (seed.hasNonNull("article")) {
            String articleOriginId = seed.get("article").asText();
            Article article = articleMapper.selectByOrigin(run.siteId, run.origin, articleOriginId);
            if (article == null) {
                logger.warn("Seed link skipped, article originId not found: {} (link={})",
                        articleOriginId, bean.getName());
                return false;
            }
            bean.setArticleId(article.getId());
            bean.setArticle(article);
        } else {
            bean.setLinkUrl(seed.get("url").asText());
        }
        bean.setLogo(run.assets.expand(text(seed, "logo")));
        bean.setDescription(text(seed, "description"));
        bean.setTargetBlank(seed.hasNonNull("targetBlank")
                ? seed.get("targetBlank").asBoolean() : defaults.getTargetBlank());
        bean.setEnabled(seed.hasNonNull("enabled")
                ? seed.get("enabled").asBoolean() : defaults.getEnabled());
        return true;
    }

    /**
     * 按种子序插入组内链接（服务层置 order = 雪花ID——插入序即 {@code (order_, id_)} 序，
     * 重导出按此序复现种子序，D4）。引用解析失败的链接跳过并计 skipped。
     */
    private List<Link> insertLinks(Run run, JsonNode seed, LinkGroup group) {
        List<Link> links = new ArrayList<>(seed.get("links").size());
        for (JsonNode linkSeed : seed.get("links")) {
            Link bean = new Link();
            bean.setSiteId(run.siteId);
            bean.setGroupId(group.getId());
            if (!applyLinkSeed(run, linkSeed, bean)) {
                run.linkItems.skipped++;
                continue;
            }
            linkService.insert(bean);
            run.linkItems.imported++;
            links.add(bean);
        }
        return links;
    }

    /**
     * 向 linked 操作员链接组填空种子链接（A6 条目组口径：按三选一目标幂等——url / 栏目引用 /
     * 文章引用各按其值比对；任何覆盖策略下恒只填空；落入的链接为普通操作员行——linked 组无
     * 三元组，移除演示数据不触及，既档残留）。
     */
    private void fillLinks(Run run, JsonNode seed, LinkGroup group) {
        List<Link> existing = linksOf(group.getId());
        for (JsonNode linkSeed : seed.get("links")) {
            Link bean = new Link();
            bean.setSiteId(run.siteId);
            bean.setGroupId(group.getId());
            if (!applyLinkSeed(run, linkSeed, bean)) {
                run.linkItems.skipped++;
                continue;
            }
            boolean duplicate = existing.stream().anyMatch(link ->
                    bean.getChannelId() != null ? bean.getChannelId().equals(link.getChannelId())
                            : bean.getArticleId() != null ? bean.getArticleId().equals(link.getArticleId())
                            : bean.getLinkUrl() != null && bean.getLinkUrl().equals(link.getLinkUrl()));
            if (duplicate) {
                run.linkItems.skipped++;
                continue;
            }
            linkService.insert(bean);
            run.linkItems.imported++;
        }
    }

    /**
     * 组内链接（{@code (order_, id_)} 升序——投影与发射的规范序）。
     */
    private List<Link> linksOf(Long groupId) {
        return linkService.selectList(LinkArgs.of().groupId(groupId));
    }

    // ----- dicts（model-seeding D8：字典类型定义组 upsert，别名撞可见集 → linked；条目随类型）-----

    private void importDictFile(Run run, File file) {
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_DICTS);
        for (JsonNode seed : node.get("dictTypes")) {
            String originId = seed.get("originId").asText();
            try {
                importDictTypeDef(run, seed, originId);
            } catch (RuntimeException e) {
                run.fail(run.dicts, Failure.CAT_DICTS, originId, e);
                logger.error("Seed dict type import failed: originId={} ({})", originId, file, e);
            }
        }
    }

    /**
     * 导入/刷新一个字典类型定义（model-seeding D8）：{@code (siteId, origin, originId)}
     * select-then-insert 幂等；别名撞可见集（本站 ∪ 全局）→ linked 恒不收编（D23 同法；
     * 本站的空类型例外：补上种子条目）。
     * 条目随类型（即定义的一部分，平铺）：刷新按 <b>value</b> 匹配既有条目（保留 id），
     * 未命中即新条目，缺席删除；条目 order 按种子序写（数组序即定义序）；
     * scope / 类型级 order / sys 恒不写（操作员/系统状态）。
     */
    private void importDictTypeDef(Run run, JsonNode seed, String originId) {
        DictType existing = dictTypeMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            if (run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(), dictTypeRowHash(existing)))) {
                applyDictTypeSeed(seed, existing, aliasOf(seed, originId));
                dictTypeService.update(existing, run.siteId);
                applyDictEntries(existing.getId(), seed.get("entries"));
                dictTypeMapper.updateOriginHash(existing.getId(), dictTypeRowHash(existing));
                run.dicts.updated++;
            } else {
                run.dicts.skipped++;
            }
            registerDictType(run, existing);
            return;
        }
        String alias = aliasOf(seed, originId);
        DictType collided = resolveDictType(run, alias);
        if (collided != null) {
            // 恒不收编，唯一例外：本站的同别名类型一条条目都没有时，补上种子条目（只填空）。空类型对任何
            // 选择字段都不可用，补条目不覆盖操作员的任何内容；它修复旧版「移除演示数据」留下的空类型
            // （条目已清空、类型因被其它站点的模型引用而删除失败），否则再次导入后前台筛选项为空。
            if (run.siteId.equals(collided.getSiteId()) && dictService.listByTypeId(collided.getId()).isEmpty()) {
                applyDictEntries(collided.getId(), seed.get("entries"));
            }
            run.dicts.linked++;
            registerDictType(run, collided);
            return;
        }
        DictType bean = new DictType();
        applyDictTypeSeed(seed, bean, alias);
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        // scope 缺省 0（本站私有）——种子行恒不建全局字典（D8）
        dictTypeService.insert(bean, run.siteId);
        applyDictEntries(bean.getId(), seed.get("entries"));
        dictTypeMapper.updateOriginHash(bean.getId(), dictTypeRowHash(bean));
        run.dicts.imported++;
        registerDictType(run, bean);
    }

    /**
     * 行 + 现存条目的投影散列（R4 脏检测与落库散列同源）。
     */
    private String dictTypeRowHash(DictType bean) {
        return SeedHash.dictTypeHash(bean, dictService.listByTypeId(bean.getId()));
    }

    /**
     * 把种子的投影字段写到字典类型 bean 上（新建与刷新共用；缺省的可选字段取全新 bean 的缺省值）。
     */
    private static void applyDictTypeSeed(JsonNode seed, DictType bean, String alias) {
        DictType defaults = new DictType();
        bean.setAlias(alias);
        bean.setName(seed.get("name").asText());
        bean.setRemark(text(seed, "remark"));
        bean.setDataType(seed.hasNonNull("dataType")
                ? (short) seed.get("dataType").asInt() : defaults.getDataType());
    }

    /**
     * 条目调和（平铺）：按 value 匹配既有条目保留 id，未命中插入，缺席删除；
     * order 按种子序 1..N 写——数组序即定义序，往返稳定。
     */
    private void applyDictEntries(Long typeId, @Nullable JsonNode entrySeeds) {
        List<Dict> remaining = new ArrayList<>(dictService.listByTypeId(typeId));
        int order = 1;
        if (entrySeeds != null && !entrySeeds.isNull()) {
            for (JsonNode entrySeed : entrySeeds) {
                String value = entrySeed.get("value").asText();
                Dict matched = null;
                for (Dict candidate : remaining) {
                    if (candidate.getValue().equals(value)) {
                        matched = candidate;
                        break;
                    }
                }
                Dict entry;
                if (matched != null) {
                    remaining.remove(matched);
                    entry = matched;
                } else {
                    entry = new Dict();
                    entry.setTypeId(typeId);
                }
                Dict defaults = new Dict();
                entry.setName(entrySeed.get("name").asText());
                entry.setValue(value);
                entry.setRemark(text(entrySeed, "remark"));
                entry.setEnabled(entrySeed.hasNonNull("enabled")
                        ? entrySeed.get("enabled").asBoolean() : defaults.getEnabled());
                entry.setOrder(order++);
                if (matched != null) {
                    dictService.update(entry);
                } else {
                    dictService.insert(entry);
                }
            }
        }
        for (Dict leftover : remaining) {
            dictService.delete(leftover.getId());
        }
    }

    /**
     * 按别名解析站点可见（本站 ∪ 全局）字典类型；本站行胜出。惰性装载一次（区块同法），
     * 导入的类型经 {@code registerDictType} 并入。
     */
    @Nullable
    private DictType resolveDictType(Run run, String alias) {
        return dictTypesByAlias(run).get(alias);
    }

    private Map<String, DictType> dictTypesByAlias(Run run) {
        ensureDictTypes(run);
        return run.dictTypesByAlias;
    }

    /**
     * 站点可见字典类型的 ID → 别名映射（模型投影散列与绑定解析共用，D8/D11）。
     */
    private Map<Long, String> dictAliasById(Run run) {
        ensureDictTypes(run);
        return run.dictAliasById;
    }

    private void ensureDictTypes(Run run) {
        if (run.dictTypesByAlias != null) {
            return;
        }
        run.dictTypesByAlias = new HashMap<>(32);
        run.dictAliasById = new HashMap<>(32);
        List<DictType> visible = dictTypeService.selectList(DictTypeArgs.of().scopeSiteId(run.siteId));
        for (DictType dictType : visible) {
            // 本站行胜出：先装本站私有行，再补全局行
            if (run.siteId.equals(dictType.getSiteId())) {
                run.dictTypesByAlias.put(dictType.getAlias(), dictType);
            }
        }
        for (DictType dictType : visible) {
            run.dictTypesByAlias.putIfAbsent(dictType.getAlias(), dictType);
        }
        for (DictType dictType : visible) {
            run.dictAliasById.put(dictType.getId(), dictType.getAlias());
        }
    }

    private void registerDictType(Run run, DictType dictType) {
        ensureDictTypes(run);
        run.dictTypesByAlias.put(dictType.getAlias(), dictType);
        run.dictAliasById.put(dictType.getId(), dictType.getAlias());
    }

    // ----- models（model-seeding D1–D5：模型定义组 upsert，(type, name) 撞可见集 → linked）-----

    private void importModelFile(Run run, File file) {
        JsonNode node = readAndValidate(file, CompositionSchemas.SEED_MODELS);
        for (JsonNode seed : node.get("models")) {
            String originId = seed.get("originId").asText();
            try {
                importModelDef(run, seed, originId);
            } catch (RuntimeException e) {
                run.fail(run.models, Failure.CAT_MODELS, originId, e);
                logger.error("Seed model import failed: originId={} ({})", originId, file, e);
            }
        }
    }

    /**
     * 导入/刷新一个模型定义（model-seeding D1–D3）：{@code (siteId, origin, originId)}
     * select-then-insert 幂等；(type, name) 撞可见集（本站 ∪ 全局，本站行胜出）→ linked
     * 恒不收编——每个站点都有同名出厂模型，linked 是常态路径。type 即身份：三元组命中而
     * type 不符 → {@code modelTypeMismatch} 失败（刷新恒不改写 type）；刷新只写
     * name + 三个字段集列（scope / order 操作员所有），且只触及散列未改行（§3.1 安全论证）。
     * 字段集树内 dictTypeId 别名 → 站内ID（字典先行，D5/D8）。
     */
    private void importModelDef(Run run, JsonNode seed, String originId) {
        String type = seed.get("type").asText();
        Model existing = modelMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            if (!type.equals(existing.getType())) {
                throw new SeedFailureException(Failure.REASON_MODEL_TYPE_MISMATCH,
                        "Seed model '" + originId + "' declares type '" + type
                                + "' but the imported row has type '" + existing.getType() + "'");
            }
            if (run.overwrite.shouldOverwrite(isUnmodified(existing.getOriginHash(),
                    SeedHash.modelHash(existing, dictAliasById(run))))) {
                applyModelSeed(run, seed, existing);
                modelService.update(existing, run.siteId);
                modelMapper.updateOriginHash(existing.getId(),
                        SeedHash.modelHash(existing, dictAliasById(run)));
                run.models.updated++;
            } else {
                run.models.skipped++;
            }
            return;
        }
        Model collided = resolveModelExact(run, type, seed.get("name").asText());
        if (collided != null) {
            run.models.linked++;
            return;
        }
        Model bean = new Model();
        bean.setType(type);
        applyModelSeed(run, seed, bean);
        bean.setOrigin(run.origin);
        bean.setOriginId(originId);
        // scope 缺省 0（本站私有）——种子行恒不建全局模型（D1）
        modelService.insert(bean, run.siteId);
        modelMapper.updateOriginHash(bean.getId(), SeedHash.modelHash(bean, dictAliasById(run)));
        run.models.imported++;
    }

    /**
     * 把种子的投影字段写到模型 bean 上（新建与刷新共用）：name + 三个字段集列
     * （树 → 紧凑串；dictTypeId 别名 → 站内ID，解析不到丢键 + 告警——D8 断链软化）。
     * type 不在此写（身份字段，仅新建时设置）。
     */
    private void applyModelSeed(Run run, JsonNode seed, Model bean) {
        bean.setName(seed.get("name").asText());
        bean.setMains(defJson(run, seed, "mains"));
        bean.setAsides(defJson(run, seed, "asides"));
        bean.setCustoms(defJson(run, seed, "customs"));
    }

    /**
     * 种子字段集树 → 存储串：deep copy 后把 dictTypeId 的别名取值写回站内ID（字符串形，存储惯例）；
     * 可见集解析不到的别名/悬挂数字ID丢键 + 告警（行照常导入——一处断链不失败整个模型，H5 口径）。
     */
    @Nullable
    private String defJson(Run run, JsonNode seed, String field) {
        if (!seed.hasNonNull(field)) {
            return null;
        }
        JsonNode tree = seed.get(field).deepCopy();
        resolveDictBindings(run, tree);
        try {
            return MAPPER.writeValueAsString(tree);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize model field defs", e);
        }
    }

    private void resolveDictBindings(Run run, JsonNode node) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            JsonNode binding = objectNode.get("dictTypeId");
            if (binding != null && (binding.isTextual() || binding.isNumber())) {
                String text = binding.asText();
                boolean numeric = !text.isEmpty() && text.chars().allMatch(Character::isDigit);
                if (numeric) {
                    // 数字形（手工包遗留）：可见集内的ID放行，悬挂ID丢键（跨站错绑比空绑定更糟，D8）
                    if (!dictAliasById(run).containsKey(Long.parseLong(text))) {
                        objectNode.remove("dictTypeId");
                        logger.warn("Seed model dict binding dropped (dangling id {}) for site {}",
                                text, run.siteId);
                    }
                } else {
                    DictType dictType = resolveDictType(run, text);
                    if (dictType != null) {
                        objectNode.put("dictTypeId", String.valueOf(dictType.getId()));
                    } else {
                        objectNode.remove("dictTypeId");
                        logger.warn("Seed model dict binding dropped (alias '{}' not visible) for site {}",
                                text, run.siteId);
                    }
                }
            }
            objectNode.properties().forEach(entry -> resolveDictBindings(run, entry.getValue()));
        } else if (node instanceof com.fasterxml.jackson.databind.node.ArrayNode arrayNode) {
            arrayNode.forEach(element -> resolveDictBindings(run, element));
        }
    }

    /**
     * 按 (type, name) 精确解析站点可见模型（本站行胜出）——linked 冲突检测用；
     * 与 {@link #resolveModel} 不同：无首个同类回落。
     */
    @Nullable
    private Model resolveModelExact(Run run, String type, String name) {
        List<Model> models = modelService.selectList(ModelArgs.of().scopeSiteId(run.siteId).type(type));
        Model global = null;
        for (Model model : models) {
            if (!name.equals(model.getName())) {
                continue;
            }
            if (run.siteId.equals(model.getSiteId())) {
                return model;
            }
            if (global == null) {
                global = model;
            }
        }
        return global;
    }

    // ----- plan (§6.2 seed/preview) -----

    /**
     * 内容种子干跑计划（只读，§6.2）。资源令牌以干跑会话展开（不落盘、不登记附件）；
     * 种子侧散列只为<b>已存在的本源行</b>计算（模拟保存期处理：正文 RICH 清洗、customs 按
     * 行上模型 {@code sanitizeMap}），与导入时刻落库的 {@code origin_hash_} 同形可比。
     * 散列缺失的行视作未改且无法判定种子变化（宁可少报，R4）。
     */
    public ContentSeedPlan plan(Long siteId, String themeName, File themeDir) {
        File contentDir = new File(new File(themeDir, SEED_DIR), CONTENT_DIR);
        if (!contentDir.isDirectory()) {
            return ContentSeedPlan.EMPTY;
        }
        Site site = siteService.select(siteId);
        if (site == null) {
            throw new IllegalArgumentException("Site not found: " + siteId);
        }
        Run run = new Run(siteId, ThemeSeedImporter.originOf(themeName),
                ThemeSettingsService.themeKey(siteId, themeName), site, User.ANONYMOUS_ID,
                SeedOverwrite.FILL_ONLY, assetCopier.openDry(siteId, themeName));
        PlanCounts channels = new PlanCounts();
        PlanCounts articles = new PlanCounts();
        PlanCounts topics = new PlanCounts();
        PlanCounts blocks = new PlanCounts();
        PlanCounts votes = new PlanCounts();
        PlanCounts surveys = new PlanCounts();
        PlanCounts forms = new PlanCounts();
        PlanCounts linkGroups = new PlanCounts();
        PlanCounts models = new PlanCounts();
        PlanCounts dicts = new PlanCounts();
        List<Collision> collisions = new ArrayList<>();

        // 字典 → 模型先行（导入序镜像，D5）
        for (File file : listFiles(new File(contentDir, DICTS_DIR), DICTS_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_DICTS).get("dictTypes")) {
                planDictType(run, seed, dicts, collisions);
            }
        }
        for (File file : listFiles(new File(contentDir, MODELS_DIR), MODELS_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_MODELS).get("models")) {
                planModel(run, seed, models, collisions);
            }
        }
        File channelsFile = new File(contentDir, CHANNELS_FILE);
        if (channelsFile.isFile()) {
            for (JsonNode seed : readAndValidate(channelsFile, CompositionSchemas.SEED_CHANNELS).get("channels")) {
                planChannel(run, seed, channels, collisions);
            }
        }
        for (File file : listFiles(new File(contentDir, ARTICLES_DIR), ARTICLES_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_ARTICLES).get("articles")) {
                planArticle(run, seed, articles);
            }
        }
        for (File file : listFiles(new File(contentDir, TOPICS_DIR), TOPICS_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_TOPICS).get("topics")) {
                planTopic(run, seed, topics, collisions);
            }
        }
        for (File file : listFiles(new File(contentDir, BLOCKS_DIR), BLOCKS_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_BLOCKS).get("blocks")) {
                if (seed.hasNonNull("originId")) {
                    // 仅定义组入计划（条目组是 A6 权宜填空形，恒不入计划）
                    planBlock(run, seed, blocks, collisions);
                }
            }
        }
        for (File file : listFiles(new File(contentDir, VOTES_DIR), VOTES_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_VOTES).get("votes")) {
                planVote(run, seed, votes);
            }
        }
        for (File file : listFiles(new File(contentDir, SURVEYS_DIR), SURVEYS_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_SURVEYS).get("surveys")) {
                planSurvey(run, seed, surveys);
            }
        }
        for (File file : listFiles(new File(contentDir, FORMS_DIR), FORMS_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_FORMS).get("forms")) {
                planForm(run, seed, forms);
            }
        }
        for (File file : listFiles(new File(contentDir, LINK_GROUPS_DIR), LINK_GROUPS_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_LINK_GROUPS).get("linkGroups")) {
                planLinkGroup(run, seed, linkGroups, collisions);
            }
        }
        return new ContentSeedPlan(channels.toPlan(), articles.toPlan(), topics.toPlan(), blocks.toPlan(),
                votes.toPlan(), surveys.toPlan(), forms.toPlan(), linkGroups.toPlan(),
                models.toPlan(), dicts.toPlan(), collisions);
    }

    private void planDictType(Run run, JsonNode seed, PlanCounts counts, List<Collision> collisions) {
        String originId = seed.get("originId").asText();
        String alias = aliasOf(seed, originId);
        DictType existing = dictTypeMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            counts.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                if (!stored.equals(dictTypeRowHash(existing))) {
                    counts.operatorModified++;
                }
                if (!stored.equals(dictTypeSeedHash(seed, alias))) {
                    counts.seedChanged++;
                }
            }
            return;
        }
        DictType collided = resolveDictType(run, alias);
        if (collided != null) {
            counts.linked++;
            collisions.add(new Collision("dict", originId, alias, collided.getId()));
            return;
        }
        counts.added++;
    }

    /**
     * 种子侧字典类型投影散列（条目以合成ID排序——ID 不入投影，直接应用即可比）。
     */
    private static String dictTypeSeedHash(JsonNode seed, String alias) {
        DictType bean = new DictType();
        applyDictTypeSeed(seed, bean, alias);
        return SeedHash.dictTypeHash(bean, buildSeedDictEntries(seed.get("entries")));
    }

    /**
     * 种子条目 → Dict 列表（合成ID + 序；仅投影散列用，不落库）。
     */
    private static List<Dict> buildSeedDictEntries(@Nullable JsonNode entrySeeds) {
        List<Dict> out = new ArrayList<>();
        if (entrySeeds == null || entrySeeds.isNull()) {
            return out;
        }
        Dict defaults = new Dict();
        int order = 1;
        for (JsonNode entrySeed : entrySeeds) {
            Dict entry = new Dict();
            entry.setId(out.size() + 1L);
            entry.setName(entrySeed.get("name").asText());
            entry.setValue(entrySeed.get("value").asText());
            entry.setRemark(text(entrySeed, "remark"));
            entry.setEnabled(entrySeed.hasNonNull("enabled")
                    ? entrySeed.get("enabled").asBoolean() : defaults.getEnabled());
            entry.setOrder(order++);
            out.add(entry);
        }
        return out;
    }

    private void planModel(Run run, JsonNode seed, PlanCounts counts, List<Collision> collisions) {
        String originId = seed.get("originId").asText();
        String type = seed.get("type").asText();
        String name = seed.get("name").asText();
        Model existing = modelMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            counts.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                if (!stored.equals(SeedHash.modelHash(existing, dictAliasById(run)))) {
                    counts.operatorModified++;
                }
                if (!stored.equals(modelSeedHash(run, seed, type))) {
                    counts.seedChanged++;
                }
            }
            return;
        }
        Model collided = resolveModelExact(run, type, name);
        if (collided != null) {
            counts.linked++;
            collisions.add(new Collision("model", originId, name, collided.getId()));
            return;
        }
        counts.added++;
    }

    /**
     * 种子侧模型投影散列（干跑近似：字典别名以站点<b>现状</b>可见集解析——种子字典尚未导入时，
     * 新别名绑定按丢键口径入散列，宁可多报 seedChanged）。
     */
    private String modelSeedHash(Run run, JsonNode seed, String type) {
        Model bean = new Model();
        bean.setType(type);
        applyModelSeed(run, seed, bean);
        return SeedHash.modelHash(bean, dictAliasById(run));
    }

    private void planChannel(Run run, JsonNode seed, PlanCounts counts, List<Collision> collisions) {
        String originId = seed.get("originId").asText();
        String alias = aliasOf(seed, originId);
        Channel existing = channelMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            counts.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                if (!stored.equals(SeedHash.channelHash(existing))) {
                    counts.operatorModified++;
                }
                if (!stored.equals(channelSeedHash(run, seed, existing))) {
                    counts.seedChanged++;
                }
            }
            return;
        }
        Channel collided = channelService.findBySiteIdAndSlug(run.siteId, alias);
        if (collided != null) {
            counts.linked++;
            collisions.add(new Collision("channel", originId, alias, collided.getId()));
            return;
        }
        counts.added++;
    }

    /**
     * 种子侧栏目投影散列（模拟 {@code ChannelService.insert} 的 customs 清洗，用行上模型）。
     */
    private String channelSeedHash(Run run, JsonNode seed, Channel existing) {
        Channel bean = new Channel();
        applyChannelSeed(run, seed, bean, aliasOf(seed, existing.getOriginId()));
        bean.setCustoms(existing.getChannelModel().sanitizeMap(bean.getCustoms(), sanitizeService));
        return SeedHash.channelHash(bean);
    }

    private void planArticle(Run run, JsonNode seed, PlanCounts counts) {
        String originId = seed.get("originId").asText();
        Article existing = articleMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing == null) {
            counts.added++;
            return;
        }
        counts.existing++;
        String stored = existing.getOriginHash();
        if (stored != null) {
            if (!stored.equals(SeedHash.articleHash(existing, tagNames(existing)))) {
                counts.operatorModified++;
            }
            if (!stored.equals(articleSeedHash(run, seed, existing))) {
                counts.seedChanged++;
            }
        }
    }

    /**
     * 种子侧文章投影散列（模拟 {@code ArticleService.insert} 的保存期处理：正文 RICH 清洗、
     * customs 按行上栏目的文章模型清洗）。
     */
    private String articleSeedHash(Run run, JsonNode seed, Article existing) {
        Article bean = new Article();
        applyArticleSeed(run, seed, bean);
        bean.setText(sanitizeService.sanitize(bean.getText(), PolicyTier.RICH));
        Model model = existing.getChannel().getArticleModel();
        bean.setCustoms(model.sanitizeMap(bean.getCustoms(), sanitizeService));
        return SeedHash.articleHash(bean, stringList(seed, "tags"));
    }

    private void planTopic(Run run, JsonNode seed, PlanCounts counts, List<Collision> collisions) {
        String originId = seed.get("originId").asText();
        String alias = aliasOf(seed, originId);
        Topic existing = topicMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            counts.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                if (!stored.equals(SeedHash.topicHash(existing))) {
                    counts.operatorModified++;
                }
                if (!stored.equals(topicSeedHash(run, seed, existing))) {
                    counts.seedChanged++;
                }
            }
            return;
        }
        Topic collided = topicService.findBySiteIdAndSlug(run.siteId, alias);
        if (collided != null) {
            counts.linked++;
            collisions.add(new Collision("topic", originId, alias, collided.getId()));
            return;
        }
        counts.added++;
    }

    /**
     * 种子侧专题投影散列（customs 按行上专题模型清洗）。
     */
    private String topicSeedHash(Run run, JsonNode seed, Topic existing) {
        Topic bean = new Topic();
        applyTopicSeed(run, seed, bean, aliasOf(seed, existing.getOriginId()));
        bean.setCustoms(existing.getTopicModel().sanitizeMap(bean.getCustoms(), sanitizeService));
        return SeedHash.topicHash(bean);
    }

    private void planBlock(Run run, JsonNode seed, PlanCounts counts, List<Collision> collisions) {
        String originId = seed.get("originId").asText();
        String alias = aliasOf(seed, originId);
        Block existing = blockMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            counts.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                if (!stored.equals(SeedHash.blockHash(existing))) {
                    counts.operatorModified++;
                }
                if (!stored.equals(blockSeedHash(seed, existing))) {
                    counts.seedChanged++;
                }
            }
            return;
        }
        Block collided = resolveBlock(run, alias);
        if (collided != null) {
            counts.linked++;
            collisions.add(new Collision("block", originId, alias, collided.getId()));
            return;
        }
        counts.added++;
    }

    /**
     * 种子侧区块投影散列（区块无 customs 清洗——投影字段直接应用即可比，E4a）。
     */
    private static String blockSeedHash(JsonNode seed, Block existing) {
        Block bean = new Block();
        applyBlockSeed(seed, bean, aliasOf(seed, existing.getOriginId()));
        return SeedHash.blockHash(bean);
    }

    private void planVote(Run run, JsonNode seed, PlanCounts counts) {
        String originId = seed.get("originId").asText();
        Vote existing = voteMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            counts.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                if (!stored.equals(SeedHash.voteHash(existing))) {
                    counts.operatorModified++;
                }
                if (!stored.equals(voteSeedHash(seed))) {
                    counts.seedChanged++;
                }
            }
            return;
        }
        // 投票无别名——无冲突可撞、恒不 linked（E4b / D29）
        counts.added++;
    }

    /**
     * 种子侧投票投影散列（选项即标题列表，直接应用即可比，E4b）。
     */
    private static String voteSeedHash(JsonNode seed) {
        Vote bean = new Vote();
        applyVoteSeed(seed, bean);
        bean.setOptions(buildVoteOptions(seed, List.of()));
        return SeedHash.voteHash(bean);
    }

    private void planSurvey(Run run, JsonNode seed, PlanCounts counts) {
        String originId = seed.get("originId").asText();
        Survey existing = surveyMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            counts.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                if (!stored.equals(SeedHash.surveyHash(existing))) {
                    counts.operatorModified++;
                }
                if (!stored.equals(surveySeedHash(seed))) {
                    counts.seedChanged++;
                }
            }
            return;
        }
        // 问卷无别名——无冲突可撞、恒不 linked（E4d / D35）
        counts.added++;
    }

    /**
     * 种子侧问卷投影散列（条目/选项即标题结构，直接应用即可比，E4d）。
     */
    private static String surveySeedHash(JsonNode seed) {
        Survey bean = new Survey();
        applySurveySeed(seed, bean);
        bean.setItems(buildSurveyItems(seed, List.of()));
        return SeedHash.surveyHash(bean);
    }

    private void planForm(Run run, JsonNode seed, PlanCounts counts) {
        String originId = seed.get("originId").asText();
        FormType existing = formTypeMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            counts.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                if (!stored.equals(SeedHash.formTypeHash(existing, modelNameById(existing.getModelId())))) {
                    counts.operatorModified++;
                }
                if (!stored.equals(formSeedHash(seed))) {
                    counts.seedChanged++;
                }
            }
            return;
        }
        // 表单无别名——无冲突可撞、恒不 linked（E4e / D38）
        counts.added++;
    }

    /**
     * 种子侧表单类型投影散列（模型名即随行，投影即种子字段，E4e）。绑定模型的名称直接取自种子
     * （{@code model} 字段），无需查库——干跑与落库后按同一（bean, 模型名）计算，同形可比。
     */
    private static String formSeedHash(JsonNode seed) {
        FormType bean = new FormType();
        String modelName = text(seed, "model");
        bean.setName(seed.get("name").asText());
        bean.setListTemplate(text(seed, "listTemplate"));
        bean.setItemTemplate(text(seed, "itemTemplate"));
        FormType defaults = new FormType();
        bean.setMode(seed.hasNonNull("mode") ? (short) seed.get("mode").asInt() : defaults.getMode());
        bean.setViewable(seed.hasNonNull("viewable") ? seed.get("viewable").asBoolean() : defaults.getViewable());
        bean.setEnabled(seed.hasNonNull("enabled") ? seed.get("enabled").asBoolean() : defaults.getEnabled());
        return SeedHash.formTypeHash(bean, modelName);
    }

    private void planLinkGroup(Run run, JsonNode seed, PlanCounts counts, List<Collision> collisions) {
        String originId = seed.get("originId").asText();
        String alias = aliasOf(seed, originId);
        LinkGroup existing = linkGroupMapper.selectByOrigin(run.siteId, run.origin, originId);
        if (existing != null) {
            counts.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                List<Link> links = linksOf(existing.getId());
                if (!stored.equals(SeedHash.linkGroupHash(existing, links))) {
                    counts.operatorModified++;
                }
                if (!stored.equals(linkGroupSeedHash(run, seed, existing))) {
                    counts.seedChanged++;
                }
            }
            return;
        }
        LinkGroup collided = linkGroupService.selectByAlias(run.siteId, alias);
        if (collided != null) {
            counts.linked++;
            collisions.add(new Collision("linkGroup", originId, alias, collided.getId()));
            return;
        }
        counts.added++;
    }

    /**
     * 种子侧链接组投影散列（链接随定义，logo 经干跑资源会话展开即可比，E4f）。
     * 引用解析失败的链接同导入路径一样跳过——两侧散列口径一致。
     */
    private String linkGroupSeedHash(Run run, JsonNode seed, LinkGroup existing) {
        LinkGroup bean = new LinkGroup();
        applyLinkGroupSeed(seed, bean, aliasOf(seed, existing.getOriginId()));
        List<Link> links = new ArrayList<>(seed.get("links").size());
        for (JsonNode linkSeed : seed.get("links")) {
            Link link = new Link();
            if (applyLinkSeed(run, linkSeed, link)) {
                links.add(link);
            }
        }
        return SeedHash.linkGroupHash(bean, links);
    }

    /**
     * 声明计数（影响报告 {@code seedContent} 用）：只数种子文件里的条目，不查库。
     * 区块只数定义组（E4a——条目组是 A6 填空形，不属声明内容）。
     */
    public DeclaredContent countDeclared(File themeDir) {
        File contentDir = new File(new File(themeDir, SEED_DIR), CONTENT_DIR);
        if (!contentDir.isDirectory()) {
            return DeclaredContent.EMPTY;
        }
        int channels = 0;
        File channelsFile = new File(contentDir, CHANNELS_FILE);
        if (channelsFile.isFile()) {
            channels = readAndValidate(channelsFile, CompositionSchemas.SEED_CHANNELS).get("channels").size();
        }
        int articles = 0;
        for (File file : listFiles(new File(contentDir, ARTICLES_DIR), ARTICLES_SUFFIX)) {
            articles += readAndValidate(file, CompositionSchemas.SEED_ARTICLES).get("articles").size();
        }
        int topics = 0;
        for (File file : listFiles(new File(contentDir, TOPICS_DIR), TOPICS_SUFFIX)) {
            topics += readAndValidate(file, CompositionSchemas.SEED_TOPICS).get("topics").size();
        }
        int blocks = 0;
        for (File file : listFiles(new File(contentDir, BLOCKS_DIR), BLOCKS_SUFFIX)) {
            for (JsonNode seed : readAndValidate(file, CompositionSchemas.SEED_BLOCKS).get("blocks")) {
                if (seed.hasNonNull("originId")) {
                    blocks++;
                }
            }
        }
        int votes = 0;
        for (File file : listFiles(new File(contentDir, VOTES_DIR), VOTES_SUFFIX)) {
            votes += readAndValidate(file, CompositionSchemas.SEED_VOTES).get("votes").size();
        }
        int surveys = 0;
        for (File file : listFiles(new File(contentDir, SURVEYS_DIR), SURVEYS_SUFFIX)) {
            surveys += readAndValidate(file, CompositionSchemas.SEED_SURVEYS).get("surveys").size();
        }
        int forms = 0;
        for (File file : listFiles(new File(contentDir, FORMS_DIR), FORMS_SUFFIX)) {
            forms += readAndValidate(file, CompositionSchemas.SEED_FORMS).get("forms").size();
        }
        int linkGroups = 0;
        for (File file : listFiles(new File(contentDir, LINK_GROUPS_DIR), LINK_GROUPS_SUFFIX)) {
            linkGroups += readAndValidate(file, CompositionSchemas.SEED_LINK_GROUPS).get("linkGroups").size();
        }
        int models = 0;
        for (File file : listFiles(new File(contentDir, MODELS_DIR), MODELS_SUFFIX)) {
            models += readAndValidate(file, CompositionSchemas.SEED_MODELS).get("models").size();
        }
        int dicts = 0;
        for (File file : listFiles(new File(contentDir, DICTS_DIR), DICTS_SUFFIX)) {
            dicts += readAndValidate(file, CompositionSchemas.SEED_DICTS).get("dictTypes").size();
        }
        return new DeclaredContent(channels, articles, topics, blocks, votes, surveys, forms, linkGroups,
                models, dicts);
    }

    // ----- helpers -----

    /**
     * R4 单一脏检测：行「操作员未改动」当且仅当当前行投影散列复现 {@code origin_hash_}；
     * 散列缺失视作未改（无从判定，宁可少报）。
     */
    private static boolean isUnmodified(@Nullable String storedHash, String rowHash) {
        return storedHash == null || storedHash.equals(rowHash);
    }

    private static String aliasOf(JsonNode seed, @Nullable String originId) {
        String alias = text(seed, "alias");
        return alias != null ? alias : String.valueOf(originId);
    }

    private Model requireModel(Run run, String type, @Nullable String name) {
        Model model = resolveModel(run, type, name);
        if (model == null) {
            throw new SeedFailureException(Failure.REASON_MODEL_MISSING,
                    "No model of type '" + type + "' exists for site " + run.siteId);
        }
        return model;
    }

    /**
     * 按名称解析站点可见（本站 ∪ 全局）模型；未命中回落首个同类模型（站点默认模型约定）并告警。
     */
    @Nullable
    private Model resolveModel(Run run, String type, @Nullable String name) {
        List<Model> models = run.modelsByType.computeIfAbsent(type,
                t -> modelService.selectList(ModelArgs.of().scopeSiteId(run.siteId).type(t)));
        if (models.isEmpty()) {
            return null;
        }
        if (name != null) {
            for (Model model : models) {
                if (name.equals(model.getName())) {
                    return model;
                }
            }
            logger.warn("Seed model '{}' (type {}) not found for site {}, falling back to '{}'",
                    name, type, run.siteId, models.get(0).getName());
        }
        return models.get(0);
    }

    private JsonNode readAndValidate(File file, String schemaName) {
        JsonNode node;
        try {
            node = MAPPER.readTree(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new CompositionLoadException(file.getPath(), e);
        } catch (IOException e) {
            throw new CompositionLoadException(file.getPath(), e);
        }
        List<String> errors = schemas.validate(schemaName, node);
        if (!errors.isEmpty()) {
            throw new CompositionLoadException(file.getPath(), errors);
        }
        return node;
    }

    @Nullable
    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    @Nullable
    private static Integer integer(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    @Nullable
    private static Long longInt(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asLong() : null;
    }

    @Nullable
    private static Map<String, Object> map(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        return MAPPER.convertValue(node.get(field), new TypeReference<>() {
        });
    }

    private static List<String> stringList(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        node.get(field).forEach(item -> out.add(item.asText()));
        return out;
    }

    private static File[] listFiles(File dir, String suffix) {
        File[] files = dir.listFiles(file -> file.isFile() && file.getName().endsWith(suffix));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    /**
     * 单次导入运行的上下文（引用映射 + 计数 + 资源会话）
     */
    private static final class Run {
        final Long siteId;
        final String origin;
        /**
         * 安装主题键（{@code siteId/themeName}）：模板轨道物化的变体绑定查询用
         * （template-track-variants D8；「先装不激活」时不能依赖站点活动主题）
         */
        final String themeKey;
        final Site site;
        final long userId;
        final SeedOverwrite overwrite;
        final SeedAssetCopier.Session assets;
        final Map<String, Long> channelIdByOrigin = new LinkedHashMap<>(16);
        final Map<String, Long> channelIdByAlias = new LinkedHashMap<>(16);
        final Map<String, Long> articleIdByOrigin = new LinkedHashMap<>(32);
        final Map<String, List<Model>> modelsByType = new HashMap<>(4);
        @Nullable
        Map<String, Block> blocksByAlias;
        /**
         * 站点可见字典类型（别名 → 行，本站行胜出；惰性装载，导入并入——model-seeding D8）
         */
        @Nullable
        Map<String, DictType> dictTypesByAlias;
        /**
         * 站点可见字典类型（ID → 别名；模型投影散列与绑定解析共用）
         */
        @Nullable
        Map<Long, String> dictAliasById;
        final Counts channels = new Counts();
        final Counts articles = new Counts();
        final Counts topics = new Counts();
        final Counts blocks = new Counts();
        final Counts votes = new Counts();
        final Counts surveys = new Counts();
        final Counts forms = new Counts();
        final Counts linkGroups = new Counts();
        final Counts models = new Counts();
        final Counts dicts = new Counts();
        final Counts blockItems = new Counts();
        final Counts linkItems = new Counts();
        final List<Failure> failures = new ArrayList<>();

        Run(Long siteId, String origin, String themeKey, Site site, long userId, SeedOverwrite overwrite,
            SeedAssetCopier.Session assets) {
            this.siteId = siteId;
            this.origin = origin;
            this.themeKey = themeKey;
            this.site = site;
            this.userId = userId;
            this.overwrite = overwrite;
            this.assets = assets;
        }

        /**
         * 记录一次实体级失败（§8.1 / D22）：计数 + 明细。已知失败点的原因码取自
         * {@link SeedFailureException}，其余异常记 {@code unknown}（详情为异常消息）。
         */
        void fail(Counts counts, String category, String ref, RuntimeException e) {
            counts.failed++;
            if (e instanceof SeedFailureException failure) {
                addFailure(new Failure(category, ref, failure.getReason(), failure.getMessage()));
            } else {
                String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                addFailure(new Failure(category, ref, Failure.REASON_UNKNOWN, detail));
            }
        }

        void addFailure(Failure failure) {
            if (failures.size() < Failure.MAX_DETAILS) {
                failures.add(failure);
            }
        }
    }

    private static final class Counts {
        int imported;
        int skipped;
        int linked;
        int failed;
        int updated;

        CategoryResult toResult() {
            return new CategoryResult(imported, skipped, linked, failed, updated);
        }
    }

    /**
     * 干跑计划计数器
     */
    private static final class PlanCounts {
        int added;
        int linked;
        int existing;
        int operatorModified;
        int seedChanged;

        CategoryPlan toPlan() {
            return new CategoryPlan(added, linked, existing, operatorModified, seedChanged);
        }
    }

    /**
     * 单类内容的导入计数（SeedImportResult v2 口径，§5.4 / §6.1）
     *
     * @param imported 新导入行数
     * @param skipped  已存在跳过行数（幂等；含被覆盖策略判为「操作员已改」而跳过的行）
     * @param linked   别名撞上非本源行、指向既有行的数目（不插入、不认领）
     * @param failed   失败行数（逐条日志；重跑可续传）
     * @param updated  按覆盖策略刷新的既有本源行数（syncUnmodified / resetAllOrigin，A3）
     */
    public record CategoryResult(int imported, int skipped, int linked, int failed, int updated) {
    }

    /**
     * 内容种子导入结果（§5.4 SeedImportResult v2 的内容部分）
     *
     * @param channels     栏目计数
     * @param articles     文章计数
     * @param topics       专题计数（功能面为 T2，定义任何版本都落库——D46）
     * @param blocks       区块定义计数（theme-seed-export E4a / D25：来源三元组 upsert，
     *                     别名冲突 → linked）
     * @param votes        投票定义计数（theme-seed-export E4b / D29：文章式来源三元组 upsert，
     *                     无别名、linked 恒为 0）
     * @param surveys      问卷定义计数（theme-seed-export E4d / D35：文章式；写入经核心 SurveyService，
     *                     任何版本都落库——D46）
     * @param forms           表单类型定义计数（theme-seed-export E4e / D38：文章式；写入经核心 FormTypeService，
     *                        任何版本都落库——D46；绑定模型按名随行）
     * @param linkGroups     链接组计数（theme-seed-export E4f / D42：来源三元组 upsert，
     *                        别名撞既有分组 → linked 恒不收编；链接随定义）
     * @param models          模型定义计数（model-seeding D1：来源三元组 upsert，(type, name) 撞可见集
     *                        → linked——出厂同名即常态；字典 → 模型先于一切内容导入，D5）
     * @param dicts           字典类型定义计数（model-seeding D8：别名撞可见集 → linked；条目随类型）
     * @param blockItems      区块条目计数（A6 权宜格式保留：恒只填空，linked/updated 恒为 0；
     *                        不参与干跑计划与影响报告的 seedContent）
     * @param linkItems 链接条目计数（E4f：随分组定义出入——新建/刷新按种子序整组落地计
     *                        imported，linked 分组按 (typeId, url) 填空计 imported/skipped；
     *                        无三元组，不参与干跑计划与声明计数）
     * @param assetsCopied    复制进上传存储的资源文件数
     * @param failures        失败明细（theme-seed-export §8.1 / D22；至多 {@link Failure#MAX_DETAILS} 条，
     *                        计数以各类别 {@code failed} 为准）
     */
    public record ContentSeedResult(CategoryResult channels, CategoryResult articles, CategoryResult topics,
                                    CategoryResult blocks, CategoryResult votes, CategoryResult surveys,
                                    CategoryResult forms, CategoryResult linkGroups, CategoryResult models,
                                    CategoryResult dicts, CategoryResult blockItems,
                                    CategoryResult linkItems, int assetsCopied, List<Failure> failures) {
        public static final ContentSeedResult EMPTY = new ContentSeedResult(
                new CategoryResult(0, 0, 0, 0, 0), new CategoryResult(0, 0, 0, 0, 0),
                new CategoryResult(0, 0, 0, 0, 0), new CategoryResult(0, 0, 0, 0, 0),
                new CategoryResult(0, 0, 0, 0, 0), new CategoryResult(0, 0, 0, 0, 0),
                new CategoryResult(0, 0, 0, 0, 0), new CategoryResult(0, 0, 0, 0, 0),
                new CategoryResult(0, 0, 0, 0, 0), new CategoryResult(0, 0, 0, 0, 0),
                new CategoryResult(0, 0, 0, 0, 0), new CategoryResult(0, 0, 0, 0, 0),
                0, List.of());
    }

    /**
     * 实体级失败明细（theme-seed-export §8.1 / D22）：原因码可译（控制台 {@code theme.seed.failure.<reason>}，
     * 译文 ≠ 键判定回退 {@code detail} 原文），线上格式与语言无关。
     *
     * @param category 类别（复数键形，直接复用控制台 {@code theme.seed.cat.*} 文案）
     * @param ref      失败实体的引用（originId / 别名 / {@code 别名/文章originId}）
     * @param reason   原因码（{@code REASON_*}；未知异常为 {@code unknown}）
     * @param detail   英文详情（日志同源；未登记原因码时的展示回退）
     */
    public record Failure(String category, String ref, String reason, String detail) {
        public static final int MAX_DETAILS = 50;

        public static final String CAT_CHANNELS = "channels";
        public static final String CAT_ARTICLES = "articles";
        public static final String CAT_TOPICS = "topics";
        public static final String CAT_BLOCKS = "blocks";
        public static final String CAT_VOTES = "votes";
        public static final String CAT_SURVEYS = "surveys";
        public static final String CAT_FORMS = "forms";
        public static final String CAT_LINK_GROUPS = "linkGroups";
        public static final String CAT_MODELS = "models";
        public static final String CAT_DICTS = "dicts";
        public static final String CAT_BLOCK_ITEMS = "blockItems";
        /** 构成种子的面板类别（{@link ThemeSeedImporter} 复用本明细形——R2 通道对构成的延伸） */
        public static final String CAT_PANELS = "panels";

        /** 站点尚无专题模型（D10：模型不可随主题携带）——建模后重新导入即补齐 */
        public static final String REASON_TOPIC_MODEL_MISSING = "topicModelMissing";
        /** 站点缺少所需类型的模型（栏目/文章模型一个都没有） */
        public static final String REASON_MODEL_MISSING = "modelMissing";
        /** 父栏目未解析（种子须树序：父先于子） */
        public static final String REASON_CHANNEL_PARENT_UNRESOLVED = "channelParentUnresolved";
        /** 文章目标栏目未解析 */
        public static final String REASON_ARTICLE_CHANNEL_UNRESOLVED = "articleChannelUnresolved";
        /** 站点无此别名的区块 */
        public static final String REASON_BLOCK_NOT_FOUND = "blockNotFound";
        /** 区块条目的文章未解析 */
        public static final String REASON_BLOCK_ITEM_ARTICLE_UNRESOLVED = "blockItemArticleUnresolved";
        /** 模型三元组命中而 type 不符（model-seeding D3：type 即身份，刷新恒不改写） */
        public static final String REASON_MODEL_TYPE_MISMATCH = "modelTypeMismatch";
        /** 面板设置的引用令牌不可解析（如 seedScope=composition 未带内容）——建目标内容后重导即补齐 */
        public static final String REASON_PANEL_TOKEN_UNRESOLVED = "panelTokenUnresolved";
        /** 其余异常（详情为异常消息） */
        public static final String REASON_UNKNOWN = "unknown";
    }

    /**
     * 已知失败点的实体级失败（§8.1 / D22）：携带 i18n 可译原因码；导入循环捕获后记入
     * {@link ContentSeedResult#failures}。
     */
    public static class SeedFailureException extends RuntimeException {
        private final String reason;

        public SeedFailureException(String reason, String detail) {
            super(detail);
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 内容种子干跑计划（{@code SeedPlan} 的内容部分，§6.2；E4a 增区块定义类别、E4b 增投票类别、
     * E4f 增链接组类别、model-seeding 增模型/字典类别）
     */
    public record ContentSeedPlan(CategoryPlan channels, CategoryPlan articles, CategoryPlan topics,
                                  CategoryPlan blocks, CategoryPlan votes, CategoryPlan surveys,
                                  CategoryPlan forms, CategoryPlan linkGroups, CategoryPlan models,
                                  CategoryPlan dicts, List<Collision> collisions) {
        public static final ContentSeedPlan EMPTY = new ContentSeedPlan(
                CategoryPlan.EMPTY, CategoryPlan.EMPTY, CategoryPlan.EMPTY, CategoryPlan.EMPTY,
                CategoryPlan.EMPTY, CategoryPlan.EMPTY, CategoryPlan.EMPTY, CategoryPlan.EMPTY,
                CategoryPlan.EMPTY, CategoryPlan.EMPTY, List.of());
    }

    /**
     * 种子声明的内容条目数（影响报告 {@code seedContent}，§6.2；E4a 增区块定义计数、E4b 增投票计数、
     * E4e 增表单计数、E4f 增链接组计数、model-seeding 增模型/字典计数）
     */
    public record DeclaredContent(int channels, int articles, int topics, int blocks, int votes,
                                  int surveys, int forms, int linkGroups, int models, int dicts) {
        public static final DeclaredContent EMPTY = new DeclaredContent(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
