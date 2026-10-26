package com.ujcms.cms.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@link User#cancel()} is the whole scrub list of a self-service deregistration
 * (docs/spec/membership/member-center.md §13): the row survives as 已注销, every personal
 * field on it is cleared, and the username is replaced by the id-based placeholder.
 */
class UserCancelTest {

    @Test
    void cancelMarksCancelledRenamesAndScrubsPersonalData() {
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setPassword("encoded");
        user.setEmail("alice@example.com");
        user.setMobile("13800138000");
        user.setAlias("https://blog.example.com");
        user.setNickname("Alice");
        user.setRealName("Alice Example");
        user.setGender((short) 2);
        user.setAvatar("/uploads/avatar/42/a.png");
        user.setCustoms(new HashMap<>(Map.of("wechat", "alice-wx")));
        UserExt ext = user.getExt();
        ext.setBirthday(OffsetDateTime.now());
        ext.setLocation("Shanghai");
        ext.setBio("hello");
        ext.setHistoryPassword("old1,old2");
        ext.setLoginCount(9);

        user.cancel();

        assertTrue(user.isCancelled());
        assertEquals(User.STATUS_CANCELLED, user.getStatus());
        assertEquals("cancelled_42", user.getUsername());
        assertNull(user.getPassword());
        assertFalse(user.isHasPassword());
        assertNull(user.getEmail());
        assertNull(user.getMobile());
        assertNull(user.getAlias());
        assertEquals(User.CANCELLED_NICKNAME, user.getNickname());
        assertNull(user.getRealName());
        assertEquals(User.GENDER_SECRET, user.getGender());
        assertNull(user.getAvatar());
        assertTrue(user.getCustoms().isEmpty());
        assertNull(ext.getBirthday());
        assertNull(ext.getLocation());
        assertNull(ext.getBio());
        assertNull(ext.getHistoryPassword());
        // login statistics are not personal profile data and stay
        assertEquals(9, ext.getLoginCount());
    }
}
