package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.AttachmentReferBase;

import java.io.Serializable;

/**
 * 附件引用实体类
 *
 * @author PONY
 */
public class AttachmentRefer extends AttachmentReferBase implements Serializable {
    public AttachmentRefer() {
    }

    public AttachmentRefer(Integer attachmentId, String referType, Integer referId) {
        setAttachmentId(attachmentId);
        setReferType(referType);
        setReferId(referId);
    }
}