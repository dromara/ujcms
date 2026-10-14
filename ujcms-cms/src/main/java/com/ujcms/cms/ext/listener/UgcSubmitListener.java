package com.ujcms.cms.ext.listener;

import java.util.List;

import org.springframework.lang.Nullable;

import com.ujcms.cms.core.component.WordScreeningService.WordHit;

/**
 * UGC提交监听器（docs/spec/ai/README.md §5）。评论、留言两个提交路径在事务提交后
 * 异步回调（经 {@code UgcSubmitDispatcher}），供商业版审核流水线挂接；免费版无实现，列表为空。
 *
 * @author PONY
 */
public interface UgcSubmitListener {
    /**
     * UGC提交后回调（事务提交后异步执行）
     *
     * @param submission 提交内容
     */
    void onUgcSubmitted(UgcSubmission submission);

    /**
     * UGC提交内容
     *
     * @param type       内容类型：{@link #TYPE_COMMENT}/{@link #TYPE_MESSAGE_BOARD}
     * @param refId      内容ID
     * @param siteId     站点ID
     * @param userId     提交用户ID。匿名提交为 {@code User.ANONYMOUS_ID}
     * @param text       提交的纯文本内容（词库筛查所用的同一文本）
     * @param dictHits   词库命中（仅敏感词；易错词与UGC无关，不参与）
     * @param heldStatus 内容落库时的状态。命中敏感词被扣住时为待审核状态
     */
    record UgcSubmission(String type, Long refId, Long siteId, @Nullable Long userId, String text,
                         List<WordHit> dictHits, Short heldStatus) {
        public static final String TYPE_COMMENT = "comment";
        public static final String TYPE_MESSAGE_BOARD = "messageBoard";
    }
}
