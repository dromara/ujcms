package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.base.ArticleBase;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.commons.db.order.OrderEntity;
import com.ujcms.commons.file.FilesEx;
import com.ujcms.commons.web.HtmlParserUtils;
import com.ujcms.commons.web.PageUrlResolver;
import com.ujcms.commons.web.Strings;
import com.ujcms.commons.web.Views;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hibernate.validator.constraints.Length;
import org.jsoup.Jsoup;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ujcms.cms.core.support.Constants.MAPPER;
import static com.ujcms.commons.web.Strings.formatDuration;

/**
 * 文章实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Article extends ArticleBase implements PageUrlResolver, Anchor, OrderEntity, Serializable {
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
                .map(html -> StringEscapeUtils.escapeHtml4(Jsoup.parse(html).body().text()))
                // 多个空格替换为一个空格，中文空格unicode为 \u3000
                .map(text -> text.replaceAll("[\\s\u3000]+", " "))
                .map(String::trim)
                .orElse("");
    }

    @Schema(description = "文件尺寸。自动使用合适的单位，如 KB、MB 等")
    public String getFileSize() {
        return FilesEx.getSize(getFileLength());
    }

    @Schema(description = "文章模板")
    public String getTemplate() {
        String template = getArticleTemplate();
        if (StringUtils.isBlank(template)) {
            template = getChannel().getArticleTemplate();
            // 默认模板名为 article
            if (StringUtils.isBlank(template)) {
                template = TEMPLATE_PREFIX;
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
        return getAttachmentUrls(getChannel().getArticleModel());
    }

    public List<String> getAttachmentUrls(Model model) {
        List<String> urls = new ArrayList<>();
        Optional.ofNullable(getExt().getImage()).ifPresent(urls::add);
        Optional.ofNullable(getExt().getVideo()).ifPresent(urls::add);
        Optional.ofNullable(getExt().getFile()).ifPresent(urls::add);
        Optional.ofNullable(getExt().getDoc()).ifPresent(urls::add);
        getImageList().forEach(image -> urls.add(image.getUrl()));
        getFileList().forEach(file -> urls.add(file.getUrl()));
        // 获取正文中的附件url
        urls.addAll(HtmlParserUtils.getUrls(getExt().getText()));
        // 获取自定义字段中的url
        urls.addAll(model.getUrlsFromMap(getCustoms()));
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


    /**
     * 根据上下线日期调整状态。
     *
     * <li>如果状态为已发布、已归档，则根据上下线时间调整为待发布、已下线或保持不变
     * <li>如果状态待发布，则根据上下线时间调整为已发布、已下线，或保持不变
     * <li>其它状态保持不变。
     *
     * @param status 状态
     */
    public void adjustStatus(Short status) {
        setStatus(status);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime onlineDate = getOnlineDate();
        OffsetDateTime offlineDate = getOfflineDate();
        if (status == STATUS_PUBLISHED || status == STATUS_ARCHIVED) {
            if (onlineDate != null && onlineDate.isAfter(now)) {
                setStatus(STATUS_READY);
            }
            if (offlineDate != null && !offlineDate.isAfter(now)) {
                setStatus(STATUS_OFFLINE);
            }
        } else if (status == STATUS_READY) {
            if (onlineDate == null || !onlineDate.isAfter(now)) {
                setStatus(STATUS_PUBLISHED);
            }
            if (offlineDate != null && !offlineDate.isAfter(now)) {
                setStatus(STATUS_OFFLINE);
            }
        }
    }

    /**
     * 根据上下线日期调整自身的状态
     *
     * @see #adjustStatus(Short)
     */
    public void adjustStatus() {
        adjustStatus(getStatus());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Article bean = (Article) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
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
        if (StringUtils.isNotBlank(linkUrl)) {
            return getSite().assembleLinkUrl(linkUrl);
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
        String linkUrl = getLinkUrl();
        if (StringUtils.isNotBlank(linkUrl)) {
            return getSite().assembleLinkUrl(linkUrl);
        }
        return Contexts.isMobile() ? getMobileStaticUrl(page) : getNormalStaticUrl(page);
    }

    public String getNormalStaticUrl(int page) {
        return getSite().getNormalStaticUrl(getArticleUrl(page));
    }

    public String getMobileStaticUrl(int page) {
        return getSite().getMobileStaticUrl(getArticleUrl(page));
    }

    private String getArticleUrl(int page) {
        return getSite().getHtml().getArticleUrl(getArticleStaticPath(),
                getChannelId(), getChannel().getAlias(), getId(), getAlias(), getCreated(), page);
    }

    public String getNormalStaticPath(int page) {
        return getSite().getNormalStaticPath(getArticlePath(page));
    }

    public String getMobileStaticPath(int page) {
        return getSite().getMobileStaticPath(getArticlePath(page));
    }

    private String getArticlePath(int page) {
        return getSite().getHtml().getArticlePath(getArticleStaticPath(),
                getChannelId(), getChannel().getAlias(), getId(), getAlias(), getCreated(), page);
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
    private transient List<ArticleImage> imageList;
    /**
     * 文件列表
     */
    @Nullable
    private transient List<ArticleFile> fileList;
    /**
     * 自定义字段
     */
    @Nullable
    private transient Map<String, Object> customs;

    public List<ArticleImage> getImageList() {
        if (imageList == null) {
            String json = getImageListJson();
            if (StringUtils.isBlank(json)) {
                return Collections.emptyList();
            }
            try {
                imageList = MAPPER.readValue(json, new TypeReference<List<ArticleImage>>() {
                });
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        return imageList;
    }

    public void setImageList(List<ArticleImage> imageList) {
        this.imageList = imageList;
        try {
            setImageListJson(MAPPER.writeValueAsString(imageList));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of List<ArticleImage>", e);
        }
    }

    public List<ArticleFile> getFileList() {
        if (fileList == null) {
            String json = getFileListJson();
            if (StringUtils.isBlank(json)) {
                return Collections.emptyList();
            }
            try {
                fileList = MAPPER.readValue(json, new TypeReference<List<ArticleFile>>() {
                });
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        return fileList;
    }

    public void setFileList(List<ArticleFile> fileList) {
        this.fileList = fileList;
        try {
            setFileListJson(MAPPER.writeValueAsString(fileList));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of List<ArticleFile>", e);
        }
    }

    public Map<String, Object> getCustoms() {
        if (customs != null) {
            return customs;
        }
        customs = new HashMap<>(16);
        Model model = getChannel().getArticleModel();
        customs.putAll(model.assembleMap(getMainsJson()));
        customs.putAll(model.assembleMap(getClobsJson()));
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
     * 站点
     */
    @JsonIncludeProperties({"id", "name", "url"})
    private Site site = new Site();
    /**
     * 栏目
     */
    @JsonIncludeProperties({"id", "name", "url", "paths", "articleModelId", "nav", "orderDesc"})
    private Channel channel = new Channel();
    /**
     * 组织
     */
    @JsonIncludeProperties({"id", "name", "paths"})
    private Org org = new Org();
    /**
     * 创建者
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    private User user = new User();
    /**
     * 修改者
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    private User modifiedUser = new User();
    /**
     * 区块项列表
     */
    @JsonIncludeProperties({"id", "title", "enabled", "block"})
    private List<BlockItem> blockItemList = Collections.emptyList();
    /**
     * TAG列表
     */
    @JsonIncludeProperties({"id", "name"})
    private List<Tag> tags = new ArrayList<>();
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

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
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
        Article articleSrc = getSrc();
        if (getType() == TYPE_REFER && articleSrc != null) {
            return articleSrc.getUrl();
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

    @Schema(description = "源视频URL")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getVideoOrig() {
        return getExt().getVideoOrig();
    }

    public void setVideoOrig(@Nullable String videoOrig) {
        getExt().setVideoOrig(videoOrig);
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

    @Schema(description = "源音频URL")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getAudioOrig() {
        return getExt().getAudioOrig();
    }

    public void setAudioOrig(@Nullable String audioOrig) {
        getExt().setAudioOrig(audioOrig);
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

    @Schema(description = "源文库URL")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getDocOrig() {
        return getExt().getDocOrig();
    }

    public void setDocOrig(@Nullable String docOrig) {
        getExt().setDocOrig(docOrig);
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

    @Schema(description = "文章独立模板")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getArticleTemplate() {
        return getExt().getArticleTemplate();
    }

    public void setArticleTemplate(@Nullable String articleTemplate) {
        getExt().setArticleTemplate(articleTemplate);
    }

    @Schema(description = "文章独立静态路径")
    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.)[\\w-{}/]*$")
    public String getArticleStaticPath() {
        return getExt().getArticleStaticPath();
    }

    public void setArticleStaticPath(@Nullable String articleStaticPath) {
        getExt().setArticleStaticPath(articleStaticPath);
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

    @JsonIgnore
    @Nullable
    public String getMainsJson() {
        return getExt().getMainsJson();
    }

    public void setMainsJson(String mainsJson) {
        getExt().setMainsJson(mainsJson);
    }

    @JsonIgnore
    @Nullable
    public String getClobsJson() {
        return getExt().getClobsJson();
    }

    public void setClobsJson(String clobsJson) {
        getExt().setClobsJson(clobsJson);
    }
    // endregion

    // region ArticleBuffer

    @Schema(description = "评论次数")
    public int getComments() {
        return getExt().getComments();
    }

    @Schema(description = "下载次数")
    public int getDownloads() {
        return getExt().getDownloads();
    }

    @Schema(description = "收藏次数")
    public int getFavorites() {
        return getExt().getFavorites();
    }

    @Schema(description = "顶次数")
    public int getUps() {
        return getExt().getUps();
    }

    @Schema(description = "踩次数")
    public int getDowns() {
        return getExt().getDowns();
    }

    @Schema(description = "浏览次数")
    public long getViews() {
        return getExt().getViews();
    }

    public void setViews(long views) {
        getExt().setViews(views);
    }

    @Schema(description = "日浏览次数")
    public int getDayViews() {
        return getExt().getDayViews();
    }

    @Schema(description = "周浏览次数")
    public int getWeekViews() {
        return getExt().getWeekViews();
    }

    @Schema(description = "月浏览次数")
    public int getMonthViews() {
        return getExt().getMonthViews();
    }

    @Schema(description = "季浏览次数")
    public int getQuarterViews() {
        return getExt().getQuarterViews();
    }

    @Schema(description = "年浏览次数")
    public long getYearViews() {
        return getExt().getYearViews();
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
     * 动作类型：顶踩
     */
    public static final String ACTION_TYPE_UP_DOWN = "ArticleUpDown";
    /**
     * 动作选项：顶
     */
    public static final String ACTION_OPTION_UP = "up";
    /**
     * 动作选项：踩
     */
    public static final String ACTION_OPTION_DOWN = "down";
    /**
     * 文章审核流程类型
     */
    public static final String PROCESS_CATEGORY = "sys_article";
    public static final String PROCESS_VARIABLE_CHANNEL_ID = "processChannelId";
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

    /**
     * 编辑器类型：富文本编辑器
     */
    public static final short EDITOR_TYPE_HTML = 1;
    /**
     * 编辑器类型：Markdown编辑器
     */
    public static final short EDITOR_TYPE_MARKDOWN = 2;


    /**
     * 文章模型类别
     */
    public static final String MODEL_TYPE = "article";
    /**
     * 文章模板前缀
     */
    public static final String TEMPLATE_PREFIX = "article";

    public static final String NOT_FOUND = "Article not found. ID : ";
    // endregion
}