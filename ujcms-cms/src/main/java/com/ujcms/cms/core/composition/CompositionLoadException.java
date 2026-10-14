package com.ujcms.cms.core.composition;

import java.util.List;

/**
 * 构成制品（Shape、页面清单、主题清单、种子）加载/校验异常。
 * 携带制品来源描述（文件路径或 classpath 资源名），便于按 §9.3 的失败行为给出可定位的错误。
 *
 * @author PONY
 */
public class CompositionLoadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CompositionLoadException(String sourceDesc, List<String> errors) {
        super(sourceDesc + ": " + String.join("; ", errors));
    }

    public CompositionLoadException(String sourceDesc, String message) {
        super(sourceDesc + ": " + message);
    }

    public CompositionLoadException(String sourceDesc, Throwable cause) {
        super(sourceDesc + ": " + cause.getMessage(), cause);
    }
}
