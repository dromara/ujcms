package com.ujcms.cms.core.domain;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 文章导出用的行：文章、扩展表、组织、用户的列一次取出（{@code ArticleMapper#listForExport}），不含正文。
 * 只读，不参与保存
 *
 * @author PONY
 */
public class ArticleExportRow implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private Long id = 0L;
    /**
     * 栏目ID
     */
    private Long channelId = 0L;
    /**
     * 创建日期
     */
    @Nullable
    private OffsetDateTime created;
    /**
     * 修改日期
     */
    @Nullable
    private OffsetDateTime modified;
    /**
     * 发布日期
     */
    @Nullable
    private OffsetDateTime publishDate;
    /**
     * 上线日期
     */
    @Nullable
    private OffsetDateTime onlineDate;
    /**
     * 下线日期
     */
    @Nullable
    private OffsetDateTime offlineDate;
    /**
     * 置顶
     */
    private Short sticky = (short) 0;
    /**
     * 是否有标题图
     */
    private Boolean withImage = false;
    /**
     * 类型
     */
    private Short type = (short) 0;
    /**
     * 状态
     */
    private Short status = (short) 0;
    /**
     * 别名
     */
    private String slug = "";
    /**
     * 标题
     */
    private String title = "";
    /**
     * 副标题
     */
    @Nullable
    private String subtitle;
    /**
     * 完整标题
     */
    @Nullable
    private String fullTitle;
    /**
     * 转向链接
     */
    @Nullable
    private String linkUrl;
    /**
     * SEO 关键词
     */
    @Nullable
    private String seoKeywords;
    /**
     * 摘要
     */
    @Nullable
    private String seoDescription;
    /**
     * 作者
     */
    @Nullable
    private String author;
    /**
     * 编辑
     */
    @Nullable
    private String editor;
    /**
     * 来源
     */
    @Nullable
    private String source;
    /**
     * 文章静态页路径
     */
    @Nullable
    private String articleStaticPath;
    /**
     * 评论数
     */
    private Integer comments = 0;
    /**
     * 顶
     */
    private Integer ups = 0;
    /**
     * 踩
     */
    private Integer downs = 0;
    /**
     * 浏览量
     */
    private Long views = 0L;
    /**
     * 自定义字段 JSON
     */
    @Nullable
    private String mainsJson;
    /**
     * 组织名称
     */
    @Nullable
    private String orgName;
    /**
     * 录入人用户名
     */
    @Nullable
    private String username;
    /**
     * 修改人用户名
     */
    @Nullable
    private String modifiedUsername;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    @Nullable
    public OffsetDateTime getCreated() {
        return created;
    }

    public void setCreated(@Nullable OffsetDateTime created) {
        this.created = created;
    }

    @Nullable
    public OffsetDateTime getModified() {
        return modified;
    }

    public void setModified(@Nullable OffsetDateTime modified) {
        this.modified = modified;
    }

    @Nullable
    public OffsetDateTime getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(@Nullable OffsetDateTime publishDate) {
        this.publishDate = publishDate;
    }

    @Nullable
    public OffsetDateTime getOnlineDate() {
        return onlineDate;
    }

    public void setOnlineDate(@Nullable OffsetDateTime onlineDate) {
        this.onlineDate = onlineDate;
    }

    @Nullable
    public OffsetDateTime getOfflineDate() {
        return offlineDate;
    }

    public void setOfflineDate(@Nullable OffsetDateTime offlineDate) {
        this.offlineDate = offlineDate;
    }

    public Short getSticky() {
        return sticky;
    }

    public void setSticky(Short sticky) {
        this.sticky = sticky;
    }

    public Boolean getWithImage() {
        return withImage;
    }

    public void setWithImage(Boolean withImage) {
        this.withImage = withImage;
    }

    public Short getType() {
        return type;
    }

    public void setType(Short type) {
        this.type = type;
    }

    public Short getStatus() {
        return status;
    }

    public void setStatus(Short status) {
        this.status = status;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(@Nullable String subtitle) {
        this.subtitle = subtitle;
    }

    @Nullable
    public String getFullTitle() {
        return fullTitle;
    }

    public void setFullTitle(@Nullable String fullTitle) {
        this.fullTitle = fullTitle;
    }

    @Nullable
    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(@Nullable String linkUrl) {
        this.linkUrl = linkUrl;
    }

    @Nullable
    public String getSeoKeywords() {
        return seoKeywords;
    }

    public void setSeoKeywords(@Nullable String seoKeywords) {
        this.seoKeywords = seoKeywords;
    }

    @Nullable
    public String getSeoDescription() {
        return seoDescription;
    }

    public void setSeoDescription(@Nullable String seoDescription) {
        this.seoDescription = seoDescription;
    }

    @Nullable
    public String getAuthor() {
        return author;
    }

    public void setAuthor(@Nullable String author) {
        this.author = author;
    }

    @Nullable
    public String getEditor() {
        return editor;
    }

    public void setEditor(@Nullable String editor) {
        this.editor = editor;
    }

    @Nullable
    public String getSource() {
        return source;
    }

    public void setSource(@Nullable String source) {
        this.source = source;
    }

    @Nullable
    public String getArticleStaticPath() {
        return articleStaticPath;
    }

    public void setArticleStaticPath(@Nullable String articleStaticPath) {
        this.articleStaticPath = articleStaticPath;
    }

    public Integer getComments() {
        return comments;
    }

    public void setComments(Integer comments) {
        this.comments = comments;
    }

    public Integer getUps() {
        return ups;
    }

    public void setUps(Integer ups) {
        this.ups = ups;
    }

    public Integer getDowns() {
        return downs;
    }

    public void setDowns(Integer downs) {
        this.downs = downs;
    }

    public Long getViews() {
        return views;
    }

    public void setViews(Long views) {
        this.views = views;
    }

    @Nullable
    public String getMainsJson() {
        return mainsJson;
    }

    public void setMainsJson(@Nullable String mainsJson) {
        this.mainsJson = mainsJson;
    }

    @Nullable
    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(@Nullable String orgName) {
        this.orgName = orgName;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    @Nullable
    public String getModifiedUsername() {
        return modifiedUsername;
    }

    public void setModifiedUsername(@Nullable String modifiedUsername) {
        this.modifiedUsername = modifiedUsername;
    }
}
