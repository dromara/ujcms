package com.ofwise.util.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * 计时器。用于记录请求执行时间。
 *
 * @author liufang
 */
public class TimerInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(TimerInterceptor.class);
    public static final String TIMER_START = "_timer_start";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        long begin = System.currentTimeMillis();
        request.setAttribute(TIMER_START, begin);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        if (logger.isDebugEnabled()) {
            Long begin = (Long) request.getAttribute(TIMER_START);
            if (begin != null) {
                long end = System.currentTimeMillis();
                BigDecimal processed = new BigDecimal(end - begin).divide(new BigDecimal(1000), 3, RoundingMode.HALF_UP);
                String uri = ((HttpServletRequest) request).getRequestURI();
                logger.debug("Processed in {} second(s). URI={}", new DecimalFormat("0.000").format(processed), uri);
            }
        }
    }

}
