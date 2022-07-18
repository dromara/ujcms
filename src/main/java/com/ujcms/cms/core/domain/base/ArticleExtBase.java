package com.ujcms.cms.core.domain.base;

import java.time.OffsetDateTime;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.springframework.lang.Nullable;

/**
 * This class was generated by MyBatis Generator.
 *
 * @author MyBatis Generator
 */
public class ArticleExtBase {
    /**
     * 数据库表名
     */
    public static final String TABLE_NAME = "article_ext";

    /**
     * 文章ID
     */
    @NotNull
    private Integer id = 0;

    /**
     * 标题
     */
    @Length(max = 150)
    @NotNull
    private String title = "";

    /**
     * 副标题
     */
    @Length(max = 150)
    @Nullable
    private String subtitle;

    /**
     * 完整标题
     */
    @Length(max = 150)
    @Nullable
    private String fullTitle;

    /**
     * 别名
     */
    @Length(max = 160)
    @Nullable
    private String alias;

    /**
     * 转向链接地址
     */
    @Length(max = 255)
    @Nullable
    private String linkUrl;

    /**
     * 是否新窗口打开
     */
    @NotNull
    private Boolean targetBlank = false;

    /**
     * SEO关键词
     */
    @Length(max = 150)
    @Nullable
    private String seoKeywords;

    /**
     * 摘要
     */
    @Length(max = 1000)
    @Nullable
    private String seoDescription;

    /**
     * 作者
     */
    @Length(max = 50)
    @Nullable
    private String author;

    /**
     * 编辑
     */
    @Length(max = 50)
    @Nullable
    private String editor;

    /**
     * 来源
     */
    @Length(max = 50)
    @Nullable
    private String source;

    /**
     * 下线日期
     */
    @Nullable
    private OffsetDateTime offlineDate;

    /**
     * 置顶时间
     */
    @Nullable
    private OffsetDateTime stickyDate;

    /**
     * 图片
     */
    @Length(max = 255)
    @Nullable
    private String image;

    /**
     * 视频
     */
    @Length(max = 255)
    @Nullable
    private String video;

    /**
     * 原视频
     */
    @Length(max = 255)
    @Nullable
    private String videoOrig;

    /**
     * 视频时长
     */
    @Nullable
    private Integer videoDuration;

    /**
     * 音频
     */
    @Length(max = 255)
    @Nullable
    private String audio;

    /**
     * 原音频
     */
    @Length(max = 255)
    @Nullable
    private String audioOrig;

    /**
     * 音频时长
     */
    @Nullable
    private Integer audioDuration;

    /**
     * 文件
     */
    @Length(max = 255)
    @Nullable
    private String file;

    /**
     * 文件名称
     */
    @Length(max = 150)
    @Nullable
    private String fileName;

    /**
     * 文件大小
     */
    @Nullable
    private Long fileLength;

    /**
     * 文库
     */
    @Length(max = 255)
    @Nullable
    private String doc;

    /**
     * 文库原文档
     */
    @Length(max = 255)
    @Nullable
    private String docOrig;

    /**
     * 文库名称
     */
    @Length(max = 150)
    @Nullable
    private String docName;

    /**
     * 文库大小
     */
    @Nullable
    private Long docLength;

    /**
     * 独立模板
     */
    @Length(max = 255)
    @Nullable
    private String articleTemplate;

    /**
     * 是否允许评论
     */
    @NotNull
    private Boolean allowComment = true;

    /**
     * 静态页文件
     */
    @Length(max = 255)
    @Nullable
    private String staticFile;

    /**
     * 手机端静态页文件
     */
    @Length(max = 255)
    @Nullable
    private String mobileStaticFile;

    /**
     * 创建日期
     */
    @NotNull
    private OffsetDateTime created = OffsetDateTime.now();

    /**
     * 修改日期
     */
    @Nullable
    private OffsetDateTime modified;

    /**
     * 流程实例ID
     */
    @Length(max = 64)
    @Nullable
    private String processInstanceId;

    /**
     * 退回原因
     */
    @Length(max = 300)
    @Nullable
    private String rejectReason;

    /**
     * 是否百度推送
     */
    @NotNull
    private Boolean baiduPush = false;

    /**
     * 类型(常规:0,投稿:1,采集:2,接口:3,站内推送:4,站外推送:5)
     */
    @NotNull
    private Short type = 0;

    /**
     * 编辑器类型(1:富文本编辑器,2:Markdown编辑器)
     */
    @NotNull
    private Short editorType = 1;

    /**
     * 正文
     */
    @Nullable
    private String text;

