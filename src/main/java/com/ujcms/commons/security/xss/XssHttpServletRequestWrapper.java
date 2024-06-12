package com.ujcms.commons.security.xss;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ujcms.commons.web.Strings;
import org.apache.commons.io.IOUtils;
import org.springframework.lang.Nullable;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 跨站脚本过滤 HttpServletRequestWrapper
 * <p>
 * 在 Vue 和 Freemarker 默认都对html进行转义的情况下，没有必要再次进行转义。
 *
 * @author PONY
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {
    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Nullable
    @Override
    public String getHeader(String name) {
        return Strings.htmlEscape(super.getHeader(name));
    }

    @Nullable
    @Override
    public String getQueryString() {
        return Strings.htmlEscape(super.getQueryString());
    }

    @Nullable
    @Override
    public String getParameter(String name) {
        return Strings.htmlEscape(super.getParameter(name));
    }

    @Nullable
    @Override
    public String[] getParameterValues(String name) {
        String[] parameterValues = super.getParameterValues(name);
        if (parameterValues == null) {
            return null;
        }
        for (int i = 0, len = parameterValues.length; i < len; i++) {
            String value = parameterValues[i];
            parameterValues[i] = Strings.htmlEscape(value);
        }
        return parameterValues;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        String applicationJsonHeader = "application/json";
        if (!getContentType().contains(applicationJsonHeader)) {
            return super.getInputStream();
        }
        String requestBody = readRequestBody();
        Map<String, Object> map = MAPPER.readValue(requestBody, new TypeReference<Map<String, Object>>() {
        });
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                entry.setValue(Strings.htmlEscape((String) value));
            }
        }
        String escapedBody = MAPPER.writeValueAsString(map);
        final ByteArrayInputStream bodyStream = new ByteArrayInputStream(escapedBody.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override
            public int read() {
                return bodyStream.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // unnecessary
            }
        };
    }

    private String readRequestBody() throws IOException {
        try (InputStream inputStream = super.getInputStream()) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        }
    }

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            // .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // .addModule(new JavaTimeModule())
            .build();
}
