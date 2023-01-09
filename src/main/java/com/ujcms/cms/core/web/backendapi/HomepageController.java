package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.support.Props;
import com.ujcms.util.security.Secures;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final PasswordEncoder passwordEncoder;
    private final Props props;

    public HomepageController(PasswordEncoder passwordEncoder, Props props) {
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @GetMapping("generated-key")
    @PreAuthorize("hasAnyAuthority('homepage:generatedKey','*')")
    public String generatedKey() {
        Secures.Pair keyPair = Secures.generateSm2QdKeyPair();
        String newline = "\n";
        StringBuilder buff = new StringBuilder();
        buff.append("# 以下密钥皆随机生成，用于application.yaml配置文件。注意不要和原配置重复，同一配置项只允许出现一次。").append(newline);
        buff.append("jwt.secret: ").append(Secures.randomAlphanumeric(64)).append(newline);
        buff.append("jwt.token-secret: ").append(Secures.randomAlphanumeric(16)).append(newline);
        buff.append("ujcms.download-secret: ").append(Secures.randomAlphanumeric(32)).append(newline);
        buff.append("ujcms.client-sm2-public-key: ").append(keyPair.getPublicKey()).append(newline);
        buff.append("ujcms.client-sm2-private-key: ").append(keyPair.getPrivateKey()).append(newline);
        String password = passwordEncoder.encode("password");
        buff.append(newline);
        buff.append("# 以下内容只用于修改数据库数据，不用于application.yaml配置文件。").append(newline);
        buff.append("# ujcms.password-pepper修改后，用户密码Hash值也会改变，原密码将无法登录。").append(newline);
        buff.append("# 需要根据以下值修改数据库ujcms_user表的password_字段的值，修改后用户密码将被重置为password").append(newline);
        buff.append("ujcms_user.password_: ").append(password).append(newline);
        return buff.toString();
    }

    @GetMapping("environment")
    @PreAuthorize("hasAnyAuthority('homepage:environment','*')")
    public Map<String, Object> environment() {
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
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long availableMemory = maxMemory - totalMemory + freeMemory;
        int div = 1000;
        double freeMemoryMb = ((double) freeMemory) / div / div;
        double totalMemoryMb = ((double) totalMemory) / div / div;
        double usedMemoryMb = ((double) usedMemory) / div / div;
        double maxMemoryMb = ((double) maxMemory) / div / div;
        double availableMemoryMb = ((double) availableMemory) / div / div;
        result.put("maxMemory", maxMemoryMb);
        result.put("totalMemory", totalMemoryMb);
        result.put("usedMemory", usedMemoryMb);
        result.put("availableMemory", availableMemoryMb);
        result.put("freeMemory", freeMemoryMb);
        return result;
    }
}
