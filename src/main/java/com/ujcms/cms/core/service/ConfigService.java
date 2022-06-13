package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.mapper.ConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全局配置 Service
 *
 * @author PONY
 */
@Service
public class ConfigService {
    private AttachmentService attachmentService;
    private ConfigMapper mapper;

    public ConfigService(AttachmentService attachmentService, ConfigMapper mapper) {
        this.attachmentService = attachmentService;
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Config bean) {
        mapper.update(bean);
        attachmentService.updateRefer(Config.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    public Config getUnique() {
        Config config = mapper.findUnique();
        if (config == null) {
            throw new IllegalStateException("Config data not exist!");
        }
        return config;
    }
}