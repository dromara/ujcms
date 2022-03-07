package com.ofwise.util.web;

import org.apache.shiro.web.util.WebUtils;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static com.ofwise.util.web.Servlets.getMessage;
import static javax.servlet.http.HttpServletResponse.*;

/**
 * HTTP响应类
 *
 * @author PONY
 * @see ResponseEntityExceptionHandler
 * @see DefaultErrorAttributes
 */
public final class Responses {
    /**
     * 操作成功
     */
    public static ResponseEntity<Body> ok() {
        return ResponseEntity.ok().body(new Body(Body.SUCCESS));
    }

    /**
     * 操作成功
     */
    public static ResponseEntity<Body> ok(Map<String, Object> result) {
        return ResponseEntity.ok().body(new Body(Body.SUCCESS, result));
    }

    /**
     * 操作成功
     *
     * @param message 提供信息用于前台显示，如“用户新增成功”。
     */
    public static ResponseEntity<Body> ok(String message) {
        return ResponseEntity.ok().body(new Body(Body.SUCCESS, message));
    }

    /**
     * 操作成功
     */
    public static ResponseEntity<Body> ok(HttpServletRequest request, String code) {
        return ResponseEntity.ok().body(new Body(Body.SUCCESS, getMessage(request, code)));
    }

    /**
     * 操作成功
     */
    public static ResponseEntity<Body> ok(HttpServletRequest request, String code, @Nullable Object... args) {
        return ResponseEntity.ok().body(new Body(Body.SUCCESS, getMessage(request, code, args)));
    }

    /**
     * 操作失败
     */
    public static ResponseEntity<Body> failure() {
        return ResponseEntity.ok().body(new Body(Body.FAILURE));
    }

    /**
     * 操作失败
     */
    public static ResponseEntity<Body> failure(String message) {
        return ResponseEntity.ok().body(new Body(Body.FAILURE, message));
    }

    /**
     * 操作失败
     */
    public static ResponseEntity<Body> failure(HttpServletRequest request, String code) {
        return ResponseEntity.ok().body(new Body(Body.FAILURE, getMessage(request, code)));
    }

    /**
     * 操作失败
     */
    public static ResponseEntity<Body> failure(HttpServletRequest request, String code, @Nullable Object... args) {
        return ResponseEntity.ok().body(new Body(Body.FAILURE, getMessage(request, code, args)));
    }

    /**
     * 返回逻辑状态码 {@code -1}，HTTP状态码依然为 {@code 200}。可用于登录处理。
     *
     * @param exception 异常类的类名
     */
    public static ResponseEntity<Body> exception(String exception) {
        return ResponseEntity.ok().body(new Body(Body.FAILURE, exception));
    }

    /**
     * 返回逻辑状态码，HTTP状态码依然为200。用于POST请求返回逻辑信息。比如，评论成功但需等待审核；登录密码错误。
     */
    public static ResponseEntity<Body> status(int status) {
        return ResponseEntity.ok().body(new Body(status));
    }

    /**
     * 返回逻辑状态码。
     *
     * @see Responses#status(int)
     */
    public static ResponseEntity<Body> status(int status, String message) {
        return ResponseEntity.ok().body(new Body(status, message));
    }

    /**
     * 返回逻辑状态码。
     *
     * @see Responses#status(int)
     */
    public static ResponseEntity<Body> status(int status, HttpServletRequest request, String code) {
        return ResponseEntity.ok().body(new Body(status, getMessage(request, code)));
    }


    /**
     * 返回逻辑状态码。
     *
     * @see Responses#status(int)
     */
    public static ResponseEntity<Body> status(int status, HttpServletRequest request, String code,
                                              @Nullable Object... args) {
        return ResponseEntity.ok().body(new Body(status, getMessage(request, code, args)));
    }

    /**
     * 错误的请求
     */
    public static ResponseEntity<Body> badRequest(String message) {
        return ResponseEntity.status(SC_BAD_REQUEST).body(new Body(SC_BAD_REQUEST, message));
    }

    /**
     * 错误的请求
     */
    public static ResponseEntity<Body> badRequest(HttpServletRequest request, String code) {
        return ResponseEntity.status(SC_BAD_REQUEST).body(new Body(SC_BAD_REQUEST, getMessage(request, code)));
    }

    /**
     * 错误的请求
     */
    public static ResponseEntity<Body> badRequest(HttpServletRequest request, String code, @Nullable Object... args) {
        return ResponseEntity.status(SC_BAD_REQUEST).body(new Body(SC_BAD_REQUEST, getMessage(request, code, args)));
    }

    /**
     * 找不到页面
     */
    public static ResponseEntity<Body> notFound(String message) {
        return ResponseEntity.status(SC_NOT_FOUND).body(new Body(SC_NOT_FOUND, message));
    }

    /**
     * 找不到页面
     */
    public static ResponseEntity<Body> notFound(HttpServletRequest request, String code) {
        return ResponseEntity.status(SC_NOT_FOUND).body(new Body(SC_NOT_FOUND, getMessage(request, code)));
    }

    /**
     * 找不到页面
     */
    public static ResponseEntity<Body> notFound(HttpServletRequest request, String code, @Nullable Object... args) {
        return ResponseEntity.status(SC_NOT_FOUND).body(new Body(SC_NOT_FOUND, getMessage(request, code, args)));
    }

    /**
     * 禁止访问
     */
    public static ResponseEntity<Body> forbidden(String message) {
        return ResponseEntity.status(SC_BAD_REQUEST).body(new Body(SC_BAD_REQUEST, message));
    }

    /**
     * 禁止访问
     */
    public static ResponseEntity<Body> forbidden(HttpServletRequest request, String code) {
        return ResponseEntity.status(SC_BAD_REQUEST).body(new Body(SC_BAD_REQUEST, getMessage(request, code)));
    }

