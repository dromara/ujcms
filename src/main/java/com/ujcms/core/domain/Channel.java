package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.core.domain.base.ChannelBase;
import com.ujcms.core.domain.base.GroupBase;
import com.ujcms.core.support.UrlConstants;
import com.ofwise.util.web.HtmlUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
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
public class Channel extends ChannelBase implements Serializable {
    public String getUrl() {
        String linkUrl = getSite().getLinkUrl(getExt().getLinkUrl());
        if (linkUrl != null) {
            return linkUrl;
        }
        String prefix = Optional.ofNullable(getSite().getGlobal().getChannelUrl()).orElse(UrlConstants.CHANNEL);
        return getSite().getUrl(prefix + "/" + getAlias());
    }

    @Nullable
    public String getTemplate() {
        return getSite().getTemplate(getExt().getChannelTemplate());
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

    public String getTitle() {
        return Optional.ofNullable(getExt().getSeoTitle()).orElse(getName());
    }

    public String getKeywords() {
        return Optional.ofNullable(getExt().getSeoKeywords()).orElse("");
    }

    public String getDescription() {
        return Optional.ofNullable(getExt().getSeoDescription()).orElse("");
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
        urls.addAll(HtmlUtils.getUrls(getExt().getText()));
        getChannelModel().handleCustoms(getCustomList(), new Model.GetUrlsHandle(urls));
        return urls;
    }

    public Map<String, Object> getCustoms() {
        return getChannelModel().assembleCustoms(getCustomList());
    }

    public void setCustoms(Map<String, Object> customs) {
        getCustomList().clear();
        customs.forEach((key, value) -> {
            if (value instanceof ArrayList) {
                ((ArrayList<?>) value).forEach(val -> {
                    getCustomList().add(new ChannelCustom(getId(), key, String.valueOf(val)));
                });
                return;
            }
            Optional.ofNullable(value).map(String::valueOf).filter(StringUtils::isNotBlank).ifPresent(it -> {
                getCustomList().add(new ChannelCustom(getId(), key, it));
            });
        });
    }

    /**
     * 用户组ID列表。非数据库属性，用于接收前台请求。
     */
    @Nullable
    private List<Integer> groupIds;

    @Nullable
    public List<Integer> getGroupIds() {
        if (groupIds == null) {
            groupIds = getGroupList().stream().map(GroupBase::getId).collect(Collectors.toList());
        }
        return groupIds;
    }

    public void setGroupIds(@Nullable List<Integer> groupIds) {
        this.groupIds = groupIds;
    }

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
    @JsonIgnore
    private List<Channel> children = new ArrayList<>();
    /**
     * 用户组列表
     */
    @JsonIgnore
    private List<Group> groupList = new ArrayList<>();
    /**
     * 自定义字段列表
     */
    @JsonIgnore
    private List<ChannelCustom> customList = new ArrayList<>();
    /**
     * 上级栏目
     */
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

    public void setCustomList(List<ChannelCustom> customList) {
        this.customList = customList;
    }

    @Nullable
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

    // ChannelExt 对象属性

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
    public String getArticleTemplate() {
        return getExt().getArticleTemplate();
    }

    public void setArticleTemplate(@Nullable String articleTemplate) {
        getExt().setArticleTemplate(articleTemplate);
    }

    @Nullable
    public String getChannelTemplate() {
        return getExt().getChannelTemplate();
    }

    public void setChannelTemplate(@Nullable String channelTemplate) {
        getExt().setChannelTemplate(channelTemplate);
    }

    public short getPageSize() {
        return getExt().getPageSize();
    }

    public void setPageSize(short pageSize) {
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

    public boolean isTargetBlank() {
        return getExt().isTargetBlank();
    }

    public void setTargetBlank(boolean targetBlank) {
        getExt().setTargetBlank(targetBlank);
    }

    public boolean isAllowComment() {
        return getExt().isAllowComment();
    }

    public void setAllowComment(boolean allowComment) {
        getExt().setAllowComment(allowComment);
    }

    public boolean isAllowContribute() {
        return getExt().isAllowContribute();
    }

    public void setAllowContribute(boolean allowContribute) {
        getExt().setAllowContribute(allowContribute);
    }

    public boolean isAllowSearch() {
        return getExt().isAllowSearch();
    }

    public void setAllowSearch(boolean allowSearch) {
        getExt().setAllowSearch(allowSearch);
    }

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

    // ChannelBuffer 对象属性

    public long getViews() {
        return getBuffer().getViews();
    }
}