package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.base.ArticleBase;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.file.FilesEx;
import com.ujcms.util.web.HtmlParserUtils;
import com.ujcms.util.web.PageUrlResolver;
import com.ujcms.util.web.Strings;
import com.ujcms.util.web.Views;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.jsoup.Jsoup;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.ujcms.util.web.Strings.formatDuration;

/**
 * 文章实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Article extends ArticleBase implements PageUrlResolver, Anchor, Serializable {
    private static final long serialVersionUID = 1L;
    // region Normal

    @JsonIgnore
    @Override
    public String getName() {
        return getTitle();
    }

    @Schema(description = "摘要。获取`seoDescription`，如不存在，则从正文中截取。最长 255 个字符（一个中文占两个字符）。")
    public String getDescription() {
        return Optional.ofNullable(getExt().getSeoDescription())
                .orElseGet(() -> Strings.substring(getPlainText(), 255));
    }

    @Schema(description = "纯文本格式的正文")
    @JsonView(Views.Whole.class)
    public String getPlainText() {
        return Optional.ofNullable(getText())
                .map(html -> Jsoup.parse(html).body().text())
                // 多个空格替换为一个空格，中文空格unicode为 \u3000
                .map(text -> text.replaceAll("[ \\u3000]+", " "))
                .map(String::trim)
                .orElse("");
    }

    @Schema(description = "文件尺寸。自动使用合适的单位，如 KB、MB 等")
    public String getFileSize() {
        return FilesEx.getSize(getFileLength());
    }

    @JsonIgnore
    public String getTemplate() {
        String template = getArticleTemplate();
        if (StringUtils.isBlank(template)) {
            template = getChannel().getArticleTemplate();
            // 默认模板名为 article
            if (StringUtils.isBlank(template)) {
                template = "article";
            }
        }
        return getSite().assembleTemplate(template);
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
        Optional.ofNullable(getExt().getVideo()).ifPresent(urls::add);
        Optional.ofNullable(getExt().getFile()).ifPresent(urls::add);
        Optional.ofNullable(getExt().getDoc()).ifPresent(urls::add);
        getImageList().forEach(image -> urls.add(image.getUrl()));
        getFileList().forEach(file -> urls.add(file.getUrl()));
        // 获取正文中的附件url
        urls.addAll(HtmlParserUtils.getUrls(getExt().getText()));
        getChannel().getArticleModel().handleCustoms(getCustomList(), new Model.GetUrlsHandle(urls));
        return urls;
    }

    @Schema(description = "区块列表")
    @JsonIncludeProperties({"id", "name"})
    public List<Block> getBlocks() {
        List<Block> blocks = new ArrayList<>(getBlockItemList().size());
        for (BlockItem item : getBlockItemList()) {
            blocks.add(item.getBlock());
        }
        return blocks;
    }

    @Schema(description = "TAG列表")
    public List<Tag> getTags() {
        List<Tag> tags = new ArrayList<>(getArticleTagList().size());
        for (ArticleTag articleTag : getArticleTagList()) {
            tags.add(articleTag.getTag());
        }
        return tags;
    }

    @Schema(description = "TAG名称列表")
    public List<String> getTagNames() {
        return getTags().stream().map(Tag::getName).collect(Collectors.toList());
    }

    @Schema(description = "是否正常状态（可访问）")
    public boolean isNormal() {
        return getStatus() == STATUS_PUBLISHED || getStatus() == STATUS_ARCHIVED;
    }

    @Schema(description = "是否可编辑")
    public boolean isEditable() {
        return StringUtils.isBlank(getChannel().getProcessKey()) ||
                (getStatus() != STATUS_PUBLISHED && getStatus() != STATUS_ARCHIVED && getStatus() != STATUS_READY
                        && getStatus() != STATUS_REVIEWING);
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
        if (StringUtils.isNotBlank(getLinkUrl())) {
            return getSite().assembleLinkUrl(getLinkUrl());
        }
        StringBuilder buff = new StringBuilder(getSite().getDynamicUrl());
        String prefix = Optional.ofNullable(getSite().getConfig().getArticleUrl()).orElse(UrlConstants.ARTICLE);
        buff.append(prefix).append("/").append(getId());
        String alias = getExt().getAlias();
        if (alias != null) {
            buff.append("/").append(alias);
        }
        if (page > 1) {
            buff.append("/").append(page);
        }
        return buff.toString();
    }

    public String getStaticUrl(int page) {
        if (StringUtils.isNotBlank(getLinkUrl())) {
            return getSite().assembleLinkUrl(getLinkUrl());
        }
        return Contexts.isMobile() ? getMobileStaticUrl(page) : getNormalStaticUrl(page);
    }

    public String getNormalStaticUrl(int page) {
        return getSite().getNormalStaticUrl(getArticlePath(page));
    }

    public String getMobileStaticUrl(int page) {
        return getSite().getMobileStaticUrl(getArticlePath(page));
    }

    private String getArticlePath(int page) {
        return getSite().getHtml().getArticlePath(
                getChannelId(), getChannel().getAlias(), getId(), getAlias(), getCreated(), page);
    }

    public String getNormalStaticPath(int page) {
        return getSite().getNormalStaticPath(getArticlePath(page));
    }

    public String getMobileStaticPath(int page) {
        return getSite().getMobileStaticPath(getArticlePath(page));
    }

    @Schema(description = "是否是链接")
    public boolean isLink() {
        return StringUtils.isNotBlank(getLinkUrl());
    }
    // endregion

    // region TempFields

    @Schema(description = "任务ID")
    @Nullable
    private String taskId;

    @Nullable
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(@Nullable String taskId) {
        this.taskId = taskId;
    }
    // endregion

    // region JsonFields
    /**
     * 图片列表
     */
    @Nullable
    private List<ArticleImage> imageList;
    /**
     * 文件列表
     */
    @Nullable
    private List<ArticleFile> fileList;
    /**
     * 自定义字段
     */
    @Nullable
    private Map<String, Object> customs;

    public List<ArticleImage> getImageList() {
        if (imageList == null) {
            if (StringUtils.isNotBlank(getImageListJson())) {
                try {
                    imageList = Constants.MAPPER.readValue(getImageListJson(), new TypeReference<List<ArticleImage>>() {
                    });
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            } else {
                imageList = Collections.emptyList();
            }
        }
        return imageList;
    }

    public void setImageList(List<ArticleImage> imageList) {
        this.imageList = imageList;
        try {
            setImageListJson(Constants.MAPPER.writeValueAsString(imageList));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of List<ArticleImage>", e);
        }
    }

    public List<ArticleFile> getFileList() {
        if (fileList == null) {
            if (StringUtils.isNotBlank(getFileListJson())) {
                try {
                    fileList = Constants.MAPPER.readValue(getFileListJson(), new TypeReference<List<ArticleFile>>() {
                    });
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            } else {
                fileList = Collections.emptyList();
            }
        }
        return fileList;
    }

    public void setFileList(List<ArticleFile> fileList) {
        this.fileList = fileList;
        try {
            setFileListJson(Constants.MAPPER.writeValueAsString(fileList));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of List<ArticleFile>", e);
        }
    }

    public Map<String, Object> getCustoms() {
        if (customs == null) {
            customs = getChannel().getArticleModel().assembleCustoms(getCustomList());
        }
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        this.customs = customs;
    }

    public static List<ArticleCustom> disassembleCustoms(Model model, Integer id, Map<String, Object> customs) {
        List<ArticleCustom> list = new ArrayList<>();
        model.disassembleCustoms(customs, (name, type, value) ->
                list.add(new ArticleCustom(id, name, type, value)));
        return list;
    }
    // endregion

    // region Associations
    /**
     * 源文章
     */
    @Nullable
    private Article src;
    /**
     * 文章扩展对象
     */
    @JsonIgnore
    private ArticleExt ext = new ArticleExt();
    /**
     * 文章缓冲对象
     */
    @JsonIgnore
    private ArticleBuffer buffer = new ArticleBuffer();
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name", "url"})
    private Site site = new Site();
    /**
     * 栏目
     */
    @JsonIncludeProperties({"id", "name", "url", "paths", "articleModelId"})
    private Channel channel = new Channel();
    /**
     * 组织
     */
    @JsonIncludeProperties({"id", "name"})
    private Org org = new Org();
    /**
     * 创建者
     */
    @JsonIncludeProperties({"id", "username"})
    private User user = new User();
    /**
     * 修改者
     */
    @JsonIncludeProperties({"id", "username"})
    private User modifiedUser = new User();
    /**
     * 自定义字段列表
     */
    @JsonIgnore
    private List<ArticleCustom> customList = Collections.emptyList();
    /**
     * 区块项列表
     */
    @JsonIncludeProperties({"id", "title", "enabled", "block"})
    private List<BlockItem> blockItemList = Collections.emptyList();
    /**
     * TAG列表
     */
    @JsonIgnore
    private List<ArticleTag> articleTagList = new ArrayList<>();
    /**
     * 引用列表
     */
    @JsonView(Views.Whole.class)
    @JsonIncludeProperties({"id", "title", "channel", "site", "type"})
    private List<Article> destList = Collections.emptyList();

    @Nullable
    public Article getSrc() {
        return src;
    }

    public void setSrc(@Nullable Article src) {
        this.src = src;
    }

    public ArticleExt getExt() {
        return ext;
    }

    public void setExt(ArticleExt ext) {
        this.ext = ext;
    }

    public ArticleBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(ArticleBuffer buffer) {
        this.buffer = buffer;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public List<ArticleCustom> getCustomList() {
        return customList;
    }

    public void setCustomList(List<ArticleCustom> customList) {
        this.customList = customList;
    }

    public Org getOrg() {
        return org;
    }

    public void setOrg(Org org) {
        this.org = org;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getModifiedUser() {
        return modifiedUser;
    }

    public void setModifiedUser(User modifiedUser) {
        this.modifiedUser = modifiedUser;
    }

    public List<BlockItem> getBlockItemList() {
        return blockItemList;
    }

    public void setBlockItemList(List<BlockItem> blockItemList) {
        this.blockItemList = blockItemList;
    }

    public List<ArticleTag> getArticleTagList() {
        return articleTagList;
    }

    public void setArticleTagList(List<ArticleTag> articleTagList) {
        this.articleTagList = articleTagList;
    }

    public List<Article> getDestList() {
        return destList;
    }

    public void setDestList(List<Article> destList) {
        this.destList = destList;
    }
    // endregion

    // region ArticleExt

    @Schema(description = "标题")
    public String getTitle() {
        return getExt().getTitle();
    }

    public void setTitle(String title) {
        getExt().setTitle(title);
    }

    @Schema(description = "副标题")
    @Nullable
    public String getSubtitle() {
        return getExt().getSubtitle();
    }

    public void setSubtitle(@Nullable String subtitle) {
        getExt().setSubtitle(subtitle);
    }

    @Schema(description = "完整标题")
    @Nullable
    public String getFullTitle() {
        return getExt().getFullTitle();
    }

    public void setFullTitle(@Nullable String fullTitle) {
        getExt().setFullTitle(fullTitle);
    }

    @Schema(description = "别名")
    @Nullable
    @Pattern(regexp = "^[\\w-]*$")
    public String getAlias() {
        return getExt().getAlias();
    }

    public void setAlias(@Nullable String alias) {
        getExt().setAlias(alias);
    }

    @Schema(description = "跳转链接")
    @Nullable
    @Pattern(regexp = "^(http|/).*$")
    public String getLinkUrl() {
        if (getType() == TYPE_REFER && getSrc() != null) {
            return getSrc().getUrl();
        }
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

    @Schema(description = "SEO关键字")
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

    @Schema(description = "作者")
    @Nullable
    public String getAuthor() {
        return getExt().getAuthor();
    }

    public void setAuthor(@Nullable String author) {
        getExt().setAuthor(author);
    }

    @Schema(description = "编辑")
    @Nullable
    public String getEditor() {
        return getExt().getEditor();
    }

    public void setEditor(@Nullable String editor) {
        getExt().setEditor(editor);
    }

    @Schema(description = "来源")
    @Nullable
    public String getSource() {
        return getExt().getSource();
    }

    public void setSource(@Nullable String source) {
        getExt().setSource(source);
    }

    @Schema(description = "下线日期")
    @Nullable
    public OffsetDateTime getOfflineDate() {
        return getExt().getOfflineDate();
    }

    public void setOfflineDate(@Nullable OffsetDateTime offlineDate) {
        getExt().setOfflineDate(offlineDate);
    }

    @Schema(description = "置顶日期")
    @Nullable
    public OffsetDateTime getStickyDate() {
        return getExt().getStickyDate();
    }

    public void setStickyDate(@Nullable OffsetDateTime stickyDate) {
        getExt().setStickyDate(stickyDate);
    }

    @JsonIgnore
    @Nullable
    public String getImageListJson() {
        return getExt().getImageListJson();
    }

    public void setImageListJson(String imageListJson) {
        getExt().setImageListJson(imageListJson);
    }

    @JsonIgnore
    @Nullable
    public String getFileListJson() {
        return getExt().getFileListJson();
    }

    public void setFileListJson(String fileListJson) {
        getExt().setFileListJson(fileListJson);
    }

    @Schema(description = "图片URL")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getImage() {
        return getExt().getImage();
    }

    public void setImage(@Nullable String image) {
        getExt().setImage(image);
    }

    @Schema(description = "视频URL")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getVideo() {
        return getExt().getVideo();
    }

    public void setVideo(@Nullable String video) {
        getExt().setVideo(video);
    }

    @Schema(description = "视频时长")
    @Nullable
    public Integer getVideoDuration() {
        return getExt().getVideoDuration();
    }

    public void setVideoDuration(@Nullable Integer videoDuration) {
        getExt().setVideoDuration(videoDuration);
    }

    @Schema(description = "视频时长。字符串格式，如：`20:15`")
    @Nullable
    public String getVideoTime() {
        Integer duration = getVideoDuration();
        if (duration == null) {
            return null;
        }
        return formatDuration(duration);
    }

    @Schema(description = "音频URL")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getAudio() {
        return getExt().getAudio();
    }

    public void setAudio(@Nullable String audio) {
        getExt().setAudio(audio);
    }

    @Schema(description = "音频时长")
    @Nullable
    public Integer getAudioDuration() {
        return getExt().getAudioDuration();
    }

    public void setAudioDuration(@Nullable Integer audioDuration) {
        getExt().setAudioDuration(audioDuration);
    }

    @Schema(description = "音频时长。字符串格式，如：`20:15`")
    @Nullable
    public String getAudioTime() {
        Integer duration = getAudioDuration();
        if (duration == null) {
            return null;
        }
        return formatDuration(duration);
    }

    @Schema(description = "文件URL")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getFile() {
        return getExt().getFile();
    }

    public void setFile(@Nullable String file) {
        getExt().setFile(file);
    }

    @Schema(description = "文件名称")
    @Nullable
    public String getFileName() {
        return getExt().getFileName();
    }

    public void setFileName(@Nullable String fileName) {
        getExt().setFileName(fileName);
    }

    @Schema(description = "文件长度")
    @Nullable
    public Long getFileLength() {
        return getExt().getFileLength();
    }

    public void setFileLength(@Nullable Long fileLength) {
        getExt().setFileLength(fileLength);
    }

    @Schema(description = "文库URL")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getDoc() {
        return getExt().getDoc();
    }

    public void setDoc(@Nullable String doc) {
        getExt().setDoc(doc);
    }

    @Schema(description = "文库名称")
    @Nullable
    public String getDocName() {
        return getExt().getDocName();
    }

    public void setDocName(@Nullable String docName) {
        getExt().setDocName(docName);
    }

    @Schema(description = "文库长度")
    @Nullable
    public Long getDocLength() {
        return getExt().getDocLength();
    }

    public void setDocLength(@Nullable Long docLength) {
        getExt().setDocLength(docLength);
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

    @Schema(description = "是否允许评论")
    public Boolean getAllowComment() {
        return getExt().getAllowComment();
    }

    public void setAllowComment(boolean allowComment) {
        getExt().setAllowComment(allowComment);
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

    @Schema(description = "创建日期")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public OffsetDateTime getCreated() {
        return getExt().getCreated();
    }

    public void setCreated(OffsetDateTime created) {
        getExt().setCreated(created);
    }

    @Schema(description = "修改日期")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Nullable
    public OffsetDateTime getModified() {
        return getExt().getModified();
    }

    public void setModified(@Nullable OffsetDateTime modified) {
        getExt().setModified(modified);
    }

    @Schema(description = "流程实例ID")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Nullable
    public String getProcessInstanceId() {
        return getExt().getProcessInstanceId();
    }

    public void setProcessInstanceId(@Nullable String processInstanceId) {
        getExt().setProcessInstanceId(processInstanceId);
    }

    @Schema(description = "审核拒绝原因")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Nullable
    public String getRejectReason() {
        return getExt().getRejectReason();
    }

    public void setRejectReason(String rejectReason) {
        getExt().setRejectReason(rejectReason);
    }

    @Schema(description = "是否推送到百度")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Boolean getBaiduPush() {
        return getExt().getBaiduPush();
    }

    public void setBaiduPush(Boolean baiduPush) {
        getExt().setBaiduPush(baiduPush);
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
    public Short getEditorType() {
        return getExt().getEditorType();
    }

    public void setEditorType(Short editorType) {
        getExt().setEditorType(editorType);
    }
    // endregion

    // region ArticleBuffer

    @Schema(description = "评论次数")
    public int getComments() {
        return getBuffer().getComments();
    }

    @Schema(description = "下载次数")
    public int getDownloads() {
        return getBuffer().getDownloads();
    }

    @Schema(description = "收藏次数")
    public int getFavorites() {
        return getBuffer().getFavorites();
    }

    @Schema(description = "顶次数")
    public int getUps() {
        return getBuffer().getUps();
    }

    @Schema(description = "踩次数")
    public int getDowns() {
        return getBuffer().getDowns();
    }

    @Schema(description = "浏览次数")
    public long getViews() {
        return getBuffer().getViews();
    }

    public void setViews(long views) {
        getBuffer().setViews(views);
    }

    @Schema(description = "日浏览次数")
    public int getDayViews() {
        return getBuffer().getDayViews();
    }

    @Schema(description = "周浏览次数")
    public int getWeekViews() {
        return getBuffer().getWeekViews();
    }

    @Schema(description = "月浏览次数")
    public int getMonthViews() {
        return getBuffer().getMonthViews();
    }

    @Schema(description = "季浏览次数")
    public int getQuarterViews() {
        return getBuffer().getQuarterViews();
    }

    @Schema(description = "年浏览次数")
    public long getYearViews() {
        return getBuffer().getYearViews();
    }
    // endregion

    /**
     * 文章图片集实体类
     */
    public static class ArticleImage {
        /**
         * 图片名称
         */
        @Length(max = 150)
        @Nullable
        private String name;
        /**
         * 图片描述
         */
        @Length(max = 1000)
        @Nullable
        private String description;
        /**
         * 图片URL
         */
        @Length(max = 255)
        @NotNull
        private String url = "";

        @Nullable
        public String getName() {
            return name;
        }

        public void setName(@Nullable String name) {
            this.name = name;
        }

        @Nullable
        public String getDescription() {
            return description;
        }

        public void setDescription(@Nullable String description) {
            this.description = description;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * 文章文件集实体类
     */
    public static class ArticleFile {
        /**
         * 文件名称
         */
        @Length(max = 150)
        @NotNull
        private String name = "";
        /**
         * 文件URL
         */
        @Length(max = 255)
        @NotNull
        private String url = "";
        /**
         * 文件大小
         */
        @NotNull
        private Long length = 0L;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Long getLength() {
            return length;
        }

        public void setLength(Long length) {
            this.length = length;
        }
    }

    // region StaticFields
    /**
     * 文章审核流程类型
     */
    public static final String PROCESS_CATEGORY = "sys_article";
    public static final String PROCESS_VARIABLE_CHANNEL_ID = "channelId";
    public static final String PROCESS_VARIABLE_ORG_ID = "orgId";
    public static final String PROCESS_VARIABLE_USER_ID = "userId";
    /**
     * 状态：已发布
     */
    public static final short STATUS_PUBLISHED = 0;
    /**
     * 状态：已归档
     */
    public static final short STATUS_ARCHIVED = 1;
    /**
     * 状态：待发布
     */
    public static final short STATUS_READY = 5;

    /**
     * 状态：草稿
     */
    public static final short STATUS_DRAFT = 10;
    /**
     * 状态：待审核
     */
    public static final short STATUS_PENDING = 11;
    /**
     * 状态：审核中
     */
    public static final short STATUS_REVIEWING = 12;

    /**
     * 状态：已删除
     */
    public static final short STATUS_DELETED = 20;
    /**
     * 状态：已下线
     */
    public static final short STATUS_OFFLINE = 21;
    /**
     * 状态：已退回
     */
    public static final short STATUS_REJECTED = 22;

    /**
     * 类型：常规
     */
    public static final short TYPE_NORMAL = 0;
    /**
     * 类型：复制
     */
    public static final short TYPE_COPY = 1;
    /**
     * 类型：映射
     */
    public static final short TYPE_MAP = 2;
    /**
     * 类型：引用
     */
    public static final short TYPE_REFER = 3;

    /**
     * 录入类型：常规
     */
    public static final short INPUT_TYPE_NORMAL = 0;
    /**
     * 录入类型：投稿
     */
    public static final short INPUT_TYPE_CONTRIBUTE = 1;
    /**
     * 录入类型：采集
     */
    public static final short INPUT_TYPE_COLLECTION = 2;
    /**
     * 录入类型：接口
     */
    public static final short INPUT_TYPE_INTERFACE = 3;
    /**
     * 录入类型：站内推送
     */
    public static final short INPUT_TYPE_INTERNAL_PUSH = 4;
    /**
     * 录入类型：站群推送
     */
    public static final short INPUT_TYPE_EXTERNAL_PUSH = 5;
    // endregion
}