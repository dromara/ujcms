package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.domain.Link;
import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.cms.ext.domain.Survey;
import com.ujcms.cms.ext.domain.SurveyItem;
import com.ujcms.cms.ext.domain.SurveyOption;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.domain.VoteOption;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 种子来源散列（theme-marketplace R4：唯一的脏检测机制）。{@code origin_hash_} = 导入时刻
 * 种子形投影的规范化 JSON 的 SHA-256（十六进制小写）——某行「未被操作员改动」当且仅当
 * 以当前行重导出同一投影仍复现该散列。以此区分「操作员改过」与「新版主题的种子变了」：
 * 旧实现对比现有行与<b>新</b>种子，两种情形混为一谈。
 *
 * <p><b>规范化</b>：对象键按字典序排序（递归）、紧凑序列化、UTF-8。数值/布尔按 Jackson 树模型
 * 序列化——导入时与自库行重解析时同为 {@code readTree}/{@code valueToTree} 路径，形态一致。
 * 跨版本稳定性由 {@code SeedHashTest} 钉死（散列变化会把全部存量行误判为「操作员已改」）。
 *
 * <p><b>投影口径</b>（各表的种子形投影，均可自当前行重导出）：
 * <ul>
 *   <li>面板投放：{@code {pageRef, slotName, panelShape, settings, enabled}}
 *       （settings 为令牌展开后的设置值——行里存的就是展开值；Phase D 起投影不含退役的
 *       sourceKind/source/sectionData；ssr-reduction P6 起不含退役的 track——投影键集变化即散列重置，
 *       存量行按「操作员已改」保守处理）；</li>
 *   <li>区域：占位面板ID的结构树（行布局键 + 每列面板数——面板内容各自有散列，树里的真实投放ID
 *       是每次导入随机分配的雪花ID，不入散列）。</li>
 * </ul>
 *
 * @author PONY
 */
public final class SeedHash {

    private SeedHash() {
    }

    /**
     * 面板投放的种子形投影散列（自实体重导出；调用前须已填充 settings——即 {@code disassemble} 前或
     * {@code getSettings()} 可用时）。设置块（component-settings §4.3）仅在非空时计入；
     * 显示设备（design-review P2-5）仅在非缺省（{@code all}）时计入——投影键集对存量行不变，
     * 特性上线前落库的 origin_hash_ 依旧复现（不误判「操作员已改」）。
     */
    public static String placementHash(Placement placement) {
        return sha256Canonical(placementProjection(placement));
    }

