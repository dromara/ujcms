package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.composition.RegionTemplateSupport;
import com.ujcms.cms.core.composition.ResolvedRegion;
import com.ujcms.cms.core.composition.RowLayoutCatalog;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import freemarker.core.Environment;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateScalarModel;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 区域 指令（page-regions §7.4 / component-settings §4.2）。{@code <@region name='body' [page='home'] />}
 * —— 按 (站点, 活动主题, 渲染模板名) 解析模板轨道页面区域，<b>自身铺出</b> 行→列→面板的 Tailwind 栅格
 * （与 React {@code rowLayouts.ts} 逐键一致），
 * 并把每个面板按其组件分派给主题片段 {@code snippets/panels/<Shape>.html} 渲染（片段以
 * {@code items}/{@code settings}/{@code panel} 取得已解析内容——标题等装饰由组件自渲染，
 * 框架级标题行已随 sectionData 退役）。与 {@code <@slot>} 不同：插槽渲染主题撰写的标签体，
 * 区域指令自己拥有栅格、面板交给片段。
 *
 * <p>优雅降级（与 React/Vue 渲染器一致，§8）：空区域 → 仅输出区域壳（可视化搭建器的投放目标，visual-builder P0）；
 * 未知行布局 → 回退 {@code full}（单列堆叠）；未知面板组件（无对应片段）→ 占位提示；片段渲染异常 → 占位；
 * 禁用面板 → 生产跳过、插槽预览下输出 {@code data-panel-disabled} 占位桩。各列带 {@code data-col} 序号。
 * 任意环节都不破坏页面（§9.3）。
 *
 * @author PONY
 */
public class RegionDirective implements TemplateDirectiveModel {
    private static final Logger logger = LoggerFactory.getLogger(RegionDirective.class);

    /**
     * 区域名。字符串(String)。必填。
     */
    public static final String NAME = "name";
    /**
     * 页面名覆盖。字符串(String)。可选——边缘场景显式指定页面清单名。
     */
    public static final String PAGE = "page";
    /**
     * 渲染上下文中的当前频道变量名（频道页携带，§14.2 $ctx.channel）
     */
    private static final String CHANNEL = "channel";

    /**
     * 面板片段目录（相对当前页面模板解析——页面位于 {@code <theme>/templates/}，
     * 片段位于 {@code <theme>/snippets/panels/}，镜像页面 {@code [#include '../snippets/…']} 的相对寻址）。
     */
    private static final String SNIPPET_DIR = "../snippets/panels/";
    private static final String SNIPPET_SUFFIX = ".html";
    /**
     * 面板片段可见的绑定名：条目列表 / 契约设置值 / 面板元信息（component-settings §4.2）。
     */
    private static final String VAR_ITEMS = "items";
    private static final String VAR_SETTINGS = "settings";
    private static final String VAR_PANEL = "panel";

    private final RegionTemplateSupport support;

    public RegionDirective(RegionTemplateSupport support) {
        this.support = support;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        String name = Directives.getString(params, NAME);
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Param name is required.");
        }
        String page = Directives.getString(params, PAGE);
        Site site = findSite(env);
        String templateName = getTemplateName(env);
        Channel channel = findChannel(env);
        String channelSlug = channel != null ? channel.getSlug() : null;
        Long channelId = channel != null ? channel.getId() : null;
        // 活动布局变体（前台控制器写入模型 layoutVariant，"<surface>:<value>"）→ 范围覆盖候选（§9）
        String variant = getModelString(env, Frontends.LAYOUT_VARIANT);

