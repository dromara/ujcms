package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.User;

/**
 * 文章审批流程端口：文章提交/删除时启动或撤销 Flowable 流程实例。
 * <p>
 * 审批工作流是商业版 T1 能力（{@code articleReview}），Flowable 依赖随之只在 ujcms-commercial 声明；
 * 实现类 {@code ArticleProcessPortT1Impl} 在 t1 包。社区版类路径无实现：栏目即使配置了
 * {@code processKey} 也视同无流程，文章直接发布（与 {@code InstallSitePort}/{@code SandboxSiteRegistry}
 * 同型的跨模块 SPI，由 {@link ArticleService} 经 ObjectProvider 取用）。
 *
 * @author PONY
 */
public interface ArticleProcessPort {
    /**
     * 启动审批流程：实现须把文章置为待审核并记录流程实例 ID（调用方随后持久化）。
     */
    void start(Article article, String processKey, Long userId);

    /**
     * 撤销文章名下所有还在进行的流程实例。按业务键（文章ID）与租户（站点ID）查找，不依赖文章上记着的流程实例ID：
     * 文章改换到没有流程的栏目、栏目的流程被清空之后，文章上的实例ID可能已经清掉或对不上，那个实例却还在跑——
     * 留着它，旧流程的审核人日后“通过”“退回”就会把这篇文章的状态又改掉
     * （docs/spec/article-management/plan.md Phase 1.5）。没有进行中的实例时什么都不做
     *
     * @param reason 撤销原因，记入流程历史
     */
    void cancelAll(Long articleId, Long siteId, String reason);

    /**
     * 文章上记着的流程实例是否还在进行，且属于该流程键。提交“待审核”的文章时据此判断：是，则它已经在这个栏目的
     * 流程里等待审核，无需处理；否（实例已被删除，或属于栏目以前的流程），则重新进入流程
     */
    boolean isRunning(Article article, String processKey);

    /**
     * 操作员是否持有该文章正在进行的审核任务，即审核列表会列给他的那一个（本站租户、受理人或候选人、
     * 文章权限与数据范围之内）。审核对话框沿用文章表单保存：据此放行审核人对审核中文章的修改
     * （docs/spec/article-data-permission/plan.md D5、D6）。
     */
    boolean isReviewer(Article article, User user);
}
