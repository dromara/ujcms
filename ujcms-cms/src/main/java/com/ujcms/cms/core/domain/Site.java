package com.ujcms.cms.core.domain;

import static com.ujcms.common.web.UrlBuilder.DOUBLE_SLASH;
import static com.ujcms.common.web.UrlBuilder.HTTPS_PROTOCOL;
import static com.ujcms.common.web.UrlBuilder.HTTP_PROTOCOL;
import static com.ujcms.common.web.UrlBuilder.SLASH;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hibernate.validator.constraints.Length;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.generated.GeneratedSite;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.common.db.tree.TreeEntity;
import com.ujcms.common.web.UrlBuilder;
import com.ujcms.common.web.Views;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 站点实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"watermarkSettings", "htmlSettings", "messageBoardSettings", "commentSettings",
        "disclosureSettings", "mailboxSettings", "interviewSettings", "censusSettings",
        "accessibilitySettings", "aiSettings", "geoSettings", "permalinkSettings", "handler"})
public class Site extends GeneratedSite implements Anchor, TreeEntity {

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
        return assembleTemplate(getTemplateName());
    }

    /**
     * 主题预览覆盖（design-review P2-1 / H1）：前台请求带已鉴权的 {@code ?_theme=<候选主题>} 时
     * （{@link Contexts#findThemePreview()}，由 {@code FrontendInterceptor} 置位、{@code SiteResolver}
     * 校验可激活性），本 getter 返回候选主题名——模板组装、主题键、资源路径、区域清单、主题设置
     * 全部渲染面随之以候选主题解析。与 {@link #assembleTemplate} 读 {@code Contexts.isMobile()}
     * 同一模式：请求态经线程变量进入实体 getter，实体自身不被改写（站点行可能来自共享缓存）。
     * 后台 API / 静态页生成线程不置该变量，不受影响。
     */
    @Override
    public String getTheme() {
        String preview = Contexts.findThemePreview();
        return preview != null ? preview : super.getTheme();
    }

    /**
     * 主题预览覆盖对移动主题同样生效（P2-1）：预览以候选主题渲染任意设备的访问——等同「桌面+移动
     * 同时切换」的试看口径；正式激活进哪个槽位（桌面 / 手机 / 两者，{@code ActivateTarget}）仍由激活动作决定。
     */
    @Override
    public String getMobileTheme() {
        String preview = Contexts.findThemePreview();
        return preview != null ? preview : super.getMobileTheme();
    }

    /**
     * 桌面主题的规范化主题键 {@code {siteId}/{themeName}}（R2）。构成注册表命名空间、
     * 引擎缓存、主题设置的统一键。{@code theme_} 列只存裸主题名（如 {@code default}），
     * 站点ID前缀由本方法拼装——它始终是本行自身的ID，是可导出的，不是数据。
     */
    @JsonIgnore
    public String getThemeKey() {
        return getId() + "/" + getTheme();
    }

    /**
     * 移动主题的规范化主题键 {@code {siteId}/{themeName}}（R2）。
     */
    @JsonIgnore
    public String getMobileThemeKey() {
        return getId() + "/" + getMobileTheme();
    }

    /**
     * 首页的未组装模板名（渲染上下文钩子用，页面构成 §10.2）
     */
    @JsonIgnore
    public String getTemplateName() {
        return TEMPLATE_FILE;
    }

    /**
     * 拼装模板路径（不含后缀，由视图解析器添加 {@code .html}）。
     * <p>
     * 主题采用结构化布局，页面模板统一位于 {@code <theme>/templates/<name>}。
     * {@code theme_} 列只存裸主题名（R2），{@code /{siteId}/} 前缀在此拼装。
     */
    public String assembleTemplate(String template) {
        String themeKey = Contexts.isMobile() ? getMobileThemeKey() : getThemeKey();
        return "/" + themeKey + "/templates/" + template;
    }

    /**
     * 获得模板的基础路径。/{id} + path。例如：/1/...
     */
    @Schema(description = "模板的基础路径。/{id} + path。例如：/1/...")
    public String getBasePath(String path) {
        return "/" + getId() + path;
    }

    /**
     * 获取模板资源路径。如：/themes/1/default/assets
     * <p>对应 {@code ${assets}}，指向主题目录 {@code assets/}。
     */
    @Schema(description = "模板资源路径。如：/themes/1/default/assets")
    public String getAssetsPath() {
        return UrlBuilder.of()
                .appendPath(getConfig().getContextPath())
                .appendPath(getConfig().getTemplateStorage().getUrl())
                .appendPath("/" + (Contexts.isMobile() ? getMobileThemeKey() : getThemeKey()))
                .appendPath(Constants.TEMPLATE_ASSETS)
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
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public List<String> getNames() {
        return getPaths().stream().map(GeneratedSite::getName).toList();
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

    private boolean isMultiDomain() {
        return Boolean.TRUE.equals(getConfig().getMultiDomain());
    }

    /**
     * URL地址
     */
    @Schema(description = "URL地址")
    @Override
    public String getUrl() {
        return getHtml().isEnabled() ? getStaticUrl() : getDynamicUrl();
    }

    /**
     * 完整URL地址（始终含协议、域名、端口）
     */
    @Schema(description = "完整URL地址")
    @Override
    public String getFullUrl() {
        if (getHtml().isEnabled()) {
            return Contexts.isMobile() ? getMobileStaticFullUrl("") : getNormalStaticFullUrl("");
        }
        return getDynamicFullUrl();
    }

    /**
     * 动态URL地址。单域名模式下为根相对路径（{@code /} 或 {@code /ctx/sub}）——子目录站点在
     * 无部署路径的根安装下同样以 {@code /} 开头（与 {@link #getDy()} 同款拼装；
     * {@code PreviewUrlResolver} 等消费方依赖该形态）。
     */
    @Schema(description = "动态URL地址")
    public String getDynamicUrl() {
        UrlBuilder builder = UrlBuilder.of();
        if (isMultiDomain()) {
            builder.appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getConfig().getPort());
        }
        builder.appendPath(getConfig().getContextPath());
        if (StringUtils.isNotBlank(getSubDir())) {
            builder.appendPath(SLASH).appendPath(getSubDir());
        }
        String url = builder.toString();
        return url.isEmpty() ? "/" : url;
    }

    /**
     * 完整动态URL地址（始终含协议、域名、端口）
     */
    public String getDynamicFullUrl() {
        return UrlBuilder.of()
                .appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getConfig().getPort())
                .appendPath(getConfig().getContextPath())
                .appendPath(getSubDir())
                .toString();
    }

    /**
     * 站内资源地址转绝对地址（模板与 sitemap、JSON-LD 共用）：上传文件默认存为根相对路径（{@code /uploads/...}），
     * 补站点动态地址的 origin（{@code scheme://host[:port]}，不含上下文路径与子目录）；已是绝对地址
     * （含配置为 CDN 的上传前缀）原样返回；协议相对地址补协议；其它相对路径无法判定，返回 {@code null}。
     * 模板用法：{@code site.absoluteUrl(article.image!)}，结果可能为空，须以 {@code !} 兜底
     *
     * @param url 站内地址
     * @return 绝对地址；无法判定时为 {@code null}
     */
    @Nullable
    public String absoluteUrl(@Nullable String url) {
        return toAbsoluteUrl(getDynamicFullUrl(), url);
    }

    /**
     * {@link #absoluteUrl(String)} 的静态形式，便于不持有站点实例（或站点为测试替身）时使用。
     * 不与实例方法同名：避免 FreeMarker 对 {@code site.absoluteUrl(x)} 做重载解析
     *
     * @param dynamicFullUrl 站点完整动态地址（{@link #getDynamicFullUrl()}）
     * @param url            站内地址
     * @return 绝对地址；无法判定时为 {@code null}
     */
    @Nullable
    public static String toAbsoluteUrl(@Nullable String dynamicFullUrl, @Nullable String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String origin = origin(dynamicFullUrl);
        if (origin == null) {
            return null;
        }
        if (trimmed.startsWith("//")) {
            return origin.substring(0, origin.indexOf(':') + 1) + trimmed;
        }
        return trimmed.startsWith("/") ? origin + trimmed : null;
    }

    /**
     * 站点动态地址的 origin（scheme://host[:port]）：根相对路径相对于主机根，不含上下文路径与子目录
     */
    @Nullable
    private static String origin(@Nullable String dynamicFullUrl) {
        if (dynamicFullUrl == null) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(dynamicFullUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    /**
     * 补全根路径。无部署路径的子目录站点会生成 {@code subDir} 这样的地址，浏览器会将其当作相对当前页面的地址解析，须补上开头的 {@code /}
     */
    private static String normalizeUrl(String url) {
        if (Strings.CS.startsWithAny(url, SLASH, HTTP_PROTOCOL, HTTPS_PROTOCOL)) {
            return url;
        }
        return SLASH + url;
    }

    private String getStaticUrl(String storageUrl, boolean isAppendMobilePath, String url) {
        UrlBuilder builder = UrlBuilder.of();
        if (!Strings.CS.startsWithAny(storageUrl, HTTP_PROTOCOL, HTTPS_PROTOCOL)) {
            if (isMultiDomain()) {
                builder.appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getConfig().getPort());
            }
            builder.appendPath(getConfig().getContextPath());
        }
        builder.appendPath(storageUrl).appendPath(getSubDir());
        builder.appendPath(isAppendMobilePath ? Html.MOBILE_PATH : null);
        builder.appendPath(url);
        return normalizeUrl(builder.toString());
    }

    private String getStaticFullUrl(String storageUrl, boolean isAppendMobilePath, String url) {
        UrlBuilder builder = UrlBuilder.of();
        if (!Strings.CS.startsWithAny(storageUrl, HTTP_PROTOCOL, HTTPS_PROTOCOL)) {
            builder.appendProtocol(getProtocol()).appendDomain(getDomain()).appendPort(getConfig().getPort());
            builder.appendPath(getConfig().getContextPath());
        }
        builder.appendPath(storageUrl).appendPath(getSubDir());
        builder.appendPath(isAppendMobilePath ? Html.MOBILE_PATH : null);
        builder.appendPath(url);
        return builder.toString();
    }

    public String getNormalStaticFullUrl(String url) {
        return getStaticFullUrl(getConfig().getHtmlStorage().getUrl(), false, url);
    }

    public String getMobileStaticFullUrl(String url) {
        if (!hasMobileTheme()) {
            return getNormalStaticFullUrl(url);
        }
        return getStaticFullUrl(getConfig().getHtmlStorage().getUrl(), true, url);
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

    public void disassembleCustoms(Model model, HtmlSanitizeService sanitizeService) {
        Map<String, Object> map = model.sanitizeMap(getCustoms(), sanitizeService);
        setCustoms(map);
        model.disassembleMap(map, this::setMainsJson, this::setClobsJson);
    }

    /**
     * 水印设置
     */
    @Nullable
    private Watermark watermark;

    @JsonView(Views.Whole.class)
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
    @JsonView(Views.Whole.class)
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
    @JsonView(Views.Whole.class)
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

    /**
     * 评论设置
     */
    @Nullable
    private Comment comment;

    @Valid
    public Comment getComment() {
        if (comment != null) {
            return comment;
        }
        String settings = getCommentSettings();
        if (StringUtils.isBlank(settings)) {
            return new Comment();
        }
        try {
            return Constants.MAPPER.readValue(settings, Comment.class);
        } catch (JsonProcessingException e) {
            return new Comment();
        }
    }

    public void setComment(Comment comment) {
        this.comment = comment;
        try {
            setCommentSettings(Constants.MAPPER.writeValueAsString(comment));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Comment", e);
        }
    }

    /**
     * 依申请公开设置
     */
    @Nullable
    private Disclosure disclosure;

    @Valid
    public Disclosure getDisclosure() {
        if (disclosure != null) {
            return disclosure;
        }
        String settings = getDisclosureSettings();
        if (StringUtils.isBlank(settings)) {
            return new Disclosure();
        }
        try {
            return Constants.MAPPER.readValue(settings, Disclosure.class);
        } catch (JsonProcessingException e) {
            return new Disclosure();
        }
    }

    public void setDisclosure(Disclosure disclosure) {
        this.disclosure = disclosure;
        try {
            setDisclosureSettings(Constants.MAPPER.writeValueAsString(disclosure));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Disclosure", e);
        }
    }

    /**
     * 领导信箱设置
     */
    @Nullable
    private Mailbox mailbox;

    @Valid
    public Mailbox getMailbox() {
        if (mailbox != null) {
            return mailbox;
        }
        String settings = getMailboxSettings();
        if (StringUtils.isBlank(settings)) {
            return new Mailbox();
        }
        try {
            return Constants.MAPPER.readValue(settings, Mailbox.class);
        } catch (JsonProcessingException e) {
            return new Mailbox();
        }
    }

    public void setMailbox(Mailbox mailbox) {
        this.mailbox = mailbox;
        try {
            setMailboxSettings(Constants.MAPPER.writeValueAsString(mailbox));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Mailbox", e);
        }
    }

    /**
     * 在线访谈设置
     */
    @Nullable
    private Interview interview;

    @Valid
    public Interview getInterview() {
        if (interview != null) {
            return interview;
        }
        String settings = getInterviewSettings();
        if (StringUtils.isBlank(settings)) {
            return new Interview();
        }
        try {
            return Constants.MAPPER.readValue(settings, Interview.class);
        } catch (JsonProcessingException e) {
            return new Interview();
        }
    }

    public void setInterview(Interview interview) {
        this.interview = interview;
        try {
            setInterviewSettings(Constants.MAPPER.writeValueAsString(interview));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Interview", e);
        }
    }

    /**
     * 无障碍工具条设置（商业版 T4，EaseBar）
     */
    @Nullable
    private Accessibility accessibility;

    @Valid
    public Accessibility getAccessibility() {
        if (accessibility != null) {
            return accessibility;
        }
        String settings = getAccessibilitySettings();
        if (StringUtils.isBlank(settings)) {
            return new Accessibility();
        }
        try {
            return Constants.MAPPER.readValue(settings, Accessibility.class);
        } catch (JsonProcessingException e) {
            return new Accessibility();
        }
    }

    public void setAccessibility(Accessibility accessibility) {
        this.accessibility = accessibility;
        try {
            setAccessibilitySettings(Constants.MAPPER.writeValueAsString(accessibility));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Accessibility", e);
        }
    }

    /**
     * 网站普查设置
     */
    @Nullable
    private Census census;

    @Valid
    public Census getCensus() {
        if (census != null) {
            return census;
        }
        String settings = getCensusSettings();
        if (StringUtils.isBlank(settings)) {
            return new Census();
        }
        try {
            return Constants.MAPPER.readValue(settings, Census.class);
        } catch (JsonProcessingException e) {
            return new Census();
        }
    }

    public void setCensus(Census census) {
        this.census = census;
        try {
            setCensusSettings(Constants.MAPPER.writeValueAsString(census));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Census", e);
        }
    }

    /**
     * AI设置。整体 JSON 存于 ai_settings_，镜像评论设置的处理方式（见 docs/spec/ai/ai-foundation.md §3.2）
     */
    @Nullable
    private Ai ai;

    @Valid
    public Ai getAi() {
        if (ai != null) {
            return ai;
        }
        String settings = getAiSettings();
        if (StringUtils.isBlank(settings)) {
            return new Ai();
        }
        try {
            return Constants.MAPPER.readValue(settings, Ai.class);
        } catch (JsonProcessingException e) {
            return new Ai();
        }
    }

    public void setAi(Ai ai) {
        this.ai = ai;
        try {
            setAiSettings(Constants.MAPPER.writeValueAsString(ai));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Ai", e);
        }
    }

    /**
     * AI设置原始 JSON 字段。映射 ujcms_site.ai_settings_，不放入 GeneratedSite（generated 文件不手工编辑）
     */
    @Nullable
    private String aiSettings;

    @Nullable
    public String getAiSettings() {
        return aiSettings;
    }

    public void setAiSettings(@Nullable String aiSettings) {
        this.aiSettings = aiSettings;
    }

    /**
     * GEO设置。整体 JSON 存于 geo_settings_，镜像AI设置的处理方式（见 docs/spec/ai/geo.md §3.1）
     */
    @Nullable
    private Geo geo;

    @Valid
    public Geo getGeo() {
        if (geo != null) {
            return geo;
        }
        String settings = getGeoSettings();
        if (StringUtils.isBlank(settings)) {
            return new Geo();
        }
        try {
            return Constants.MAPPER.readValue(settings, Geo.class);
        } catch (JsonProcessingException e) {
            return new Geo();
        }
    }

    public void setGeo(Geo geo) {
        this.geo = geo;
        try {
            setGeoSettings(Constants.MAPPER.writeValueAsString(geo));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Geo", e);
        }
    }

    /**
     * GEO设置原始 JSON 字段。映射 ujcms_site.geo_settings_，不放入 GeneratedSite（generated 文件不手工编辑）
     */
    @Nullable
    private String geoSettings;

    @Nullable
    public String getGeoSettings() {
        return geoSettings;
    }

    public void setGeoSettings(@Nullable String geoSettings) {
        this.geoSettings = geoSettings;
    }

    /**
     * 固定链接设置。整体 JSON 存于 permalink_settings_，镜像 AI 设置的处理方式（docs/spec/permalinks/plan.md D5）。
     * 列表接口需可见（文章表单据此决定是否显示别名字段），故不加 {@code @JsonView(Views.Whole)}
     */
    @Nullable
    private Permalink permalink;

    @Valid
    public Permalink getPermalink() {
        if (permalink != null) {
            return permalink;
        }
        String settings = getPermalinkSettings();
        if (StringUtils.isBlank(settings)) {
            return new Permalink();
        }
        try {
            return Constants.MAPPER.readValue(settings, Permalink.class);
        } catch (JsonProcessingException e) {
            return new Permalink();
        }
    }

    public void setPermalink(Permalink permalink) {
        this.permalink = permalink;
        try {
            setPermalinkSettings(Constants.MAPPER.writeValueAsString(permalink));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Permalink", e);
        }
    }

    /**
     * 固定链接设置原始 JSON 字段。映射 ujcms_site.permalink_settings_，不放入 GeneratedSite（generated 文件不手工编辑）
     */
    @Nullable
    private String permalinkSettings;

    @Nullable
    public String getPermalinkSettings() {
        return permalinkSettings;
    }

    public void setPermalinkSettings(@Nullable String permalinkSettings) {
        this.permalinkSettings = permalinkSettings;
    }

    @Nullable
    private transient Map<String, Object> editor;

    @JsonView(Views.Whole.class)
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
     * 是否有子站点。只有在查询站点列表时有效；查询站点对象时无效，请使用 `site.children?size > 0` 判断
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

    // region GeneratedSite

    /**
     * 域名
     */
    @Override
    @Pattern(regexp = "^(?!(uploads|themes|WEB-INF|cp)$)[a-z0-9-.]*", flags = Pattern.Flag.CASE_INSENSITIVE)
    public String getDomain() {
        return super.getDomain();
    }

    /**
     * 子目录格式：保留字在原 uploads|themes|WEB-INF|cp 基础上扩展 api|frontend（REST 前缀
     * 不可被遮蔽）与 m（移动端静态页输出目录 {@link Html#MOBILE_PATH}，子目录取 m 会与
     * 根站点移动端产物在磁盘上交错）。忽略大小写。控制台经 Bean Validation 校验，
     * 安装清单路径（{@code InstallService}）复用同一正则手工校验（D44）
     */
    public static final String SUB_DIR_PATTERN = "^(?!(uploads|themes|WEB-INF|cp|api|frontend|m)$)[\\w-]*";

    /**
     * 语言标签格式（BCP-47 子集）。安装清单路径复用同一正则（D44）
     */
    public static final String LANG_PATTERN = "^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$";

    /**
     * 子目录。保留字说明见 {@link #SUB_DIR_PATTERN}
     */
    @Override
    @Nullable
    @Pattern(regexp = SUB_DIR_PATTERN, flags = Pattern.Flag.CASE_INSENSITIVE)
    public String getSubDir() {
        return super.getSubDir();
    }

    /**
     * 语言(BCP-47)
     */
    @Override
    @Pattern(regexp = LANG_PATTERN)
    public String getLang() {
        return super.getLang();
    }

    /**
     * 是否语言组主站点（含不属于任何组的独立站点）
     */
    @JsonIgnore
    public boolean isLangMaster() {
        return getLangParentId() == null;
    }

    /**
     * 默认语言
     */
    public static final String DEFAULT_LANG = "zh-CN";

    /**
     * 是否属于某个语言组
     */
    @JsonIgnore
    public boolean isInLangGroup() {
        return getLangParentId() != null;
    }

    /**
     * html lang 属性值：lang_ 为空时回退默认（模板用 {@code ${site.langTag}}）
     */
    @Schema(description = "语言标签，未设置时回退 zh-CN")
    public String getLangTag() {
        return StringUtils.defaultIfBlank(getLang(), DEFAULT_LANG);
    }

    /**
     * 语言组ID：主站点为自身ID，变体为 langParentId
     */
    @JsonIgnore
    public Long getLangGroupMasterId() {
        Long langParentId = getLangParentId();
        return langParentId != null ? langParentId : getId();
    }
    // endregion

    @Schema(name = "Site.Watermark", description = "水印设置")
    public static final class Watermark implements Serializable {
        @Serial
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
        @Serial
        private static final long serialVersionUID = 1L;

        public static final String PATH_YEAR = "{year}";
        public static final String PATH_MONTH = "{month}";
        public static final String PATH_DAY = "{day}";
        public static final String PATH_CHANNEL_ID = "{channel_id}";
        public static final String PATH_CHANNEL_SLUG = "{channel_slug}";
        public static final String PATH_ARTICLE_ID = "{article_id}";
        public static final String PATH_ARTICLE_SLUG = "{article_slug}";
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
        private String channel = "/channels/{channel_slug}/index";
        /**
         * 文章静态页路径
         */
        @Length(max = 100)
        @Pattern(regexp = "^(?!.*\\.\\.)[\\w-{}/]*$")
        private String article = "/articles/{article_id}";

        public String getChannelPath(@Nullable String channelStaticPath, Long channelId, String channelSlug,
                                     int page) {
            if (StringUtils.isBlank(channelStaticPath)) {
                channelStaticPath = channel;
            }
            String path = StringUtils.replaceEach(channelStaticPath,
                    new String[]{PATH_CHANNEL_ID, PATH_CHANNEL_SLUG},
                    new String[]{String.valueOf(channelId), channelSlug});
            if (page > 1) {
                return path + "_" + page + SUFFIX;
            }
            return path + SUFFIX;
        }

        public String getChannelUrl(@Nullable String channelStaticPath, Long channelId, String channelSlug, int page) {
            String path = getChannelPath(channelStaticPath, channelId, channelSlug, page);
            if (path.endsWith(DEFAULT_PAGE)) {
                // /index.html 改为 / 结尾
                return path.substring(0, path.length() - DEFAULT_PAGE.length() + 1);
            }
            return path;
        }

        public String getArticlePath(@Nullable String articleStaticPath, Long channelId, String channelSlug,
                                     Long articleId, @Nullable String articleSlug, OffsetDateTime createdDate,
                                     int page) {
            if (StringUtils.isBlank(articleStaticPath)) {
                articleStaticPath = article;
            }
            String year = String.valueOf(createdDate.getYear());
            String month = StringUtils.leftPad(String.valueOf(createdDate.getMonthValue()), 2, '0');
            String day = StringUtils.leftPad(String.valueOf(createdDate.getDayOfMonth()), 2, '0');
            String url = StringUtils.replaceEach(articleStaticPath,
                    new String[]{PATH_CHANNEL_ID, PATH_CHANNEL_SLUG, PATH_ARTICLE_ID, PATH_ARTICLE_SLUG,
                            PATH_YEAR, PATH_MONTH, PATH_DAY},
                    new String[]{String.valueOf(channelId), channelSlug, String.valueOf(articleId),
                            StringUtils.isNotBlank(articleSlug) ? articleSlug : String.valueOf(articleId),
                            year, month, day});
            if (page > 1) {
                return url + "_" + page + SUFFIX;
            }
            return url + SUFFIX;
        }

        public String getArticleUrl(@Nullable String articleStaticPath, Long channelId, String channelSlug,
                                    Long articleId, @Nullable String articleSlug, OffsetDateTime createdDate,
                                    int page) {
            String path = getArticlePath(articleStaticPath, channelId, channelSlug,
                    articleId, articleSlug, createdDate, page);
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
        @Serial
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

    @Schema(name = "Site.Comment", description = "评论设置")
    public static class Comment implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 是否开启
         */
        private boolean enabled = true;
        /**
         * 是否需要登录
         */
        private boolean loginRequired = true;
        /**
         * 是否需要审核
         */
        private boolean auditRequired = true;
        /**
         * 是否开启图片。新增的存储和滥用面，默认关闭
         */
        private boolean imageEnabled = false;
        /**
         * 单条评论最多图片数
         */
        private int imageMaxCount = 3;

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

        public boolean isAuditRequired() {
            return auditRequired;
        }

        public void setAuditRequired(boolean auditRequired) {
            this.auditRequired = auditRequired;
        }

        public boolean isImageEnabled() {
            return imageEnabled;
        }

        public void setImageEnabled(boolean imageEnabled) {
            this.imageEnabled = imageEnabled;
        }

        public int getImageMaxCount() {
            return imageMaxCount;
        }

        public void setImageMaxCount(int imageMaxCount) {
            this.imageMaxCount = imageMaxCount;
        }
    }

    @Schema(name = "Site.Disclosure", description = "依申请公开设置")
    public static class Disclosure implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 是否开启
         */
        private boolean enabled = false;
        /**
         * 是否需要登录
         */
        private boolean loginRequired = false;

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

    @Schema(name = "Site.Mailbox", description = "领导信箱设置")
    public static class Mailbox implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 是否开启
         */
        private boolean enabled = false;
        /**
         * 是否需要登录
         */
        private boolean loginRequired = false;
        /**
         * 办理期限（工作日）
         */
        @Min(1)
        @Max(60)
        private int handlingDays = 7;
        /**
         * 延期后办理期限（工作日）
         */
        @Min(1)
        @Max(120)
        private int extendedDays = 15;
        /**
         * 是否开启答复短信通知（需先在全局设置中配置短信服务商）
         */
        private boolean smsEnabled = false;
        /**
         * 答复通知短信模板（阿里云为模板Code，腾讯云为模板ID；模板参数为信件编号）
         */
        @Nullable
        @Length(max = 32)
        private String smsTemplateCode;
        /**
         * 短信模板变量名称（仅阿里云需要，腾讯云为位置参数）
         */
        @Length(max = 20)
        private String smsCodeName = "code";

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

        public int getHandlingDays() {
            return handlingDays;
        }

        public void setHandlingDays(int handlingDays) {
            this.handlingDays = handlingDays;
        }

        public int getExtendedDays() {
            return extendedDays;
        }

        public void setExtendedDays(int extendedDays) {
            this.extendedDays = extendedDays;
        }

        public boolean isSmsEnabled() {
            return smsEnabled;
        }

        public void setSmsEnabled(boolean smsEnabled) {
            this.smsEnabled = smsEnabled;
        }

        @Nullable
        public String getSmsTemplateCode() {
            return smsTemplateCode;
        }

        public void setSmsTemplateCode(@Nullable String smsTemplateCode) {
            this.smsTemplateCode = smsTemplateCode;
        }

        public String getSmsCodeName() {
            return smsCodeName;
        }

        public void setSmsCodeName(String smsCodeName) {
            this.smsCodeName = smsCodeName;
        }
    }

    @Schema(name = "Site.Interview", description = "在线访谈设置")
    public static class Interview implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 是否开启
         */
        private boolean enabled = false;
        /**
         * 提问是否需要登录
         */
        private boolean loginRequired = false;

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

    /**
     * 无障碍·适老化辅助浏览工具条（EaseBar）站点设置。商业版 T4；参考主题以
     * {@code [#if config.commercialTier gte 4 && site.accessibility.enabled]} 守卫加载
     * {@code /static/easebar/easebar.min.js}，各项经 {@code data-*} 传给脚本。
     */
    @Schema(name = "Site.Accessibility", description = "无障碍工具条设置")
    public static class Accessibility implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 是否开启
         */
        private boolean enabled = false;
        /**
         * 显示页面左侧悬浮“无障碍”入口按钮
         */
        private boolean entryButton = true;
        /**
         * 首次访问自动展开工具条
         */
        private boolean defaultOpen = false;
        /**
         * 站点自有《无障碍声明/帮助》页面地址；留空使用工具条内置说明弹窗
         */
        @Nullable
        @Length(max = 255)
        private String helpUrl;
        /**
         * 长者专版/关怀版页面地址；留空使用工具条内置关怀模式
         */
        @Nullable
        @Length(max = 255)
        private String elderUrl;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEntryButton() {
            return entryButton;
        }

        public void setEntryButton(boolean entryButton) {
            this.entryButton = entryButton;
        }

        public boolean isDefaultOpen() {
            return defaultOpen;
        }

        public void setDefaultOpen(boolean defaultOpen) {
            this.defaultOpen = defaultOpen;
        }

        @Nullable
        public String getHelpUrl() {
            return helpUrl;
        }

        public void setHelpUrl(@Nullable String helpUrl) {
            this.helpUrl = helpUrl;
        }

        @Nullable
        public String getElderUrl() {
            return elderUrl;
        }

        public void setElderUrl(@Nullable String elderUrl) {
            this.elderUrl = elderUrl;
        }
    }

    /**
     * 固定链接设置（docs/spec/permalinks/plan.md）。文章动态 URL 形态三选一；解析端接受全部形态，
     * 非规范形 301 至规范形，因此切换形态不破坏既有外链。静态化开启时以 {@link Html#getArticle()} 路径模板为准。
     * 切换形态后需全文重建（索引中存有 URL）。
     */
    @Schema(name = "Site.Permalink", description = "固定链接设置")
    public static class Permalink implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 数字型：/article/{id}
         */
        public static final String ARTICLE_ID = "id";
        /**
         * 混合型：/article/{id}-{slug}（默认）
         */
        public static final String ARTICLE_ID_SLUG = "id-slug";
        /**
         * 别名型：/article/{slug}
         */
        public static final String ARTICLE_SLUG = "slug";

        /**
         * 文章固定链接形态
         */
        @NotNull
        @Pattern(regexp = "^(id|id-slug|slug)$")
        private String article = ARTICLE_ID_SLUG;
        /**
         * 自动生成别名时，中文标题是否转为拼音（关闭时中文标题的别名回退为 ID）
         */
        private boolean pinyin = false;

        public String getArticle() {
            return article;
        }

        public void setArticle(String article) {
            this.article = article;
        }

        public boolean isPinyin() {
            return pinyin;
        }

        public void setPinyin(boolean pinyin) {
            this.pinyin = pinyin;
        }
    }

    @Schema(name = "Site.Census", description = "网站普查设置")
    public static class Census implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 动态要闻类栏目ID列表（按2周未更新检查）
         */
        private List<Long> newsChannelIds = new ArrayList<>();
        /**
         * 政策文件、通知公告类栏目ID列表（按6个月未更新检查）
         */
        private List<Long> policyChannelIds = new ArrayList<>();
        /**
         * 不参与检查的栏目ID列表
         */
        private List<Long> excludedChannelIds = new ArrayList<>();
        /**
         * 其他栏目长期未更新阈值（月）
         */
        @Min(1)
        @Max(120)
        private int staleMonths = 12;
        /**
         * 是否开启链接可用性检查
         */
        private boolean linkCheckEnabled = true;
        /**
         * 链接检查超时时间（毫秒）
         */
        @Min(1000)
        @Max(30000)
        private int linkTimeoutMs = 5000;
        /**
         * 单次普查检查链接数上限
         */
        @Min(100)
        @Max(100000)
        private int maxUrls = 2000;

        public List<Long> getNewsChannelIds() {
            return newsChannelIds;
        }

        public void setNewsChannelIds(List<Long> newsChannelIds) {
            this.newsChannelIds = newsChannelIds;
        }

        public List<Long> getPolicyChannelIds() {
            return policyChannelIds;
        }

        public void setPolicyChannelIds(List<Long> policyChannelIds) {
            this.policyChannelIds = policyChannelIds;
        }

        public List<Long> getExcludedChannelIds() {
            return excludedChannelIds;
        }

        public void setExcludedChannelIds(List<Long> excludedChannelIds) {
            this.excludedChannelIds = excludedChannelIds;
        }

        public int getStaleMonths() {
            return staleMonths;
        }

        public void setStaleMonths(int staleMonths) {
            this.staleMonths = staleMonths;
        }

        public boolean isLinkCheckEnabled() {
            return linkCheckEnabled;
        }

        public void setLinkCheckEnabled(boolean linkCheckEnabled) {
            this.linkCheckEnabled = linkCheckEnabled;
        }

        public int getLinkTimeoutMs() {
            return linkTimeoutMs;
        }

        public void setLinkTimeoutMs(int linkTimeoutMs) {
            this.linkTimeoutMs = linkTimeoutMs;
        }

        public int getMaxUrls() {
            return maxUrls;
        }

        public void setMaxUrls(int maxUrls) {
            this.maxUrls = maxUrls;
        }
    }

    @Schema(name = "Site.Ai", description = "AI设置")
    public static class Ai implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 审核模式：关闭
         */
        public static final int MODERATION_MODE_OFF = 0;
        /**
         * 审核模式：标记。仅打标供人工参考，不改变内容状态
         */
        public static final int MODERATION_MODE_FLAG = 1;
        /**
         * 审核模式：自动处置。仅作用于待审内容，不触碰已发布内容
         */
        public static final int MODERATION_MODE_ENFORCE = 2;

        /**
         * 是否开启智能SEO（元信息生成）
         */
        private boolean seoAssist = true;
        /**
         * 是否开启写作助手
         */
        private boolean writingAssist = true;
        /**
         * 是否开启内容筛查
         */
        private boolean screening = true;
        /**
         * 审核模式(0:关闭,1:标记,2:自动处置)。默认标记，人工负责制的安全姿态
         */
        @Min(0)
        @Max(2)
        private int moderationMode = MODERATION_MODE_FLAG;
        /**
         * 自动通过阈值。置信度低于该值自动通过，仅自动处置模式生效
         */
        @Min(0)
        @Max(100)
        private int autoApproveBelow = 20;
        /**
         * 自动拦截阈值。置信度高于该值自动拦截，仅自动处置模式生效。须大于自动通过阈值
         */
        @Min(0)
        @Max(100)
        private int autoBlockAbove = 90;
        /**
         * 是否开启回复拟稿
         */
        private boolean replyDraft = true;
        /**
         * 是否开启AI预翻译（语言组内对已链接草稿的预翻译填充）
         */
        private boolean translate = true;
        /**
         * 是否开启相关文章智能推荐（AI 在启发式候选池上重排并给出理由；关闭后控制台仅显示启发式候选）
         */
        private boolean relatedAssist = true;
        /**
         * 站点每日token配额（提示词+生成）。0为不限制，仍受全局每日上限约束
         */
        @Min(0)
        private long dailyTokenQuota = 0;

        public boolean isSeoAssist() {
            return seoAssist;
        }

        public void setSeoAssist(boolean seoAssist) {
            this.seoAssist = seoAssist;
        }

        public boolean isWritingAssist() {
            return writingAssist;
        }

        public void setWritingAssist(boolean writingAssist) {
            this.writingAssist = writingAssist;
        }

        public boolean isScreening() {
            return screening;
        }

        public void setScreening(boolean screening) {
            this.screening = screening;
        }

        public int getModerationMode() {
            return moderationMode;
        }

        public void setModerationMode(int moderationMode) {
            this.moderationMode = moderationMode;
        }

        public int getAutoApproveBelow() {
            return autoApproveBelow;
        }

        public void setAutoApproveBelow(int autoApproveBelow) {
            this.autoApproveBelow = autoApproveBelow;
        }

        public int getAutoBlockAbove() {
            return autoBlockAbove;
        }

        public void setAutoBlockAbove(int autoBlockAbove) {
            this.autoBlockAbove = autoBlockAbove;
        }

        public boolean isReplyDraft() {
            return replyDraft;
        }

        public void setReplyDraft(boolean replyDraft) {
            this.replyDraft = replyDraft;
        }

        public boolean isTranslate() {
            return translate;
        }

        public void setTranslate(boolean translate) {
            this.translate = translate;
        }

        public boolean isRelatedAssist() {
            return relatedAssist;
        }

        public void setRelatedAssist(boolean relatedAssist) {
            this.relatedAssist = relatedAssist;
        }

        public long getDailyTokenQuota() {
            return dailyTokenQuota;
        }

        public void setDailyTokenQuota(long dailyTokenQuota) {
            this.dailyTokenQuota = dailyTokenQuota;
        }
    }

    /**
     * GEO（生成式引擎优化）设置。独立于 {@link Ai}：robots.txt 等基础设施文件不受AI总开关影响（docs/spec/ai/geo.md §3.1）
     */
    @Schema(name = "Site.Geo", description = "GEO设置")
    public static class Geo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 是否输出JSON-LD结构化数据
         */
        private boolean jsonLdEnabled = true;
        /**
         * 是否开启 llms.txt
         */
        private boolean llmsTxtEnabled = true;
        /**
         * llms.txt 栏目ID列表。为空默认取前两级导航栏目
         */
        @Nullable
        private List<Long> llmsTxtChannelIds;
        /**
         * llms.txt 最新文章条数
         */
        @Min(0)
        @Max(100)
        private int llmsTxtArticleCount = 20;
        /**
         * 是否开启 robots.txt
         */
        private boolean robotsEnabled = true;
        /**
         * robots.txt 自定义内容。非空则原样输出并忽略生成规则；自身未含 {@code Sitemap:} 行时才追加 Sitemap 行。
         * 长度上限与控制台 textarea 的 maxlength 一致
         */
        @Nullable
        @Length(max = 30000)
        private String robotsCustom;
        /**
         * 禁止抓取的AI爬虫 User-agent 列表。拒绝名单：未列出的默认允许（GEO 默认欢迎AI爬虫收录）。
         * 每项限定 robots.txt product token 字符集：一个换行就能把一项拆成两条指令，故在入口校验
         */
        @Size(max = 100)
        private List<@Pattern(regexp = AI_AGENT_PATTERN) String> disallowedAiAgents =
                new ArrayList<>(List.of("Bytespider"));
        /**
         * 是否开启百度普通收录 API 推送（主动推送）：文章发布后自动提交文章地址（docs/spec/sitemap/plan.md D13）
         */
        private boolean baiduPushEnabled = false;
        /**
         * 百度推送接口调用地址：搜索资源平台「普通收录 → API提交」页面给出的完整地址（含 site 与 token 参数）。
         * 限定官方域名，防止把站内地址列表投递到任意主机
         */
        @Nullable
        @Length(max = 300)
        @Pattern(regexp = "^https?://data\\.zz\\.baidu\\.com/urls\\?.+$")
        private String baiduPushUrl;
        /**
         * 是否开启 IndexNow 推送（Bing、Yandex、Naver、Seznam 等共享）：文章发布、更新、彻底删除后通知
         */
        private boolean indexNowEnabled = false;
        /**
         * IndexNow 密钥：8～128 位字母、数字、连字符。密钥文件由本站 {@code /indexnow-{key}.txt} 动态提供
         */
        @Nullable
        @Pattern(regexp = "^[A-Za-z0-9-]{8,128}$")
        private String indexNowKey;

        /**
         * AI 爬虫 User-agent 项的合法形态：字母、数字、下划线、点、连字符，1～100 位
         */
        public static final String AI_AGENT_PATTERN = "^[A-Za-z0-9_.\\-]{1,100}$";

        public boolean isJsonLdEnabled() {
            return jsonLdEnabled;
        }

        public void setJsonLdEnabled(boolean jsonLdEnabled) {
            this.jsonLdEnabled = jsonLdEnabled;
        }

        public boolean isLlmsTxtEnabled() {
            return llmsTxtEnabled;
        }

        public void setLlmsTxtEnabled(boolean llmsTxtEnabled) {
            this.llmsTxtEnabled = llmsTxtEnabled;
        }

        @Nullable
        public List<Long> getLlmsTxtChannelIds() {
            return llmsTxtChannelIds;
        }

        public void setLlmsTxtChannelIds(@Nullable List<Long> llmsTxtChannelIds) {
            this.llmsTxtChannelIds = llmsTxtChannelIds;
        }

        public int getLlmsTxtArticleCount() {
            return llmsTxtArticleCount;
        }

        public void setLlmsTxtArticleCount(int llmsTxtArticleCount) {
            this.llmsTxtArticleCount = llmsTxtArticleCount;
        }

        public boolean isRobotsEnabled() {
            return robotsEnabled;
        }

        public void setRobotsEnabled(boolean robotsEnabled) {
            this.robotsEnabled = robotsEnabled;
        }

        @Nullable
        public String getRobotsCustom() {
            return robotsCustom;
        }

        public void setRobotsCustom(@Nullable String robotsCustom) {
            this.robotsCustom = robotsCustom;
        }

        public List<String> getDisallowedAiAgents() {
            return disallowedAiAgents;
        }

        public void setDisallowedAiAgents(List<String> disallowedAiAgents) {
            this.disallowedAiAgents = disallowedAiAgents;
        }

        public boolean isBaiduPushEnabled() {
            return baiduPushEnabled;
        }

        public void setBaiduPushEnabled(boolean baiduPushEnabled) {
            this.baiduPushEnabled = baiduPushEnabled;
        }

        @Nullable
        public String getBaiduPushUrl() {
            return baiduPushUrl;
        }

        public void setBaiduPushUrl(@Nullable String baiduPushUrl) {
            this.baiduPushUrl = baiduPushUrl;
        }

        public boolean isIndexNowEnabled() {
            return indexNowEnabled;
        }

        public void setIndexNowEnabled(boolean indexNowEnabled) {
            this.indexNowEnabled = indexNowEnabled;
        }

        @Nullable
        public String getIndexNowKey() {
            return indexNowKey;
        }

        public void setIndexNowKey(@Nullable String indexNowKey) {
            this.indexNowKey = indexNowKey;
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
