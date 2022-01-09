package com.ujcms.core.service;

import com.ujcms.core.domain.Global;
import com.ujcms.core.mapper.GlobalMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全局 Service
 *
 * @author PONY
 */
@Service
public class GlobalService {
    private AttachmentService attachmentService;
    private GlobalMapper mapper;

    public GlobalService(AttachmentService attachmentService, GlobalMapper mapper) {
        this.attachmentService = attachmentService;
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Global bean) {
        mapper.update(bean);
        attachmentService.updateRefer(Global.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    public Global getUnique() {
        Global global = mapper.findUnique();
        if (global == null) {
            throw new IllegalStateException("Global data not exist!");
        }
        return global;
    }
}