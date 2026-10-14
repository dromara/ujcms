package com.ujcms.cms.core.support;

import com.ujcms.common.web.PathResolver;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 安装令牌文件（install-flow D49）：随机生成的一次性安装令牌除打印启动日志外，同时写入
 * {@value #FILE_URI}，供看不到启动日志的部署环境（Tomcat WAR、Windows 服务、日志重定向到
 * 文件或丢弃的 JAR 启动）读取。文件位于 {@code WEB-INF} 下——与 Lucene 索引、备份同一个
 * 可写数据根，JAR 与 WAR 两种启动方式、Windows 与 Linux 都经 Servlet 真实路径解析，且 Web
 * 层永不对外提供 {@code WEB-INF} 内容。
 *
 * <p>安全性质不变：令牌证明的是"能读服务器文件"，日志与文件是同一证明的两条通道。
 * 写入失败（只读目录、无真实路径）只告警不阻断启动，此时向导仅提示日志通道。安装完成
 * 与预设令牌时删除文件；已安装系统启动时也清理残留（集群另一节点完成、翻转后崩溃）。
 *
 * @author PONY
 */
@Component
public class InstallTokenFile {
    private static final Logger logger = LoggerFactory.getLogger(InstallTokenFile.class);

    /**
     * 令牌文件的 Servlet 路径（真实路径经 {@link PathResolver} 解析）
     */
    public static final String FILE_URI = "/WEB-INF/install-token.txt";

    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Supplier<Path> pathSupplier;

    @Autowired
    public InstallTokenFile(ObjectProvider<PathResolver> pathResolver) {
        this(() -> resolveDefault(pathResolver));
    }

    /**
     * 测试用：固定文件位置
     */
    InstallTokenFile(Supplier<Path> pathSupplier) {
        this.pathSupplier = pathSupplier;
    }

    /**
     * 写入令牌明文。
     *
     * @return 文件绝对路径；写入失败返回 {@code null}（已告警）
     */
    public @Nullable Path write(String token) {
        Path path = resolve();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, token + System.lineSeparator(), StandardCharsets.UTF_8);
            restrictToOwner(path);
            return path.toAbsolutePath();
        } catch (IOException | RuntimeException e) {
            logger.warn("Install token file not written ({}): {}", path, e.toString());
            return null;
        }
    }

    /**
     * 删除令牌文件（不存在即无操作，失败只告警）。
     */
    public void delete() {
        Path path = resolve();
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException e) {
            logger.warn("Install token file not deleted ({}): {}", path, e.toString());
        }
    }

    /**
     * 当前存在的令牌文件绝对路径；文件不存在（预设令牌、写入失败、已删除）返回 {@code null}。
     */
    public @Nullable String existingPath() {
        Path path = resolve();
        return Files.isRegularFile(path) ? path.toAbsolutePath().toString() : null;
    }

    private Path resolve() {
        return pathSupplier.get();
    }

    /**
     * POSIX 文件系统上收紧为仅属主可读写；Windows 等不支持的文件系统跳过。
     */
    private static void restrictToOwner(Path path) {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        } catch (IOException | UnsupportedOperationException e) {
            logger.debug("Install token file permissions not restricted ({}): {}", path, e.toString());
        }
    }

    /**
     * Servlet 真实路径（JAR：程序静态资源目录下的 {@code WEB-INF}；WAR：应用目录下的
     * {@code WEB-INF}）；无 Servlet 上下文或无真实路径（如符号链接）时回退到工作目录。
     */
    private static Path resolveDefault(ObjectProvider<PathResolver> pathResolver) {
        try {
            PathResolver resolver = pathResolver.getIfAvailable();
            String real = resolver != null ? resolver.getRealPath(FILE_URI) : null;
            if (real != null) {
                return Paths.get(real);
            }
        } catch (RuntimeException e) {
            logger.debug("Servlet real path unavailable for {}: {}", FILE_URI, e.toString());
        }
        return Paths.get(System.getProperty("user.dir"), "install-token.txt");
    }
}
