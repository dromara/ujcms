package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.domain.base.SiteBase;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.db.tree.TreeEntity;
import com.ujcms.util.web.UrlBuilder;
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

import static com.ujcms.util.web.UrlBuilder.*;

/**
 * 站点 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties({"watermarkSettings", "htmlSettings"})
public class Site extends SiteBase implements Anchor, TreeEntity, Serializable {
    /**
     * 获取动态地址，含部署路径、子目录，不含域名、端口。如：{@code /contextPath/subDir}
     * <p>
     * 动态地址用于拼接动态页面地址，如留言、搜索页面等。PC站和手机站可拥有不同域名，所以不带域名信息。
     */
    public String getDy() {
        return new UrlBuilder()
                .appendPath(getConfig().getContextPath())
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
                .appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getConfig().getPort())
                .appendPath(getConfig().getContextPath())
                .appendPath(UrlConstants.API)
                .toString();
    }

    public String assembleLinkUrl(String linkUrl) {
        if (linkUrl.startsWith(SLASH) && !linkUrl.startsWith(DOUBLE_SLASH)) {
            return new UrlBuilder()
                    .appendPath(getConfig().getContextPath())
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
        return getConfig().getTemplateStorage().getUrl() + getTheme() + "/" + Constants.TEMPLATE_FILES;
    }

    public String getTitle() {
        if (StringUtils.isNotBlank(getSeoTitle())) {
            return getSeoTitle();
        }
        return getName();
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

    public boolean hasMobileTheme() {
        return !getTheme().equals(getMobileTheme());
    }

    /**
     * 站点首页模板
     */
    public static final String TEMPLATE_FILE = "index";

    // region Urls

    @Override
    public String getUrl() {
        return getHtml().isEnabled() ? getStaticUrl() : getDynamicUrl();
    }

    public String getDynamicUrl() {
        return new UrlBuilder()
                .appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getConfig().getPort())
                .appendPath(getConfig().getContextPath())
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
        return getStaticUrl(getConfig().getHtmlStorage().getUrl(), false, url);
    }

    public String getMobileStaticUrl(String url) {
        if (!hasMobileTheme()) {
            return getNormalStaticUrl(url);
        }
        return getStaticUrl(getConfig().getHtmlStorage().getUrl(), true, url);
    }

    private String getStaticUrl(String storageUrl, boolean isAppendMobilePath, String url) {
        UrlBuilder builder = new UrlBuilder();
        if (!StringUtils.startsWithAny(storageUrl, HTTP_PROTOCOL, HTTPS_PROTOCOL)) {
            builder.appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getConfig().getPort());
            builder.appendPath(getConfig().getContextPath());
        }
        builder.appendPath(storageUrl).appendPath(getSubDir());
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
        return getStaticPath(getConfig().getHtmlStorage().getPath(), false, path);
    }

    public String getMobileStaticPath(String path) {
        return getStaticPath(getConfig().getHtmlStorage().getPath(), true, path);
    }

    public String getStaticBase() {
        return getStaticPath(getConfig().getHtmlStorage().getPath(), false, "");
    }

    public String getStaticPath(String storagePath, boolean isAppendMobilePath, String path) {
        UrlBuilder builder = new UrlBuilder();
        builder.appendPath(storagePath);
        // 多域名的情况下，要加上域名作为路径
        if (getConfig().getMultiDomain()) {
            builder.appendPath(getDomain());
        }
        builder.appendPath(getSubDir());
        builder.appendPath(isAppendMobilePath ? Html.MOBILE_PATH : null);
        builder.appendPath(path);
        return builder.toString();
    }
    // endregion

    // region JsonFields

    public Map<String, Object> getCustoms() {
        if (customs == null) {
            customs = getModel().assembleCustoms(getCustomList());
        }
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        getCustomList().clear();
        getModel().disassembleCustoms(customs, (name, type, value) ->
                getCustomList().add(new SiteCustom(getId(), name, type, value)));
        this.customs = null;
    }

    @Nullable
    private Map<String, Object> customs;

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
    // endregion

    // region TempFields

    /**
     * 复制站点ID
     */
    @Nullable
    private Integer copyFromId;
    /**
     * 复制数据
     */
    private List<String> copyData = new ArrayList<>();

    @Nullable
    @JsonIgnore
    public Integer getCopyFromId() {
        return copyFromId;
    }

    @JsonProperty
    public void setCopyFromId(@Nullable Integer copyFromId) {
        this.copyFromId = copyFromId;
    }

    @JsonIgnore
    public List<String> getCopyData() {
        return copyData;
    }

    @JsonProperty
    public void setCopyData(List<String> copyData) {
        this.copyData = copyData;
    }

    public static final String COPY_DATA_TEMPLATE = "template";
    public static final String COPY_DATA_MODEL = "model";
    public static final String COPY_DATA_CHANNEL = "channel";
    // endregion

    // region Associations
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
    private Config config = new Config();
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
    private Model model = new Model();

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

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    @Nullable
    @Override
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
    // endregion

    // region SiteBase

    @Override
    @Pattern(regexp = "^(?!(uploads|templates|WEB-INF|cp)$)[a-zA-Z0-9-.]*", flags = Pattern.Flag.CASE_INSENSITIVE)
    public String getDomain() {
        return super.getDomain();
    }

    @Override
    @Nullable
    @Pattern(regexp = "^(?!(uploads|templates|WEB-INF|cp)$)[\\w-]*", flags = Pattern.Flag.CASE_INSENSITIVE)
    public String getSubDir() {
        return super.getSubDir();
    }
    // endregion

    // region SiteBuffer

    public long getViews() {
        return getBuffer().getViews();
    }
    // endregion

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