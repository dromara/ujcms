package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
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

    /** 引用对象的显示名称（文章标题、栏目名等）。瞬时字段，仅附件详情接口填充；无法解析时为 null。 */
    @Nullable
    private String referTitle;

    @Nullable
    public String getReferTitle() {
        return referTitle;
    }

    public void setReferTitle(@Nullable String referTitle) {
        this.referTitle = referTitle;
    }
}