package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ofwise.util.file.FilesEx;
import com.ofwise.util.web.HtmlUtils;
import com.ofwise.util.web.PageUrlResolver;
import com.ujcms.core.domain.base.ArticleBase;
import com.ujcms.core.support.Anchor;
import com.ujcms.core.support.Contexts;
import com.ujcms.core.support.UrlConstants;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.lang.Nullable;

import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文章 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties("handler")
public class Article extends ArticleBase implements PageUrlResolver, Anchor, Serializable {
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
        String prefix = Optional.ofNullable(getSite().getGlobal().getArticleUrl()).orElse(UrlConstants.ARTICLE);
        buff.append(prefix).append("/").append(getId());
        String alias = getExt().getAlias();
        if (alias != null) {
            buff.append("/").append(alias);
        }
        if (page > 1) {
            buff.append("_").append(page);
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

    @JsonIgnore
    @Override
    public String getName() {
        return getTitle();
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
        urls.addAll(HtmlUtils.getUrls(getExt().getText()));
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

    public Map<String, Object> getCustoms() {
        return getChannel().getArticleModel().assembleCustoms(getCustomList());
    }

    public void setCustoms(Map<String, Object> customs) {
        getCustomList().clear();
        customs.forEach((key, value) -> {
            if (value instanceof ArrayList) {
                ((ArrayList<?>) value).forEach(val ->
                        getCustomList().add(new ArticleCustom(getId(), key, String.valueOf(val))));
                return;
            }
            Optional.ofNullable(value).map(String::valueOf).filter(StringUtils::isNotBlank).ifPresent(it ->
                    getCustomList().add(new ArticleCustom(getId(), key, it)));
        });
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
    private List<ArticleCustom> customList = new ArrayList<>();
    /**
     * 图片列表
     */
    private List<ArticleImage> imageList = new ArrayList<>();
    /**
     * 文件列表
     */
    private List<ArticleFile> fileList = new ArrayList<>();
    /**
     * 区块项列表
     */
    @JsonIncludeProperties({"id", "title", "block"})
    private List<BlockItem> blockItemList = new ArrayList<>();

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

    // ArticleExt 对象

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
    public String getVideoTime() {
        return getExt().getVideoTime();
    }

    public void setVideoTime(@Nullable String videoTime) {
        getExt().setVideoTime(videoTime);
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
    public String getText() {
        return getExt().getText();
    }

    public void setText(@Nullable String text) {
        getExt().setText(text);
    }

    // ArticleBuffer 对象属性

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
}