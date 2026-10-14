package com.ujcms.cms.core.security;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.owasp.html.CssSchema;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.beans.factory.annotation.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 防跨站攻击的 HTML 清理服务。按 {@link PolicyTier} 提供宽严不同的 OWASP HTML Sanitizer 过滤策略。
 *
 * <p>策略在构造时一次性创建（{@link PolicyFactory} 线程安全、可复用），清理只在写入（insert/update）时
 * 执行，读取直接返回库中已清理的内容，因此不增加读取开销，也不影响 headless API 的安全性。
 *
 * <p>{@link PolicyTier#RICH} 是一套自包含的宽松白名单，并且仍是此前所用策略的超集（由单元测试校验），
 * 因此既放宽了对可信编辑者的限制，又不会在再次保存时收紧已存储的内容。
 *
 * @author PONY
 */
@Service
public class HtmlSanitizeService {
    private final Map<PolicyTier, PolicyFactory> factories;
    /** OWASP 渲染器输出的数值字符实体：{@code &#1234;} 或 {@code &#x1f600;}。 */
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(?:x([0-9a-fA-F]{1,6})|([0-9]{1,7}));");

    public HtmlSanitizeService(
            @Value("${ujcms.html-sanitize.iframe-allow-hosts:}") String iframeAllowHosts) {
        List<String> hosts = Arrays.stream(iframeAllowHosts.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        Map<PolicyTier, PolicyFactory> map = new EnumMap<>(PolicyTier.class);
        map.put(PolicyTier.RICH, buildRich(hosts));
        map.put(PolicyTier.BASIC, Sanitizers.FORMATTING.and(Sanitizers.BLOCKS)
                .and(Sanitizers.IMAGES).and(Sanitizers.LINKS));
        // Sanitizers.LINKS 会自动给链接加 rel="nofollow"。
        map.put(PolicyTier.STRICT, Sanitizers.FORMATTING.and(Sanitizers.LINKS));
        // 不允许任何元素：去除全部标签，仅保留文本内容。
        map.put(PolicyTier.TEXT, new HtmlPolicyBuilder().toFactory());
        this.factories = Collections.unmodifiableMap(map);
    }

    /**
     * 按指定层级清理 HTML。
     *
     * @param html 原始 HTML，可为 {@code null}
     * @param tier 策略层级
     * @return 清理后的 HTML；入参为 {@code null} 时返回 {@code null}
     */
    @Nullable
    public String sanitize(@Nullable String html, PolicyTier tier) {
        if (html == null) {
            return null;
        }
        return decodeNonAsciiEntities(getFactory(tier).sanitize(html));
    }

    /**
     * 把 OWASP 渲染器输出中的非 ASCII 字符实体还原为字面字符。
     *
     * <p>OWASP 为防御“下游先做 Unicode 归一化再解析 HTML”的攻击面，会把 U+FE60 以上的全部字符
     * （含全角标点“？，！”、全角字母数字）以及所有增补平面字符（emoji）输出为 {@code &#xNNNN;} 实体。
     * 浏览器渲染无差别，但中文正文会因此膨胀约 10%，且直接读取库中 HTML 的消费方（AI 翻译提示词、
     * 导出、原始 SQL 检索、日后任何服务端读正文的功能）都会看到大量实体噪音。本系统只把 HTML 交给
     * 浏览器解析，不存在归一化后重新解析的下游，该编码没有防御价值，故在此统一还原。
     *
     * <p>只还原码点 ≥ U+00A0 的实体：ASCII 范围的 {@code &lt; &gt; &amp; &#34; &#39;} 原样保留——
     * 它们才有结构意义；非 ASCII 字符不可能构成标签或属性，还原不改变策略已做出的判断（策略本就在
     * 解码后的文本上判定）。U+2028/U+2029（JS 字符串行终止符）、U+FEFF（BOM）与非字符保持实体形式。
     * 结果对再次清理稳定：重新清理会先编码再还原得到同一字符串（种子往返 / 未改动行重存不改写）。
     */
    static String decodeNonAsciiEntities(String html) {
        if (html.indexOf("&#") < 0) {
            return html;
        }
        Matcher m = NUMERIC_ENTITY.matcher(html);
        StringBuilder sb = new StringBuilder(html.length());
        int last = 0;
        while (m.find()) {
            int cp = m.group(1) != null ? Integer.parseInt(m.group(1), 16) : Integer.parseInt(m.group(2));
            if (!isSafeAsLiteral(cp)) {
                continue;
            }
            sb.append(html, last, m.start()).appendCodePoint(cp);
            last = m.end();
        }
        if (last == 0) {
            return html;
        }
        return sb.append(html, last, html.length()).toString();
    }

    private static boolean isSafeAsLiteral(int cp) {
        if (cp < 0xA0 || cp > Character.MAX_CODE_POINT) {
            return false;
        }
        if (cp == 0x2028 || cp == 0x2029 || cp == 0xFEFF) {
            return false;
        }
        if (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE) {
            return false;
        }
        // Unicode 非字符：U+FDD0..U+FDEF 及每个平面末尾的 xxFFFE/xxFFFF
        return (cp & 0xFFFE) != 0xFFFE && (cp < 0xFDD0 || cp > 0xFDEF);
    }

    /**
     * 获取指定层级的过滤策略工厂。未知层级回退到 {@link PolicyTier#RICH}。
     */
    public PolicyFactory getFactory(PolicyTier tier) {
        return factories.getOrDefault(tier, factories.get(PolicyTier.RICH));
    }

    /**
     * 构建富文本（可信编辑者）策略：一套自包含的、尽可能宽松的白名单，覆盖富文本编辑器可能产生的各类安全元素与属性
     * （对标 DOMPurify 默认 HTML 白名单）。放宽的只是“覆盖面”，安全性由下列不变量保证，与宽松程度无关：
     * <ul>
     *   <li>绝不允许任何 {@code on*} 事件处理属性（白名单天然拦截，这是 XSS 的主要载体）；</li>
     *   <li>绝不允许 script / style(元素) / object / embed / applet / base / meta / link / frame / svg / math
     *       （svg/math 携带本库无法安全清理的脚本向量，故一并排除）；</li>
     *   <li>URL 属性（href/src/action 等）逐元素放行并强制 {@code allowStandardUrlProtocols}，杜绝 javascript:/data: 等；</li>
     *   <li>iframe 仅按域名白名单放行；CSS 由 {@code Sanitizers.STYLES} 逐属性值校验，杜绝 expression()/url(javascript:) 等。</li>
     * </ul>
     * 已知限制：OWASP 不支持属性名通配符，故 {@code data-*} 只能枚举常见项（其值为惰性，浏览器不会执行，无安全风险）。
     */
    private static PolicyFactory buildRich(List<String> iframeAllowHosts) {
        HtmlPolicyBuilder b = new HtmlPolicyBuilder();
        b.allowStandardUrlProtocols();
        b.allowElements(
                "section", "nav", "article", "aside", "header", "footer", "main", "figure", "figcaption",
                "hgroup", "div", "p", "span", "hr", "br", "wbr", "pre", "blockquote", "address",
                "details", "summary", "dialog", "menu",
                "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd",
                "a", "abbr", "b", "bdi", "bdo", "cite", "code", "data", "dfn", "em", "i", "kbd", "mark", "q",
                "rp", "rt", "ruby", "s", "samp", "small", "strong", "sub", "sup", "time", "u", "var", "del", "ins",
                "tt", "big", "center", "font", "strike", "nobr",
                "img", "picture", "source", "track", "map", "area", "video", "audio",
                "table", "caption", "colgroup", "col", "thead", "tbody", "tfoot", "tr", "td", "th",
                "form", "fieldset", "legend", "label", "input", "button", "select", "optgroup", "option",
                "textarea", "datalist", "output", "progress", "meter");
        // 全局安全属性：均为惰性的元数据/表现属性，既非 URL 也非事件处理属性。
        b.allowAttributes(
                "class", "id", "title", "dir", "lang", "translate", "role", "tabindex", "contenteditable",
                "align", "valign", "width", "height", "bgcolor", "color", "face", "size", "border",
                "cellpadding", "cellspacing", "span", "start", "reversed", "value", "datetime",
                "colspan", "rowspan", "headers", "scope", "abbr", "nowrap",
                "alt", "sizes", "decoding", "loading", "ismap", "usemap",
                // 表单控件（非 URL、非事件）属性
                "name", "type", "placeholder", "disabled", "readonly", "required", "checked", "selected",
                "multiple", "min", "max", "step", "maxlength", "minlength", "pattern", "rows", "cols", "for",
                "accept", "accept-charset", "autocomplete", "wrap", "list", "open", "method", "enctype",
                "novalidate", "label", "high", "low", "optimum", "controls", "loop", "muted", "preload",
                "autoplay", "playsinline", "kind", "srclang", "default",
                // ARIA 标准词表（均为惰性元数据）
                "aria-label", "aria-labelledby", "aria-describedby", "aria-details", "aria-description",
                "aria-hidden", "aria-expanded", "aria-controls", "aria-live", "aria-atomic", "aria-relevant",
                "aria-busy", "aria-level", "aria-current", "aria-disabled", "aria-selected", "aria-checked",
                "aria-pressed", "aria-haspopup", "aria-valuemin", "aria-valuemax", "aria-valuenow",
                "aria-valuetext", "aria-orientation", "aria-placeholder", "aria-readonly", "aria-required",
                "aria-multiline", "aria-multiselectable", "aria-owns", "aria-flowto", "aria-activedescendant",
                "aria-colcount", "aria-colindex", "aria-colspan", "aria-rowcount", "aria-rowindex",
                "aria-rowspan", "aria-setsize", "aria-posinset", "aria-sort", "aria-roledescription",
                "aria-keyshortcuts", "aria-modal", "aria-errormessage", "aria-invalid", "aria-autocomplete",
                // 常见 data-*（本库无法用通配符匹配属性名；data-* 值为惰性，浏览器不会执行）
                "data-id", "data-type", "data-name", "data-value", "data-index", "data-key", "data-align",
                "data-lang", "data-language", "data-line", "data-row", "data-col", "data-href", "data-src",
                "data-url", "data-target", "data-toggle", "data-title", "data-content", "data-footnote",
                "data-footnote-ref", "data-footnote-id", "data-backticks").globally();
        // URL 属性：逐元素放行，由 allowStandardUrlProtocols 强制协议校验（杜绝 javascript:/data: 等）。
        b.allowAttributes("href").onElements("a", "area");
        b.allowAttributes("src").onElements("img", "input", "track", "video", "audio", "source");
        b.allowAttributes("srcset").onElements("img", "source");
        b.allowAttributes("poster").onElements("video");
        b.allowAttributes("cite").onElements("blockquote", "q", "del", "ins");
        b.allowAttributes("action").onElements("form");
        b.allowAttributes("formaction").onElements("button", "input");
        b.allowAttributes("target", "rel", "download", "hreflang", "referrerpolicy").onElements("a", "area");
        // 浮动 + 图片居中用的 display:block（Sanitizers.STYLES/CssSchema.DEFAULT 不含 display；只允许 block，避免 display:none 隐藏内容）。
        b.allowStyling(CssSchema.withProperties(ImmutableSet.of("float")));
        b.allowStyling(CssSchema.withProperties(ImmutableMap.of("display",
                new CssSchema.Property(0, ImmutableSet.of("block"), ImmutableMap.of()))));
        PolicyFactory rich = b.toFactory();

        // Sanitizers.STYLES：逐属性值校验的安全 CSS（颜色/对齐/字号/边距等）；Sanitizers.TABLES：表格属性。
        PolicyFactory result = rich
                .and(Sanitizers.STYLES)
                .and(Sanitizers.TABLES);
        if (!iframeAllowHosts.isEmpty()) {
            result = result.and(buildIframe(iframeAllowHosts));
        }
        return result;
    }

    /**
     * 仅允许 src 指向白名单域名的 iframe（嵌入视频等）。白名单为空时不调用本方法（默认禁止 iframe）。
     */
    private static PolicyFactory buildIframe(List<String> hosts) {
        String hostAlt = hosts.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        // 允许 https?:// 或协议相对 //，可带任意子域名，主机须命中白名单。
        Pattern src = Pattern.compile(
                "^(https?:)?//([\\w-]+\\.)*(" + hostAlt + ")(:\\d+)?(/.*)?$", Pattern.CASE_INSENSITIVE);
        return new HtmlPolicyBuilder()
                .allowElements("iframe")
                .allowStandardUrlProtocols()
                .allowAttributes("src").matching(src).onElements("iframe")
                .allowAttributes("width", "height", "frameborder", "allow", "allowfullscreen",
                        "loading", "title", "referrerpolicy").onElements("iframe")
                .toFactory();
    }
}
