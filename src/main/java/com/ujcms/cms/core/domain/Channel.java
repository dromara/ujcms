package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.ChannelBase;
import com.ujcms.cms.core.domain.base.GroupBase;
import com.ujcms.cms.core.domain.base.RoleBase;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.db.tree.TreeEntity;
import com.ujcms.util.web.HtmlParserUtils;
import com.ujcms.util.web.PageUrlResolver;
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
 * 栏目 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties("handler")
public class Channel extends ChannelBase implements PageUrlResolver, Anchor, TreeEntity, Serializable {
    // region Normal

    public String getTitle() {
        return Optional.ofNullable(getExt().getSeoTitle()).orElseGet(this::getName);
    }

    public String getKeywords() {
        return Optional.ofNullable(getExt().getSeoKeywords()).orElse("");
    }

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

    @Override
    public String getUrl() {
        return getUrl(1);
    }

    @Override
    public String getUrl(int page) {
        return getSite().getHtml().isEnabled() ? getStaticUrl(page) : getDynamicUrl(page);
    }

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
    public boolean isLink() {
        return getType() == TYPE_LINK || getType() == TYPE_LINK_ARTICLE || getType() == TYPE_LINK_CHILD;
    }
    // endregion

    // region JsonFields

    public Map<String, Object> getCustoms() {
        if (customs == null) {
            customs = getChannelModel().assembleCustoms(getCustomList());
        }
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        getCustomList().clear();
        getChannelModel().disassembleCustoms(customs, (name, type, value) -> {
            getCustomList().add(new ChannelCustom(getId(), name, type, value));
        });
        this.customs = null;
    }

    @Nullable
    private Map<String, Object> customs;
    // endregion

    // region TempFields
    /**
     * 用户组ID列表。非数据库属性，用于接收前台请求。
     */
    @Nullable
    private List<Integer> groupIds;
    @Nullable
    private List<Integer> roleIds;

    public List<Integer> getGroupIds() {
        if (groupIds == null) {
            groupIds = getGroupList().stream().map(GroupBase::getId).collect(Collectors.toList());
        }
        return groupIds;
    }

    public void setGroupIds(List<Integer> groupIds) {
        this.groupIds = groupIds;
    }

    public List<Integer> getRoleIds() {
        if (roleIds == null) {
            roleIds = getRoleList().stream().map(RoleBase::getId).collect(Collectors.toList());
        }
        return roleIds;
    }

    public void setRoleIds(List<Integer> roleIds) {
        this.roleIds = roleIds;
    }
    // endregion

    // region Associations
    /**
     * 栏目扩展对象
     */
    @JsonIgnore
    private ChannelExt ext = new ChannelExt();
    /**
     * 缓冲对象
     */
    @JsonIgnore
    private ChannelBuffer buffer = new ChannelBuffer();
    /**
     * 子栏目列表
     */
    @JsonIncludeProperties({"id", "name", "url"})
    private List<Channel> children = Collections.emptyList();
    /**
     * 用户组列表
     */
    @JsonIgnore
    private List<Group> groupList = Collections.emptyList();
    /**
     * 角色列表
     */
    @JsonIgnore
    private List<Role> roleList = Collections.emptyList();
    /**
     * 自定义字段列表
     */
    @JsonIgnore
    private List<ChannelCustom> customList = Collections.emptyList();
    /**
     * 上级栏目
     */
    @JsonIncludeProperties({"id", "name", "url"})
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

    @JsonIgnore
    @Nullable
    private Article firstArticle;

    @JsonIgnore
    @Nullable
    private Channel firstChild;

    public ChannelExt getExt() {
        return ext;
    }

    public void setExt(ChannelExt ext) {
        this.ext = ext;
    }

    public ChannelBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(ChannelBuffer buffer) {
        this.buffer = buffer;
    }

    public List<Channel> getChildren() {
        return children;
    }

    public void setChildren(List<Channel> children) {
        this.children = children;
    }

    public List<Group> getGroupList() {
        return groupList;
    }

    public void setGroupList(List<Group> groupList) {
        this.groupList = groupList;
    }

    public List<ChannelCustom> getCustomList() {
        return customList;
    }

    public List<Role> getRoleList() {
        return roleList;
    }

    public void setRoleList(List<Role> roleList) {
        this.roleList = roleList;
    }

    public void setCustomList(List<ChannelCustom> customList) {
        this.customList = customList;
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
    public Article getFirstArticle() {
        return firstArticle;
    }

    public void setFirstArticle(@Nullable Article firstArticle) {
        this.firstArticle = firstArticle;
    }

    @Nullable
    public Channel getFirstChild() {
        return firstChild;
    }

    public void setFirstChild(@Nullable Channel firstChild) {
        this.firstChild = firstChild;
    }
    // endregion

    // region ChannelBase

    @Override
    @NotNull
    @Pattern(regexp = "^[\\w-]*$")
    public String getAlias() {
        return super.getAlias();
    }
    // endregion

    // region ChannelExt

    @Nullable
    public String getSeoTitle() {
        return getExt().getSeoTitle();
    }

    public void setSeoTitle(@Nullable String seoTitle) {
        getExt().setSeoTitle(seoTitle);
    }

    @Nullable
    public String getSeoKeywords() {
        return getExt().getSeoKeywords();
    }

    public void setSeoKeywords(@Nullable String seoKeywords) {
        getExt().setSeoKeywords(seoKeywords);
    }

    @Nullable
    public String getSeoDescription() {
        return getExt().getSeoDescription();
    }

    public void setSeoDescription(@Nullable String seoDescription) {
        getExt().setSeoDescription(seoDescription);
    }

    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getArticleTemplate() {
        return getExt().getArticleTemplate();
    }

    public void setArticleTemplate(@Nullable String articleTemplate) {
        getExt().setArticleTemplate(articleTemplate);
    }

    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getChannelTemplate() {
        return getExt().getChannelTemplate();
    }

    public void setChannelTemplate(@Nullable String channelTemplate) {
        getExt().setChannelTemplate(channelTemplate);
    }

    public Short getPageSize() {
        return getExt().getPageSize();
    }

    public void setPageSize(Short pageSize) {
        getExt().setPageSize(pageSize);
    }

    @Nullable
    public String getImage() {
        return getExt().getImage();
    }

    public void setImage(@Nullable String image) {
        getExt().setImage(image);
    }

    @Nullable
    public String getLinkUrl() {
        return getExt().getLinkUrl();
    }

    public void setLinkUrl(@Nullable String linkUrl) {
        getExt().setLinkUrl(linkUrl);
    }

    @Override
    public Boolean getTargetBlank() {
        return getExt().getTargetBlank();
    }

    public void setTargetBlank(boolean targetBlank) {
        getExt().setTargetBlank(targetBlank);
    }

    public Boolean getAllowComment() {
        return getExt().getAllowComment();
    }

    public void setAllowComment(boolean allowComment) {
        getExt().setAllowComment(allowComment);
    }

    public Boolean isAllowContribute() {
        return getExt().getAllowContribute();
    }

    public void setAllowContribute(boolean allowContribute) {
        getExt().setAllowContribute(allowContribute);
    }

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

    @Nullable
    public String getMobileStaticFile() {
        return getExt().getMobileStaticFile();
    }

    public void setMobileStaticFile(@Nullable String mobileStaticFile) {
        getExt().setMobileStaticFile(mobileStaticFile);
    }

    @Nullable
    public String getText() {
        return getExt().getText();
    }

    public void setText(@Nullable String text) {
        getExt().setText(text);
    }
    // endregion

    // region ChannelBuffer

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