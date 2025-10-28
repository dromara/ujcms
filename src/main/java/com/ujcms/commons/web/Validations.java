package com.ujcms.commons.web;

import static com.ujcms.commons.file.FilesEx.DOUBLE_DOT;
import static org.apache.commons.lang3.Strings.CS;

import org.springframework.lang.Nullable;

import com.ujcms.commons.web.exception.Http400Exception;

/**
 * 校验器
 *
 * @author PONY
 */
public final class Validations {
    public static void uri(String name, @Nullable String value) {
        if (CS.contains(value, DOUBLE_DOT)) {
            throw new Http400Exception("Parameter '" + name + "' cannot contains '..' characters: " + value);
        }
    }

    public static void uri(String name, @Nullable String value, String prefix) {
        uri(name, value);
        if (!CS.startsWith(value, prefix)) {
            throw new Http400Exception("Parameter '" + name + "' must starts with '" + prefix + "': " + value);
        }
    }

    /**
     * 工具类不能实例化
     */
    private Validations() {
    }
}
