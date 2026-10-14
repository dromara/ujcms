package com.ujcms.common.freemarker;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OsTemplateLoader} 单元测试（design-review P3-5 / T7）：存在性探测的连接释放与 404 负缓存。
 * 以进程内 HttpServer 作远程模板存储桩，按路径计数请求验证探测次数。
 *
 * @author PONY
 */
class OsTemplateLoaderTest {
    private HttpServer server;
    private String prefix;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final List<String> methods = new CopyOnWriteArrayList<>();
    /**
     * 路径 → 响应码（缺省 404）
     */
    private final Map<String, Integer> responses = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            methods.add(exchange.getRequestMethod());
            int code = responses.getOrDefault(path, 404);
            byte[] body = "<p>tpl</p>".getBytes(StandardCharsets.UTF_8);
            boolean head = "HEAD".equals(exchange.getRequestMethod());
            if (code == 200) {
                exchange.sendResponseHeaders(200, head ? -1 : body.length);
                if (!head) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            } else {
                exchange.sendResponseHeaders(code, -1);
            }
            exchange.close();
        });
        server.start();
        prefix = "http://127.0.0.1:" + server.getAddress().getPort() + "/themes";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private int hits(String path) {
        AtomicInteger count = hits.get(path);
        return count != null ? count.get() : 0;
    }

    @Test
    void okReturnsUrlAndProbesWithHead() {
        responses.put("/themes/page.html", 200);
        OsTemplateLoader loader = new OsTemplateLoader(prefix);
        URL url = loader.getURL("page.html");
        assertNotNull(url);
        assertEquals(prefix + "/page.html", url.toString());
        assertEquals(List.of("HEAD"), methods);
        // 命中不缓存：新鲜度由 FreeMarker 模板缓存的 update delay 管理
        assertNotNull(loader.getURL("page.html"));
        assertEquals(2, hits("/themes/page.html"));
    }

    @Test
    void notFoundIsNegativeCachedWithinTtl() {
        OsTemplateLoader loader = new OsTemplateLoader(prefix, 60_000L);
        assertNull(loader.getURL("missing_zh_CN.html"));
        assertNull(loader.getURL("missing_zh_CN.html"));
        assertNull(loader.getURL("missing_zh_CN.html"));
        assertEquals(1, hits("/themes/missing_zh_CN.html"));
    }

    @Test
    void negativeCacheExpiresAndNewTemplateBecomesVisible() throws InterruptedException {
        OsTemplateLoader loader = new OsTemplateLoader(prefix, 50L);
        assertNull(loader.getURL("late.html"));
        assertEquals(1, hits("/themes/late.html"));
        // TTL 内新建模板暂不可见（与既有模板的修改可见延迟同级）
        responses.put("/themes/late.html", 200);
        assertNull(loader.getURL("late.html"));
        assertEquals(1, hits("/themes/late.html"));
        Thread.sleep(80L);
        assertNotNull(loader.getURL("late.html"));
        assertEquals(2, hits("/themes/late.html"));
    }

    @Test
    void zeroTtlDisablesNegativeCache() {
        OsTemplateLoader loader = new OsTemplateLoader(prefix, 0L);
        assertNull(loader.getURL("missing.html"));
        assertNull(loader.getURL("missing.html"));
        assertEquals(2, hits("/themes/missing.html"));
    }

    @Test
    void serverErrorIsNotNegativeCached() {
        responses.put("/themes/flaky.html", 500);
        OsTemplateLoader loader = new OsTemplateLoader(prefix, 60_000L);
        assertNull(loader.getURL("flaky.html"));
        // 暂时故障恢复后立即可见，不受负缓存 TTL 影响
        responses.put("/themes/flaky.html", 200);
        assertNotNull(loader.getURL("flaky.html"));
        assertEquals(2, hits("/themes/flaky.html"));
    }

    @Test
    void prefixWithTrailingSlashIsNormalized() {
        responses.put("/themes/a.html", 200);
        OsTemplateLoader loader = new OsTemplateLoader(prefix + "/");
        assertNotNull(loader.getURL("a.html"));
        assertTrue(hits("/themes/a.html") > 0);
    }
}
