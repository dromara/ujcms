package com.ujcms.cms.core.service;

/**
 * 审批流程定义端口：某个流程键是否已为站点部署（docs/spec/article-data-permission/plan.md D23）。
 * <p>
 * 流程按租户（站点）部署，以 {@code startProcessInstanceByKeyAndTenantId} 启动，审核列表再按类别
 * （{@code sys_article} / {@code sys_form}）取任务：栏目、表单类型保存的流程键须指向本站已部署的、同类别的流程，
 * 否则发布时启动失败，或启动的流程没有任何审核列表会列出。实现类 {@code ProcessDefinitionPortT1Impl} 在 t1 包，
 * 社区版类路径无实现：没有流程可以指向，非空的流程键一律拒绝（与 {@link ArticleProcessPort} 同型的跨模块 SPI）。
 *
 * @author PONY
 */
public interface ProcessDefinitionPort {
    /**
     * 站点是否部署了该类别、该键的流程（最新版本）
     *
     * @param siteId     站点ID（租户）
     * @param category   流程类别，如 {@code Article.PROCESS_CATEGORY}
     * @param processKey 流程键
     */
    boolean isDeployed(Long siteId, String category, String processKey);
}
