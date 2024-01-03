package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.base.ConfigBase;
import com.ujcms.cms.core.mapper.ConfigMapper;
import com.ujcms.cms.core.support.Props;
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
        attachmentService.updateRefer(ConfigBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
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