    /**
     * 面板投放的种子形投影（theme-seed-export D1：投影即导出序列化的唯一口径——导出器与散列共用，
     * 绝不出现第二份投影代码）。
     */
    public static TreeMap<String, Object> placementProjection(Placement placement) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("pageRef", placement.getPageRef());
        projection.put("slotName", placement.getSlotName());
        projection.put("panelShape", placement.getPanelShape());
        if (!placement.getSettings().isEmpty()) {
            projection.put("settings", placement.getSettings());
        }
        projection.put("enabled", placement.getEnabled());
        if (!Placement.VISIBILITY_ALL.equals(placement.getVisibility())) {
            projection.put("visibility", placement.getVisibility());
        }
        return projection;
    }

    /**
     * 栏目的种子形投影散列（内容种子，theme-marketplace §5.3；自实体重导出）。
     * 口径为种子可设置且可自当前行重导出的内容字段；有意排除：模型引用（行存ID、种子存名称，
     * 无法稳定重导出）、树位置 parent/order（结构由树服务管理）、processKey/权限（不可种子化）。
     *
     * <p>D45 起链接型栏目可种子化：{@code linkUrl} 仅非空时入投影、{@code targetBlank} 仅为真时入投影
     * （条件入键——存量行键集不变，散列不重置；导出发射与投影同谓词）。
     */
    public static String channelHash(Channel channel) {
        return sha256Canonical(channelProjection(channel));
    }

    /**
     * 栏目的种子形投影（导出序列化与散列共用，theme-seed-export D1）。
     */
    public static TreeMap<String, Object> channelProjection(Channel channel) {
        TreeMap<String, Object> projection = new TreeMap<>();
        // 投影键沿用种子语法的自然键名 alias（实体字段已更名 slug，permalinks D12）：此键参与 origin_hash_ 散列，
        // 改名会翻转全部既有站点的栏目/专题脏检测，且使 13.0 主题包失效
        projection.put("alias", channel.getSlug());
        projection.put("name", channel.getName());
        projection.put("nav", channel.getNav());
        projection.put("type", channel.getType());
        projection.put("pageSize", channel.getPageSize());
        projection.put("image", channel.getImage());
        projection.put("seoTitle", channel.getSeoTitle());
        projection.put("seoKeywords", channel.getSeoKeywords());
        projection.put("seoDescription", channel.getSeoDescription());
        projection.put("text", channel.getText());
        projection.put("customs", customsOf(channel.getCustoms()));
        if (StringUtils.isNotBlank(channel.getLinkUrl())) {
            projection.put("linkUrl", channel.getLinkUrl());
        }
        if (Boolean.TRUE.equals(channel.getTargetBlank())) {
            projection.put("targetBlank", Boolean.TRUE);
        }
        return projection;
    }

    /**
     * 文章的种子形投影散列（自实体重导出；text/customs 须为服务层保存后的最终状态——清洗在保存内发生）。
     * 标签以名称列表入散列（导入时即种子声明的列表）；有意排除：status / publishDate
     * （生命周期与导入时刻相对值，非内容）、栏目引用（行存ID、种子存别名）。
     *
     * <p>A6 起投影扩展媒体扩展字段（video/file/doc/imageList 等，随种子文法扩展）——投影键集变化
     * 即散列重置：A6 前落库的 origin_hash_ 一律不再复现（存量行按「操作员已改」保守处理，宁可少报）。
     * D45 起附件列表 {@code fileList} 仅非空时入投影（条件入键，不重置存量散列）。
     */
    public static String articleHash(Article article, List<String> tagNames) {
        return sha256Canonical(articleProjection(article, tagNames));
    }

    /**
     * 文章的种子形投影（导出序列化与散列共用，theme-seed-export D1）。
     */
    public static TreeMap<String, Object> articleProjection(Article article, List<String> tagNames) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("title", article.getTitle());
        projection.put("subtitle", article.getSubtitle());
        projection.put("image", article.getImage());
        projection.put("text", article.getText());
        projection.put("author", article.getAuthor());
        projection.put("editor", article.getEditor());
        projection.put("source", article.getSource());
        projection.put("seoKeywords", article.getSeoKeywords());
        projection.put("seoDescription", article.getSeoDescription());
        projection.put("video", article.getVideo());
        projection.put("videoDuration", article.getVideoDuration());
        projection.put("file", article.getFile());
        projection.put("fileName", article.getFileName());
        projection.put("fileLength", article.getFileLength());
        projection.put("doc", article.getDoc());
        projection.put("docName", article.getDocName());
        projection.put("docLength", article.getDocLength());
        // 图片集归一：未设置与空列表同形（getImageList 对空 JSON 列返回空列表）
        projection.put("imageList", article.getImageList());
        if (!article.getFileList().isEmpty()) {
            projection.put("fileList", article.getFileList());
        }
        projection.put("tags", tagNames);
        projection.put("customs", customsOf(article.getCustoms()));
        return projection;
    }

    /**
     * 专题的种子形投影散列（自实体重导出）。有意排除：文章关联（存于关联表，导入仅在新插入时绑定，
     * 之后属操作员编排）、树位置。
     */
    public static String topicHash(Topic topic) {
        return sha256Canonical(topicProjection(topic));
    }

    /**
     * 专题的种子形投影（导出序列化与散列共用，theme-seed-export D1）。
     */
    public static TreeMap<String, Object> topicProjection(Topic topic) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("alias", topic.getSlug());
        projection.put("title", topic.getTitle());
        projection.put("image", topic.getImage());
        projection.put("text", topic.getText());
        projection.put("seoTitle", topic.getSeoTitle());
        projection.put("seoKeywords", topic.getSeoKeywords());
        projection.put("seoDescription", topic.getSeoDescription());
        projection.put("customs", customsOf(topic.getCustoms()));
        return projection;
    }

    /**
     * 区块定义的种子形投影散列（theme-seed-export E4a / D25；自实体重导出）。有意排除：
     * 模型引用（行存ID、种子存名称，同栏目口径）、scope / siteId（种子定义恒为本站私有，D23）、
     * order（操作员排列）。
     */
    public static String blockHash(Block block) {
        return sha256Canonical(blockProjection(block));
    }

    /**
     * 区块定义的种子形投影（导出序列化与散列共用，D1）。
     */
    public static TreeMap<String, Object> blockProjection(Block block) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("alias", block.getAlias());
        projection.put("name", block.getName());
        projection.put("category", block.getCategory());
        projection.put("title", block.getTitle());
        projection.put("moreUrl", block.getMoreUrl());
        projection.put("recommendable", block.getRecommendable());
        projection.put("enabled", block.getEnabled());
        return projection;
    }

    /**
     * 投票定义的种子形投影散列（theme-seed-export E4b / D29；自实体重导出）。有意排除：
     * times / 选项 count（运行期计票，§7.2）、created / order（操作员/运行期状态）、
     * startDate / endDate（投票窗口属运营状态——跨站安装不得携带过期档期）。
     * 选项以标题列表入散列（选项即定义的一部分，随定义出入）。
     */
    public static String voteHash(Vote vote) {
        return sha256Canonical(voteProjection(vote));
    }

    /**
     * 投票定义的种子形投影（导出序列化与散列共用，D1）。
     */
    public static TreeMap<String, Object> voteProjection(Vote vote) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("title", vote.getTitle());
        projection.put("description", vote.getDescription());
        projection.put("interval", vote.getInterval());
        projection.put("mode", vote.getMode());
        projection.put("multiple", vote.getMultiple());
        projection.put("enabled", vote.getEnabled());
        List<String> options = new ArrayList<>(vote.getOptions().size());
        for (VoteOption option : vote.getOptions()) {
            options.add(option.getTitle());
        }
        projection.put("options", options);
        return projection;
    }

    /**
     * 问卷定义的种子形投影散列（theme-seed-export E4d / D35；自实体重导出）。有意排除：
     * times / 选项 count / 三张反馈表（运行期与用户数据，§7.2）、created / order、
     * startDate / endDate（问卷窗口属运营状态，D29 口径）。条目（标题/多选/问答 + 选项标题
     * 列表）随定义入散列。
     */
    public static String surveyHash(Survey survey) {
        return sha256Canonical(surveyProjection(survey));
    }

    /**
     * 问卷定义的种子形投影（导出序列化与散列共用，D1）。
     */
    public static TreeMap<String, Object> surveyProjection(Survey survey) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("title", survey.getTitle());
        projection.put("description", survey.getDescription());
        projection.put("mode", survey.getMode());
        projection.put("interval", survey.getInterval());
        projection.put("enabled", survey.getEnabled());
        List<Object> items = new ArrayList<>(survey.getItems().size());
        for (SurveyItem item : survey.getItems()) {
            TreeMap<String, Object> node = new TreeMap<>();
            node.put("title", item.getTitle());
            node.put("multiple", item.getMultiple());
            node.put("essay", item.getEssay());
            List<String> options = new ArrayList<>(item.getOptions().size());
            for (SurveyOption option : item.getOptions()) {
                options.add(option.getTitle());
            }
            node.put("options", options);
            items.add(node);
        }
        projection.put("items", items);
        return projection;
    }

    /**
     * 表单类型定义的种子形投影散列（theme-seed-export E4e / D38；自实体重导出）。有意排除：
     * processKey（流程绑定属站点运营状态，§7.2——导入的表单在操作员重新绑定前无审批运行）、
     * order（操作员编排）、提交（{@code ujcms_form} 用户数据，§7.2 永久排除）。绑定模型以<b>名称</b>
     * 入投影（栏目约定——模型即表单的本体；按名迟绑，model-seeding D4）。
     *
     * @param formType  表单类型行
     * @param modelName 绑定模型的名称（{@code null} → 投影无 model 值，与导出端同形）
     */
    public static String formTypeHash(FormType formType, @org.jspecify.annotations.Nullable String modelName) {
        return sha256Canonical(formTypeProjection(formType, modelName));
    }

    /**
     * 表单类型定义的种子形投影（导出序列化与散列共用，D1）。
     *
     * @param formType  表单类型行
     * @param modelName 绑定模型的名称（按 id 解析所得；导入反向按名解析回 id，散列自洽）
     */
    public static TreeMap<String, Object> formTypeProjection(FormType formType,
                                                             @org.jspecify.annotations.Nullable String modelName) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("name", formType.getName());
        projection.put("model", modelName);
        projection.put("listTemplate", formType.getListTemplate());
        projection.put("itemTemplate", formType.getItemTemplate());
        projection.put("mode", formType.getMode());
        projection.put("viewable", formType.getViewable());
        projection.put("enabled", formType.getEnabled());
        return projection;
    }

    /**
     * 链接组的种子形投影散列（theme-seed-export E4f / D43；自实体重导出）。有意排除：
     * 两级 {@code order}（操作员排列——发射序即可移植排序，导入顺序插入自然复现）、
     * siteId（链接组恒为本站私有）。链接行随定义入散列（选项式，D29 口径）：logo 以行存值
     * （展开后 URL）入散列——发射时另行令牌化（D9 口径：散列恒为展开后投影）。
     *
     * @param group 链接组行
     * @param links 组下链接（按 {@code order_, id_} 升序）
     */
    public static String linkGroupHash(LinkGroup group, List<Link> links) {
        return sha256Canonical(linkGroupProjection(group, links));
    }

    /**
     * 链接组的种子形投影（导出序列化与散列共用，D1）。链接行三选一以可移植形入投影
     * （link-groups D6）：url 取原始列值（渲染派生值不入散列）；栏目引用取别名、文章引用取
     * originId（要求装配 association——Mapper 载入与导入器构造均满足；文章无来源三元组时
     * 以字符串化 id 兜底，两侧确定性一致，仅入散列不入发射）。
     */
    public static TreeMap<String, Object> linkGroupProjection(LinkGroup group, List<Link> links) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("alias", group.getAlias());
        projection.put("name", group.getName());
        projection.put("type", group.getType());
        projection.put("imageWidth", group.getImageWidth());
        projection.put("imageHeight", group.getImageHeight());
        projection.put("description", group.getDescription());
        projection.put("slot", group.getSlot());
        List<Object> linkNodes = new ArrayList<>(links.size());
        for (Link link : links) {
            TreeMap<String, Object> node = new TreeMap<>();
            node.put("name", link.getName());
            if (link.getChannelId() != null) {
                node.put("channel", link.getChannel() != null
                        ? link.getChannel().getSlug() : String.valueOf(link.getChannelId()));
            } else if (link.getArticleId() != null) {
                Article referArticle = link.getArticle();
                String originId = referArticle != null ? referArticle.getOriginId() : null;
                node.put("article", originId != null ? originId : String.valueOf(link.getArticleId()));
            } else {
                node.put("url", link.getLinkUrl());
            }
            node.put("logo", link.getLogo());
            node.put("description", link.getDescription());
            node.put("targetBlank", link.getTargetBlank());
            node.put("enabled", link.getEnabled());
            linkNodes.add(node);
        }
        projection.put("links", linkNodes);
        return projection;
    }

    /**
     * 模型定义的种子形投影散列（model-seeding D3/D11）。
     */
    public static String modelHash(Model model, Map<Long, String> dictAliasById) {
        return sha256Canonical(modelProjection(model, dictAliasById));
    }

    /**
     * 模型定义的种子形投影（导出序列化与散列共用，D1）：{@code {type, name, mains, asides, customs}}，
     * 三个字段集列以<b>解析后的 JSON 树</b>入投影（存储串的格式差异不入散列；数组序保留——编辑器
     * 布局有语义，{@code canonicalTree} 只排对象键）。树内一切 {@code dictTypeId} 键的数字取值改写为
     * 字典类型<b>别名</b>（导入反向写回站内ID，散列自洽——D11）；解析不到可见字典类型的悬挂ID
     * 从投影删除（投影即发射：导出端同步丢弃 + 告警）。{@code scope}/{@code order}/{@code siteId}
     * 有意排除（操作员/站点状态，D3）。
     *
     * @param model         模型行
     * @param dictAliasById 站点可见字典类型的 ID → 别名映射（D8）
     */
    public static TreeMap<String, Object> modelProjection(Model model, Map<Long, String> dictAliasById) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("type", model.getType());
        projection.put("name", model.getName());
        projection.put("mains", defTree(model.getMains(), dictAliasById));
        projection.put("asides", defTree(model.getAsides(), dictAliasById));
        projection.put("customs", defTree(model.getCustoms(), dictAliasById));
        return projection;
    }

    /**
     * 字段集 JSON 列 → 投影树（null/空白列 → JSON null；坏 JSON 快速失败——控制台编辑器同样无法
     * 处理这样的行）。
     */
    @org.jspecify.annotations.Nullable
    private static JsonNode defTree(@org.jspecify.annotations.Nullable String json,
                                    Map<Long, String> dictAliasById) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode tree;
        try {
            tree = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("Malformed model field-def JSON", e);
        }
        return rewriteDictBindings(tree.deepCopy(), dictAliasById);
    }

    /**
     * 树内一切 {@code dictTypeId} 键的字典绑定：数字取值 → 可见字典类型别名；悬挂ID删键；
     * 非数字取值（已是别名形）原样保留。键驱动而非字段类型驱动（model-seeding D4）。
     */
    private static JsonNode rewriteDictBindings(JsonNode node, Map<Long, String> dictAliasById) {
        if (node instanceof ObjectNode objectNode) {
            JsonNode binding = objectNode.get("dictTypeId");
            if (binding != null && (binding.isTextual() || binding.isNumber())) {
                String text = binding.asText();
                if (text.chars().allMatch(Character::isDigit) && !text.isEmpty()) {
                    String alias = dictAliasById.get(Long.parseLong(text));
                    if (alias != null) {
                        objectNode.put("dictTypeId", alias);
                    } else {
                        objectNode.remove("dictTypeId");
                    }
                }
            }
            objectNode.properties().forEach(entry -> rewriteDictBindings(entry.getValue(), dictAliasById));
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(element -> rewriteDictBindings(element, dictAliasById));
        }
        return node;
    }

    /**
     * 字段集 JSON 里的 dictTypeId 数字取值收集（键驱动，D4；导出闭包与移除守卫共用）。
     * 解析失败按无绑定处理——投影侧对坏 JSON 快速失败，此处不重复抛。
     */
    static void collectDictBindings(@org.jspecify.annotations.Nullable String json, java.util.Set<Long> out) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            collectDictBindings(MAPPER.readTree(json), out);
        } catch (JacksonException e) {
            // 投影/散列侧对坏 JSON 快速失败；此处静默即可（同一行必先经过投影）
        }
    }

    private static void collectDictBindings(JsonNode node, java.util.Set<Long> out) {
        if (node instanceof ObjectNode objectNode) {
            JsonNode binding = objectNode.get("dictTypeId");
            if (binding != null && (binding.isTextual() || binding.isNumber())) {
                String text = binding.asText();
                if (!text.isEmpty() && text.chars().allMatch(Character::isDigit)) {
                    out.add(Long.parseLong(text));
                }
            }
            objectNode.properties().forEach(entry -> collectDictBindings(entry.getValue(), out));
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(element -> collectDictBindings(element, out));
        }
    }

    /**
     * 字典类型定义的种子形投影散列（model-seeding D8；条目随类型——投票选项同法）。
     */
    public static String dictTypeHash(DictType dictType, List<Dict> entries) {
        return sha256Canonical(dictTypeProjection(dictType, entries));
    }

    /**
     * 字典类型定义的种子形投影（导出序列化与散列共用，D1）：{@code {alias, name, remark, dataType}} +
     * {@code entries} 平铺列表 {@code {name, value, remark, enabled}}。
     * 条目按 {@code (order, id)} 排序（导入按种子序写 order——数组序即定义序）；
     * {@code scope}/{@code order}/{@code sys}/{@code siteId} 有意排除（操作员/系统状态，D8）。
     *
     * @param dictType 字典类型行
     * @param entries  该类型的全部条目（任意顺序）
     */
    public static TreeMap<String, Object> dictTypeProjection(DictType dictType, List<Dict> entries) {
        TreeMap<String, Object> projection = new TreeMap<>();
        projection.put("alias", dictType.getAlias());
        projection.put("name", dictType.getName());
        projection.put("remark", dictType.getRemark());
        projection.put("dataType", dictType.getDataType());
        List<Dict> sorted = new ArrayList<>(entries);
        sorted.sort(java.util.Comparator.comparing(Dict::getOrder).thenComparing(Dict::getId));
        List<Object> nodes = new ArrayList<>(sorted.size());
        for (Dict entry : sorted) {
            TreeMap<String, Object> node = new TreeMap<>();
            node.put("name", entry.getName());
            node.put("value", entry.getValue());
            node.put("remark", entry.getRemark());
            node.put("enabled", entry.getEnabled());
            nodes.add(node);
        }
        projection.put("entries", nodes);
        return projection;
    }

    /**
     * customs 归一：null 与空映射同形（种子未声明 customs 与模型无自定义字段的行等价）。
     */
    private static Map<String, Object> customsOf(@org.jspecify.annotations.Nullable Map<String, Object> customs) {
        return customs == null ? Map.of() : customs;
    }

    /**
     * 区域结构树的种子形投影散列：行ID + 行布局键 + 每列面板数（面板ID以占位符 {@code _} 归一）。
     */
    public static String regionHash(RegionLayout layout) {
        List<Object> rows = new ArrayList<>(layout.rows().size());
        for (RegionLayout.Row row : layout.rows()) {
            TreeMap<String, Object> rowMap = new TreeMap<>();
            rowMap.put("id", row.id());
            rowMap.put("layout", row.layout());
            List<Integer> columns = new ArrayList<>(row.columns().size());
            for (RegionLayout.Column column : row.columns()) {
                columns.add(column.panels().size());
            }
            rowMap.put("columns", columns);
            rows.add(rowMap);
        }
        return sha256Canonical(rows);
    }

    /**
     * 任意值的规范化 JSON SHA-256（对象键递归排序 + 紧凑序列化 + UTF-8）。
     */
    public static String sha256Canonical(Object value) {
        try {
            String canonical = canonicalWriter().writeValueAsString(canonicalTree(value));
            return sha256Hex(canonical);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot canonicalize seed projection", e);
        }
    }

    /**
     * 规范化序列化的写出器（散列与导出共用）：BigDecimal 恒以普通记法写出——
     * 树节点工厂会剥尾零（{@code 42800 → 4.28E+4}），不加此项则带尾零的数值型自定义字段
     * （价格等）以科学记数法落入种子文件；普通记法下 {@code 42800} 与 {@code 42800.0}
     * 归一为同一字节序列（散列同形，往返稳定）。
     */
    public static ObjectWriter canonicalWriter() {
        return MAPPER.writer().with(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    /**
     * 任意值的规范化 JSON 树（对象键递归字典序；散列与种子导出共用同一序列化口径，
     * theme-seed-export D1/D4：确定性输出的唯一来源）。
     */
    public static JsonNode canonicalTree(Object value) {
        return sortKeys(MAPPER.valueToTree(value));
    }

    private static JsonNode sortKeys(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            objectNode.properties().forEach(entry -> sorted.put(entry.getKey(), sortKeys(entry.getValue())));
            ObjectNode out = MAPPER.createObjectNode();
            sorted.forEach(out::set);
            return out;
        }
        if (node instanceof ArrayNode arrayNode) {
            ArrayNode out = MAPPER.createArrayNode();
            arrayNode.forEach(element -> out.add(sortKeys(element)));
            return out;
        }
        return node;
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
