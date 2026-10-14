package com.ujcms.cms.core.support;

/**
 * 首装演示数据 SPI。ext / commercial 模块以 {@code @Component} 实现本接口，
 * 经服务层为首装站点创建少量<b>无种子家族</b>的演示数据（P2 起仅余示例留言——
 * 演示投票/调查已迁入默认主题种子，D13），core 无需反向依赖。
 *
 * <p>锚点自 install-flow P1 起从"首装启动后"改为<b>安装完成时</b>（D13）：由
 * {@link com.ujcms.cms.core.service.InstallService#complete()} 在守卫翻转 INSTALLED 之后
 * 由唯一赢家逐个调用——续装/并发下绝不重复播种，既有库上绝不自愈重跑。单个实现失败仅
 * 记录日志，不阻断安装，也不影响其它实现。
 *
 * @author PONY
 */
public interface FirstInitSeeder {

    /**
     * 为首装站点创建演示数据。
     *
     * @param siteId 首装站点ID（引导约定：站点 1）
     * @param userId 引导管理员用户ID（约定：用户 1）
     */
    void seed(Long siteId, Long userId);
}
