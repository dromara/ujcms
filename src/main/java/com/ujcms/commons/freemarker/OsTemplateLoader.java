package com.ujcms.commons.freemarker;

import com.ujcms.commons.file.FilesEx;
import freemarker.cache.URLTemplateLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.AbstractFileResolvingResource;
import org.springframework.lang.Nullable;
import org.springframework.ui.freemarker.SpringTemplateLoader;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 对象存储模板加载器
 * <p>
 * spring.freemarker.template-loader-path 也支持 URL格式，但自定义的方式有更好的控制，如处理因 Locale 导致多次加载模板等问题。
 *
 * @author PONY
 * @see SpringTemplateLoader
 */
public class OsTemplateLoader extends URLTemplateLoader {
    private String prefix;

    public OsTemplateLoader(String prefix) {
        this.prefix = StringUtils.endsWith(prefix, FilesEx.SLASH) ? prefix : prefix + FilesEx.SLASH;
    }

    /**
     * @see AbstractFileResolvingResource#exists()
     */
    @Nullable
    @Override
    protected URL getURL(String name) {
        try {
            URL url = new URL(prefix + name);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                return url;
            }
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            conn.disconnect();
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
