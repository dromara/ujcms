package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Site;

/**
 * 安装引擎的建站端口（install-flow §7 / D11）：清单 {@code sites} 首条之外的站点经 T2
 * 建站复制路径（{@code copyFromId} + 有序 {@code copyData}）创建——多站点是 T2 能力，
 * 实现类在 ujcms-commercial；社区版类路径无实现，引擎据此 fail-loud，绝不静默降级为单站
 * （与 {@code ArticleProcessPort} 同型的跨模块 SPI）。
 *
 * @author PONY
 */
public interface InstallSitePort {
    /**
     * 创建清单站点。调用方（{@link InstallService}）负责幂等判定（同名站点已在即跳过）与
     * 站点 bean 组装；实现负责地址/组织校验与 T2 复制建站。
     *
     * @param site 待建站点（已设 name/orgId/copyFromId/copyData，语言变体另设
     *             lang/langParentId/subDir，D44）
     * @return 新站点 ID
     */
    Long create(Site site);
}
