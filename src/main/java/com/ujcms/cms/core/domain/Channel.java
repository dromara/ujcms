package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.ChannelBase;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.db.tree.TreeEntity;
import com.ujcms.util.web.HtmlParserUtils;
import com.ujcms.util.web.PageUrlResolver;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            template = "channel";
        }
        return getSite().assembleTemplate(template);
    }

    @Schema(description = "栏目层级。从一级栏目到当前栏目的列表")
    @JsonIncludeProperties({"id", "name", "url"})
    public List<Channel> getPaths() {
        LinkedList<Channel> parents = new LinkedList<>();
        Channel bean = this;
        while (bean != null) {
            parents.addFirst(bean);
            bean = bean.getParent();
        }
        return parents;
    }

    @Schema(description = "栏目层级名称。从一级栏目到当前栏目的名称列表")
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
        List<String> urls = new ArrayList<>();
        Optional.ofNullable(getExt().getImage()).ifPresent(urls::add);
        // 获取正文中的附件url
        urls.addAll(HtmlParserUtils.getUrls(getExt().getText()));
        getChannelModel().handleCustoms(getCustomList(), new Model.GetUrlsHandle(urls));
        return urls;
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
        return "Channel{id=" + getId() + ", name=" + getName() + ", site=" + getSite().getName() + '}';
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
        return getSite().getHtml().isEnabled() ? getStaticUrl(page) : getDynamicUrl(page);
    }

    @Schema(description = "动态URL地址")
    public String getDynamicUrl() {
        return getDynamicUrl(1);
    }

    @Override
    public String getDynamicUrl(int page) {
        if (getType() == TYPE_LINK && StringUtils.isNotBlank(getLinkUrl())) {
            return getSite().assembleLinkUrl(getLinkUrl());
        }
        if (getType() == TYPE_LINK_ARTICLE) {
            return Optional.ofNullable(getFirstArticle()).map(article -> article.getDynamicUrl(page)).orElse("");
        }
        if (getType() == TYPE_LINK_CHILD) {
            return Optional.ofNullable(getFirstChild()).map(channel -> channel.getDynamicUrl(page)).orElse("");
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
        if (getType() == TYPE_LINK && StringUtils.isNotBlank(getLinkUrl())) {
            return getSite().assembleLinkUrl(getLinkUrl());
        }
        if (getType() == TYPE_LINK_ARTICLE) {
            return Optional.ofNullable(getFirstArticle()).map(article -> article.getStaticUrl(page)).orElse("");
        }
        if (getType() == TYPE_LINK_CHILD) {
            return Optional.ofNullable(getFirstChild()).map(channel -> channel.getStaticUrl(page)).orElse("");
        }
        // 如果页数大于最大静态化页数，则使用动态页
        if (page > getSite().getHtml().getListPages()) {
            return getDynamicUrl(page);
        }
        return Contexts.isMobile() ? getMobileStaticUrl(page) : getNormalStaticUrl(page);
    }

    public String getNormalStaticUrl(int page) {
        return getSite().getNormalStaticUrl(getChannelUrl(page));
    }

    public String getMobileStaticUrl(int page) {
        return getSite().getMobileStaticUrl(getChannelUrl(page));
    }

    private String getChannelUrl(int page) {
        return getSite().getHtml().getChannelUrl(getId(), getAlias(), page);
    }

    public String getNormalStaticPath(int page) {
        return getSite().getNormalStaticPath(getChannelPath(page));
    }

    public String getMobileStaticPath(int page) {
        return getSite().getMobileStaticPath(getChannelPath(page));
    }

    private String getChannelPath(int page) {
        return getSite().getHtml().getChannelPath(getId(), getAlias(), page);
    }

    /**
     * 是否是链接
     */
    @Schema(description = "是否链接")
    public boolean isLink() {
        return getType() == TYPE_LINK || getType() == TYPE_LINK_ARTICLE || getType() == TYPE_LINK_CHILD;
    }
    // endregion

    // region JsonFields

    @Schema(description = "自定义字段")
    public Map<String, Object> getCustoms() {
        if (customs == null) {
            customs = getChannelModel().assembleCustoms(getCustomList());
        }
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        this.customs = customs;
    }

    public static List<ChannelCustom> disassembleCustoms(Model model, Integer id, Map<String, Object> customs) {
        List<ChannelCustom> list = new ArrayList<>();
        model.disassembleCustoms(customs, (name, type, value) -> list.add(new ChannelCustom(id, name, type, value)));
        return list;
    }

    @Nullable
    private Map<String, Object> customs;
    // endregion

    // region Associations
    /**
     * 栏目扩展对象
     */
    private ChannelExt ext = new ChannelExt();
    /**
     * 缓冲对象
     */
    private ChannelBuffer buffer = new ChannelBuffer();
    /**
     * 子栏目列表
     */
    private List<Channel> children = Collections.emptyList();
    /**
     * 自定义字段列表
     */
    private List<ChannelCustom> customList = Collections.emptyList();
    /**
     * 上级栏目
     */
    @Nullable
    private Channel parent;
    /**
     * 站点
     */
    private Site site = new Site();
    /**
     * 栏目模型
     */
    private Model channelModel = new Model();
    /**
     * 文章模型
     */
    private Model articleModel = new Model();

    @Nullable
    private Article firstArticle;

    @Nullable
    private Channel firstChild;

    private boolean fetchedFirstData = false;

    @JsonIgnore
    public ChannelExt getExt() {
        return ext;
    }

    public void setExt(ChannelExt ext) {
        this.ext = ext;
    }

    @JsonIgnore
    public ChannelBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(ChannelBuffer buffer) {
        this.buffer = buffer;
    }

    @Schema(description = "子栏目列表")
    @JsonIncludeProperties({"id", "name", "url"})
    public List<Channel> getChildren() {
        return children;
    }

    public void setChildren(List<Channel> children) {
        this.children = children;
    }

    @JsonIgnore
    public List<ChannelCustom> getCustomList() {
        return customList;
    }

    public void setCustomList(List<ChannelCustom> customList) {
        this.customList = customList;
    }

    @Schema(description = "上级栏目")
    @JsonIncludeProperties({"id", "name", "url"})
    @Nullable
    @Override
    public Channel getParent() {
        return parent;
    }

    public void setParent(Channel parent) {
        this.parent = parent;
    }

    @Schema(description = "站点")
    @JsonIncludeProperties({"id", "name"})
    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    @Schema(description = "栏目模型")
    @JsonIncludeProperties({"id", "name"})
    public Model getChannelModel() {
        return channelModel;
    }

    public void setChannelModel(Model channelModel) {
        this.channelModel = channelModel;
    }

    @Schema(description = "文章模型")
    @JsonIncludeProperties({"id", "name"})
    public Model getArticleModel() {
        return articleModel;
    }

    public void setArticleModel(Model articleModel) {
        this.articleModel = articleModel;
    }

    @JsonIgnore
    @Nullable
    public Article getFirstArticle() {
        return firstArticle;
    }

    public void setFirstArticle(@Nullable Article firstArticle) {
        this.firstArticle = firstArticle;
    }

    @JsonIgnore
    @Nullable
    public Channel getFirstChild() {
        return firstChild;
    }

    public void setFirstChild(@Nullable Channel firstChild) {
        this.firstChild = firstChild;
    }

    @JsonIgnore
    public boolean isFetchedFirstData() {
        return fetchedFirstData;
    }

    public void setFetchedFirstData(boolean fetchedFirstData) {
        this.fetchedFirstData = fetchedFirstData;
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
    @Nullable
    public String getSeoTitle() {
        return getExt().getSeoTitle();
    }

    public void setSeoTitle(@Nullable String seoTitle) {
        getExt().setSeoTitle(seoTitle);
    }

    @Schema(description = "SEO关键词")
    @Nullable
    public String getSeoKeywords() {
        return getExt().getSeoKeywords();
    }

    public void setSeoKeywords(@Nullable String seoKeywords) {
        getExt().setSeoKeywords(seoKeywords);
    }

    @Schema(description = "SEO描述")
    @Nullable
    public String getSeoDescription() {
        return getExt().getSeoDescription();
    }

    public void setSeoDescription(@Nullable String seoDescription) {
        getExt().setSeoDescription(seoDescription);
    }

    @Schema(description = "文章模板")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getArticleTemplate() {
        return getExt().getArticleTemplate();
    }

    public void setArticleTemplate(@Nullable String articleTemplate) {
        getExt().setArticleTemplate(articleTemplate);
    }

    @Schema(description = "栏目模板")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getChannelTemplate() {
        return getExt().getChannelTemplate();
    }

    public void setChannelTemplate(@Nullable String channelTemplate) {
        getExt().setChannelTemplate(channelTemplate);
    }

    @Schema(description = "每页条数")
    public Short getPageSize() {
        return getExt().getPageSize();
    }

    public void setPageSize(Short pageSize) {
        getExt().setPageSize(pageSize);
    }

    @Schema(description = "图片")
    @Nullable
    public String getImage() {
        return getExt().getImage();
    }

    public void setImage(@Nullable String image) {
        getExt().setImage(image);
    }

    @Schema(description = "转向链接")
    @Nullable
    @Pattern(regexp = "^(http|/).*$")
    public String getLinkUrl() {
        return getExt().getLinkUrl();
    }

    public void setLinkUrl(@Nullable String linkUrl) {
        getExt().setLinkUrl(linkUrl);
    }

    @Schema(description = "是否新窗口打开")
    @Override
    public Boolean getTargetBlank() {
        return getExt().getTargetBlank();
    }

    public void setTargetBlank(boolean targetBlank) {
        getExt().setTargetBlank(targetBlank);
    }

    @Schema(description = "是否允许评论")
    public Boolean getAllowComment() {
        return getExt().getAllowComment();
    }

    public void setAllowComment(boolean allowComment) {
        getExt().setAllowComment(allowComment);
    }

    @Schema(description = "是否允许投稿")
    public Boolean isAllowContribute() {
        return getExt().getAllowContribute();
    }

    public void setAllowContribute(boolean allowContribute) {
        getExt().setAllowContribute(allowContribute);
    }

    @Schema(description = "是否允许搜索")
    public Boolean isAllowSearch() {
        return getExt().getAllowSearch();
    }

    public void setAllowSearch(boolean allowSearch) {
        getExt().setAllowSearch(allowSearch);
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
    @Nullable
    public String getText() {
        return getExt().getText();
    }

    public void setText(@Nullable String text) {
        getExt().setText(text);
    }
    // endregion

    // region ChannelBuffer

    @Schema(description = "浏览次数")
    public long getViews() {
        return getBuffer().getViews();
    }

    public void setViews(long views) {
        getBuffer().setViews(views);
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
     * 链接到第一篇文章
     */
    public static final short TYPE_LINK_ARTICLE = 4;
    /**
     * 链接到第一个子栏目
     */
    public static final short TYPE_LINK_CHILD = 5;
    // endregion
}