    /**
     * Markdown正文
     */
    @Nullable
    private String markdown;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
    public String getAlias() {
        return alias;
    }

    public void setAlias(@Nullable String alias) {
        this.alias = alias;
    }

    @Nullable
    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(@Nullable String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public Boolean getTargetBlank() {
        return targetBlank;
    }

    public void setTargetBlank(Boolean targetBlank) {
        this.targetBlank = targetBlank;
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
    public OffsetDateTime getOfflineDate() {
        return offlineDate;
    }

    public void setOfflineDate(@Nullable OffsetDateTime offlineDate) {
        this.offlineDate = offlineDate;
    }

    @Nullable
    public OffsetDateTime getStickyDate() {
        return stickyDate;
    }

    public void setStickyDate(@Nullable OffsetDateTime stickyDate) {
        this.stickyDate = stickyDate;
    }

    @Nullable
    public String getImage() {
        return image;
    }

    public void setImage(@Nullable String image) {
        this.image = image;
    }

    @Nullable
    public String getVideo() {
        return video;
    }

    public void setVideo(@Nullable String video) {
        this.video = video;
    }

    @Nullable
    public String getVideoOrig() {
        return videoOrig;
    }

    public void setVideoOrig(@Nullable String videoOrig) {
        this.videoOrig = videoOrig;
    }

    @Nullable
    public Integer getVideoDuration() {
        return videoDuration;
    }

    public void setVideoDuration(@Nullable Integer videoDuration) {
        this.videoDuration = videoDuration;
    }

    @Nullable
    public String getAudio() {
        return audio;
    }

    public void setAudio(@Nullable String audio) {
        this.audio = audio;
    }

    @Nullable
    public String getAudioOrig() {
        return audioOrig;
    }

    public void setAudioOrig(@Nullable String audioOrig) {
        this.audioOrig = audioOrig;
    }

    @Nullable
    public Integer getAudioDuration() {
        return audioDuration;
    }

    public void setAudioDuration(@Nullable Integer audioDuration) {
        this.audioDuration = audioDuration;
    }

    @Nullable
    public String getFile() {
        return file;
    }

    public void setFile(@Nullable String file) {
        this.file = file;
    }

    @Nullable
    public String getFileName() {
        return fileName;
    }

    public void setFileName(@Nullable String fileName) {
        this.fileName = fileName;
    }

    @Nullable
    public Long getFileLength() {
        return fileLength;
    }

    public void setFileLength(@Nullable Long fileLength) {
        this.fileLength = fileLength;
    }

    @Nullable
    public String getDoc() {
        return doc;
    }

    public void setDoc(@Nullable String doc) {
        this.doc = doc;
    }

    @Nullable
    public String getDocOrig() {
        return docOrig;
    }

    public void setDocOrig(@Nullable String docOrig) {
        this.docOrig = docOrig;
    }

    @Nullable
    public String getDocName() {
        return docName;
    }

    public void setDocName(@Nullable String docName) {
        this.docName = docName;
    }

    @Nullable
    public Long getDocLength() {
        return docLength;
    }

    public void setDocLength(@Nullable Long docLength) {
        this.docLength = docLength;
    }

    @Nullable
    public String getArticleTemplate() {
        return articleTemplate;
    }

    public void setArticleTemplate(@Nullable String articleTemplate) {
        this.articleTemplate = articleTemplate;
    }

    public Boolean getAllowComment() {
        return allowComment;
    }

    public void setAllowComment(Boolean allowComment) {
        this.allowComment = allowComment;
    }

    @Nullable
    public String getStaticFile() {
        return staticFile;
    }

    public void setStaticFile(@Nullable String staticFile) {
        this.staticFile = staticFile;
    }

    @Nullable
    public String getMobileStaticFile() {
        return mobileStaticFile;
    }

    public void setMobileStaticFile(@Nullable String mobileStaticFile) {
        this.mobileStaticFile = mobileStaticFile;
    }

    public OffsetDateTime getCreated() {
        return created;
    }

    public void setCreated(OffsetDateTime created) {
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
    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(@Nullable String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    @Nullable
    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(@Nullable String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Boolean getBaiduPush() {
        return baiduPush;
    }

    public void setBaiduPush(Boolean baiduPush) {
        this.baiduPush = baiduPush;
    }

    public Short getType() {
        return type;
    }

    public void setType(Short type) {
        this.type = type;
    }

    public Short getEditorType() {
        return editorType;
    }

    public void setEditorType(Short editorType) {
        this.editorType = editorType;
    }

    @Nullable
    public String getText() {
        return text;
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    @Nullable
    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(@Nullable String markdown) {
        this.markdown = markdown;
    }
}