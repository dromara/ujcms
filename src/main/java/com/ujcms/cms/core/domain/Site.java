package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.base.SiteBase;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.commons.db.tree.TreeEntity;
import com.ujcms.commons.web.UrlBuilder;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.ujcms.commons.web.UrlBuilder.*;

/**
 * 站点实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"watermarkSettings", "htmlSettings", "messageBoardSettings", "handler"})
public class Site extends SiteBase implements Anchor, TreeEntity, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 获取动态地址，含部署路径、子目录，不含域名、端口。如：{@code /contextPath/subDir}
     * <p>
     * 动态地址用于拼接动态页面地址，如留言、搜索页面等。PC站和手机站可拥有不同域名，所以不带域名信息。
     */
    @Schema(description = "动态地址，含部署路径、子目录，不含域名、端口。如：{@code /contextPath/subDir}")
    public String getDy() {
        UrlBuilder buff = UrlBuilder.of()
                .appendPath(getConfig().getContextPath());
        if (StringUtils.isNotBlank(getSubDir())) {
            buff.appendPath(SLASH).appendPath(getSubDir());
        }
        return buff.toString();
    }

    /**
     * 获取 API 接口地址。如：{@code /contextPath/frontend}
     */
    @Schema(description = "API 接口地址。如：{@code /contextPath/frontend}")
    public String getApi() {
        return UrlBuilder.of()
                .appendPath(getConfig().getContextPath())
                .appendPath(UrlConstants.FRONTEND_API)
                .toString();
    }

    public String assembleLinkUrl(String linkUrl) {
        if (linkUrl.startsWith(SLASH) && !linkUrl.startsWith(DOUBLE_SLASH)) {
            return UrlBuilder.of(SLASH)
                    .appendPath(getConfig().getContextPath())
                    .appendPath(getSubDir())
                    .appendPath(linkUrl)
                    .toString();
        }
        return linkUrl;
    }

    public String getTemplate() {
        return assembleTemplate(TEMPLATE_FILE);
    }

    public String assembleTemplate(String template) {
        return (Contexts.isMobile() ? getMobileTheme() : getTheme()) + "/" + template;
    }

    /**
     * 获得模板的基础路径。/{id} + path。例如：/1/...
     */
    @Schema(description = "模板的基础路径。/{id} + path。例如：/1/...")
    public String getBasePath(String path) {
        return "/" + getId() + path;
    }

    /**
     * 获取模板文件路径。如：/templates/1/default/_files
     */
    @Schema(description = "模板文件路径。如：/templates/1/default/_files")
    public String getFilesPath() {
        return UrlBuilder.of()
                .appendPath(getConfig().getContextPath())
                .appendPath(getConfig().getTemplateStorage().getUrl())
                .appendPath(Contexts.isMobile() ? getMobileTheme() : getTheme())
                .appendPath(Constants.TEMPLATE_FILES)
                .toString();
    }

    /**
     * 获取标题。先获取SeoTitle，如不存在则获取网站名称
     */
    @Schema(description = "标题。先获取SeoTitle，如不存在则获取网站名称")
    public String getTitle() {
        String seoTitle = getSeoTitle();
        if (StringUtils.isNotBlank(seoTitle)) {
            return seoTitle;
        }
        return getName();
    }

    /**
     * 站点层级。从一级站点到当前站点的列表
     */
    @Schema(description = "站点层级。从一级站点到当前站点的列表")
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

    @Schema(description = "站点层级名称。从一级栏目到当前栏目的名称列表")
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
        return getAttachmentUrls(getModel());
    }

    public List<String> getAttachmentUrls(Model model) {
        List<String> urls = new ArrayList<>();
        Optional.ofNullable(getLogo()).ifPresent(urls::add);
        // 获取正文中的附件url
        urls.addAll(model.getUrlsFromMap(getCustoms()));
        return urls;
    }

    public boolean hasMobileTheme() {
        return !getTheme().equals(getMobileTheme());
    }

    public boolean isEnabled() {
        return getStatus() == STATUS_NORMAL;
    }

    public boolean isDisabled() {
        return !isEnabled();
    }

    public boolean isHtmlEnabled() {
        return isEnabled() && getHtml().isEnabled();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Site bean = (Site) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    /**
     * 站点首页模板
     */
    public static final String TEMPLATE_FILE = "index";

    // region Urls

    /**
     * URL地址
     */
    @Schema(description = "URL地址")
    @Override
    public String getUrl() {
        return getHtml().isEnabled() ? getStaticUrl() : getDynamicUrl();
    }

    /**
     * 动态URL地址
     */
    @Schema(description = "动态URL地址")
    public String getDynamicUrl() {
        return UrlBuilder.of()
                .appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getConfig().getPort())
                .appendPath(getConfig().getContextPath())
                .appendPath(getSubDir())
                .toString();
    }

    /**
     * 静态URL地址
     */
    @Schema(description = "静态URL地址")
    public String getStaticUrl() {
        return Contexts.isMobile() ? getMobileStaticUrl() : getNormalStaticUrl();
    }

    /**
     * PC端静态URL地址
     */
    @Schema(description = "PC端静态URL地址")
    public String getNormalStaticUrl() {
        return getNormalStaticUrl("");
    }

    /**
     * 手机端静态URL地址
     */
    @Schema(description = "手机端静态URL地址")
    public String getMobileStaticUrl() {
        return getMobileStaticUrl("");
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
        UrlBuilder builder = UrlBuilder.of();
        if (!StringUtils.startsWithAny(storageUrl, HTTP_PROTOCOL, HTTPS_PROTOCOL)) {
            builder.appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getConfig().getPort());
            builder.appendPath(getConfig().getContextPath());
        }
        builder.appendPath(storageUrl).appendPath(getSubDir());
        builder.appendPath(isAppendMobilePath ? Html.MOBILE_PATH : null);
        builder.appendPath(url);
        return builder.toString();
    }

    @JsonIgnore
    public String getNormalStaticPath() {
        return getNormalStaticPath(Html.DEFAULT_PAGE);
    }

    @JsonIgnore
    public String getMobileStaticPath() {
        return getMobileStaticPath(Html.DEFAULT_PAGE);
    }

    public String getNormalStaticPath(String path) {
        return getStaticPath(false, path);
    }

    public String getMobileStaticPath(String path) {
        return getStaticPath(true, path);
    }

    public String getStaticBase() {
        return getStaticPath(false, "");
    }

    public String getStaticPath(boolean isAppendMobilePath, String path) {
        UrlBuilder builder = UrlBuilder.of();
        // 多域名的情况下，要加上域名作为路径
        if (Boolean.TRUE.equals(getConfig().getMultiDomain())) {
            builder.appendPath(getDomain());
        }
        builder.appendPath(getSubDir());
        builder.appendPath(isAppendMobilePath ? Html.MOBILE_PATH : null);
        builder.appendPath(path);
        return builder.toString();
    }
    // endregion

    // region JsonFields
    /**
     * 自定义字段
     */
    @Nullable
    private transient Map<String, Object> customs;

    @Schema(description = "自定义字段")
    public Map<String, Object> getCustoms() {
        if (customs != null) {
            return customs;
        }
        customs = new HashMap<>(16);
        customs.putAll(getModel().assembleMap(getMainsJson()));
        customs.putAll(getModel().assembleMap(getClobsJson()));
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

    /**
     * 水印设置
     */
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
            throw new IllegalStateException("Cannot read value of Watermark: " + settings, e);
        }
    }

    public void setWatermark(Watermark watermark) {
        this.watermark = watermark;
        try {
            setWatermarkSettings(Constants.MAPPER.writeValueAsString(watermark));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Watermark", e);
        }
    }

    /**
     * 静态页设置
     */
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
            throw new IllegalStateException("Cannot write value of Html", e);
        }
    }

    /**
     * 留言板设置
     */
    @Nullable
    private MessageBoard messageBoard;

    @Valid
    public MessageBoard getMessageBoard() {
        if (messageBoard != null) {
            return messageBoard;
        }
        String settings = getMessageBoardSettings();
        if (StringUtils.isBlank(settings)) {
            return new MessageBoard();
        }
        try {
            return Constants.MAPPER.readValue(settings, MessageBoard.class);
        } catch (JsonProcessingException e) {
            return new MessageBoard();
        }
    }

    public void setMessageBoard(MessageBoard messageBoard) {
        this.messageBoard = messageBoard;
        try {
            setMessageBoardSettings(Constants.MAPPER.writeValueAsString(messageBoard));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of MessageBoard", e);
        }
    }

    @Nullable
    private transient Map<String, Object> editor;

    public Map<String, Object> getEditor() {
        if (editor != null) {
            return editor;
        }
        String settings = getEditorSettings();
        if (StringUtils.isBlank(settings)) {
            return Collections.emptyMap();
        }
        try {
            return Constants.MAPPER.readValue(settings, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    public void setEditor(Map<String, Object> editor) {
        this.editor = editor;
        try {
            setEditorSettings(Constants.MAPPER.writeValueAsString(editor));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Editor", e);
        }
    }

    // endregion

    // region TempFields

    /**
     * 复制站点ID
     */
    @Nullable
    private Long copyFromId;
    /**
     * 复制数据
     */
    private List<String> copyData = new ArrayList<>();

    @Nullable
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public Long getCopyFromId() {
        return copyFromId;
    }

    public void setCopyFromId(@Nullable Long copyFromId) {
        this.copyFromId = copyFromId;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public List<String> getCopyData() {
        return copyData;
    }

    public void setCopyData(List<String> copyData) {
        this.copyData = copyData;
    }

    public static final String COPY_DATA_TEMPLATE = "template";
    public static final String COPY_DATA_MODEL = "model";
    public static final String COPY_DATA_CHANNEL = "channel";
    // endregion

    // region Associations
    /**
     * 是否有子站点
     */
    @Nullable
    private Boolean hasChildren;
    /**
     * 子站点列表
     */
    @JsonIgnore
    private List<Site> children = new ArrayList<>();
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

    @Nullable
    public Boolean getHasChildren() {
        return hasChildren;
    }

    public void setHasChildren(@Nullable Boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    @Nullable
    public List<Site> getChildren() {
        return children;
    }

    public void setChildren(List<Site> children) {
        this.children = children;
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

    /**
     * 域名
     */
    @Override
    @Pattern(regexp = "^(?!(uploads|templates|WEB-INF|cp)$)[a-z0-9-.]*", flags = Pattern.Flag.CASE_INSENSITIVE)
    public String getDomain() {
        return super.getDomain();
    }

    /**
     * 子目录
     */
    @Override
    @Nullable
    @Pattern(regexp = "^(?!(uploads|templates|WEB-INF|cp)$)[\\w-]*", flags = Pattern.Flag.CASE_INSENSITIVE)
    public String getSubDir() {
        return super.getSubDir();
    }
    // endregion

    @Schema(name = "Site.Watermark", description = "水印设置")
    public static final class Watermark implements Serializable {
        private static final long serialVersionUID = 1L;
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

    @Schema(name = "Site.Html", description = "静态页配置")
    public static final class Html implements Serializable {
        private static final long serialVersionUID = 1L;

        public static final String PATH_YEAR = "{year}";
        public static final String PATH_MONTH = "{month}";
        public static final String PATH_DAY = "{day}";
        public static final String PATH_CHANNEL_ID = "{channel_id}";
        public static final String PATH_CHANNEL_ALIAS = "{channel_alias}";
        public static final String PATH_ARTICLE_ID = "{article_id}";
        public static final String PATH_ARTICLE_ALIAS = "{article_alias}";
        public static final String SUFFIX = ".html";
        public static final String DEFAULT_PAGE = "/index.html";
        @SuppressWarnings("java:S1075")
        public static final String MOBILE_PATH = "/m";

        public boolean isEnabledAndAuto() {
            return this.enabled && this.auto;
        }

        /**
         * 是否开启
         */
        private boolean enabled = false;
        /**
         * 是否自动生成
         */
        private boolean auto = true;
        /**
         * 列表静态页生成数
         */
        private int listPages = 1;
        /**
         * 栏目静态页路径
         */
        @Length(max = 100)
        @Pattern(regexp = "^(?!.*\\.\\.)[\\w-{}/]*$")
        private String channel = "/channels/{channel_alias}/index";
        /**
         * 文章静态页路径
         */
        @Length(max = 100)
        @Pattern(regexp = "^(?!.*\\.\\.)[\\w-{}/]*$")
        private String article = "/articles/{article_id}";

        public String getChannelPath(@Nullable String channelStaticPath, Long channelId, String channelAlias,
                                     int page) {
            if (StringUtils.isBlank(channelStaticPath)) {
                channelStaticPath = channel;
            }
            String path = StringUtils.replaceEach(channelStaticPath,
                    new String[]{PATH_CHANNEL_ID, PATH_CHANNEL_ALIAS},
                    new String[]{String.valueOf(channelId), channelAlias});
            if (page > 1) {
                return path + "_" + page + SUFFIX;
            }
            return path + SUFFIX;
        }

        public String getChannelUrl(@Nullable String channelStaticPath, Long channelId, String channelAlias, int page) {
            String path = getChannelPath(channelStaticPath, channelId, channelAlias, page);
            if (path.endsWith(DEFAULT_PAGE)) {
                // /index.html 改为 / 结尾
                return path.substring(0, path.length() - DEFAULT_PAGE.length() + 1);
            }
            return path;
        }

        public String getArticlePath(@Nullable String articleStaticPath, Long channelId, String channelAlias,
                                     Long articleId, @Nullable String articleAlias, OffsetDateTime createdDate,
                                     int page) {
            if (StringUtils.isBlank(articleStaticPath)) {
                articleStaticPath = article;
            }
            String year = String.valueOf(createdDate.getYear());
            String month = StringUtils.leftPad(String.valueOf(createdDate.getMonthValue()), 2, '0');
            String day = StringUtils.leftPad(String.valueOf(createdDate.getDayOfMonth()), 2, '0');
            String url = StringUtils.replaceEach(articleStaticPath,
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

        public String getArticleUrl(@Nullable String articleStaticPath, Long channelId, String channelAlias,
                                    Long articleId, @Nullable String articleAlias, OffsetDateTime createdDate,
                                    int page) {
            String path = getArticlePath(articleStaticPath, channelId, channelAlias,
                    articleId, articleAlias, createdDate, page);
            if (path.endsWith(DEFAULT_PAGE)) {
                // /index.html 改为 / 结尾
                return path.substring(0, path.length() - DEFAULT_PAGE.length() + 1);
            }
            return path;
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

    @Schema(name = "Site.MessageBoard", description = "留言板设置")
    public static class MessageBoard implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 是否开启
         */
        private boolean enabled = false;
        /**
         * 是否需要登录
         */
        private boolean loginRequired = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLoginRequired() {
            return loginRequired;
        }

        public void setLoginRequired(boolean loginRequired) {
            this.loginRequired = loginRequired;
        }
    }

    // region StaticFields
    /**
     * 状态：正常
     */
    public static final short STATUS_NORMAL = 0;
    /**
     * 状态：关闭
     */
    public static final short STATUS_DISABLED = 1;

    public static final String NOT_FOUND = "Site not found. ID: ";
    // endregion
}