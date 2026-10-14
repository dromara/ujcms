package com.ujcms.cms.core.domain;

import org.jspecify.annotations.Nullable;

/**
 * 文章的一处落位：哪一行、它的源文章、在哪个栏目（docs/spec/article-data-permission/plan.md D21）。栏目合并前
 * 用它核对落位家族——源文章及 {@code src_id_} 指向它的子行——不会在目标栏目里出现两次
 *
 * @param id        文章ID
 * @param srcId     源文章ID。源文章自身为 {@code null}
 * @param channelId 所在栏目ID
 * @author PONY
 */
public record ArticlePlacement(Long id, @Nullable Long srcId, Long channelId) {
    /**
     * 落位家族的标识：源文章的ID
     */
    public Long familyId() {
        return srcId != null ? srcId : id;
    }
}
