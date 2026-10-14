package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.RegistryReload;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动扫描（§9.5 检测路径 A）。扫描已安装主题目录并建立首个注册表快照。
 * 这是小体量配置装载（典型快照 &lt;1MB），不属于昂贵状态的自愈重建；
 * 主题 Shape / 页面清单损坏时启动失败并指明文件与错误（§9.3）。
 *
 * <p>显式最高优先级：首装种子导入（{@code InstallAutoRunner}，末位 Runner）校验主题
 * Shape / 布局变体，依赖此处建立的首个快照。
 *
 * @author PONY
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class CompositionBootScan implements ApplicationRunner {
    private final ManifestService manifestService;

    public CompositionBootScan(ManifestService manifestService) {
        this.manifestService = manifestService;
    }

    @Override
    public void run(ApplicationArguments args) {
        manifestService.scanAll(RegistryReload.SOURCE_BOOT, null);
    }
}
