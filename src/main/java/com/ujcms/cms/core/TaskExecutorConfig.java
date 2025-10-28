package com.ujcms.cms.core;

import java.time.Duration;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 任务执行器配置
 *
 * @author PONY
 */
@Configuration
@EnableAsync
public class TaskExecutorConfig {
    /**
     * 默认任务执行器。
     * <p>
     * 用于代替 `@EnableAsync` 的
     * {@link TaskExecutorConfigurations#TaskExecutorConfiguration#applicationTaskExecutor(ThreadPoolTaskExecutorBuilder)} (ThreadPoolTaskExecutorBuilder)}
     * 因为增加了自定义的 `ThreadPoolTaskExecutor`，这个默认的 `ThreadPoolTaskExecutor` 就不会再创建了，需要自行创建。
     *
     * @see TaskExecutionAutoConfiguration
     */
    @Lazy
    @Primary
    @Bean(name = {TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
            AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME})
    public ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    /**
     * 生成器任务执行器。为生成功能定义专门的任务执行器，和公用的任务执行器分开，以免相互影响。
     */
    @Lazy
    @Bean(name = "generatorTaskExecutor")
    public ThreadPoolTaskExecutor generatorTaskExecutor(TaskExecutionProperties properties) {
        ThreadPoolTaskExecutorBuilder builder = new ThreadPoolTaskExecutorBuilder();
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
