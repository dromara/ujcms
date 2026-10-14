package com.ujcms.cms.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.common.sms.SmsError;

/**
 * Pins how provider error codes are classified. The codes and their meaning are taken from the official
 * references — Alibaba Cloud: https://help.aliyun.com/zh/sms/developer-reference/api-error-codes,
 * Tencent Cloud: https://cloud.tencent.com/document/product/382/55981 — and only per-phone-number
 * frequency limits count as {@code RATE_LIMITED}: account/application quotas are operator settings a
 * retry cannot fix, so they stay {@code OTHER}.
 */
class SmsProviderErrorClassificationTest {
    private static final String MOBILE = "13800138000";

    private static SmsError aliyun(String json) {
        return aliyun(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static SmsError aliyun(ResponseCreator response) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(response);
        return new AliyunSmsService(builder).sendSms("id", "secret", "sign", "SMS_1", "code", "123456", MOBILE);
    }

    private static SmsError tencent(String json) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        TencentSmsService.SmsConfig config = new TencentSmsService.SmsConfig("id", "key", "1400000000",
                "ap-guangzhou", "sign", "1");
        return new TencentSmsService(builder, new ObjectMapper()).sendSms(config, MOBILE, "123456");
    }

    @Test
    void successReturnsNoError() {
        assertNull(aliyun("{\"Code\":\"OK\",\"Message\":\"OK\"}"));
        assertNull(tencent("{\"Response\":{\"SendStatusSet\":[{\"Code\":\"Ok\",\"Message\":\"send success\"}]}}"));
    }

    /**
     * Alibaba Cloud reports credential and signature problems as HTTP 4xx with the same Code / Message
     * body. A misconfigured provider must come back as a classified error the caller can turn into a
     * friendly message — not as an exception: flows that depend on the code (two-factor login) would
     * otherwise answer 500 exactly when the operator most needs to be told what is wrong.
     */
    @Test
    void aliyunHttpErrorIsReturnedAsAnErrorNotThrown() {
        SmsError error = aliyun(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                .body("{\"Code\":\"InvalidAccessKeyId.NotFound\",\"Message\":\"Specified access key is not found.\"}"));

        assertNotNull(error);
        assertEquals(SmsError.Kind.OTHER, error.kind());
        assertEquals("InvalidAccessKeyId.NotFound", error.code());
        assertEquals("Specified access key is not found.", error.message());
    }

    @Test
    void aliyunHttpErrorWithoutJsonBodyReportsTheStatus() {
        SmsError error = aliyun(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML)
                .body("<html>Bad Gateway</html>"));

        assertNotNull(error);
        assertEquals(SmsError.Kind.OTHER, error.kind());
        assertEquals("HTTP 502", error.message());
    }

    @Test
    void aliyunNetworkFailureIsReturnedAsAnErrorNotThrown() {
        SmsError error = aliyun(withException(new IOException("connection reset")));

        assertNotNull(error);
        assertEquals(SmsError.Kind.OTHER, error.kind());
        assertEquals(AliyunSmsService.PROVIDER, error.provider());
    }

    @ParameterizedTest
    @CsvSource({
            "isv.BUSINESS_LIMIT_CONTROL, RATE_LIMITED",
            "isv.MOBILE_NUMBER_ILLEGAL, INVALID_MOBILE",
            // account-level quotas set in the provider console
            "isv.DAY_LIMIT_CONTROL, OTHER",
            "isv.MONTH_LIMIT_CONTROL, OTHER",
            "isv.AMOUNT_NOT_ENOUGH, OTHER",
    })
    void aliyunCodes(String code, SmsError.Kind kind) {
        SmsError error = aliyun("{\"Code\":\"" + code + "\",\"Message\":\"m\"}");

        assertNotNull(error);
        assertEquals(kind, error.kind());
        assertEquals(AliyunSmsService.PROVIDER, error.provider());
        assertEquals(code, error.code());
    }

    @ParameterizedTest
    @CsvSource({
            "LimitExceeded.PhoneNumberThirtySecondLimit, RATE_LIMITED",
            "LimitExceeded.PhoneNumberOneHourLimit, RATE_LIMITED",
            "LimitExceeded.PhoneNumberDailyLimit, RATE_LIMITED",
            "LimitExceeded.PhoneNumberSameContentDailyLimit, RATE_LIMITED",
            "LimitExceeded.DeliveryFrequencyLimit, RATE_LIMITED",
            "InvalidParameterValue.IncorrectPhoneNumber, INVALID_MOBILE",
            // too many numbers in one call — shares the PhoneNumber prefix but is not a frequency limit
            "LimitExceeded.PhoneNumberCountLimit, OTHER",
            // application-level quotas set in the provider console
            "LimitExceeded.AppDailyLimit, OTHER",
            "LimitExceeded.AppMainlandChinaDailyLimit, OTHER",
            "FailedOperation.InsufficientBalanceInSmsPackage, OTHER",
    })
    void tencentCodes(String code, SmsError.Kind kind) {
        // per-number failures arrive in SendStatusSet, request-level failures in Error
        SmsError status = tencent("{\"Response\":{\"SendStatusSet\":[{\"Code\":\"" + code + "\",\"Message\":\"m\"}]}}");
        SmsError top = tencent("{\"Response\":{\"Error\":{\"Code\":\"" + code + "\",\"Message\":\"m\"}}}");

        for (SmsError error : new SmsError[]{status, top}) {
            assertNotNull(error);
            assertEquals(kind, error.kind());
            assertEquals(TencentSmsService.PROVIDER, error.provider());
            assertEquals(code, error.code());
        }
    }
}
