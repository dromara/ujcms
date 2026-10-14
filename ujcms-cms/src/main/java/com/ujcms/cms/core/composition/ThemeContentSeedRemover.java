package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ArticleTopicMapper;
import com.ujcms.cms.core.mapper.BlockItemMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.service.ActionService;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.TopicService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.domain.Link;
import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.cms.ext.domain.Survey;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.mapper.FormMapper;
import com.ujcms.cms.ext.mapper.FormTypeMapper;
import com.ujcms.cms.ext.mapper.LinkGroupMapper;
import com.ujcms.cms.ext.mapper.SurveyMapper;
import com.ujcms.cms.ext.mapper.VoteMapper;
import com.ujcms.cms.ext.service.FormTypeService;
import com.ujcms.cms.ext.service.LinkService;
import com.ujcms.cms.ext.service.SurveyFeedbackService;
import com.ujcms.cms.ext.service.SurveyService;
import com.ujcms.cms.ext.service.LinkGroupService;
import com.ujcms.cms.ext.service.VoteService;
import com.ujcms.cms.ext.service.args.FormTypeArgs;
import com.ujcms.cms.ext.service.args.LinkArgs;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 演示数据移除器（theme-marketplace §6.1，A3）。对内容本源行（栏目/文章/专题，
 * {@code origin_ = theme:<name>}）执行「移除演示数据」与卸载 dataPolicy 的内容分支。
 * 移除即物理删除——不进回收站（回收模式已裁撤：回收站文章占用栏目致栏目守卫必然不过、
 * 来源被清成单向门）。<b>全部经服务层</b>（{@link ArticleService#completelyDelete} /
 * {@link ChannelService#delete} / {@link TopicService#delete}）——Lucene、附件引用、栏目树、
 * 审批流程清理照常发生（D1）。
 *
 * <p><b>守卫</b>（D7：操作员内容神圣不可侵犯）：
 * <ul>
 *   <li>只触碰 {@code origin_} 匹配的行（安全不变量 1——按来源列表定位，绝不按别名/树扫）；</li>
 *   <li>栏目仅在「无任何文章 + 无任何子栏目」时删除——非本源子栏目/文章天然阻止删除；
 *       本源子栏目自下而上先行处理；</li>
 *   <li>专题仅在「无非本源文章绑定」时删除（绑定级联清理；本源文章的绑定属演示编排）；</li>
 *   <li>守卫不过的栏目/专题清除来源标记并报告（转为普通操作员行）；</li>
 *   <li>{@code dryRun} 干跑：只算计数不落库（安全不变量 2——危险操作先出示计数）。</li>
 * </ul>
 *
 * <p><b>页面组件不动，但如实告知</b>（theme-marketplace D24）：移除是内容操作，面板行一概不触碰；
 * 设置里钉住了将删实体ID的在用面板计入 {@code panelRefsBroken}——移除后它们在健康页列为失效引用，
 * 下一次 导入演示数据（只填空）对散列证明未动过的本源面板自动修复。
 *
 * <p>无整体事务：逐实体经服务层各自提交（与内容导入同理，§5.4）；单实体失败记日志并继续，
 * 该行按守卫不过处理（清标记），重跑可续。
 *
 * @author PONY
 */
@Component
public class ThemeContentSeedRemover {
    private static final Logger logger = LoggerFactory.getLogger(ThemeContentSeedRemover.class);

    private final ChannelService channelService;
    private final ArticleService articleService;
    private final TopicService topicService;
    private final BlockService blockService;
    private final UserService userService;
    private final ChannelMapper channelMapper;
    private final ArticleMapper articleMapper;
    private final TopicMapper topicMapper;
    private final ArticleTopicMapper articleTopicMapper;
    private final BlockMapper blockMapper;
    private final BlockItemMapper blockItemMapper;
    private final VoteService voteService;
    private final VoteMapper voteMapper;
    private final ActionService actionService;
    private final SurveyMapper surveyMapper;
    private final SurveyService surveyService;
    private final SurveyFeedbackService surveyFeedbackService;
    private final FormTypeService formTypeService;
    private final FormTypeMapper formTypeMapper;
    private final FormMapper formMapper;
    private final LinkGroupService linkGroupService;
    private final LinkService linkService;
    private final LinkGroupMapper linkGroupMapper;
    private final com.ujcms.cms.core.service.ModelService modelService;
    private final com.ujcms.cms.core.mapper.ModelMapper modelMapper;
    private final com.ujcms.cms.core.service.DictTypeService dictTypeService;
    private final com.ujcms.cms.core.service.DictService dictService;
    private final com.ujcms.cms.core.mapper.DictTypeMapper dictTypeMapper;
    private final PlacementService placementService;

    public ThemeContentSeedRemover(ChannelService channelService, ArticleService articleService,
                                   TopicService topicService, BlockService blockService, UserService userService,
                                   ChannelMapper channelMapper, ArticleMapper articleMapper,
                                   TopicMapper topicMapper, ArticleTopicMapper articleTopicMapper,
                                   BlockMapper blockMapper, BlockItemMapper blockItemMapper,
                                   VoteService voteService, VoteMapper voteMapper, ActionService actionService,
                                   SurveyMapper surveyMapper, SurveyService surveyService,
                                   SurveyFeedbackService surveyFeedbackService,
                                   FormTypeService formTypeService, FormTypeMapper formTypeMapper,
                                   FormMapper formMapper,
                                   LinkGroupService linkGroupService, LinkService linkService,
                                   LinkGroupMapper linkGroupMapper,
                                   com.ujcms.cms.core.service.ModelService modelService,
                                   com.ujcms.cms.core.mapper.ModelMapper modelMapper,
                                   com.ujcms.cms.core.service.DictTypeService dictTypeService,
                                   com.ujcms.cms.core.service.DictService dictService,
                                   com.ujcms.cms.core.mapper.DictTypeMapper dictTypeMapper,
                                   PlacementService placementService) {
        this.channelService = channelService;
        this.articleService = articleService;
        this.topicService = topicService;
        this.blockService = blockService;
        this.userService = userService;
        this.channelMapper = channelMapper;
        this.articleMapper = articleMapper;
        this.topicMapper = topicMapper;
        this.articleTopicMapper = articleTopicMapper;
        this.blockMapper = blockMapper;
        this.blockItemMapper = blockItemMapper;
        this.voteService = voteService;
        this.voteMapper = voteMapper;
        this.actionService = actionService;
        this.surveyMapper = surveyMapper;
        this.surveyService = surveyService;
        this.surveyFeedbackService = surveyFeedbackService;
        this.formTypeService = formTypeService;
        this.formTypeMapper = formTypeMapper;
        this.formMapper = formMapper;
        this.linkGroupService = linkGroupService;
        this.linkService = linkService;
        this.linkGroupMapper = linkGroupMapper;
        this.modelService = modelService;
        this.modelMapper = modelMapper;
        this.dictTypeService = dictTypeService;
        this.dictService = dictService;
        this.dictTypeMapper = dictTypeMapper;
        this.placementService = placementService;
    }

    /**
     * 移除主题演示数据（§6.1「移除演示数据」/ 卸载 DELETE 的内容分支）。演示数据只有一种移除
     * 方式：物理删除（含扩展/标签/附件引用/Lucene，不进回收站）——回收模式已裁撤：回收站文章
     * 占用栏目导致栏目守卫必然不过、来源标记被清，硬删自此永远收不回（单向门）。
     *
     * @param siteId    站点ID
     * @param themeName 主题名
     * @param dryRun    干跑：只返回计数，不改任何行
     * @param operator  操作员（彻底删除需要；空则以匿名用户记账）
     * @return 逐类别计数（干跑与实做同一算法，计数一致）
     */
    public RemoveResult remove(Long siteId, String themeName, boolean dryRun,
                               @Nullable User operator) {
        String origin = ThemeSeedImporter.originOf(themeName);
        User user = operator != null ? operator : userService.select(User.ANONYMOUS_ID);
        List<Channel> channels = channelMapper.listByOrigin(siteId, origin);
        List<Article> articles = articleMapper.listByOrigin(siteId, origin);
        List<Topic> topics = topicMapper.listByOrigin(siteId, origin);

        Tally tally = new Tally();

        // ----- 链接组（E4f / D44）：散列守卫——分组连同链接仍为种子形（存储散列复现当前投影）
        // 才删（链接先行经服务层、分组随后，RESTRICT 外键序）；任何操作员改动（分组字段/链接增改删，
        // 散列覆盖两级）即清标记转普通操作员行——链接无独立身份，「无操作员行残留」即「投影未被改动」。
        // linked 分组无三元组，恒不触及（其内种子链接为普通操作员行，既档残留）。
        // 次序在文章/栏目之前：指向栏目/文章的链接随目标删除被 LinkService 的删除监听收走
        // （link-groups D3），守卫若排在其后，实做时投影已缺行、种子形分组被误判为操作员改过，
        // 干跑却报可删（不变量 2）；先链接后内容也正是外键序。 -----
        for (LinkGroup linkGroup : linkGroupMapper.listByOrigin(siteId, origin)) {
            try {
                List<Link> links = linkService.selectList(
                        LinkArgs.of().groupId(linkGroup.getId()));
                String storedHash = linkGroup.getOriginHash();
                boolean seedShaped = storedHash == null
                        || storedHash.equals(SeedHash.linkGroupHash(linkGroup, links));
                if (seedShaped) {
                    if (!dryRun) {
                        for (Link link : links) {
                            linkService.delete(link.getId());
                        }
                        linkGroupService.delete(linkGroup.getId());
                    }
                    tally.linkGroupsDeleted++;
                } else {
                    tally.linkGroupsOriginCleared++;
                }
            } catch (RuntimeException e) {
                tally.linkGroupsOriginCleared++;
                logger.warn("Seed link group delete failed, clearing origin instead: id={}",
                        linkGroup.getId(), e);
            }
        }

        // ----- 文章：物理删除（回收站中的本源文章同样收走） -----
        Set<Long> originArticleIds = new HashSet<>(articles.size());
        // 栏目ID → 本站该主题来源文章数（干跑时从总数中扣除）
        Map<Long, Integer> originArticlesByChannel = new HashMap<>(16);
        for (Article article : articles) {
            originArticleIds.add(article.getId());
            originArticlesByChannel.merge(article.getChannelId(), 1, Integer::sum);
        }
        Set<Long> goneArticleIds = new HashSet<>(articles.size());
        for (Article article : articles) {
            try {
                if (!dryRun) {
                    articleService.completelyDelete(article, user);
                }
                goneArticleIds.add(article.getId());
                tally.articlesDeleted++;
            } catch (RuntimeException e) {
                tally.articlesFailed++;
                logger.error("Seed article remove failed: id={}, originId={}", article.getId(),
                        article.getOriginId(), e);
            }
        }

        // ----- 栏目：自下而上；仅在完全为空（无任何文章 + 无任何子栏目）时删除，否则清来源标记 -----
        Set<Long> goneChannelIds = new HashSet<>(channels.size());
        for (Channel channel : postOrder(channels)) {
            try {
                if (channelDeletable(channel, dryRun, originArticlesByChannel, goneChannelIds)) {
                    if (!dryRun) {
                        channelService.delete(channel);
                    }
                    goneChannelIds.add(channel.getId());
                    tally.channelsDeleted++;
                } else {
                    tally.channelsOriginCleared++;
                }
            } catch (RuntimeException e) {
                // 守卫与删除之间的竞态（如并发新增文章）：按守卫不过处理，清标记并继续
                tally.channelsOriginCleared++;
                logger.warn("Seed channel delete failed, clearing origin instead: id={}", channel.getId(), e);
            }
        }

        // ----- 专题：仅在无非本源文章绑定时删除（本源绑定级联清理），否则清来源标记 -----
        Set<Long> goneTopicIds = new HashSet<>(topics.size());
        for (Topic topic : topics) {
            try {
                boolean hasForeignBinding = articleTopicMapper.listByTopicId(topic.getId()).stream()
                        .anyMatch(binding -> !originArticleIds.contains(binding.getArticleId()));
                if (hasForeignBinding) {
                    tally.topicsOriginCleared++;
                } else {
                    if (!dryRun) {
                        topicService.delete(topic);
                    }
                    goneTopicIds.add(topic.getId());
                    tally.topicsDeleted++;
                }
            } catch (RuntimeException e) {
                tally.topicsOriginCleared++;
                logger.warn("Seed topic delete failed, clearing origin instead: id={}", topic.getId(), e);
            }
        }

        // ----- 区块定义（E4a / D26）：仅本次文章处置后不再有条目时删除，否则清来源标记 -----
        // 干跑按「现存条目 −（随本源文章级联消失的条目）」推算（栏目守卫同法）。
        // 全局区块绝不携带本站 origin（D26）。
        Map<Long, Integer> originItemsByBlock = new HashMap<>(8);
        if (dryRun) {
            for (Long articleId : originArticleIds) {
                for (BlockItem item : blockItemMapper.listByArticleId(articleId)) {
                    originItemsByBlock.merge(item.getBlockId(), 1, Integer::sum);
                }
            }
        }
        Set<Long> goneBlockIds = new HashSet<>(8);
        for (Block block : blockMapper.listByOrigin(siteId, origin)) {
            try {
                int itemCount = blockItemMapper.countByBlockId(block.getId());
                if (dryRun) {
                    itemCount -= originItemsByBlock.getOrDefault(block.getId(), 0);
                }
                if (itemCount <= 0) {
                    if (!dryRun) {
                        // 守卫已保证无条目残留——BlockService.delete 的条目级联不会触及操作员内容
                        blockService.delete(block.getId());
                    }
                    goneBlockIds.add(block.getId());
                    tally.blocksDeleted++;
                } else {
                    tally.blocksOriginCleared++;
                }
            } catch (RuntimeException e) {
                tally.blocksOriginCleared++;
                logger.warn("Seed block delete failed, clearing origin instead: id={}", block.getId(), e);
            }
        }

        // ----- 投票定义（E4b / D31）：仅无选票记录时删除，否则清来源标记（用户参与恒不销毁） -----
        // 守卫双查：times 计数与 Action 选票记录任一存在即不删（计数可幸存于记录清理，反之亦然）。
        Set<Long> goneVoteIds = new HashSet<>(8);
        for (Vote vote : voteMapper.listByOrigin(siteId, origin)) {
            try {
                boolean hasBallots = vote.getTimes() > 0
                        || actionService.existsBy(Vote.ACTION_TYPE, vote.getId(), null, null, null, null, null);
                if (!hasBallots) {
                    if (!dryRun) {
                        // 守卫已保证无选票——VoteService.delete 的选项级联属定义本身
                        voteService.delete(vote.getId());
                    }
                    goneVoteIds.add(vote.getId());
                    tally.votesDeleted++;
                } else {
                    tally.votesOriginCleared++;
                }
            } catch (RuntimeException e) {
                tally.votesOriginCleared++;
                logger.warn("Seed vote delete failed, clearing origin instead: id={}", vote.getId(), e);
            }
        }

        // ----- 问卷定义（E4d / D35）：删除经核心 SurveyService（任何版本都可及，D46）；
        // 反馈守卫：times 或反馈记录任一存在即清标记不删 -----
        for (Survey survey : surveyMapper.listByOrigin(siteId, origin)) {
            try {
                boolean hasFeedback = survey.getTimes() > 0 || surveyFeedbackService.existsBySurveyId(survey.getId());
                if (!hasFeedback) {
                    if (!dryRun) {
                        // 守卫已保证无反馈——条目/选项级联属定义本身
                        surveyService.delete(survey.getId());
                    }
                    tally.surveysDeleted++;
                } else {
                    tally.surveysOriginCleared++;
                }
            } catch (RuntimeException e) {
                tally.surveysOriginCleared++;
                logger.warn("Seed survey delete failed, clearing origin instead: id={}", survey.getId(), e);
            }
        }

        // ----- 表单类型定义（E4e / D40）：删除经核心 FormTypeService（任何版本都可及，D46）；
        // 提交守卫：existsByTypeId 存在提交即清标记不删（用户提交恒不销毁——FormTypeService.delete 的
        // error.formType.hasForms 抛出自此不可达，服务层守卫仍作兜底）。次序在模型之前（D40）——
        // 表单删除释放其模型绑定，模型守卫据此判定。 -----
        Set<Long> goneFormTypeIds = new HashSet<>(8);
        for (FormType formType : formTypeMapper.listByOrigin(siteId, origin)) {
            try {
                boolean hasSubmission = formMapper.existsByTypeId(formType.getId()) > 0;
                if (!hasSubmission) {
                    if (!dryRun) {
                        formTypeService.delete(formType.getId());
                    }
                    goneFormTypeIds.add(formType.getId());
                    tally.formsDeleted++;
                } else {
                    tally.formsOriginCleared++;
                }
            } catch (RuntimeException e) {
                tally.formsOriginCleared++;
                logger.warn("Seed form delete failed, clearing origin instead: id={}", formType.getId(), e);
            }
        }

        // ----- 模型定义（model-seeding D9 + E4e）：任何幸存行仍绑定即清标记，否则删除。次序在一切
        // 内容族之后（D5 移除序）——本次处置的行已释放绑定；干跑以「幸存行」在内存推算同一口径
        // （已删/将删的行不计绑定）。表单类型绑定 form 型模型，自 E4e 起 form 模型可种子化——故表单
        // 类型在守卫面：幸存表单类型的绑定模型须保留（其被删的表单类型已计入 goneFormTypeIds）。
        // 无本源模型时整支跳过（不触碰任何站点列表——干跑零交互不变量保持）。
        Set<Long> goneModelIds = new HashSet<>(8);
        List<Model> originModels = modelMapper.listByOrigin(siteId, origin);
        if (!originModels.isEmpty()) {
            Set<Long> survivingBoundModelIds = new HashSet<>(16);
            for (Channel channel : channelService.selectList(
                    com.ujcms.cms.core.service.args.ChannelArgs.of().siteId(siteId))) {
                if (goneChannelIds.contains(channel.getId())) {
                    continue;
                }
                survivingBoundModelIds.add(channel.getChannelModelId());
                survivingBoundModelIds.add(channel.getArticleModelId());
            }
            for (Topic topic : topicService.selectList(
                    com.ujcms.cms.core.service.args.TopicArgs.of().siteId(siteId))) {
                if (!goneTopicIds.contains(topic.getId())) {
                    survivingBoundModelIds.add(topic.getTopicModelId());
                }
            }
            for (Block block : blockService.selectList(BlockArgs.of().scopeSiteId(siteId))) {
                if (!goneBlockIds.contains(block.getId()) && block.getBlockItemModelId() != null) {
                    survivingBoundModelIds.add(block.getBlockItemModelId());
                }
            }
            // 表单类型绑定（E4e）：幸存表单类型（本次未删）仍占用其 form 模型 → 该模型保留
            for (FormType formType : formTypeService.selectList(FormTypeArgs.of().siteId(siteId))) {
                if (!goneFormTypeIds.contains(formType.getId())) {
                    survivingBoundModelIds.add(formType.getModelId());
                }
            }
            for (Model model : originModels) {
                try {
                    if (survivingBoundModelIds.contains(model.getId())) {
                        tally.modelsOriginCleared++;
                    } else {
                        if (!dryRun) {
                            // ModelDeleteListener 守卫兜底：守卫与删除之间的并发新绑定 → LogicException
                            modelService.delete(model.getId());
                        }
                        goneModelIds.add(model.getId());
                        tally.modelsDeleted++;
                    }
                } catch (RuntimeException e) {
                    tally.modelsOriginCleared++;
                    logger.warn("Seed model delete failed, clearing origin instead: id={}",
                            model.getId(), e);
                }
            }
        }

        // ----- 字典类型定义（D8/D9）：任何幸存模型的字段集仍引用即清标记，否则删除。
        // 守卫与 DictTypeService.delete 同口径，遍历「所有」模型而非本站可见模型：其它站点的模型同样可能绑定
        // 本站类型（早期的站点复制不重映射字典ID），只看本站会把它误判为可删。含 sys 条目的类型同样只清标记
        // （DictTypeService.delete 的级联走 mapper，不拒绝 sys 条目，故在此先行判定）。
        // 删除交给 DictTypeService.delete 一次完成（类型 + 条目同一事务）：若先逐条清空条目、类型删除再被拒绝，
        // 留下的是条目全失的空类型——再次导入按别名 linked，前台筛选项即为空。 -----
        List<DictType> originDictTypes = dictTypeMapper.listByOrigin(siteId, origin);
        if (!originDictTypes.isEmpty()) {
            Set<Long> survivingDictBindings = new HashSet<>(16);
            for (Model model : modelService.selectList(com.ujcms.cms.core.service.args.ModelArgs.of())) {
                if (goneModelIds.contains(model.getId())) {
                    continue;
                }
                SeedHash.collectDictBindings(model.getMains(), survivingDictBindings);
                SeedHash.collectDictBindings(model.getAsides(), survivingDictBindings);
                SeedHash.collectDictBindings(model.getCustoms(), survivingDictBindings);
            }
            for (DictType dictType : originDictTypes) {
                try {
                    if (survivingDictBindings.contains(dictType.getId()) || hasSysEntry(dictType.getId())) {
                        tally.dictsOriginCleared++;
                    } else {
                        if (!dryRun) {
                            dictTypeService.delete(dictType.getId());
                        }
                        tally.dictsDeleted++;
                    }
                } catch (RuntimeException e) {
                    tally.dictsOriginCleared++;
                    logger.warn("Seed dict type delete failed, clearing origin instead: id={}",
                            dictType.getId(), e);
                }
            }
        }

        // ----- 幸存者清来源标记（删除的行已不在；文章刻意不清：删除失败的行保留标记以便重跑续收） -----
        if (!dryRun) {
            channelMapper.clearOrigin(siteId, origin);
            topicMapper.clearOrigin(siteId, origin);
            blockMapper.clearOrigin(siteId, origin);
            voteMapper.clearOrigin(siteId, origin);
            surveyMapper.clearOrigin(siteId, origin);
            formTypeMapper.clearOrigin(siteId, origin);
            linkGroupMapper.clearOrigin(siteId, origin);
            modelMapper.clearOrigin(siteId, origin);
            dictTypeMapper.clearOrigin(siteId, origin);
        }

        // ----- 页面组件引用面（D24）：设置里钉住了将删实体ID的在用面板计数——只告知，面板不动；
        // 健康页随后列为失效引用，导入演示数据（只填空）对散列证明未动过的本源面板自动修复 -----
        tally.panelRefsBroken = countPanelRefsBroken(siteId,
                List.of(goneChannelIds, goneArticleIds, goneBlockIds, goneVoteIds));

        RemoveResult result = tally.toResult(dryRun);
        logger.info("Theme content seed remove: site={}, theme={}, {}", siteId, themeName, result);
        return result;
    }

    /**
     * 引用了将删实体（栏目/文章/区块/投票）ID 的在用面板数（D24）。设置值形状无关地递归匹配裸ID
     * （数字或数字串，含 {@code {"id": …}} 与列表元素）——无需解析组件声明：种子展开只落裸ID，
     * 雪花ID 与任何普通设置值不可能撞车。无将删实体时零交互（干跑不触碰任何站点列表）。
     */
    private int countPanelRefsBroken(Long siteId, List<Set<Long>> goneIdSets) {
        Set<String> gone = new HashSet<>(64);
        for (Set<Long> ids : goneIdSets) {
            for (Long id : ids) {
                gone.add(String.valueOf(id));
            }
        }
        if (gone.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Placement placement : placementService.listBySiteId(siteId)) {
            if (placement.getPanelShape() != null && !placement.isArchived()
                    && referencesAny(placement.getSettings(), gone)) {
                count++;
            }
        }
        return count;
    }

    private static boolean referencesAny(@Nullable Object value, Set<String> ids) {
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(v -> referencesAny(v, ids));
        }
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(v -> referencesAny(v, ids));
        }
        if (value instanceof Number number) {
            return number.longValue() == number.doubleValue() && ids.contains(String.valueOf(number.longValue()));
        }
        return value instanceof String str && ids.contains(str);
    }

    /**
     * 卸载 ARCHIVE 的内容分支（§6.1）：已发布的本源文章归档（{@code STATUS_ARCHIVED}，来源标记保留——
     * 与构成行的归档口径一致，可追溯）；栏目/专题/区块/投票/问卷/表单/链接组定义无归档态，清来源标记转为普通操作员行。
     */
    public void archiveForUninstall(Long siteId, String themeName) {
        String origin = ThemeSeedImporter.originOf(themeName);
        for (Article article : articleMapper.listByOrigin(siteId, origin)) {
            try {
                articleService.archive(article);
            } catch (RuntimeException e) {
                logger.warn("Seed article archive failed: id={}", article.getId(), e);
            }
        }
        channelMapper.clearOrigin(siteId, origin);
        topicMapper.clearOrigin(siteId, origin);
        blockMapper.clearOrigin(siteId, origin);
        voteMapper.clearOrigin(siteId, origin);
        surveyMapper.clearOrigin(siteId, origin);
        formTypeMapper.clearOrigin(siteId, origin);
        linkGroupMapper.clearOrigin(siteId, origin);
        modelMapper.clearOrigin(siteId, origin);
        dictTypeMapper.clearOrigin(siteId, origin);
    }

    /**
     * 卸载 KEEP 的内容分支（§6.1）：清除来源标记，内容行转为普通操作员行。
     */
    public void keepForUninstall(Long siteId, String themeName) {
        String origin = ThemeSeedImporter.originOf(themeName);
        articleMapper.clearOrigin(siteId, origin);
        channelMapper.clearOrigin(siteId, origin);
        topicMapper.clearOrigin(siteId, origin);
        blockMapper.clearOrigin(siteId, origin);
        voteMapper.clearOrigin(siteId, origin);
        surveyMapper.clearOrigin(siteId, origin);
        formTypeMapper.clearOrigin(siteId, origin);
        linkGroupMapper.clearOrigin(siteId, origin);
        modelMapper.clearOrigin(siteId, origin);
        dictTypeMapper.clearOrigin(siteId, origin);
    }

    /**
     * 类型是否含 sys 条目。{@code DictTypeService.delete} 的级联不拒绝 sys 条目（{@code DictService.delete}
     * 才拒绝），含 sys 条目的类型不由移除器删除。
     */
    private boolean hasSysEntry(Long typeId) {
        return dictService.listByTypeId(typeId).stream().anyMatch(entry -> Boolean.TRUE.equals(entry.getSys()));
    }

    /**
     * 栏目可删守卫：无任何文章（干跑按「总数 − 本源数」推算；实做时本源文章已删，
     * 直接以当前计数为准）且无任何未随本次移除消失的子栏目。
     */
    private boolean channelDeletable(Channel channel, boolean dryRun,
                                     Map<Long, Integer> originArticlesByChannel, Set<Long> goneChannelIds) {
        int articleCount = articleMapper.countByChannelId(channel.getId());
        if (dryRun) {
            articleCount -= originArticlesByChannel.getOrDefault(channel.getId(), 0);
        }
        if (articleCount > 0) {
            return false;
        }
        return channelMapper.listChildren(channel.getId()).stream()
                .allMatch(child -> goneChannelIds.contains(child.getId()));
    }

    /**
     * 本源栏目的后序排列（子先父后）：父子关系按 {@code parentId} 在本源集合内推导，
     * 非本源父栏目下的本源子树按根处理。
     */
    private static List<Channel> postOrder(List<Channel> channels) {
        Map<Long, Channel> byId = new LinkedHashMap<>(channels.size());
        channels.forEach(channel -> byId.put(channel.getId(), channel));
        Map<Long, List<Channel>> childrenByParent = new LinkedHashMap<>(channels.size());
        List<Channel> roots = new ArrayList<>();
        for (Channel channel : channels) {
            Long parentId = channel.getParentId();
            if (parentId != null && byId.containsKey(parentId)) {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(channel);
            } else {
                roots.add(channel);
            }
        }
        List<Channel> ordered = new ArrayList<>(channels.size());
        for (Channel root : roots) {
            appendPostOrder(root, childrenByParent, ordered);
        }
        return ordered;
    }

    private static void appendPostOrder(Channel node, Map<Long, List<Channel>> childrenByParent,
                                        List<Channel> ordered) {
        for (Channel child : childrenByParent.getOrDefault(node.getId(), List.of())) {
            appendPostOrder(child, childrenByParent, ordered);
        }
        ordered.add(node);
    }

    private static final class Tally {
        int articlesDeleted;
        int articlesFailed;
        int channelsDeleted;
        int channelsOriginCleared;
        int topicsDeleted;
        int topicsOriginCleared;
        int blocksDeleted;
        int blocksOriginCleared;
        int votesDeleted;
        int votesOriginCleared;
        int surveysDeleted;
        int surveysOriginCleared;
        int formsDeleted;
        int formsOriginCleared;
        int linkGroupsDeleted;
        int linkGroupsOriginCleared;
        int modelsDeleted;
        int modelsOriginCleared;
        int dictsDeleted;
        int dictsOriginCleared;
        int panelRefsBroken;

        RemoveResult toResult(boolean dryRun) {
            return new RemoveResult(dryRun, articlesDeleted,
                    articlesFailed, channelsDeleted, channelsOriginCleared, topicsDeleted, topicsOriginCleared,
                    blocksDeleted, blocksOriginCleared, votesDeleted, votesOriginCleared,
                    surveysDeleted, surveysOriginCleared, formsDeleted, formsOriginCleared,
                    linkGroupsDeleted, linkGroupsOriginCleared,
                    modelsDeleted, modelsOriginCleared, dictsDeleted, dictsOriginCleared, panelRefsBroken);
        }
    }

    /**
     * 移除结果 / 干跑计数（§6.1 安全不变量 2：危险操作先出示同一算法算出的计数）
     *
     * @param dryRun                是否干跑（true = 未改任何行）
     * @param articlesDeleted       物理删除的文章数（含回收站中的本源文章）
     * @param articlesFailed        失败文章数（逐条日志，来源标记保留，重跑可续）
     * @param channelsDeleted       删除的栏目数（守卫通过：无任何文章与子栏目）
     * @param channelsOriginCleared 清除来源标记的栏目数（守卫不过：转为普通操作员行）
     * @param topicsDeleted         删除的专题数（守卫通过：无非本源文章绑定）
     * @param topicsOriginCleared   清除来源标记的专题数（守卫不过）
     * @param blocksDeleted         删除的区块定义数（守卫通过：本次处置后无任何条目，E4a / D26）
     * @param blocksOriginCleared   清除来源标记的区块定义数（守卫不过：仍有非本源文章的条目，
     *                              转为普通操作员行）
     * @param votesDeleted          删除的投票定义数（守卫通过：无选票记录，E4b / D31）
     * @param votesOriginCleared    清除来源标记的投票定义数（守卫不过：已有用户参与——times 计数或
     *                              Action 选票记录存在，转为普通操作员行）
     * @param surveysDeleted        删除的问卷定义数（守卫通过：无反馈记录，E4d / D35）
     * @param surveysOriginCleared  清除来源标记的问卷定义数（守卫不过：已有用户反馈——times 计数或
     *                              反馈记录存在，转为普通操作员行）
     * @param formsDeleted          删除的表单类型定义数（守卫通过：无用户提交，E4e / D40）
     * @param formsOriginCleared    清除来源标记的表单类型定义数（守卫不过：已有用户提交——existsByTypeId
     *                              存在，转为普通操作员行）
     * @param linkGroupsDeleted    删除的链接组数（守卫通过：分组连同链接仍为种子形——存储散列
     *                              复现当前投影，E4f / D44；链接随分组一并删除）
     * @param linkGroupsOriginCleared 清除来源标记的链接组数（守卫不过：操作员改过分组或链接，
     *                              转为普通操作员行）
     * @param modelsDeleted         删除的模型定义数（守卫通过：本次处置后无任何幸存行绑定，
     *                              model-seeding D9）
     * @param modelsOriginCleared   清除来源标记的模型定义数（守卫不过：仍被幸存栏目/专题/区块绑定，
     *                              转为普通操作员行）
     * @param dictsDeleted          删除的字典类型定义数（守卫通过：无任何站点的幸存模型引用、不含 sys 条目；
     *                              条目随定义同一事务删除，D8/D9）
     * @param dictsOriginCleared    清除来源标记的字典类型定义数（守卫不过：仍被幸存模型字段引用或含 sys 条目；
     *                              条目原样保留）
     * @param panelRefsBroken       设置里钉住了将删实体（栏目/文章/区块/投票）ID 的在用页面组件数（D24：
     *                              面板不动，只告知——移除后在健康页列为失效引用，导入演示数据对未改动的
     *                              本源面板自动修复）
     */
    public record RemoveResult(boolean dryRun, int articlesDeleted,
                               int articlesFailed, int channelsDeleted,
                               int channelsOriginCleared, int topicsDeleted, int topicsOriginCleared,
                               int blocksDeleted, int blocksOriginCleared,
                               int votesDeleted, int votesOriginCleared,
                               int surveysDeleted, int surveysOriginCleared,
                               int formsDeleted, int formsOriginCleared,
                               int linkGroupsDeleted, int linkGroupsOriginCleared,
                               int modelsDeleted, int modelsOriginCleared,
                               int dictsDeleted, int dictsOriginCleared, int panelRefsBroken) {
    }
}