        ResolvedRegion region = support.resolve(site, templateName, page, name, channelSlug, channelId, variant);
        List<ResolvedRegion.Row> rows = region.layout().rows();
        // 空区域也输出区域壳：可视化搭建器需要空区域作为投放目标（visual-builder P0）；生产环境为无害空 div
        Writer out = env.getOut();
        out.write("<div class=\"space-y-8\" data-region=\"" + escapeAttr(name) + "\""
                + (region.draft() ? " data-region-draft=\"1\"" : "") + ">");
        for (ResolvedRegion.Row row : rows) {
            renderRow(env, out, row);
        }
        out.write("</div>");
    }

    private void renderRow(Environment env, Writer out, ResolvedRegion.Row row)
            throws TemplateException, IOException {
        String resolvedKey = resolveRowLayoutKey(row.layout());
        Grid grid = tailwindGrid(resolvedKey);
        out.write("<div class=\"grid gap-8 " + grid.grid() + "\" data-row=\"" + escapeAttr(row.id())
                + "\" data-row-layout=\"" + escapeAttr(resolvedKey) + "\">");
        List<ResolvedRegion.Column> columns = row.columns();
        for (int i = 0; i < columns.size(); i++) {
            String colSpan = i < grid.colSpans().size() ? grid.colSpans().get(i) : "";
            out.write("<div" + (colSpan.isEmpty() ? "" : " class=\"" + colSpan + "\"")
                    + " data-col=\"" + i + "\"><div class=\"space-y-8\">");
            for (ResolvedRegion.Panel panel : columns.get(i).panels()) {
                renderPanel(env, out, panel);
            }
            out.write("</div></div>");
        }
        out.write("</div>");
    }

    private void renderPanel(Environment env, Writer out, ResolvedRegion.Panel panel)
            throws TemplateException, IOException {
        String component = panel.component() != null ? panel.component() : "";
        if (!panel.enabled()) {
            // 禁用面板：生产跳过；插槽预览下输出占位桩，供可视化搭建器显示并重新启用（visual-builder P0）
            if (Contexts.isSlotPreview()) {
                out.write("<div data-panel-disabled=\"" + escapeAttr(panel.id())
                        + "\" data-panel=\"" + escapeAttr(component) + "\"></div>");
            }
            return;
        }
        // 按设备显示（design-review P2-5 / docs/decisions/visual-builder/04）：模板轨的设备口径 = UA
        // （Contexts.isMobile，与移动主题选择、静态页双份生成同一口径）——生产不匹配即整体跳过；
        // 预览一律渲染并带 data-panel-visibility 标记，构建器画布按视口预设经桥的编辑态 CSS 隐藏。
        if (!Contexts.isSlotPreview() && !panel.visibleOn(Contexts.isMobile())) {
            return;
        }
        Template snippet = resolveSnippet(env, component);
        if (snippet == null) {
            // 未知组件（无对应片段）：占位提示，不包 data-panel 壳（与 React PanelDispatcher 一致）
            logger.warn("Region: unknown panel component '{}' — rendering placeholder", component);
            out.write("<div data-panel-unknown=\"" + escapeAttr(component)
                    + "\" class=\"rounded border border-dashed border-cms-border p-4 text-sm text-cms-content-muted\">未知面板类型："
                    + escapeHtml(component) + "</div>");
            return;
        }
        out.write("<div data-panel=\"" + escapeAttr(component)
                + "\" data-panel-id=\"" + escapeAttr(panel.id()) + "\""
                + visibilityAttr(panel) + ">");
        includePanel(env, snippet, panel);
        out.write("</div>");
    }

    /**
     * 面板壳的显示设备标记（{@code all} 不输出——未标记面板的装饰输出与 P2-5 之前逐字节一致）。
     */
    private static String visibilityAttr(ResolvedRegion.Panel panel) {
        if (com.ujcms.cms.core.domain.Placement.VISIBILITY_ALL.equals(panel.visibility())) {
            return "";
        }
        return " data-panel-visibility=\"" + escapeAttr(panel.visibility()) + "\"";
    }

    /**
     * 按 Shape 解析面板片段（{@code snippets/panels/<Shape>.html}，相对当前页面模板）。
     * 片段缺失（未知 Shape）或名非法 → {@code null}（调用方渲染占位）。
     */
    @Nullable
    private Template resolveSnippet(Environment env, String shape) {
        String safe = sanitizeShape(shape);
        if (safe.isEmpty()) {
            return null;
        }
        try {
            String full = env.toFullTemplateName(env.getCurrentTemplate().getName(),
                    SNIPPET_DIR + safe + SNIPPET_SUFFIX);
            // ignoreMissing=true：缺失片段返回 null 而非抛错，支撑“未知 Shape 占位”的优雅降级。
            return env.getTemplateForInclusion(full, null, true, true);
        } catch (IOException e) {
            logger.warn("Region: failed to load panel snippet for shape '{}': {}", shape, e.getMessage());
            return null;
        }
    }

    /**
     * 把面板内容（条目 / 契约设置 / 元信息，component-settings §4.2）暂存到当前命名空间后包含片段；
     * 渲染后还原，避免泄漏到页面其余部分。片段渲染异常 → 占位注释，坏片段不破坏页面（§9.3）。
     */
    private void includePanel(Environment env, Template snippet, ResolvedRegion.Panel panel)
            throws TemplateException, IOException {
        ObjectWrapper wrapper = env.getObjectWrapper();
        TemplateModel prevItems = env.getVariable(VAR_ITEMS);
        TemplateModel prevSettings = env.getVariable(VAR_SETTINGS);
        TemplateModel prevPanel = env.getVariable(VAR_PANEL);
        env.setVariable(VAR_ITEMS, wrapper.wrap(panel.items()));
        env.setVariable(VAR_SETTINGS, wrapper.wrap(panel.settings()));
        env.setVariable(VAR_PANEL, wrapper.wrap(panelMeta(panel)));
        try {
            env.include(snippet);
        } catch (TemplateException e) {
            logger.warn("Region: panel snippet for component '{}' failed: {}", panel.component(), e.getMessage());
            env.getOut().write("<!-- panel snippet failed: " + escapeHtml(panel.component()) + " -->");
        } finally {
            env.setVariable(VAR_ITEMS, prevItems);
            env.setVariable(VAR_SETTINGS, prevSettings);
            env.setVariable(VAR_PANEL, prevPanel);
        }
    }

    private static Map<String, Object> panelMeta(ResolvedRegion.Panel panel) {
        Map<String, Object> meta = new LinkedHashMap<>(8);
        meta.put("id", panel.id());
        meta.put("component", panel.component());
        meta.put("version", panel.version());
        meta.put("enabled", panel.enabled());
        meta.put("visibility", panel.visibility());
        return meta;
    }

    /**
     * 解析行布局键：未知键回退 {@code full}（单列堆叠）并告警，与 React {@code resolveRowLayout} 一致。
     */
    private static String resolveRowLayoutKey(@Nullable String key) {
        if (RowLayoutCatalog.contains(key)) {
            return key;
        }
        if (StringUtils.isNotBlank(key)) {
            logger.warn("Region: unknown row layout '{}' — falling back to 'full'", key);
        }
        return RowLayoutCatalog.FULL;
    }

    /**
     * 行布局 → Tailwind 栅格（与 React {@code rowLayouts.ts} 逐键一致）。
     */
    private static Grid tailwindGrid(String layoutKey) {
        return switch (layoutKey) {
            case RowLayoutCatalog.HALVES -> new Grid("grid-cols-1 md:grid-cols-2", List.of());
            case RowLayoutCatalog.THIRDS -> new Grid("grid-cols-1 md:grid-cols-3", List.of());
            case RowLayoutCatalog.TWO_THIRDS_LEFT ->
                    new Grid("grid-cols-1 md:grid-cols-3", List.of("md:col-span-2", "md:col-span-1"));
            case RowLayoutCatalog.TWO_THIRDS_RIGHT ->
                    new Grid("grid-cols-1 md:grid-cols-3", List.of("md:col-span-1", "md:col-span-2"));
            case RowLayoutCatalog.QUARTERS -> new Grid("grid-cols-1 md:grid-cols-4", List.of());
            case RowLayoutCatalog.SIDEBAR_LEFT ->
                    new Grid("grid-cols-1 md:grid-cols-4", List.of("md:col-span-1", "md:col-span-3"));
            case RowLayoutCatalog.SIDEBAR_RIGHT ->
                    new Grid("grid-cols-1 md:grid-cols-4", List.of("md:col-span-3", "md:col-span-1"));
            default -> new Grid("grid-cols-1", List.of());
        };
    }

    /**
     * Tailwind 行栅格：容器 grid class + 各列 col-span class（等宽布局为空）。
     */
    private record Grid(String grid, List<String> colSpans) {
    }

    /**
     * 仅保留 {@code [A-Za-z0-9_]}（Shape 名本就是已校验的标识符；纵深防御，避免片段路径被构造越界）。
     */
    private static String sanitizeShape(@Nullable String shape) {
        if (shape == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(shape.length());
        for (int i = 0; i < shape.length(); i++) {
            char ch = shape.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    @Nullable
    private static Site findSite(Environment env) {
        try {
            return Frontends.getSite(env);
        } catch (TemplateModelException e) {
            return null;
        }
    }

    @Nullable
    private static String getTemplateName(Environment env) throws TemplateModelException {
        return getModelString(env, Frontends.TEMPLATE_NAME);
    }

    /**
     * 读数据模型中的字符串变量（缺失/非字符串 → {@code null}；模型读取异常按缺失处理）。
     */
    @Nullable
    private static String getModelString(Environment env, String key) {
        try {
            TemplateModel model = env.getDataModel().get(key);
            if (model instanceof TemplateScalarModel scalar) {
                return scalar.getAsString();
            }
        } catch (TemplateModelException ignored) {
            return null;
        }
        return null;
    }

    @Nullable
    private static Channel findChannel(Environment env) {
        try {
            TemplateModel model = env.getDataModel().get(CHANNEL);
            if (model instanceof AdapterTemplateModel adapter
                    && adapter.getAdaptedObject(Channel.class) instanceof Channel channel) {
                return channel;
            }
        } catch (TemplateModelException ignored) {
            return null;
        }
        return null;
    }

    private static String escapeAttr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
