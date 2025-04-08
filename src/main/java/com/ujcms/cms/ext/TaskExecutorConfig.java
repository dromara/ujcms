package com.ujcms.cms.ext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

/**
 * 任务执行器配置
 *
 * @author PONY
 */
@Configuration
public class TaskExecutorConfig {
    /**
     * 生成器任务执行器。为生成功能定义专门的任务执行器，和公用的任务执行器分开，以免相互影响。
     */
    @Lazy
    @Qualifier("collector")
    @Bean
    public ThreadPoolTaskExecutor collectorTaskExecutor(TaskExecutionProperties properties) {
        TaskExecutorBuilder builder = new TaskExecutorBuilder();
        builder = builder.corePoolSize(0);
        builder = builder.maxPoolSize(10);
        builder = builder.queueCapacity(0);
        builder = builder.allowCoreThreadTimeOut(true);
        builder = builder.keepAlive(Duration.ofSeconds(60));
        TaskExecutionProperties.Shutdown shutdown = properties.getShutdown();
        builder = builder.awaitTermination(shutdown.isAwaitTermination());
        builder = builder.awaitTerminationPeriod(shutdown.getAwaitTerminationPeriod());
        builder = builder.threadNamePrefix("task-collector-");
        return builder.build();
    }
}
