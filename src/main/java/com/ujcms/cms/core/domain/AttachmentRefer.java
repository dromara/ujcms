package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedAttachmentRefer;

/**
 * 附件引用实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class AttachmentRefer extends GeneratedAttachmentRefer {

    public AttachmentRefer() {
    }

    public AttachmentRefer(Long id, Long attachmentId, String referType, Long referId) {
        setId(id);
        setAttachmentId(attachmentId);
        setReferType(referType);
        setReferId(referId);
    }
}