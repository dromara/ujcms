package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.ArticleBase;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.file.FilesEx;
import com.ujcms.util.web.HtmlParserUtils;
import com.ujcms.util.web.PageUrlResolver;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.lang.Nullable;

import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.util.web.Strings.formatDuration;

/**
 * 文章 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties("handler")
public class Article extends ArticleBase implements PageUrlResolver, Anchor, Serializable {
    // region Normal

    @JsonIgnore
    @Override
    public String getName() {
        return getTitle();
    }

    /**
     * 获取 seoDescription，如不存在，则从正文中截取（最长 1000 字符）。
     */
    public String getDescription() {
        return Optional.ofNullable(getExt().getSeoDescription())
                .orElseGet(() -> StringUtils.substring(getPlainText(), 0, 1000));
    }

    /**
     * 获取纯文本格式的正文
     */
    public String getPlainText() {
        return Optional.ofNullable(getText())
                .map(html -> Jsoup.parse(html).body().text())
                // 多个空格替换为一个空格，中文空格unicode为 \u3000
                .map(text -> text.replaceAll("[ \\u3000]+", " "))
                .map(String::trim)
                .orElse("");
    }

    /**
     * 获取文件尺寸。自动使用合适的单位，如 KB、MB 等。
     */
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

    /**
     * 获取区块列表
     *
     * @return 区块列表
     */
    @JsonIncludeProperties({"id", "name"})
    public List<Block> getBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (BlockItem item : getBlockItemList()) {
            blocks.add(item.getBlock());
        }
        return blocks;
    }

    /**
     * 是否正常状态（可访问）
     */
    public boolean isNormal() {
        return getStatus() == STATUS_PUBLISHED || getStatus() == STATUS_ARCHIVED;
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

    /**
     * 是否是链接
     */
    public boolean isLink() {
        return StringUtils.isNotBlank(getExt().getLinkUrl());
    }
    // endregion

    // region TempFields

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

    @Nullable
    private Map<String, Object> customs;
    // endregion

    // region Associations
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
    @JsonIncludeProperties({"id", "name", "url", "path", "articleModelId"})
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
     * 图片列表
     */
    private List<ArticleImage> imageList = Collections.emptyList();
    /**
     * 文件列表
     */
    private List<ArticleFile> fileList = Collections.emptyList();
    /**
     * 区块项列表
     */
    @JsonIncludeProperties({"id", "title", "block"})
    private List<BlockItem> blockItemList = Collections.emptyList();

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

    public List<ArticleImage> getImageList() {
        return imageList;
    }

    public void setImageList(List<ArticleImage> imageList) {
        this.imageList = imageList;
    }

    public List<ArticleFile> getFileList() {
        return fileList;
    }

    public void setFileList(List<ArticleFile> fileList) {
        this.fileList = fileList;
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
    // endregion

    // region ArticleExt

    public String getTitle() {
        return getExt().getTitle();
    }

    public void setTitle(String title) {
        getExt().setTitle(title);
    }

    @Nullable
    public String getSubtitle() {
        return getExt().getSubtitle();
    }

    public void setSubtitle(@Nullable String subtitle) {
        getExt().setSubtitle(subtitle);
    }

    @Nullable
    public String getFullTitle() {
        return getExt().getFullTitle();
    }

    public void setFullTitle(@Nullable String fullTitle) {
        getExt().setFullTitle(fullTitle);
    }

    @Nullable
    @Pattern(regexp = "^[\\w-]*$")
    public String getAlias() {
        return getExt().getAlias();
    }

    public void setAlias(@Nullable String alias) {
        getExt().setAlias(alias);
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
    public String getAuthor() {
        return getExt().getAuthor();
    }

    public void setAuthor(@Nullable String author) {
        getExt().setAuthor(author);
    }

    @Nullable
    public String getEditor() {
        return getExt().getEditor();
    }

    public void setEditor(@Nullable String editor) {
        getExt().setEditor(editor);
    }

    @Nullable
    public String getSource() {
        return getExt().getSource();
    }

    public void setSource(@Nullable String source) {
        getExt().setSource(source);
    }

    @Nullable
    public OffsetDateTime getOfflineDate() {
        return getExt().getOfflineDate();
    }

    public void setOfflineDate(@Nullable OffsetDateTime offlineDate) {
        getExt().setOfflineDate(offlineDate);
    }

    @Nullable
    public OffsetDateTime getStickyDate() {
        return getExt().getStickyDate();
    }

    public void setStickyDate(@Nullable OffsetDateTime stickyDate) {
        getExt().setStickyDate(stickyDate);
    }

    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getImage() {
        return getExt().getImage();
    }

    public void setImage(@Nullable String image) {
        getExt().setImage(image);
    }

    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getVideo() {
        return getExt().getVideo();
    }

    public void setVideo(@Nullable String video) {
        getExt().setVideo(video);
    }

    @Nullable
    public Integer getVideoDuration() {
        return getExt().getVideoDuration();
    }

    public void setVideoDuration(@Nullable Integer videoDuration) {
        getExt().setVideoDuration(videoDuration);
    }

    @Nullable
    public String getVideoTime() {
        Integer duration = getVideoDuration();
        if (duration == null) {
            return null;
        }
        return formatDuration(duration);
    }

    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getAudio() {
        return getExt().getAudio();
    }

    public void setAudio(@Nullable String audio) {
        getExt().setAudio(audio);
    }

    @Nullable
    public Integer getAudioDuration() {
        return getExt().getAudioDuration();
    }

    public void setAudioDuration(@Nullable Integer audioDuration) {
        getExt().setAudioDuration(audioDuration);
    }

    @Nullable
    public String getAudioTime() {
        Integer duration = getAudioDuration();
        if (duration == null) {
            return null;
        }
        return formatDuration(duration);
    }

    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getFile() {
        return getExt().getFile();
    }

    public void setFile(@Nullable String file) {
        getExt().setFile(file);
    }

    @Nullable
    public String getFileName() {
        return getExt().getFileName();
    }

    public void setFileName(@Nullable String fileName) {
        getExt().setFileName(fileName);
    }

    @Nullable
    public Long getFileLength() {
        return getExt().getFileLength();
    }

    public void setFileLength(@Nullable Long fileLength) {
        getExt().setFileLength(fileLength);
    }

    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getDoc() {
        return getExt().getDoc();
    }

    public void setDoc(@Nullable String doc) {
        getExt().setDoc(doc);
    }

    @Nullable
    public String getDocName() {
        return getExt().getDocName();
    }

    public void setDocName(@Nullable String docName) {
        getExt().setDocName(docName);
    }

    @Nullable
    public Long getDocLength() {
        return getExt().getDocLength();
    }

    public void setDocLength(@Nullable Long docLength) {
        getExt().setDocLength(docLength);
    }

    @Nullable
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getArticleTemplate() {
        return getExt().getArticleTemplate();
    }

    public void setArticleTemplate(@Nullable String articleTemplate) {
        getExt().setArticleTemplate(articleTemplate);
    }

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

    public OffsetDateTime getCreated() {
        return getExt().getCreated();
    }

    public void setCreated(OffsetDateTime created) {
        getExt().setCreated(created);
    }

    @Nullable
    public OffsetDateTime getModified() {
        return getExt().getModified();
    }

    public void setModified(@Nullable OffsetDateTime modified) {
        getExt().setModified(modified);
    }

    @Nullable
    public String getProcessInstanceId() {
        return getExt().getProcessInstanceId();
    }

    public void setProcessInstanceId(@Nullable String processInstanceId) {
        getExt().setProcessInstanceId(processInstanceId);
    }

    @Nullable
    public String getRejectReason() {
        return getExt().getRejectReason();
    }

    public void setRejectReason(String rejectReason) {
        getExt().setRejectReason(rejectReason);
    }

    @Nullable
    public String getText() {
        return getExt().getText();
    }

    public void setText(@Nullable String text) {
        getExt().setText(text);
    }

    @Nullable
    public String getMarkdown() {
        return getExt().getMarkdown();
    }

    public void setMarkdown(@Nullable String markdown) {
        getExt().setMarkdown(markdown);
    }

    public Short getEditorType() {
        return getExt().getEditorType();
    }

    public void setEditorType(Short editorType) {
        getExt().setEditorType(editorType);
    }
    // endregion

    // region ArticleBuffer

    public int getComments() {
        return getBuffer().getComments();
    }

    public int getDownloads() {
        return getBuffer().getDownloads();
    }

    public int getFavorites() {
        return getBuffer().getFavorites();
    }

    public int getUps() {
        return getBuffer().getUps();
    }

    public int getDowns() {
        return getBuffer().getDowns();
    }

    public long getViews() {
        return getBuffer().getViews();
    }

    public void setViews(long views) {
        getBuffer().setViews(views);
    }

    public int getDayViews() {
        return getBuffer().getDayViews();
    }

    public int getWeekViews() {
        return getBuffer().getWeekViews();
    }

    public int getMonthViews() {
        return getBuffer().getMonthViews();
    }

    public int getQuarterViews() {
        return getBuffer().getQuarterViews();
    }

    public long getYearViews() {
        return getBuffer().getYearViews();
    }
    // endregion

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
     * 类型：投稿
     */
    public static final short TYPE_CONTRIBUTE = 1;
    /**
     * 类型：采集
     */
    public static final short TYPE_COLLECTION = 2;
    /**
     * 类型：接口
     */
    public static final short TYPE_INTERFACE = 3;
    /**
     * 类型：站内推送
     */
    public static final short TYPE_IN_PUSH = 4;
    /**
     * 类型：站外推送
     */
    public static final short TYPE_OUT_PUSH = 5;
    // endregion
}