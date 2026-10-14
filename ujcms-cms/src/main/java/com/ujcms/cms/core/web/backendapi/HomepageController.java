package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.component.ContentStatCache;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.StaticProps;
import com.ujcms.common.security.Pbkdf2WithHmacSm3PasswordEncoder;
import com.ujcms.common.security.Secures;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 首页 Controller
 *
 * @author PONY
 */
@RestController("backendHomepageController")
@RequestMapping(BACKEND_API + "/core/homepage")
public class HomepageController {
    private final ContentStatCache contentStatCache;
    private final Props props;

    public HomepageController(ContentStatCache contentStatCache, Props props) {
        this.contentStatCache = contentStatCache;
        this.props = props;
    }

    @GetMapping("content-stat")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public ContentStatResult contentStat() {
        Long siteId = Contexts.getCurrentSiteId();
        return new ContentStatResult(
                contentStatCache.articleStat(siteId),
                contentStatCache.channelStat(siteId),
                contentStatCache.userStat(),
                contentStatCache.attachmentStat(siteId));
    }

    public record ContentStatResult(
            ContentStatCache.StatBucket article,
            ContentStatCache.StatBucket channel,
            ContentStatCache.StatBucket user,
            ContentStatCache.StatBucket attachment) {}

    /** 口令重置 SQL 所设的统一初始口令。 */
    static final String RESET_PASSWORD = "password";

    /**
     * 生成 application.yaml「密钥」段的一整套随机密钥——{@code jwt.secret}、{@code captcha.secret}、
     * {@code ujcms.download-secret}、客户端 SM2 密钥对、{@code ujcms.data-secret-key}、{@code ujcms.password-pepper}
     * （集群各节点须用同一份）——并按**新生成的** {@code password-pepper} 给出把账号口令重置为
     * {@value #RESET_PASSWORD} 的 {@code UPDATE} 语句。密钥与重置 SQL 自洽：换上这些密钥并执行该 SQL 后，全员即可用
     * 初始口令登录——从根本上规避“改胡椒后无法登录、也就取不到新胡椒哈希”的自举问题（见 runbook §2.2）。
     * <p>
     * 输出只含数据（YAML 行与 SQL），说明文字由控制台按当前语言渲染。
     */
    @GetMapping("generated-key")
    @PreAuthorize("hasAnyAuthority('homepage:generatedKey','*')")
    @SuppressWarnings("java:S6437")
    public GeneratedKeyResult generatedKey() {
        Secures.Pair keyPair = Secures.generateSm2QdKeyPair();
        String dataSecretKey = Secures.randomAlphanumeric(StaticProps.DATA_SECRET_KEY_LENGTH);
        String passwordPepper = Secures.randomAlphanumeric(32);
        // 用新生成的胡椒计算初始口令的哈希，使密钥与重置 SQL 自洽。
        String passwordHash = new Pbkdf2WithHmacSm3PasswordEncoder(passwordPepper).encode(RESET_PASSWORD);
        // 条目顺序与 application.yaml 的「密钥」段一致；签名密钥（jwt / captcha）至少 32 字节
        String yaml = "jwt.secret: " + Secures.randomAlphanumeric(64) + "\n"
                + "captcha.secret: " + Secures.randomAlphanumeric(32) + "\n"
                + "ujcms.download-secret: " + Secures.randomAlphanumeric(32) + "\n"
                + "ujcms.client-sm2-public-key: " + keyPair.getPublicKey() + "\n"
                + "ujcms.client-sm2-private-key: " + keyPair.getPrivateKey() + "\n"
                + "ujcms.data-secret-key: " + dataSecretKey + "\n"
                + "ujcms.password-pepper: " + passwordPepper + "\n";
        // 只重置设有口令的账号：纯第三方登录账号（password_ 为 PASSWORD_NONE）保持无口令，否则会平白得到一个人人皆知的初始口令
        String resetSql = "UPDATE ujcms_user SET password_='" + passwordHash + "', pwd_must_change_='1'"
                + " WHERE password_<>'" + User.PASSWORD_NONE + "';\n";
        return new GeneratedKeyResult(yaml, resetSql, RESET_PASSWORD);
    }

    /**
     * 密钥生成结果：{@code yaml} 为可直接粘贴到 application.yaml 的 {@code key: value} 行；{@code resetSql} 为按其中
     * {@code ujcms.password-pepper} 计算的口令重置语句；{@code resetPassword} 即该语句所设的初始口令。
     */
    public record GeneratedKeyResult(String yaml, String resetSql, String resetPassword) {}

    @GetMapping("system-info")
    @PreAuthorize("hasAnyAuthority('homepage:systemInfo','*')")
    public Map<String, Object> systemInfo() {
        Map<String, Object> result = new HashMap<>(16);
        Properties properties = System.getProperties();
        result.put("osName", properties.getProperty("os.name"));
        result.put("osArch", properties.getProperty("os.arch"));
        result.put("osVersion", properties.getProperty("os.version"));
        result.put("javaRuntimeName", properties.getProperty("java.runtime.name"));
        result.put("javaRuntimeVersion", properties.getProperty("java.runtime.version"));
        result.put("javaVersion", properties.getProperty("java.version"));
        result.put("javaVendor", properties.getProperty("java.vendor"));
        result.put("javaVmName", properties.getProperty("java.vm.name"));
        result.put("javaVmVersion", properties.getProperty("java.vm.version"));
        result.put("javaVmVendor", properties.getProperty("java.vm.vendor"));
        result.put("userName", properties.getProperty("user.name"));
        result.put("userDir", properties.getProperty("user.dir"));
        result.put("javaIoTmpdir", properties.getProperty("java.io.tmpdir"));
        result.put("version", props.getVersion());

        Runtime runtime = Runtime.getRuntime();
        int div = 1024;
        long maxMemory = runtime.maxMemory() / div / div;
        long totalMemory = runtime.totalMemory() / div / div;
        long freeMemory = runtime.freeMemory() / div / div;
        long usedMemory = totalMemory - freeMemory;
        long remainingMemory = maxMemory - totalMemory;
        long availableMemory = maxMemory - totalMemory + freeMemory;
        result.put("maxMemory", maxMemory);
        result.put("totalMemory", totalMemory);
        result.put("usedMemory", usedMemory);
        result.put("availableMemory", availableMemory);
        result.put("remainingMemory", remainingMemory);
        result.put("freeMemory", freeMemory);
        long currentTime = System.currentTimeMillis();
        long vmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        result.put("upDays", (currentTime - vmStartTime) / 1000 / 60 / 60 / 24);
        return result;
    }
}
