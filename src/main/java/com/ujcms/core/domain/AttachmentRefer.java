package com.ujcms.core.domain;

import com.ujcms.core.domain.base.AttachmentReferBase;

import java.io.Serializable;

/**
 * 附件引用实体类
 *
 * @author PONY
 */
public class AttachmentRefer extends AttachmentReferBase implements Serializable {
    public AttachmentRefer() {
    }

    public AttachmentRefer(int attachmentId, String referType, int referId) {
        setAttachmentId(attachmentId);
        setReferType(referType);
        setReferId(referId);
    }
}