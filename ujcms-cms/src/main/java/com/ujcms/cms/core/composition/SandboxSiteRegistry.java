package com.ujcms.cms.core.composition;

/**
 * 沙箱站点成员查询 seam（sandbox-preview-site §5 / S3）：回答「该站点是否为注册的主题沙箱」，
 * 供核心前台渲染路径（{@code FrontendInterceptor}）为沙箱站点盖 {@code X-Robots-Tag: noindex,
 * nofollow}——把爬虫挡在演示内容之外，并避免生产域名上的重复内容 SEO 损伤。
 *
 * <p>需要 seam 的原因（S1 完成记录偏差③）：注册表的唯一写入方 {@code ThemeSandboxT2Service}
 * 在 ujcms-commercial（T2），核心拦截器够不到——与 {@code InstallSitePort} 同型的跨模块 SPI：
 * 商业 T2 装配注册实现（{@code SandboxSiteRegistryT2Impl}，缓存 sandbox-site-id 集合），
 * 核心经 {@code ObjectProvider} 消费，社区/基础版无实现即回落 {@link #NONE}（永不为沙箱，
 * 与「沙箱是 T2 增量」一致——社区版注册表恒空）。
 *
 * <p>实现的缓存为<b>进程内</b>状态，失效由创建/清理/站点删除监听路径触发——与
 * {@link ThemeLifecycleLock} 同一份单写者运维约束
 * （docs/decisions/theme-marketplace/05-theme-lifecycle-lock-single-writer.md）：
 * 集群部署下他机写入不会失效本机缓存，不在此解决。
 *
 * @author PONY
 */
public interface SandboxSiteRegistry {

    /**
     * 无实现时的缺省（社区/基础版，或商业装配缺席）：任何站点都不是沙箱，失效为空操作。
     */
    SandboxSiteRegistry NONE = siteId -> false;

    /**
     * 站点是否为注册的沙箱站点（{@code ujcms_theme_sandbox.site_id_} 成员）。
     *
     * @param siteId 站点ID
     * @return 是注册沙箱则为 {@code true}
     */
    boolean isSandboxSite(Long siteId);

    /**
     * 使缓存失效（注册行增删后调用；下次查询重新加载）。缺省空操作。
     */
    default void invalidate() {
    }
}