    /**
     * 禁止访问
     */
    public static ResponseEntity<Body> forbidden(HttpServletRequest request, String code, @Nullable Object... args) {
        return ResponseEntity.status(SC_BAD_REQUEST).body(new Body(SC_BAD_REQUEST, getMessage(request, code, args)));
    }


    /**
     * 未登录
     */
    public static ResponseEntity<Body> unauthorized() {
        return ResponseEntity.status(SC_UNAUTHORIZED).body(new Body(SC_UNAUTHORIZED));
    }

    /**
     * 用于浏览器访问。错误的请求。
     */
    @Nullable
    public static String badRequest(HttpServletResponse response, String message) throws IOException {
        response.sendError(SC_BAD_REQUEST, message);
        return null;
    }

    /**
     * 用于浏览器访问。错误的请求。
     */
    @Nullable
    public static String badRequest(HttpServletResponse response, HttpServletRequest request, String code) throws IOException {
        response.sendError(SC_BAD_REQUEST, getMessage(request, code));
        return null;
    }

    /**
     * 用于浏览器访问。错误的请求。
     */
    @Nullable
    public static String badRequest(HttpServletResponse response, HttpServletRequest request, String code, @Nullable Object... args) throws IOException {
        response.sendError(SC_BAD_REQUEST, getMessage(request, code, args));
        return null;
    }

    /**
     * 用于浏览器访问。错误的请求。
     */
    @Nullable
    public static String badRequest(HttpServletResponse response) throws IOException {
        response.sendError(SC_BAD_REQUEST);
        return null;
    }

    /**
     * 用于浏览器访问。找不到页面。
     */
    @Nullable
    public static String notFound(HttpServletResponse response) throws IOException {
        response.sendError(SC_NOT_FOUND);
        return null;
    }

    /**
     * 用于浏览器访问。找不到页面。
     */
    @Nullable
    public static String notFound(HttpServletResponse response, String message) throws IOException {
        response.sendError(SC_NOT_FOUND, message);
        return null;
    }

    /**
     * 用于浏览器访问。找不到页面。
     */
    @Nullable
    public static String notFound(HttpServletResponse response, HttpServletRequest request, String code) throws IOException {
        response.sendError(SC_NOT_FOUND, getMessage(request, code));
        return null;
    }

    /**
     * 用于浏览器访问。找不到页面。
     */
    @Nullable
    public static String notFound(HttpServletResponse response, HttpServletRequest request, String code, @Nullable Object... args) throws IOException {
        response.sendError(SC_NOT_FOUND, getMessage(request, code, args));
        return null;
    }


    /**
     * 用于浏览器访问。禁止访问。
     */
    @Nullable
    public static String forbidden(HttpServletResponse response, String message) throws IOException {
        response.sendError(SC_FORBIDDEN, message);
        return null;
    }

    /**
     * 用于浏览器访问。禁止访问。
     */
    @Nullable
    public static String forbidden(HttpServletResponse response, HttpServletRequest request, String code) throws IOException {
        response.sendError(SC_FORBIDDEN, getMessage(request, code));
        return null;
    }

    /**
     * 用于浏览器访问。禁止访问。
     */
    @Nullable
    public static String forbidden(HttpServletResponse response, HttpServletRequest request, String code, @Nullable Object... args) throws IOException {
        response.sendError(SC_FORBIDDEN, getMessage(request, code, args));
        return null;
    }

    /**
     * 用于浏览器访问。禁止访问。
     */
    @Nullable
    public static String forbidden(HttpServletResponse response) throws IOException {
        response.sendError(SC_FORBIDDEN);
        return null;
    }

    /**
     * 用于浏览器访问。未登录。
     */
    public static String unauthorized(HttpServletRequest request, HttpServletResponse response, String loginUrl) throws IOException {
        WebUtils.saveRequest(request);
        response.sendError(SC_UNAUTHORIZED);
        return "redirect:" + loginUrl;
    }

    /**
     * 工具类不允许创建对象
     */
    private Responses() {
    }

    public static class Body {
        /**
         * 状态码：成功
         */
        public static final int SUCCESS = 0;
        /**
         * 状态码：失败
         */
        public static final int FAILURE = -1;
        /**
         * 逻辑状态码。0:成功, -1:失败。其它状态可以根据自己的实际逻辑随意使用。
         */
        private int status = SUCCESS;
        /**
         * 数据
         */
        @Nullable
        private Map<String, Object> result;
        /**
         * 信息。错误的具体信息。也是code的国际化信息。
         */
        @Nullable
        private String message;
        /**
         * 异常类的类名。
         */
        @Nullable
        private String exception;

        public Body() {
        }

        public Body(int status) {
            this.status = status;
        }

        public Body(int status, @Nullable String message) {
            this.status = status;
            this.message = message;
        }

        public Body(int status, @Nullable String message, @Nullable String exception) {
            this.status = status;
            this.message = message;
            this.exception = exception;
        }

        public Body(int status, @Nullable Map<String, Object> result) {
            this.status = status;
            this.result = result;
        }

        @Override
        public String toString() {
            return "Body{" + "status=" + status + ", result=" + result + ", message='" + message + '\'' + ", exception='" + exception + '\'' + '}';
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        @Nullable
        public Map<String, Object> getResult() {
            return result;
        }

        public void setResult(@Nullable Map<String, Object> result) {
            this.result = result;
        }

        @Nullable
        public String getMessage() {
            return message;
        }

        public void setMessage(@Nullable String message) {
            this.message = message;
        }

        @Nullable
        public String getException() {
            return exception;
        }

        public void setException(@Nullable String exception) {
            this.exception = exception;
        }
    }
}
