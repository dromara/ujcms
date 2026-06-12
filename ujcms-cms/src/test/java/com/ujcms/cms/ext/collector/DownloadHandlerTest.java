package com.ujcms.cms.ext.collector;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.ujcms.common.file.FileHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadHandlerTest {
    private HttpServer server;
    private DownloadHandler handler;
    private FileHandler fileHandler;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        handler = new DownloadHandler();
        fileHandler = mock(FileHandler.class);
        when(fileHandler.getDisplayPrefix()).thenReturn("");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void imageDownload_usesExtensionFromUrlPath() throws IOException {
        serve("/pic.png", "image/png", pngBytes());

        String result = handler.download(baseUrl + "/pic.png", DownloadHandler.TYPE_IMAGE,
                "ua", fileHandler, "/site");

        assertNotNull(result);
        assertTrue(result.endsWith(".png"), "expected .png extension, got " + result);
        assertTrue(result.contains("/image/"), "path should contain /image/, got " + result);
    }

    @Test
    void imageDownload_fallsBackToMimeWhenUrlExtensionMissing() throws IOException {
        // URL has no extension — mimics dynamic endpoints like /getImage?id=123
        serve("/getImage", "image/png", pngBytes());

        String result = handler.download(baseUrl + "/getImage", DownloadHandler.TYPE_IMAGE,
                "ua", fileHandler, "/site");

        assertNotNull(result);
        assertTrue(result.endsWith(".png"), "expected .png from MIME fallback, got " + result);
    }

    @Test
    void imageDownload_fallsBackToMimeWhenUrlExtensionNotImage() throws IOException {
        // URL has a non-image extension — mimics endpoints like /track.php?img=cat
        serve("/track.php", "image/jpeg", jpegBytes());

        String result = handler.download(baseUrl + "/track.php", DownloadHandler.TYPE_IMAGE,
                "ua", fileHandler, "/site");

        assertNotNull(result);
        assertTrue(result.endsWith(".jpg"), "expected .jpg from MIME fallback, got " + result);
    }

    @Test
    void imageDownload_mapsJpegMimeToJpgExtension() throws IOException {
        serve("/photo", "image/jpeg", jpegBytes());

        String result = handler.download(baseUrl + "/photo", DownloadHandler.TYPE_IMAGE,
                "ua", fileHandler, "/site");

        assertNotNull(result);
        assertTrue(result.endsWith(".jpg"), "image/jpeg should map to .jpg, got " + result);
    }

    @Test
    void imageDownload_detectsExtensionFromContentWhenMimeIsUnknown() throws IOException {
        // Server says generic "image/xyz" but sends real PNG bytes. URL extension is useless.
        // Magic-byte detection must identify the PNG.
        serve("/stream", "image/xyz", pngBytes());

        String result = handler.download(baseUrl + "/stream", DownloadHandler.TYPE_IMAGE,
                "ua", fileHandler, "/site");

        assertNotNull(result);
        assertTrue(result.endsWith(".png"), "magic bytes should yield .png, got " + result);
    }

    @Test
    void imageDownload_rejectsWhenContentIsNotRecognizableImage() {
        // MIME contains "image" (passes the header filter) but bytes are garbage.
        // With no usable extension anywhere, ImageIO fails, and we should reject.
        serve("/bad", "image/unknown", "not an image at all".getBytes());

        String result = handler.download(baseUrl + "/bad", DownloadHandler.TYPE_IMAGE,
                "ua", fileHandler, "/site");

        assertNull(result, "unrecognizable image content must be rejected");
        verify(fileHandler, never()).store(anyString(), any(File.class));
    }

    @Test
    void imageDownload_rejectsWhenContentTypeIsNotImage() {
        serve("/page.html", "text/html", "<html></html>".getBytes());

        String result = handler.download(baseUrl + "/page.html", DownloadHandler.TYPE_IMAGE,
                "ua", fileHandler, "/site");

        assertNull(result);
        verify(fileHandler, never()).store(anyString(), any(File.class));
    }

    @Test
    void imageDownload_storesFileOnSuccess() throws IOException {
        serve("/pic.png", "image/png", pngBytes());

        String result = handler.download(baseUrl + "/pic.png", DownloadHandler.TYPE_IMAGE,
                "ua", fileHandler, "/site");

        assertNotNull(result);
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileHandler).store(pathCaptor.capture(), any(File.class));
        String storedPath = pathCaptor.getValue();
        assertTrue(storedPath.startsWith("/site/image/"), "stored path should start with /site/image/, got " + storedPath);
        assertTrue(storedPath.endsWith(".png"));
        assertEquals(storedPath, result, "returned path should match stored path (empty display prefix)");
    }

    @Test
    void fileDownload_rejectsHtmlContentType() {
        serve("/page", "text/html", "<html></html>".getBytes());

        String result = handler.download(baseUrl + "/page", DownloadHandler.TYPE_FILE,
                "ua", fileHandler, "/site");

        assertNull(result);
    }

    @Test
    void fileDownload_preservesOriginalExtensionWithoutImageValidation() {
        // File-type downloads skip the ImageIO validation path — arbitrary bytes are fine.
        serve("/doc.pdf", "application/pdf", "%PDF-1.4 fake pdf".getBytes());

        String result = handler.download(baseUrl + "/doc.pdf", DownloadHandler.TYPE_FILE,
                "ua", fileHandler, "/site");

        assertNotNull(result);
        assertTrue(result.endsWith(".pdf"), "expected .pdf for file download, got " + result);
        verify(fileHandler).store(anyString(), any(File.class));
    }

    @Test
    void download_returnsNullOnNon200Response() {
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        String result = handler.download(baseUrl + "/missing", DownloadHandler.TYPE_IMAGE,
                "ua", fileHandler, "/site");

        assertNull(result);
    }

    private void serve(String path, String contentType, byte[] body) {
        server.createContext(path, new ByteHandler(contentType, body));
    }

    private static byte[] pngBytes() throws IOException {
        return encodeImage("png");
    }

    private static byte[] jpegBytes() throws IOException {
        return encodeImage("jpeg");
    }

    private static byte[] encodeImage(String format) throws IOException {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static final class ByteHandler implements HttpHandler {
        private final String contentType;
        private final byte[] body;

        ByteHandler(String contentType, byte[] body) {
            this.contentType = contentType;
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
