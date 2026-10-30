package com.ujcms.cms.ext.service;

import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.component.WordScreeningService.WordHit;
import com.ujcms.cms.core.component.WordScreeningService.WordType;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.GroupContribute;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.GroupContributeMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.ext.component.UgcSubmitDispatcher;
import com.ujcms.cms.ext.listener.UgcSubmitListener.UgcSubmission;
import com.ujcms.common.web.HtmlParserUtils;
import com.ujcms.common.web.Uploads;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ujcms.cms.core.domain.Article.INPUT_TYPE_CONTRIBUTE;
import static com.ujcms.cms.core.domain.Article.STATUS_DELETED;
import static com.ujcms.cms.core.domain.Article.STATUS_PENDING;
import static com.ujcms.cms.core.domain.Article.STATUS_REJECTED;

/**
 * 会员投稿 Service（docs/spec/member-contribute/plan.md）。
 * <p>
 * 投稿即写入栏目的 {@link Article}，不另建内容表：所有写入都经 {@link ArticleService}，复用正文净化、标签、附件引用、
 * 全文索引与发布监听。服务端设定全部可信字段（站点、栏目、作者、组织、录入类型、状态），客户端只提供标题、摘要、
 * 封面、正文、标签、作者与简单类型的自定义字段；归属判定为 {@code userId == member.id}，不走后台数据权限。
 * 投稿一律待审核：栏目有审批流程时进入流程，否则停在待审核、无流程实例，由“文章管理”提交（通过）或退回。
 * 前台投稿的规则对所有登录用户一视同仁：管理员从前台投稿同样记作投稿、同样受用户组限制。
 * <p>
 * 半可信输入的边界（D5、D10、D11）：正文按 {@link PolicyTier#BASIC} 净化；自定义字段只收简单类型、值只收标量或标量
 * 列表且有长度上限；标签只能用本站已有的——标签一入库就带引用数、立刻出现在公开的标签云里，不能让待审内容先于审核
 * 露面；新投稿按人按 24 小时计数。
 *
 * @author PONY
 */
@Service
public class ContributeService {
    /**
     * 可投稿的自定义字段类型：简单类型。媒体上传、富文本、Markdown、级联字段不向投稿者开放——既不渲染也不接受
     */
    public static final Set<String> SIMPLE_FIELD_TYPES = Set.of(Model.TYPE_TEXT, Model.TYPE_TEXTAREA,
            Model.TYPE_NUMBER, Model.TYPE_SLIDER, Model.TYPE_DATE, Model.TYPE_COLOR, Model.TYPE_SWITCH,
            Model.TYPE_RADIO, Model.TYPE_CHECKBOX, Model.TYPE_SELECT, Model.TYPE_MULTIPLE_SELECT);
    /**
     * 自定义字段单个字符串值的长度上限。模型字段没有长度配置；正文另有 200000 的上限，这里给多行文本留足、不留洞
     */
    public static final int CUSTOM_VALUE_MAX_LENGTH = 10_000;
    /**
     * 自定义字段列表值（多选、复选）的元素数上限
     */
    public static final int CUSTOM_LIST_MAX_SIZE = 100;
    /**
     * 投稿配额的计数窗口（D10）。与 {@code UploadController.MEMBER_UPLOAD_QUOTA_WINDOW} 同窗
     */
    public static final Duration QUOTA_WINDOW = Duration.ofHours(24);

    private final ArticleService articleService;
    private final ChannelService channelService;
    private final AttachmentService attachmentService;
    private final ConfigService configService;
    private final TagService tagService;
    private final GroupContributeMapper groupContributeMapper;
    private final HtmlSanitizeService sanitizeService;
    private final WordScreeningService wordScreeningService;
    private final UgcSubmitDispatcher ugcSubmitDispatcher;

    public ContributeService(ArticleService articleService, ChannelService channelService,
                             AttachmentService attachmentService, ConfigService configService, TagService tagService,
                             GroupContributeMapper groupContributeMapper, HtmlSanitizeService sanitizeService,
                             WordScreeningService wordScreeningService, UgcSubmitDispatcher ugcSubmitDispatcher) {
        this.articleService = articleService;
        this.channelService = channelService;
        this.attachmentService = attachmentService;
        this.configService = configService;
        this.tagService = tagService;
        this.groupContributeMapper = groupContributeMapper;
        this.sanitizeService = sanitizeService;
        this.wordScreeningService = wordScreeningService;
        this.ugcSubmitDispatcher = ugcSubmitDispatcher;
    }

