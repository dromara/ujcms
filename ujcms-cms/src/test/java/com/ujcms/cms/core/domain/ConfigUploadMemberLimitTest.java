package com.ujcms.cms.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 会员上传的单张图片上限（docs/spec/member-contribute/plan.md D10）：会员上限与“图片最大长度”都不为 0 时取较小者，
 * 任一为 0 则按另一个，都为 0 才不限——全局“图片最大长度”默认 0，会员接口不能跟着不限
 */
class ConfigUploadMemberLimitTest {
    private static final long MB = 1024L * 1024L;

    @Test
    void memberLimitAppliesWhenTheGlobalImageLimitIsUnlimited() {
        Config.Upload upload = new Config.Upload();
        assertEquals(0, upload.getImageLimit());
        assertEquals(5 * MB, upload.getMemberImageLimitByte());
    }

    @Test
    void theSmallerOfTheTwoLimitsWins() {
        Config.Upload upload = new Config.Upload();
        upload.setMemberImageLimit(5);
        upload.setImageLimit(2);
        assertEquals(2 * MB, upload.getMemberImageLimitByte());

        upload.setImageLimit(20);
        assertEquals(5 * MB, upload.getMemberImageLimitByte());
    }

    @Test
    void zeroMemberLimitFallsBackToTheGlobalOneAndBothZeroMeansUnlimited() {
        Config.Upload upload = new Config.Upload();
        upload.setMemberImageLimit(0);
        upload.setImageLimit(8);
        assertEquals(8 * MB, upload.getMemberImageLimitByte());

        upload.setImageLimit(0);
        assertEquals(0L, upload.getMemberImageLimitByte());
    }
}
