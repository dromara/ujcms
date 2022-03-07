package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ofwise.util.web.UrlBuilder;
import com.ujcms.core.domain.base.SiteBase;
import com.ujcms.core.support.Anchor;
import com.ujcms.core.support.Constants;
import com.ujcms.core.support.Contexts;
import com.ujcms.core.support.Props;
import com.ujcms.core.support.UrlConstants;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.springframework.lang.Nullable;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ofwise.util.web.UrlBuilder.*;

/**
 * 站点 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties({"watermarkSettings", "htmlSettings"})
public class Site extends SiteBase implements Anchor, Serializable {
    @Override
    public String getUrl() {
        return getHtml().isEnabled() ? getStaticUrl() : getDynamicUrl();
    }

    public String getDynamicUrl() {
        return new UrlBuilder()
                .appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getGlobal().getPort())
                .appendPath(getGlobal().getContextPath())
                .appendPath(getSubDir())
                .toString();
    }

    public String getStaticUrl() {
        return Contexts.isMobile() ? getMobileStaticUrl() : getNormalStaticUrl();
    }

    public String getNormalStaticUrl() {
        return getNormalStaticUrl(SLASH);
    }

    public String getMobileStaticUrl() {
        return getMobileStaticUrl(SLASH);
    }

    public String getNormalStaticUrl(String url) {
        return getStaticUrl(getHtmlStorage().getUrl(), false, url);
    }

    public String getMobileStaticUrl(String url) {
        if (!hasMobileTheme()) {
            return getNormalStaticUrl(url);
        }
        boolean isAppendMobilePath = StringUtils.equals(getHtmlStorage().getUrl(), getMobileHtmlStorage().getUrl());
        return getStaticUrl(getMobileHtmlStorage().getUrl(), isAppendMobilePath, url);
    }

    private String getStaticUrl(@Nullable String storageUrl, boolean isAppendMobilePath, String url) {
        UrlBuilder builder = new UrlBuilder();
        if (!StringUtils.startsWithAny(storageUrl, HTTP_PROTOCOL, HTTPS_PROTOCOL)) {
            builder.appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getGlobal().getPort());
            builder.appendPath(getGlobal().getContextPath());
        }
        if (StringUtils.isNotBlank(storageUrl)) {
            builder.appendPath(storageUrl);
        } else {
            builder.appendPath(getSubDir());
        }
        builder.appendPath(isAppendMobilePath ? Html.MOBILE_PATH : null);
        builder.appendPath(url);
        return builder.toString();
    }

    public String getNormalStaticPath() {
        return getNormalStaticPath(Html.DEFAULT_PAGE);
    }

    public String getMobileStaticPath() {
        return getMobileStaticPath(Html.DEFAULT_PAGE);
    }

    public String getNormalStaticPath(String path) {
        return getStaticPath(getHtmlStorage().getPath(), false, path);
    }

    public String getMobileStaticPath(String path) {
        boolean isAppendMobilePath = StringUtils.equals(getHtmlStorage().getUrl(), getMobileHtmlStorage().getUrl());
        return getStaticPath(getMobileHtmlStorage().getPath(), isAppendMobilePath, path);
    }

    public String getStaticPath(@Nullable String storagePath, boolean isAppendMobilePath, String path) {
        UrlBuilder builder = new UrlBuilder();
        if (StringUtils.isNotBlank(storagePath)) {
            builder.appendPath(storagePath);
        } else {
            builder.appendPath(getSubDir());
        }
        builder.appendPath(isAppendMobilePath ? Html.MOBILE_PATH : null);
        builder.appendPath(path);
        return builder.toString();
    }

    public boolean hasMobileTheme() {
        return !getTheme().equals(getMobileTheme());
    }

    /**
     * 获取动态地址，含部署路径、子目录，不含域名、端口。如：{@code /contextPath/subDir}
     * <p>
     * 动态地址用于拼接动态页面地址，如留言、搜索页面等。PC站和手机站可拥有不同域名，所以不带域名信息。
     */
    public String getDy() {
        return new UrlBuilder()
                .appendPath(getGlobal().getContextPath())
                .appendPath(getSubDir())
                .toString();
    }

    /**
     * 获取 API 接口地址，含协议、域名、端口和部署路径，不含子目录。如：{@code http://www.example.com/contextPath/api}
     * <p>
     * API 接口不识别子目录信息，所以地址不带子目录。地址带域名方便静态页部署到其它域名。
     */
    public String getApi() {
        return new UrlBuilder()
                .appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getGlobal().getPort())
                .appendPath(getGlobal().getContextPath())
                .appendPath(UrlConstants.API)
                .toString();
    }

    public String assembleLinkUrl(String linkUrl) {
        if (linkUrl.startsWith(SLASH)) {
            return new UrlBuilder()
                    .appendPath(getGlobal().getContextPath())
                    .appendPath(getSubDir())
                    .appendPath(linkUrl)
                    .toString();
        }
        return linkUrl;
    }

    @JsonIgnore
    public String getTemplate() {
        return assembleTemplate(TEMPLATE_FILE);
    }

    public String assembleTemplate(String template) {
        return (Contexts.isMobile() ? getMobileTheme() : getTheme()) + "/" + template;
    }

    public String getTitle() {
        if (StringUtils.isNotBlank(getSeoTitle())) {
            return getSeoTitle();
        }
        return getName();
    }

    /**
     * 获得模板的基础路径。/{id} + path。例如：/1/...
     */
    public String getBasePath(String path) {
        return "/" + getId() + path;
    }

    /**
     * 获取模板文件路径。如：/templates/1/default/_files
     */
    public String getFilesPath() {
        return Props.TEMPLATE_URL + getTheme() + "/" + Constants.TEMPLATE_FILES;
    }

    @JsonIncludeProperties({"id", "name", "url"})
    public List<Site> getPaths() {
        LinkedList<Site> parents = new LinkedList<>();
        Site bean = this;
        while (bean != null) {
            parents.addFirst(bean);
            bean = bean.getParent();
        }
        return parents;
    }

    public List<String> getNames() {
        return getPaths().stream().map(SiteBase::getName).collect(Collectors.toList());
    }

    /**
     * 获取所有字段中的附件
     *
     * @return 附件列表
     */
    @JsonIgnore
    public List<String> getAttachmentUrls() {
        List<String> urls = new ArrayList<>();
        Optional.ofNullable(getLogo()).ifPresent(urls::add);
        getModel().handleCustoms(getCustomList(), new Model.GetUrlsHandle(urls));
        return urls;
    }

    @JsonIgnore
    public Storage getMobileHtmlStorage() {
        return getMobileStorage() != null ? getMobileStorage() : getHtmlStorage();
    }

    public Map<String, Object> getCustoms() {
        return getModel().assembleCustoms(getCustomList());
    }

    public void setCustoms(Map<String, Object> customs) {
        getCustomList().clear();
        customs.forEach((key, value) -> {
            if (value instanceof ArrayList) {
                ((ArrayList<?>) value)
                        .forEach(val -> getCustomList().add(new SiteCustom(getId(), key, String.valueOf(val))));
                return;
            }
            Optional.ofNullable(value).map(String::valueOf).filter(StringUtils::isNotBlank)
                    .ifPresent(it -> getCustomList().add(new SiteCustom(getId(), key, it)));
        });
    }

    @Nullable
    private Watermark watermark;

    public Watermark getWatermark() {
        if (watermark != null) {
            return watermark;
        }
        String settings = getWatermarkSettings();
        if (StringUtils.isBlank(settings)) {
            return new Watermark();
        }
        try {
            return Constants.MAPPER.readValue(settings, Watermark.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot read value of Watermark: " + settings, e);
        }
    }

    public void setWatermark(Watermark watermark) {
        this.watermark = watermark;
        try {
            setWatermarkSettings(Constants.MAPPER.writeValueAsString(watermark));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Watermark", e);
        }
    }

    @Nullable
    private Html html;

    @Valid
    public Html getHtml() {
        if (html != null) {
            return html;
        }
        String settings = getHtmlSettings();
        if (StringUtils.isBlank(settings)) {
            return new Html();
        }
        try {
            return Constants.MAPPER.readValue(settings, Html.class);
        } catch (JsonProcessingException e) {
            return new Html();
        }
    }

    public void setHtml(Html html) {
        this.html = html;
        try {
            setHtmlSettings(Constants.MAPPER.writeValueAsString(html));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write value of Html", e);
        }
    }

    /**
     * 子站点列表
     */
    @JsonIgnore
    private List<Site> children = new ArrayList<>();
    /**
     * 自定义字段列表
     */
    @JsonIgnore
    private List<SiteCustom> customList = new ArrayList<>();
    /**
     * 站点缓冲对象
     */
    @JsonIgnore
    private SiteBuffer buffer = new SiteBuffer();
    /**
     * 全局对象
     */
    @JsonIgnore
    private Global global = new Global();
    /**
     * 上级站点
     */
    @Nullable
    private Site parent;
    /**
     * 组织
     */
    @JsonIncludeProperties({"id", "name"})
    private Org org = new Org();
    /**
     * 模型
     */
    @JsonIncludeProperties({"id", "name"})
    private Model model;
    /**
     * 附件储存点
     */
    @JsonIncludeProperties({"id", "name"})
    private Storage storage = new Storage();
    /**
     * HTML储存点
     */
    @JsonIncludeProperties({"id", "name"})
    private Storage htmlStorage = new Storage();
    /**
     * 手机HTML存储点
     */
    @Nullable
    @JsonIncludeProperties({"id", "name"})
    private Storage mobileStorage;

    public List<Site> getChildren() {
        return children;
    }

    public void setChildren(List<Site> children) {
        this.children = children;
    }

    public List<SiteCustom> getCustomList() {
        return customList;
    }

    public void setCustomList(List<SiteCustom> customList) {
        this.customList = customList;
    }

    public SiteBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(SiteBuffer buffer) {
        this.buffer = buffer;
    }

    public Global getGlobal() {
        return global;
    }

    public void setGlobal(Global global) {
        this.global = global;
    }

    @Nullable
    public Site getParent() {
        return parent;
    }

    public void setParent(Site parent) {
        this.parent = parent;
    }

    public Org getOrg() {
        return org;
    }

    public void setOrg(Org org) {
        this.org = org;
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Storage getHtmlStorage() {
        return htmlStorage;
    }

    public void setHtmlStorage(Storage htmlStorage) {
        this.htmlStorage = htmlStorage;
    }

    @Nullable
    public Storage getMobileStorage() {
        return mobileStorage;
    }

    public void setMobileStorage(@Nullable Storage mobileStorage) {
        this.mobileStorage = mobileStorage;
    }

    @Override
    @Nullable
    @Pattern(regexp = "^[\\w-]*$")
    public String getSubDir() {
        return super.getSubDir();
    }

    // SiteBuffer 对象属性

    public long getViews() {
        return getBuffer().getViews();
    }

    /**
     * 站点首页模板
     */
    public static final String TEMPLATE_FILE = "index";

    public static final class Watermark implements Serializable {
        /**
         * 是否开启
         */
        private boolean enabled = false;
        /**
         * 水印图片地址
         */
        @Nullable
        private String overlay;
        /**
         * 水印位置。1-9。NorthWest, North, NorthEast, West, Center, East, SouthWest, South, SouthEast。默认9，右下位置
         */
        private int position = 9;
        /**
         * 透明度。0-100。0: 完全透明; 100: 完全不透明。默认50
         */
        private int dissolve = 50;
        /**
         * 图片最小宽度。小于这个宽度的图片不加水印。默认300
         */
        private int minWidth = 300;
        /**
         * 图片最小高度。小于这个高度的图片不加水印。默认300
         */
        private int minHeight = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Nullable
        public String getOverlay() {
            return overlay;
        }

        public void setOverlay(@Nullable String overlay) {
            this.overlay = overlay;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public int getDissolve() {
            return dissolve;
        }

        public void setDissolve(int dissolve) {
            this.dissolve = dissolve;
        }

        public int getMinWidth() {
            return minWidth;
        }

        public void setMinWidth(int minWidth) {
            this.minWidth = minWidth;
        }

        public int getMinHeight() {
            return minHeight;
        }

        public void setMinHeight(int minHeight) {
            this.minHeight = minHeight;
        }
    }

    public static final class Html implements Serializable {
        public static final String PATH_YEAR = "{year}";
        public static final String PATH_MONTH = "{month}";
        public static final String PATH_DAY = "{day}";
        public static final String PATH_CHANNEL_ID = "{channel_id}";
        public static final String PATH_CHANNEL_ALIAS = "{channel_alias}";
        public static final String PATH_ARTICLE_ID = "{article_id}";
        public static final String PATH_ARTICLE_ALIAS = "{article_alias}";
        public static final String SUFFIX = ".html";
        public static final String DEFAULT_PAGE = "/index.html";
        public static final String MOBILE_PATH = "/m";

        private boolean enabled = true;
        private boolean auto = true;
        private int listPages = 1;

        @Length(max = 100)
        @Pattern(regexp = "^(?!.*\\.\\.)[\\w-{}/]*$")
        private String channel = "/channels/{channel_alias}/index";

        @Length(max = 100)
        @Pattern(regexp = "^(?!.*\\.\\.)[\\w-{}/]*$")
        private String article = "/articles/{article_id}";

        public String getChannelPath(Integer channelId, String channelAlias, int page) {
            String path = StringUtils.replaceEach(channel,
                    new String[]{PATH_CHANNEL_ID, PATH_CHANNEL_ALIAS},
                    new String[]{String.valueOf(channelId), channelAlias});
            if (page > 1) {
                return path + "_" + page + SUFFIX;
            }
            return path + SUFFIX;
        }

        public String getChannelUrl(Integer channelId, String channelAlias, int page) {
            String path = getChannelPath(channelId, channelAlias, page);
            if (path.endsWith(DEFAULT_PAGE)) {
                // /index.html 改为 / 结尾
                return path.substring(0, path.length() - DEFAULT_PAGE.length() + 1);
            }
            return path;
        }

        public String getArticlePath(Integer channelId, String channelAlias, Integer articleId, @Nullable String articleAlias,
                                     OffsetDateTime createdDate, int page) {
            String year = String.valueOf(createdDate.getYear());
            String month = StringUtils.leftPad(String.valueOf(createdDate.getMonthValue()), 2, '0');
            String day = StringUtils.leftPad(String.valueOf(createdDate.getDayOfMonth()), 2, '0');
            String url = StringUtils.replaceEach(article,
                    new String[]{PATH_CHANNEL_ID, PATH_CHANNEL_ALIAS, PATH_ARTICLE_ID, PATH_ARTICLE_ALIAS,
                            PATH_YEAR, PATH_MONTH, PATH_DAY},
                    new String[]{String.valueOf(channelId), channelAlias, String.valueOf(articleId),
                            StringUtils.isNotBlank(articleAlias) ? articleAlias : String.valueOf(articleId),
                            year, month, day});
            if (page > 1) {
                return url + "_" + page + SUFFIX;
            }
            return url + SUFFIX;
        }


        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAuto() {
            return auto;
        }

        public void setAuto(boolean auto) {
            this.auto = auto;
        }

        public int getListPages() {
            return listPages;
        }

        public void setListPages(int listPages) {
            this.listPages = listPages;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public String getArticle() {
            return article;
        }

        public void setArticle(String article) {
            this.article = article;
        }
    }
}