    /**
     * 投稿内容：客户端可提供的全部字段
     *
     * @param customs 自定义字段值；只有简单类型的字段被采用，{@code xKey} 伴生键一并采用
     */
    public record Content(String title, @Nullable String seoDescription, @Nullable String author,
                          @Nullable String image, @Nullable String text, @Nullable List<String> tagNames,
                          Map<String, Object> customs) {
    }

    /**
     * 站点是否开放投稿
     */
    public boolean isOpen(Site site) {
        return site.getContribute().isEnabled();
    }

    /**
     * 当前用户在本站可投稿的栏目，按栏目树顺序。站点未开放投稿时为空。整站的栏目 × 用户组矩阵一次装入，不逐栏目查询
     */
    public List<Channel> listChannels(Site site, User user) {
        if (!isOpen(site)) {
            return Collections.emptyList();
        }
        Map<Long, Set<Long>> groupsByChannel = groupContributeMapper.listBySiteId(site.getId()).stream()
                .collect(Collectors.groupingBy(GroupContribute::getChannelId,
                        Collectors.mapping(GroupContribute::getGroupId, Collectors.toSet())));
        Long groupId = user.getGroupId();
        return channelService.selectList(ChannelArgs.of().siteId(site.getId())).stream()
                .filter(channel -> accepts(channel)
                        && groupAllowed(groupsByChannel.getOrDefault(channel.getId(), Set.of()), groupId))
                .toList();
    }

    /**
     * 用户能否向该栏目投稿：站点开放、栏目属于本站且允许投稿、是文章栏目、用户组在栏目的投稿用户组之内（未设则全部）
     */
    public boolean canContribute(Site site, User user, Channel channel) {
        return isOpen(site) && site.getId().equals(channel.getSiteId()) && accepts(channel)
                && groupAllowed(new HashSet<>(groupContributeMapper.listGroupByChannelId(channel.getId())),
                user.getGroupId());
    }

    private static boolean accepts(Channel channel) {
        return Boolean.TRUE.equals(channel.getAllowContribute()) && channel.getType() == Channel.TYPE_NORMAL;
    }

    private static boolean groupAllowed(Set<Long> groupIds, @Nullable Long groupId) {
        return groupIds.isEmpty() || (groupId != null && groupIds.contains(groupId));
    }

    /**
     * 文章模型里向投稿者开放的自定义字段（简单类型），供投稿表单渲染
     */
    public List<Model.Field> simpleFields(Model model) {
        return model.getFieldList().stream().filter(field -> SIMPLE_FIELD_TYPES.contains(field.getType())).toList();
    }

    /**
     * 投稿当前是否可由作者修改、删除：已退回，或待审核而未进入审批流程。已发布、已下线、审核中等状态归编辑处置
     */
    public static boolean isEditable(Article article) {
        short status = article.getStatus();
        return status == STATUS_REJECTED || (status == STATUS_PENDING && article.getProcessInstanceId() == null);
    }

    /**
     * 新投稿。服务端设定全部可信字段；按人按 24 小时的投稿配额在此把关；落库后提交审核（栏目有流程则进入流程）
     */
    @Transactional(rollbackFor = Exception.class)
    public Article submit(Site site, User user, Channel channel, Content content) {
        requireQuota(site, user);
        Article article = new Article();
        article.setSiteId(site.getId());
        article.setChannelId(channel.getId());
        article.setChannel(channel);
        // 栏目无组织字段，投稿归属站点所属组织，便于后台按组织数据范围审核
        article.setOrgId(site.getOrgId());
        article.setType(Article.TYPE_NORMAL);
        article.setInputType(INPUT_TYPE_CONTRIBUTE);
        article.setStatus(STATUS_PENDING);
        article.setEditorType(Article.EDITOR_TYPE_HTML);
        article.setPublishDate(OffsetDateTime.now());
        Screening screening = apply(article, site, user, channel, content);
        List<String> tagNames = validateTags(site, content);
        articleService.insert(article, user.getId(), tagNames);
        articleService.submitForReview(article, user.getId());
        dispatch(article, user, screening);
        return article;
    }

    /**
     * 修改本人投稿并重新提交审核。先锁行再校验（D8）：归属不符返回 404（不泄露存在性）；状态不可修改返回 400；
     * 栏目不可改，且栏目须仍接受该用户投稿（关掉的栏目不再收重提，403）
     */
    @Transactional(rollbackFor = Exception.class)
    public Article update(Site site, User user, Long id, Content content) {
        articleService.lockArticle(id);
        Article article = requireOwn(site, user, id);
        if (!isEditable(article)) {
            throw new Http400Exception("error.contribute.notEditable");
        }
        Channel channel = article.getChannel();
        if (!canContribute(site, user, channel)) {
            throw new Http403Exception("error.contribute.channelNotAllowed");
        }
        Screening screening = apply(article, site, user, channel, content);
        List<String> tagNames = validateTags(site, content);
        articleService.update(article, user.getId(), tagNames);
        articleService.submitForReview(article, user.getId());
        dispatch(article, user, screening);
        return article;
    }

