package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.PageManifest;
import com.ujcms.cms.core.composition.SandboxSiteRegistry;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.common.web.exception.Http401Exception;
import org.jspecify.annotations.Nullable;
import ua_parser.Client;
import ua_parser.Parser;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;

/**
 * 前台拦截器
 *
 * @author PONY
 */
public class FrontendInterceptor implements HandlerInterceptor {
    /**
     * 结构化主题布局的页面模板目录段（{@link Site#assembleTemplate}），用于从视图名反推裸模板名。
     */
    private static final String TEMPLATES_SEGMENT = "/templates/";

    private final UserService userService;
    private final SiteService siteService;
    private final ConfigService configService;
    private final ThemeSettingsService themeSettingsService;
    private final ManifestService manifestService;
    private final SandboxSiteRegistry sandboxSiteRegistry;
    private final Parser uaParser;

    public FrontendInterceptor(UserService userService, SiteService siteService, ConfigService configService,
                               ThemeSettingsService themeSettingsService, ManifestService manifestService,
                               SandboxSiteRegistry sandboxSiteRegistry, Parser uaParser) {
        this.userService = userService;
        this.siteService = siteService;
        this.configService = configService;
        this.themeSettingsService = themeSettingsService;
        this.manifestService = manifestService;
        this.sandboxSiteRegistry = sandboxSiteRegistry;
        this.uaParser = uaParser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 当前用户
        Optional.ofNullable(Contexts.findCurrentUsername()).map(userService::selectByUsername)
                .ifPresent(Contexts::setCurrentUser);
        // 访问设备
        String userAgent = request.getHeader("User-Agent");
        Client client = uaParser.parse(userAgent);
        boolean isMobile = "Mobile".equals(client.device.family) || 
                          "Android".equals(client.os.family) || 
                          "iOS".equals(client.os.family);
        Contexts.setMobile(isMobile);
        // 插槽预览（§11.2）：?_preview=draft 且已认证管理员时置位（草稿可见 + 编辑深链锚点）。
        // 与 SlotController 的 preview=draft 一致——仅登录用户可见草稿（§19.4）。
        boolean slotPreview = "draft".equals(request.getParameter("_preview")) && Contexts.findCurrentUser() != null;
        Contexts.setSlotPreview(slotPreview);
        // 主题预览覆盖（design-review P2-1 / H1）：?_theme=<候选主题> 且已认证（预览令牌经
        // PreviewAuthFilter 置身份，或真实登录态）时置位——身份门槛与草稿预览同一口径。仅安全方法
        // （与 PreviewAuthFilter §21 Q12(g) 一致）：预览是只读渲染，写请求不承载覆盖。无身份时 401
        // 而非静默回退——静默渲染活动主题会让操作员误以为看到了候选主题（令牌过期同理，失败要响亮）。
        // 候选主题的存在性/可激活性在 SiteResolver 确定站点后校验。
        String themePreview = request.getParameter(Contexts.THEME_PREVIEW_PARAM);
        if (themePreview != null && !themePreview.isBlank() && isSafeMethod(request)) {
            if (Contexts.findCurrentUser() == null) {
                // preHandle 中途抛出时 Spring 不会回调本拦截器的 afterCompletion，先清掉已置的线程变量
                Contexts.clearCurrentUser();
                Contexts.clearMobile();
                Contexts.clearSlotPreview();
                throw new Http401Exception();
            }
            Contexts.setThemePreview(themePreview);
        }
        return true;
    }

