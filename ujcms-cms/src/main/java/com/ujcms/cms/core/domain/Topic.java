package com.ujcms.cms.core.domain;

import java.io.Serial;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ujcms.cms.core.domain.generated.GeneratedTopic;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.common.db.tree.TreeEntity;
import com.ujcms.common.web.PageUrlResolver;
import com.ujcms.common.web.UrlBuilder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 专题实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Topic extends GeneratedTopic implements PageUrlResolver, Anchor, TreeEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    // region Normal

    /**
     * {@code Anchor#getName()} contract: 专题的列名是 {@code title_}，但 Anchor / 面包屑 /
     * 导航等读取的是 {@code getName()}，这里把两者桥接起来，让调用方无需关心存储字段。
     */
    @Override
    @Schema(description = "名称")
    public String getName() {
        return getTitle();
    }

    @Schema(description = "关键词。获取`seoKeywords`")
    public String getKeywords() {
        return Optional.ofNullable(getSeoKeywords()).orElse("");
    }

    @Schema(description = "描述。获取`seoDescription`")
    public String getDescription() {
        return Optional.ofNullable(getSeoDescription()).orElse("");
    }

    @JsonIgnore
    public String getTemplate() {
        return getSite().assembleTemplate(getTemplateName());
    }

    /**
     * 专题页的未组装模板名（渲染上下文钩子用，页面构成 §10.2）
     */
    @JsonIgnore
    public String getTemplateName() {
        String template = getTopicTemplate();
        if (template == null || template.isBlank()) {
            template = TEMPLATE_PREFIX;
        }
        return template;
    }

    @Schema(description = "专题层级。从一级专题到当前专题的列表。只有在单独查询专题对象时，才有此属性；查询专题列表时，此属性只包含当前专题")
    @JsonIncludeProperties({"id", "name", "slug", "url"})
    public List<Topic> getPaths() {
        LinkedList<Topic> parents = new LinkedList<>();
        Topic bean = this;
        while (bean != null) {
            parents.addFirst(bean);
            bean = bean.getParent();
        }
        return parents;
    }

    @Schema(description = "专题层级名称。从一级专题到当前专题的名称列表。只有在单独查询专题对象时，才有此属性；查询专题列表时，此属性只包含当前专题名称")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public List<String> getNames() {
        return getPaths().stream().map(Topic::getName).toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Topic bean = (Topic) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "Topic{id=" + getId() + ", title=" + getTitle() + "}";
    }
    // endregion

    // region Urls

    @Schema(description = "URL地址")
    @Override
    public String getUrl() {
        return getUrl(1);
    }

    @Override
    public String getUrl(int page) {
        return getSite().isHtmlEnabled() ? getStaticUrl(page) : getDynamicUrl(page);
    }

    @Schema(description = "动态URL地址")
    public String getDynamicUrl() {
        return getDynamicUrl(1);
    }

    @Override
    public String getDynamicUrl(int page) {
        String prefix = Optional.ofNullable(getSite().getConfig().getTopicUrl()).orElse(UrlConstants.TOPIC);
        String url = UrlBuilder.of(getSite().getDynamicUrl()).appendPath(prefix).appendPath(getSlug()).toString();
        if (page <= 1) {
            return url;
        }
        return url + "/" + page;
    }

    /**
     * V11.2 Phase 1 不发布专题静态页生成，静态 URL 暂时回落到动态 URL；待 Phase 4 接入
     * 静态化时再实现 {@code Site.Html.getTopicUrl/getTopicPath}。
     */
    public String getStaticUrl() {
        return getStaticUrl(1);
    }

    public String getStaticUrl(int page) {
        return getDynamicUrl(page);
    }

    @Schema(description = "完整URL地址")
    @Override
    public String getFullUrl() {
        return getFullUrl(1);
    }

    public String getFullUrl(int page) {
        return getSite().isHtmlEnabled() ? getStaticFullUrl(page) : getDynamicFullUrl(page);
    }

    @Schema(description = "完整动态URL地址")
    public String getDynamicFullUrl() {
        return getDynamicFullUrl(1);
    }

    public String getDynamicFullUrl(int page) {
        String prefix = Optional.ofNullable(getSite().getConfig().getTopicUrl()).orElse(UrlConstants.TOPIC);
        String url = UrlBuilder.of(getSite().getDynamicFullUrl()).appendPath(prefix).appendPath(getSlug()).toString();
        if (page <= 1) {
            return url;
        }
        return url + "/" + page;
    }

    public String getStaticFullUrl(int page) {
        return getDynamicFullUrl(page);
    }
    // endregion

    // region JsonFields
    /**
     * 自定义字段
     */
    @Nullable
    private transient Map<String, Object> customs;

    @Schema(description = "自定义字段")
    public Map<String, Object> getCustoms() {
        if (customs != null) {
            return customs;
        }
        customs = new HashMap<>(16);
        customs.putAll(getTopicModel().assembleMap(getMainsJson()));
        customs.putAll(getTopicModel().assembleMap(getClobsJson()));
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        this.customs = customs;
    }

    public void disassembleCustoms(Model model, HtmlSanitizeService sanitizeService) {
        Map<String, Object> map = model.sanitizeMap(getCustoms(), sanitizeService);
        setCustoms(map);
        model.disassembleMap(map, this::setMainsJson, this::setClobsJson);
    }
    // endregion

    // region Associations
    /**
     * 是否有子专题。只有在查询专题列表时有效；查询专题对象时无效，请使用 `topic.children?size > 0` 判断
     */
    @Nullable
    private Boolean hasChildren;
    /**
     * 子专题列表。只有在单独查询专题对象时，才有此属性；查询专题列表时，此属性为`null`
     */
    @JsonIncludeProperties({"id", "name", "url", "image"})
    @Nullable
    private List<Topic> children;
    /**
     * 上级专题。只有在单独查询专题对象时，才有此属性；查询专题列表时，此属性为`null`
     */
    @JsonIncludeProperties({"id", "name", "slug", "url"})
    @Nullable
    private Topic parent;
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name"})
    private Site site = new Site();
    /**
     * 专题模型
     */
    @JsonIncludeProperties({"id", "name", "fieldList"})
    private Model topicModel = new Model();

    @Nullable
    public Boolean getHasChildren() {
        return hasChildren;
    }

    public void setHasChildren(@Nullable Boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    @Nullable
    public List<Topic> getChildren() {
        return children;
    }

    public void setChildren(@Nullable List<Topic> children) {
        this.children = children;
    }

    @Nullable
    @Override
    public Topic getParent() {
        return parent;
    }

    public void setParent(@Nullable Topic parent) {
        this.parent = parent;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Model getTopicModel() {
        return topicModel;
    }

    public void setTopicModel(Model topicModel) {
        this.topicModel = topicModel;
    }

    /**
     * 自定义字段所属模型（专题模型）
     */
    @JsonIgnore
    public Model getModel() {
        return getTopicModel();
    }
    // endregion

    // region GeneratedTopic
    @Schema(description = "别名")
    @Override
    @NotNull
    @Pattern(regexp = "^[\\w-]*$")
    public String getSlug() {
        return super.getSlug();
    }

    @Nullable
    @Override
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getTopicTemplate() {
        return super.getTopicTemplate();
    }
    // endregion

    // region StaticField
    /**
     * 编辑器类型：富文本编辑器
     */
    public static final short EDITOR_TYPE_HTML = 1;
    /**
     * 编辑器类型：Markdown编辑器
     */
    public static final short EDITOR_TYPE_MARKDOWN = 2;

    /**
     * 专题模型类别
     */
    public static final String MODEL_TYPE = "topic";
    /**
     * 专题模板前缀
     */
    public static final String TEMPLATE_PREFIX = "topic";

    public static final String NOT_FOUND = "Topic not found. ID: ";
    // endregion
}