    /**
     * 删除本人投稿：只限可修改的状态，先锁行再校验（D8）。进回收站（已删除）而不是彻底删除：行留着，按创建时间计数的
     * 投稿配额（D10）才算得住——删了再投绕不过去；编辑在“文章管理 › 已删除”看得到撤回的稿，彻底删除或还原由编辑决定。
     * 会员侧视同不存在：列表不列、详情 404
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Site site, User user, Long id) {
        articleService.lockArticle(id);
        Article article = requireOwn(site, user, id);
        if (!isEditable(article)) {
            throw new Http400Exception("error.contribute.notDeletable");
        }
        articleService.delete(article, user);
    }

    /**
     * 本人在本站的投稿，含全部状态，按 ID 倒序（最新在前）
     */
    public List<Article> myList(Site site, User user, @Nullable Short status, int offset, int limit) {
        List<Article> list = articleService.selectList(myArgs(site, user, status), offset, limit);
        articleService.attachListRelations(list);
        return list;
    }

    public com.github.pagehelper.Page<Article> myPage(Site site, User user, @Nullable Short status,
                                                      int page, int pageSize) {
        com.github.pagehelper.Page<Article> paged = articleService.selectPage(myArgs(site, user, status),
                page, pageSize);
        articleService.attachListRelations(paged);
        return paged;
    }

    /**
     * 本人投稿；非本人、非投稿、非本站、已进回收站的一律 {@code null}（控制器转 404，不泄露存在性）
     */
    @Nullable
    public Article getOwn(Site site, User user, Long id) {
        Article article = articleService.select(id);
        if (article == null || !user.getId().equals(article.getUserId())
                || article.getInputType() != INPUT_TYPE_CONTRIBUTE || !site.getId().equals(article.getSiteId())
                || article.getStatus() == STATUS_DELETED) {
            return null;
        }
        return article;
    }

    private Article requireOwn(Site site, User user, Long id) {
        Article article = getOwn(site, user, id);
        if (article == null) {
            throw new Http404Exception(Article.NOT_FOUND + id);
        }
        return article;
    }

    private static ArticleArgs myArgs(Site site, User user, @Nullable Short status) {
        ArticleArgs args = ArticleArgs.of().siteId(site.getId()).userId(user.getId())
                .inputType(INPUT_TYPE_CONTRIBUTE);
        if (status != null) {
            args.status(Collections.singleton(status));
        }
        // 会员删除的投稿进了回收站：会员侧不再列出（编辑在控制台处置）
        args.getQueryMap().put("NotIn_status_Short", List.of(STATUS_DELETED));
        args.getQueryMap().put("OrderBy", "id_desc");
        return args;
    }

    /**
     * 投稿配额（D10）：每人 24 小时内在本站的新投稿数达到站点上限时拒绝。修改重提不计；会员删除的（回收站）照常计入；0 为不限
     */
    private void requireQuota(Site site, User user) {
        int dailyLimit = site.getContribute().getDailyLimit();
        if (dailyLimit <= 0) {
            return;
        }
        ArticleArgs args = ArticleArgs.of().siteId(site.getId()).userId(user.getId()).inputType(INPUT_TYPE_CONTRIBUTE)
                .geCreated(OffsetDateTime.now().minus(QUOTA_WINDOW));
        if (articleService.count(args) >= dailyLimit) {
            throw new Http400Exception("error.contribute.dailyLimit", String.valueOf(dailyLimit));
        }
    }

