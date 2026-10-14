package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.StaticProps;
import com.ujcms.common.security.Pbkdf2WithHmacSm3PasswordEncoder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * generated-key 端点：一次性生成 application.yaml「密钥」段的整套随机密钥（含 data-secret-key、password-pepper），
 * 并按新生成的胡椒输出自洽的口令重置 UPDATE——换上这些密钥并执行该 SQL 后，设有口令的账号可用初始口令登录。
 */
class HomepageControllerTest {

    /** generatedKey 不依赖 contentStatCache / props，可传 null。 */
    private final HomepageController controller = new HomepageController(null, null);

    @Test
    void yamlCoversTheWholeKeySection_onceEach_noDeadKey() {
        String yaml = controller.generatedKey().yaml();
        List<String> keys = List.of("jwt.secret", "captcha.secret", "ujcms.download-secret",
                "ujcms.client-sm2-public-key", "ujcms.client-sm2-private-key",
                "ujcms.data-secret-key", "ujcms.password-pepper");
        // 与 application.yaml「密钥」段顺序一致，每项恰好一行
        assertEquals(keys, yaml.lines().map(l -> l.substring(0, l.indexOf(": "))).toList());
        assertFalse(yaml.contains("token-secret"), "jwt.token-secret has no consumer and must not be emitted");
        // 纯 key: value 行——说明文字由控制台渲染，不混入注释
        assertTrue(yaml.lines().allMatch(l -> l.matches("[a-z0-9.\\-]+: [A-Za-z0-9]+")), yaml);
        assertTrue(lineValue(yaml, "jwt.secret: ").length() >= 64, "HmacSM3 signing key: 64 chars recommended");
        assertEquals(32, lineValue(yaml, "captcha.secret: ").length(), "captcha signer needs >= 32 bytes");
        assertEquals(32, lineValue(yaml, "ujcms.download-secret: ").length());
        assertEquals(130, lineValue(yaml, "ujcms.client-sm2-public-key: ").length(), "04 || X || Y, HEX");
        assertEquals(64, lineValue(yaml, "ujcms.client-sm2-private-key: ").length(), "D, 32 bytes HEX");
    }

    @Test
    void generatesBothKeys_nonDefault() {
        String yaml = controller.generatedKey().yaml();
        String dataSecretKey = lineValue(yaml, "ujcms.data-secret-key: ");
        String pepper = lineValue(yaml, "ujcms.password-pepper: ");
        assertEquals(StaticProps.DATA_SECRET_KEY_LENGTH, dataSecretKey.length(), "data-secret-key must be 16 chars");
        assertNotEquals(StaticProps.DEFAULT_DATA_SECRET_KEY, dataSecretKey, "must not emit the default data-secret-key");
        assertNotEquals(StaticProps.DEFAULT_PASSWORD_PEPPER, pepper, "must not emit the default pepper");
        assertTrue(pepper.length() >= 32, "pepper should be long (>=32)");
    }

    @Test
    void resetSqlHashIsConsistentWithGeneratedPepper_andSkipsPasswordlessAccounts() {
        HomepageController.GeneratedKeyResult out = controller.generatedKey();
        String pepper = lineValue(out.yaml(), "ujcms.password-pepper: ");
        String sql = out.resetSql();
        assertTrue(sql.startsWith("UPDATE ujcms_user SET password_='"), sql);
        assertTrue(sql.contains("pwd_must_change_='1'"), "UPDATE must force first-login change");
        assertTrue(sql.contains(" WHERE password_<>'" + User.PASSWORD_NONE + "';"),
                "OAuth-only accounts (no password) must keep having no password");
        assertEquals(HomepageController.RESET_PASSWORD, out.resetPassword());
        String hash = sql.substring(sql.indexOf("password_='") + "password_='".length());
        hash = hash.substring(0, hash.indexOf('\''));
        // 自洽：哈希正是用上面生成的胡椒对初始口令计算的
        assertTrue(new Pbkdf2WithHmacSm3PasswordEncoder(pepper).matches(out.resetPassword(), hash),
                "reset hash must validate the reset password under the generated pepper");
        // 反证：用其它胡椒则不匹配
        assertFalse(new Pbkdf2WithHmacSm3PasswordEncoder("some-other-pepper").matches(out.resetPassword(), hash),
                "hash must not validate under a different pepper");
    }

    /** 取 "key: value" 行的 value（到行尾，去空白）。 */
    private static String lineValue(String text, String marker) {
        int start = text.indexOf(marker) + marker.length();
        int end = text.indexOf('\n', start);
        return (end < 0 ? text.substring(start) : text.substring(start, end)).trim();
    }
}
