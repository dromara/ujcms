package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.*;
import com.ujcms.cms.Application;
import com.ujcms.cms.core.domain.base.ChannelBase;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.commons.db.tree.TreeEntity;
import com.ujcms.commons.web.HtmlParserUtils;
import com.ujcms.commons.web.PageUrlResolver;
import com.ujcms.commons.web.Views;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 栏目实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Channel extends ChannelBase implements PageUrlResolver, Anchor, TreeEntity, Serializable {
    private static final long serialVersionUID = 1L;
    // region Normal

    @Schema(description = "标题。获取`seoTitle`，如不存在，则获取栏目名称")
    public String getTitle() {
        return Optional.ofNullable(getExt().getSeoTitle()).orElseGet(this::getName);
    }

    @Schema(description = "关键词。获取`seoKeywords`")
    public String getKeywords() {
        return Optional.ofNullable(getExt().getSeoKeywords()).orElse("");
    }

    @Schema(description = "描述。获取`seoDescription`")
    public String getDescription() {
        return Optional.ofNullable(getExt().getSeoDescription()).orElse("");
    }

    @JsonIgnore
    public String getTemplate() {
        String template = getChannelTemplate();
        if (StringUtils.isBlank(template)) {
            template = TEMPLATE_PREFIX;
        }
        return getSite().assembleTemplate(template);
    }

    @Schema(description = "栏目层级。从一级栏目到当前栏目的列表。只有在单独查询栏目对象时，才有此属性；查询栏目列表时，此属性只包含当前栏目")
    @JsonIncludeProperties({"id", "name", "alias", "url", "nav"})
    public List<Channel> getPaths() {
        LinkedList<Channel> parents = new LinkedList<>();
        Channel bean = this;
        while (bean != null) {
            parents.addFirst(bean);
            bean = bean.getParent();
        }
        return parents;
    }

    @Schema(description = "栏目层级名称。从一级栏目到当前栏目的名称列表。只有在单独查询栏目对象时，才有此属性；查询栏目列表时，此属性只包含当前栏目名称")
    public List<String> getNames() {
        return getPaths().stream().map(ChannelBase::getName).collect(Collectors.toList());
    }

    /**
     * 获取所有字段中的附件
     *
     * @return 附件列表
     */
    @JsonIgnore
    public List<String> getAttachmentUrls() {
        return getAttachmentUrls(getChannelModel());
    }

    public List<String> getAttachmentUrls(Model model) {
        List<String> urls = new ArrayList<>();
        Optional.ofNullable(getImage()).ifPresent(urls::add);
        // 获取正文中的附件url
        urls.addAll(HtmlParserUtils.getUrls(getExt().getText()));
        urls.addAll(model.getUrlsFromMap(getCustoms()));
        return urls;
    }

    @Nullable
    @Schema(description = "文章模板")
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    @Override
    public String getArticleTemplate() {
        return super.getArticleTemplate();
    }


    @Nullable
    @Schema(description = "栏目模板")
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    @Override
    public String getChannelTemplate() {
        return super.getChannelTemplate();
    }

    @Nullable
    @Schema(description = "转向链接")
    @Pattern(regexp = "^(http|/).*$")
    @Override
    public String getLinkUrl() {
        return super.getLinkUrl();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Channel bean = (Channel) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "Channel{id=" + getId() + ", name=" + getName() + "}";
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
        String linkUrl = getLinkUrl();
        if (getType() == TYPE_LINK && StringUtils.isNotBlank(linkUrl)) {
            return getSite().assembleLinkUrl(linkUrl);
        }
        if (getType() == TYPE_LINK_CHILD) {
            return getFirstChannel(getId()).map(channel -> channel.getDynamicUrl(page)).orElse("");
        }
        String prefix = Optional.ofNullable(getSite().getConfig().getChannelUrl()).orElse(UrlConstants.CHANNEL);
        String url = getSite().getDynamicUrl() + prefix + "/" + getAlias();
        if (page <= 1) {
            return url;
        }
        return url + "/" + page;
    }

    public String getStaticUrl() {
        return getStaticUrl(1);
    }

    public String getStaticUrl(int page) {
        String linkUrl = getLinkUrl();
        if (getType() == TYPE_LINK && StringUtils.isNotBlank(linkUrl)) {
            return getSite().assembleLinkUrl(linkUrl);
        }
        if (getType() == TYPE_LINK_CHILD) {
            return getFirstChannel(getId()).map(channel -> channel.getStaticUrl(page)).orElse("");
        }
        // 如果页数大于最大静态化页数，则使用动态页
        if (page > getSite().getHtml().getListPages()) {
            return getDynamicUrl(page);
        }
        return Contexts.isMobile() ? getMobileStaticUrl(page) : getNormalStaticUrl(page);
    }

    private Optional<Channel> getFirstChannel(Long channelId) {
        return Optional.ofNullable(Application.getApplicationContext())
                .map((applicationContext -> applicationContext.getBean(ChannelService.class)))
                .map(channelService -> channelService.findFirstChild(channelId));
    }

    public String getNormalStaticUrl(int page) {
        return getSite().getNormalStaticUrl(getChannelUrl(page));
    }

    public String getMobileStaticUrl(int page) {
        return getSite().getMobileStaticUrl(getChannelUrl(page));
    }

    private String getChannelUrl(int page) {
        return getSite().getHtml().getChannelUrl(getChannelStaticPath(), getId(), getAlias(), page);
    }

    public String getNormalStaticPath(int page) {
        return getSite().getNormalStaticPath(getChannelPath(page));
    }

    public String getMobileStaticPath(int page) {
        return getSite().getMobileStaticPath(getChannelPath(page));
    }

    private String getChannelPath(int page) {
        return getSite().getHtml().getChannelPath(getChannelStaticPath(), getId(), getAlias(), page);
    }

    /**
     * 是否是链接
     */
    @Schema(description = "是否链接")
    public boolean isLink() {
        return getType() == TYPE_LINK || getType() == TYPE_LINK_CHILD;
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
        customs.putAll(getChannelModel().assembleMap(getMainsJson()));
        customs.putAll(getChannelModel().assembleMap(getClobsJson()));
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        this.customs = customs;
    }

    public void disassembleCustoms(Model model, PolicyFactory policyFactory) {
        Map<String, Object> map = model.sanitizeMap(getCustoms(), policyFactory);
        setCustoms(map);
        model.disassembleMap(map, this::setMainsJson, this::setClobsJson);
    }
    // endregion

    // region Associations
    /**
     * 是否有子栏目
     */
    @Nullable
    private Boolean hasChildren;
    /**
     * 栏目扩展对象
     */
    @JsonIgnore
    private ChannelExt ext = new ChannelExt();
    /**
     * 子栏目列表。只有在单独查询栏目对象时，才有此属性；查询栏目列表时，此属性为`null`
     */
    @JsonIncludeProperties({"id", "name", "url", "image", "nav"})
    @Nullable
    private List<Channel> children;
    /**
     * 上级栏目。只有在单独查询栏目对象时，才有此属性；查询栏目列表时，此属性为`null`
     */
    @JsonIncludeProperties({"id", "name", "alias", "url", "nav"})
    @Nullable
    private Channel parent;
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name"})
    private Site site = new Site();
    /**
     * 栏目模型
     */
    @JsonIncludeProperties({"id", "name"})
    private Model channelModel = new Model();
    /**
     * 文章模型
     */
    @JsonIncludeProperties({"id", "name"})
    private Model articleModel = new Model();
    /**
     * 绩效类型
     */
    @JsonIncludeProperties({"id", "name"})
    @Nullable
    private PerformanceType performanceType;

    @Nullable
    public Boolean getHasChildren() {
        return hasChildren;
    }

    public void setHasChildren(@Nullable Boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    public ChannelExt getExt() {
        return ext;
    }

    public void setExt(ChannelExt ext) {
        this.ext = ext;
    }

    @Nullable
    public List<Channel> getChildren() {
        return children;
    }

    public void setChildren(@Nullable List<Channel> children) {
        this.children = children;
    }

    @Nullable
    @Override
    public Channel getParent() {
        return parent;
    }

    public void setParent(Channel parent) {
        this.parent = parent;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Model getChannelModel() {
        return channelModel;
    }

    public void setChannelModel(Model channelModel) {
        this.channelModel = channelModel;
    }

    public Model getArticleModel() {
        return articleModel;
    }

    public void setArticleModel(Model articleModel) {
        this.articleModel = articleModel;
    }

    @Nullable
    public PerformanceType getPerformanceType() {
        return performanceType;
    }

    public void setPerformanceType(@Nullable PerformanceType performanceType) {
        this.performanceType = performanceType;
    }
    // endregion

    // region ChannelBase

    @Schema(description = "别名")
    @Override
    @NotNull
    @Pattern(regexp = "^[\\w-]*$")
    public String getAlias() {
        return super.getAlias();
    }
    // endregion

    // region ChannelExt

    @Schema(description = "SEO标题")
    @JsonView(Views.Whole.class)
    @Nullable
    public String getSeoTitle() {
        return getExt().getSeoTitle();
    }

    public void setSeoTitle(@Nullable String seoTitle) {
        getExt().setSeoTitle(seoTitle);
    }

    @Schema(description = "SEO关键词")
    @JsonView(Views.Whole.class)
    @Nullable
    public String getSeoKeywords() {
        return getExt().getSeoKeywords();
    }

    public void setSeoKeywords(@Nullable String seoKeywords) {
        getExt().setSeoKeywords(seoKeywords);
    }

    @Schema(description = "SEO描述")
    @JsonView(Views.Whole.class)
    @Nullable
    public String getSeoDescription() {
        return getExt().getSeoDescription();
    }

    public void setSeoDescription(@Nullable String seoDescription) {
        getExt().setSeoDescription(seoDescription);
    }

    @JsonIgnore
    @Nullable
    public String getStaticFile() {
        return getExt().getStaticFile();
    }

    public void setStaticFile(@Nullable String staticFile) {
        getExt().setStaticFile(staticFile);
    }

    @JsonIgnore
    @Nullable
    public String getMobileStaticFile() {
        return getExt().getMobileStaticFile();
    }

    public void setMobileStaticFile(@Nullable String mobileStaticFile) {
        getExt().setMobileStaticFile(mobileStaticFile);
    }

    @Schema(description = "正文（HTML格式）")
    @JsonView(Views.Whole.class)
    @Nullable
    public String getText() {
        return getExt().getText();
    }

    public void setText(@Nullable String text) {
        getExt().setText(text);
    }

    @Schema(description = "正文（Markdown格式）")
    @JsonView(Views.Whole.class)
    @Nullable
    public String getMarkdown() {
        return getExt().getMarkdown();
    }

    public void setMarkdown(@Nullable String markdown) {
        getExt().setMarkdown(markdown);
    }

    @Schema(description = "正文编辑器类型")
    @JsonView(Views.Whole.class)
    public Short getEditorType() {
        return getExt().getEditorType();
    }

    public void setEditorType(Short editorType) {
        getExt().setEditorType(editorType);
    }
    // endregion

    // region StaticField
    /**
     * 常规栏目
     */
    public static final short TYPE_NORMAL = 1;
    /**
     * 单页栏目
     */
    public static final short TYPE_SINGLE = 2;
    /**
     * 转向链接
     */
    public static final short TYPE_LINK = 3;
    /**
     * 链接到第一个子栏目
     */
    public static final short TYPE_LINK_CHILD = 4;

    /**
     * 编辑器类型：富文本编辑器
     */
    public static final short EDITOR_TYPE_HTML = 1;
    /**
     * 编辑器类型：Markdown编辑器
     */
    public static final short EDITOR_TYPE_MARKDOWN = 2;

    /**
     * 栏目模型类别
     */
    public static final String MODEL_TYPE = "channel";
    /**
     * 栏目模板前缀
     */
    public static final String TEMPLATE_PREFIX = "channel";

    public static final String NOT_FOUND = "Channel not found. ID: ";
    // endregion
}