package com.ujcms.core;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

/**
 * 任务执行器 配置
 *
 * @author PONY
 */
@Configuration
public class TaskExecutorConfig {
    /**
     * 应用任务执行器
     *
     * @see TaskExecutionAutoConfiguration#applicationTaskExecutor(TaskExecutorBuilder)
     */
    @Lazy
    @Primary
    @Bean(name = {TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
            AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME})
    public ThreadPoolTaskExecutor applicationTaskExecutor(TaskExecutorBuilder builder) {
        return builder.build();
    }

    /**
     * 生成器任务执行器
     */
    @Lazy
    @Qualifier("generator")
    @Bean
    public ThreadPoolTaskExecutor generatorTaskExecutor(TaskExecutionProperties properties) {
        TaskExecutorBuilder builder = new TaskExecutorBuilder();
        builder = builder.corePoolSize(1);
        builder = builder.maxPoolSize(2);
        builder = builder.queueCapacity(2048);
        builder = builder.allowCoreThreadTimeOut(true);
        builder = builder.keepAlive(Duration.ofSeconds(60));
        TaskExecutionProperties.Shutdown shutdown = properties.getShutdown();
        builder = builder.awaitTermination(shutdown.isAwaitTermination());
        builder = builder.awaitTerminationPeriod(shutdown.getAwaitTerminationPeriod());
        builder = builder.threadNamePrefix("task-generator-");
        return builder.build();
    }
}
