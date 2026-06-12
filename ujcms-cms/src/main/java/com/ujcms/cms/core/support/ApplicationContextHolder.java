package com.ujcms.cms.core.support;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * ApplicationContext 持有者
 *
 * @author PONY
 */
@Component
public class ApplicationContextHolder implements ApplicationContextAware {
    @Nullable
    private static ApplicationContext applicationContext;

    @Override
    @SuppressWarnings("java:S2696")
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        ApplicationContextHolder.applicationContext = applicationContext;
    }

    @Nullable
    public static ApplicationContext getApplicationContext() {
        return ApplicationContextHolder.applicationContext;
    }
}
