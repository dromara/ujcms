package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.generated.GeneratedConfig;
import com.ujcms.cms.core.mapper.ConfigMapper;
import com.ujcms.cms.core.support.Props;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全局配置 Service
 *
 * @author PONY
 */
@Service
public class ConfigService {
    private final AttachmentService attachmentService;
    private final ConfigMapper mapper;
    private final Props props;

    public ConfigService(AttachmentService attachmentService, ConfigMapper mapper, Props props) {
        this.attachmentService = attachmentService;
        this.mapper = mapper;
        this.props = props;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Config bean) {
        mapper.update(bean);
        attachmentService.updateRefer(GeneratedConfig.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePort(@Nullable Integer port) {
        mapper.updatePort(port);
    }

    /**
     * 更新应用市场连接设置（remote-marketplace C2 / D-C1-1：单列写入，SM4 加密落库；
     * refresh token 单次有效，新令牌对须先落库再作废旧令牌）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateMarketplaceSettings(@Nullable String marketplaceSettings) {
        mapper.updateMarketplaceSettings(marketplaceSettings);
    }

    public Config getUnique() {
        Config config = mapper.findUnique();
        if (config == null) {
            throw new IllegalStateException("Config data not exist!");
        }
        config.setFilesExtensionBlacklist(props.getFilesExtensionBlacklist());
        config.setUploadsExtensionBlacklist(props.getUploadsExtensionBlacklist());
        return config;
    }
}