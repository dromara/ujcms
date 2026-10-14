package com.ujcms.cms.ext.component;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ujcms.cms.ext.listener.UgcSubmitListener;
import com.ujcms.cms.ext.listener.UgcSubmitListener.UgcSubmission;

/**
 * UGC提交分发器。在提交事务提交后，将 {@link UgcSubmission} 异步分发给全部
 * {@link UgcSubmitListener}（docs/spec/ai/README.md §4）：提交延迟不等待监听器，
 * 监听器读到的内容行已提交可见。免费版无监听器，分发为空操作。
 *
 * @author PONY
 */
@Component
public class UgcSubmitDispatcher {
    private final ThreadPoolTaskExecutor executor;

    public UgcSubmitDispatcher(ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    /**
     * 分发提交事件。须在提交事务内调用；事务提交后异步执行，回滚则不分发
     *
     * @param submission 提交内容
     */
    public void dispatch(UgcSubmission submission) {
        if (listeners.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fire(submission);
                }
            });
        } else {
            fire(submission);
        }
    }

    private void fire(UgcSubmission submission) {
        executor.execute(() -> listeners.forEach(listener -> listener.onUgcSubmitted(submission)));
    }

    private List<UgcSubmitListener> listeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setListeners(List<UgcSubmitListener> listeners) {
        this.listeners = listeners;
    }
}