    private static boolean isSafeMethod(HttpServletRequest request) {
        String method = request.getMethod();
        return "GET".equals(method) || "HEAD".equals(method);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           @Nullable ModelAndView modelAndView) {
        Site site = Contexts.findCurrentSite();
        Config config = configService.getUnique();
        if (site == null) {
            // 未找到站点，一般是因为抛出了异常，设置 templateUrl 让错误页面可以正确访问css、js等资源
            request.setAttribute(Frontends.TEMPLATE_URL, config.getTemplateStorage().getUrl());
            return;
        }
        // 沙箱站点盖 noindex（sandbox-preview-site §5 / D8）：沙箱是启用的、知道地址即可访问的
        // 演示站——把爬虫挡在演示内容之外，并避免生产域名上的重复内容 SEO 损伤。沙箱强制动态渲染
        // （html 关闭，D8），前台控制器响应即全部渲染面；postHandle 在视图渲染前，响应头尚可写。
        if (sandboxSiteRegistry.isSandboxSite(site.getId())) {
            response.setHeader("X-Robots-Tag", "noindex, nofollow");
        }
        Site defaultSite = siteService.getDefaultSite(config.getDefaultSiteId());
        Frontends.setData(request, defaultSite);
        Frontends.setUser(request, Contexts.findCurrentUser());
        // 主题设置模型（§8.2）：theme.config.<group>.<name>。移动端用 mobileTheme（与 setData 的资源路径一致）。
        String themeName = Contexts.isMobile() ? site.getMobileTheme() : site.getTheme();
        request.setAttribute(Frontends.THEME, themeSettingsService.themeModel(site.getId(), themeName));
        setPageConfig(request, modelAndView, site, themeName);
    }

    /**
     * 页面作用域设置模型（manifest 2.0 §4.2）：{@code pageConfig.<group>.<field>}。
     * 按渲染上下文解析：控制器写入的未组装模板名 → 活动主题的页面清单
     * （页面名），叠加控制器写入的活动变体键（{@code surface:value}）的变体作用域设置（变体胜出）。
     * 无匹配页面清单且无活动变体 → 空 Map（模板取值宽松为空）。
     */
    private void setPageConfig(HttpServletRequest request, @Nullable ModelAndView modelAndView,
                               Site site, String themeName) {
        if (modelAndView == null || (modelAndView.getViewName() != null
                && modelAndView.getViewName().startsWith("redirect:"))) {
            return;
        }
        Map<String, Object> model = modelAndView.getModel();
        // 模板名兜底（component-settings §13）：仅 首页/栏目/文章/专题 控制器写 TEMPLATE_NAME
        // （轨道变体需要）；Search/Tag/Vote/Survey/FormType/Member 等系统页控制器不写——从已
        // 组装视图名（/{themeKey}/templates/<name>）反推裸模板名，让 system 页面清单
        // （pages/system.manifest.json）在区域指令与页面设置解析中生效。
        String viewName = modelAndView.getViewName();
        if (!(model.get(Frontends.TEMPLATE_NAME) instanceof String) && viewName != null) {
            int idx = viewName.lastIndexOf(TEMPLATES_SEGMENT);
            if (idx >= 0) {
                modelAndView.addObject(Frontends.TEMPLATE_NAME,
                        viewName.substring(idx + TEMPLATES_SEGMENT.length()));
            }
        }
        String pageName = null;
        if (model.get(Frontends.TEMPLATE_NAME) instanceof String templateName) {
            String themeKey = Contexts.isMobile() ? site.getMobileThemeKey() : site.getThemeKey();
            PageManifest manifest = manifestService.getSnapshot()
                    .resolveThemeManifestByTemplate(themeKey, templateName);
            if (manifest != null) {
                pageName = manifest.getName();
            }
        }
        String variantKey = model.get(Frontends.LAYOUT_VARIANT) instanceof String key ? key : null;
        Map<String, Map<String, Object>> pageConfig =
                themeSettingsService.pageConfig(site.getId(), themeName, pageName, variantKey);
        request.setAttribute(Frontends.PAGE_CONFIG, pageConfig);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        Contexts.clearCurrentUser();
        Contexts.clearCurrentSite();
        Contexts.clearMobile();
        Contexts.clearSlotPreview();
        Contexts.clearThemePreview();
    }
}
