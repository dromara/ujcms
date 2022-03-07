package com.ujcms.core.generator;

import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.Task;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.service.TaskService;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 生成器抽象类
 *
 * @author PONY
 */
public abstract class AbstractGenerator {
    ArticleService articleService;
    TaskService taskService;
    private ThreadPoolTaskExecutor executor;

    protected AbstractGenerator(ArticleService articleService, TaskService taskService,
                                ThreadPoolTaskExecutor executor) {
        this.articleService = articleService;
        this.taskService = taskService;
        this.executor = executor;
    }

    void execute(Integer taskSiteId, Integer taskUserId, String taskName, short taskType,
                 boolean deleteOnFinished, Consumer<Integer> consumer) {
        Task task = new Task(taskSiteId, taskUserId, taskName, taskType);
        // 等待状态，在队列里等待线程池运行
        taskService.insert(task);
        Integer taskId = task.getId();
        executor.execute(() -> {
            try {
                Task bean = taskService.select(taskId);
                // 任务有可能被删除或停止
                if (bean == null || bean.getStatus() != Task.STATUS_WAITING) {
                    return;
                }
                bean.setStatus(Task.STATUS_RUNNING);
                taskService.update(bean);
                consumer.accept(taskId);

                bean = taskService.select(taskId);
                if (bean == null) {
                    return;
                }
                if (deleteOnFinished && bean.getStatus() == Task.STATUS_RUNNING) {
                    // 结束删除任务
                    taskService.delete(taskId);
                } else {
                    // 正常完成任务
                    bean.setEndDate(OffsetDateTime.now());
                    bean.setStatus(Task.STATUS_DONE);
                    taskService.update(bean);
                }
            } catch (Exception e) {
                // 记录错误信息
                Task bean = taskService.select(taskId);
                if (bean == null) {
                    return;
                }
                bean.setError(e);
                taskService.update(bean);
            }
        });
    }

    void handleArticle(Integer taskId, Integer siteId, Consumer<Article> consumer) {
        Integer minId = 0;
        int offset = 0, limit = 100, size = limit;
        while (size >= limit) {
            List<Article> list = articleService.selectByMinId(minId, siteId, null, offset, limit);
            size = list.size();
            for (int i = 0; i < size; i++) {
                Article article = list.get(i);
                consumer.accept(article);
                if (i == size - 1) {
                    minId = article.getId();
                }
            }
            if (updateTask(taskId, size)) {
                break;
            }
        }
    }

    /**
     * 更新任务
     *
     * @param taskId 任务ID
     * @param size   完成数量
     * @return 是否中止任务
     */
    boolean updateTask(Integer taskId, int size) {
        Task task = taskService.select(taskId);
        // 任务被删，中止任务
        if (task == null) {
            return true;
        }
        task.setCurrent(task.getCurrent() + size);
        taskService.update(task);
        return task.getStatus() == Task.STATUS_STOP;
    }
}
