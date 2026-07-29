package com.ujcms.cms.core.scheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * 定时任务处理器注册表。收集 Spring 容器中所有 {@link ScheduleTaskHandler} 实现。
 * 商业版模块在类路径上时，其处理器 Bean 会被自动收集，无需额外配置
 *
 * @author PONY
 */
@Component
public class ScheduleTaskHandlerRegistry {
    private final Map<String, ScheduleTaskHandler> handlers = new LinkedHashMap<>();

    public ScheduleTaskHandlerRegistry(List<ScheduleTaskHandler> handlerList) {
        for (ScheduleTaskHandler handler : handlerList) {
            handlers.put(handler.getType(), handler);
        }
    }

    public Optional<ScheduleTaskHandler> getHandler(String type) {
        return Optional.ofNullable(handlers.get(type));
    }

    /**
     * 获取所有已注册的任务类型。前端根据此列表显示可用的任务类型
     */
    public List<String> getTypes() {
        return new ArrayList<>(handlers.keySet());
    }
}
