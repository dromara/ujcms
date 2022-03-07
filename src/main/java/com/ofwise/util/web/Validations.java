package com.ofwise.util.web;

import com.ofwise.util.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import static com.ofwise.util.file.FilesEx.DOUBLE_DOT;

/**
 * 校验器
 *
 * @author PONY
 */
public class Validations {
    public static void uri(String name, @Nullable String value) {
        if (StringUtils.contains(value, DOUBLE_DOT)) {
            throw new Http400Exception("Parameter '" + name + "' cannot contains '..' characters: " + value);
        }
    }

    public static void uri(String name, @Nullable String value, String prefix) {
        uri(name, value);
        if (!StringUtils.startsWith(value, prefix)) {
            throw new Http400Exception("Parameter '" + name + "' must starts with '" + prefix + "': " + value);
        }
    }

    /**
     * 工具类不能实例化
     */
    private Validations() {
    }
}