    /**
     * 标签（D11）：去空、去重，且每个都须是本站已有的标签。新标签由编辑在审核时添加——标签一入库就计入引用数，
     * 立刻出现在公开的标签云和标签页里，不能让待审内容先于审核露面
     */
    private List<String> validateTags(Site site, Content content) {
        List<String> posted = content.tagNames();
        if (posted == null || posted.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String raw : posted) {
            String name = StringUtils.trimToNull(raw);
            if (name != null) {
                names.add(name);
            }
        }
        for (String name : names) {
            if (tagService.selectByName(site.getId(), name) == null) {
                throw new Http400Exception("error.contribute.tagUnknown", name);
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * 词库筛查结果：筛查所用的纯文本与敏感词命中
     */
    private record Screening(String text, List<WordHit> dictHits) {
    }

    /**
     * 把投稿内容按白名单写到文章上：标题、摘要、作者、封面、正文（半可信净化）、简单类型自定义字段。
     * 状态、栏目、站点、组织、录入类型从不采自客户端
     */
    private Screening apply(Article article, Site site, User user, Channel channel, Content content) {
        article.setTitle(StringUtils.trimToEmpty(content.title()));
        article.setSeoDescription(StringUtils.trimToNull(content.seoDescription()));
        article.setAuthor(StringUtils.trimToNull(content.author()));
        article.setImage(validateImage(site, user, content.image(), article.getImage()));
        // 半可信投稿的净化层级：基本格式、链接、图片，不留内联样式与 iframe；ArticleService 随后的 RICH 净化不会再放宽
        String text = sanitizeService.sanitize(content.text(), PolicyTier.BASIC);
        article.setText(text);
        article.setMarkdown(null);
        article.setCustoms(mergeCustoms(channel.getArticleModel(), article.getCustoms(), content.customs()));
        // 免费词库门禁（docs/spec/ai/screening.md）：投稿一律待审核，命中只记下交给审核流水线，不报错也不替换
        String plain = article.getTitle() + "\n" + HtmlParserUtils.getText(text);
        List<WordHit> dictHits = wordScreeningService.screen(plain).stream()
                .filter(hit -> hit.type() == WordType.SENSITIVE).toList();
        return new Screening(plain, dictHits);
    }

    /**
     * 自定义字段：只采用简单类型字段（及其 Key 伴生键），其余保留库中现值（编辑在控制台设过的上传类字段不被会员的保存抹掉）；
     * 值只收标量或标量列表且有长度上限（JSON 请求体不受正文长度限制约束，不能让任意形状、任意大小的值进库）；
     * 必填的简单字段缺值时拒绝——后台从不校验必填，这里的校验是投稿接口的
     */
    private Map<String, Object> mergeCustoms(Model model, Map<String, Object> current,
                                             @Nullable Map<String, Object> posted) {
        Map<String, Object> merged = new HashMap<>(current);
        Map<String, Object> source = posted != null ? posted : Collections.emptyMap();
        for (Model.Field field : simpleFields(model)) {
            String code = field.getCode();
            Object value = source.get(code);
            Object key = source.get(code + Model.KEY_SUFFIX);
            if (!isAcceptableValue(value) || !isAcceptableValue(key)) {
                throw new Http400Exception("error.contribute.fieldInvalid", field.getName());
            }
            if (field.isRequired() && isBlank(value)) {
                throw new Http400Exception("error.contribute.fieldRequired", field.getName());
            }
            merged.put(code, value);
            merged.put(code + Model.KEY_SUFFIX, key);
        }
        return merged;
    }

    /**
     * 可接受的自定义字段值：空、标量（有长度上限的字符串、数字、布尔），或不超过上限个标量的列表
     */
    static boolean isAcceptableValue(@Nullable Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size() <= CUSTOM_LIST_MAX_SIZE && collection.stream()
                    .allMatch(item -> item != null && !(item instanceof Collection) && isAcceptableValue(item));
        }
        return isAcceptableScalar(value);
    }

    private static boolean isAcceptableScalar(@Nullable Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return true;
        }
        return value instanceof CharSequence sequence && sequence.length() <= CUSTOM_VALUE_MAX_LENGTH;
    }

    private static boolean isBlank(@Nullable Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence sequence) {
            return StringUtils.isBlank(sequence);
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    /**
     * 封面：须是本人上传的图片附件（docs/spec/membership/comment-image.md 的归属规则）。与库中现值相同的不再校验，
     * 编辑在控制台替换过的封面不会卡住会员的保存
     */
    @Nullable
    private String validateImage(Site site, User user, @Nullable String url, @Nullable String current) {
        String image = StringUtils.trimToNull(url);
        if (image == null || image.equals(current)) {
            return image;
        }
        if (!site.getContribute().isImageEnabled()) {
            throw new Http400Exception("error.contribute.imageInvalid", image);
        }
        Attachment attachment = attachmentService.findByUrl(image);
        if (attachment == null || !user.getId().equals(attachment.getUserId())) {
            throw new Http400Exception("error.contribute.imageInvalid", image);
        }
        String imageTypes = configService.getUnique().getUpload().getImageTypes();
        if (!Uploads.isValidType(imageTypes, FilenameUtils.getExtension(attachment.getPath()))) {
            throw new Http400Exception("error.contribute.imageInvalid", image);
        }
        return image;
    }

    private void dispatch(Article article, User user, Screening screening) {
        ugcSubmitDispatcher.dispatch(new UgcSubmission(UgcSubmission.TYPE_ARTICLE, article.getId(),
                article.getSiteId(), user.getId(), screening.text(), screening.dictHits(), article.getStatus()));
    }
}
