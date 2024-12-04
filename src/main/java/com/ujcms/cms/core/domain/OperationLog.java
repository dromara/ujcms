package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.base.OperationLogBase;
import com.ujcms.commons.web.Views;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.io.Serializable;

/**
 * 操作日志实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class OperationLog extends OperationLogBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description="请求URL")
    @JsonView(Views.Whole.class)
    @Nullable
    public String getRequestUrl() {
        return getExt().getRequestUrl();
    }

    public void setRequestUrl(@Nullable String requestUrl) {
        getExt().setRequestUrl(requestUrl);
    }

    @Schema(description="请求体")
    @JsonView(Views.Whole.class)
    @Nullable
    public String getRequestBody() {
        return getExt().getRequestBody();
    }

    public void setRequestBody(@Nullable String requestBody) {
        getExt().setRequestBody(requestBody);
    }

    @Schema(description="响应体")
    @JsonView(Views.Whole.class)
    @Nullable
    public String getResponseEntity() {
        return getExt().getResponseEntity();
    }

    public void setResponseEntity(@Nullable String responseEntity) {
        getExt().setResponseEntity(responseEntity);
    }

    @Schema(description="异常堆栈")
    @JsonView(Views.Whole.class)
    @Nullable
    public String getExceptionStack() {
        return getExt().getExceptionStack();
    }

    public void setExceptionStack(@Nullable String exceptionStack) {
        getExt().setExceptionStack(exceptionStack);
    }

    /**
     * 操作日志扩展对象
     */
    @JsonIgnore
    private OperationLogExt ext = new OperationLogExt();
    /**
     * 用户
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    private User user = new User();
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name"})
    private Site site = new Site();

    public OperationLogExt getExt() {
        return ext;
    }

    public void setExt(OperationLogExt ext) {
        this.ext = ext;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    /**
     * 状态：成功
     */
    public static final short STATUS_SUCCESS = 1;
    /**
     * 状态：失败
     */
    public static final short STATUS_FAILURE = 0;
}