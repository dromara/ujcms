package com.ujcms.cms.core.service;

/**
 * 安装引擎的演示层端口（install-flow §8 / D9）：清单 {@code demo:} 段驱动<b>真实</b>
 * Flowable 引擎——部署预制两级审核流程定义、绑定 {@code processKey}、以真实服务/真实
 * 用户制造在途审核状态。工作流永不进入种子数据（theme-seed-export §7.2 永久排除项）。
 * 实现类在 ujcms-commercial T1；社区版类路径无实现，引擎据此<b>跳过</b>并落报告行
 * （与 {@link InstallSitePort} 同型的跨模块 SPI，但降级语义相反：演示层缺席不阻断安装）。
 *
 * @author PONY
 */
public interface InstallDemoPort {

    /**
     * 部署演示工作流并绑定（幂等）：预制两级审核流程定义（sys_article 文章审核 /
     * sys_form 表单审核）按 key+租户部署到站点租户（镜像控制台部署路径，含流程模型行），
     * 随后把 {@code processKey} 写到演示栏目「新闻 › 国内」（slug china）与站点表单类型上（经常规服务）。
     *
     * @param siteId 目标站点（清单演示层恒为站点 1）
     * @return 报告摘要行
     */
    String provisionWorkflow(Long siteId);

    /**
     * 制造审核状态演示数据（幂等，按标题续装收敛）：演示作者（演示用户 10 存在即用之，
     * 否则管理员 1）在绑定栏目提交待审核文章（真实 Flowable 任务），一篇由管理员退回
     * （含退回原因），一篇通过初审停在两级流程中段（审核中）；默认主题种子在该栏目下的两篇
     * 真实草稿也送入流程，一篇待审核、一篇已退回（按来源键幂等，D51）。要求
     * {@link #provisionWorkflow} 已执行（定义未部署/未绑定时 fail-loud）。
     *
     * @param siteId 目标站点（清单演示层恒为站点 1）
     * @return 报告摘要行
     */
    String provisionReviewStates(Long siteId);
}
