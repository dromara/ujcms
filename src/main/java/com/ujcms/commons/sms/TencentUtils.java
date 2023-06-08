package com.ujcms.commons.sms;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;

/**
 * 腾讯云工具类
 * <p>
 * API: https://cloud.tencent.com/document/product/382/55981
 * 状态码：https://cloud.tencent.com/document/api/382/52068#SendStatus
 *
 * @author PONY
 */
public class TencentUtils {
    /**
     * @return 返回 {@code null} 代表成功，否则返回错误信息
     */
    public static String sendSms(String secretId, String secretKey, String sdkAppId, String region,
                                 String signName, String templateId, String code, String mobile) {
        Credential cred = new Credential(secretId, secretKey);
        SmsClient client = new SmsClient(cred, region);
        SendSmsRequest req = new SendSmsRequest();
        req.setSmsSdkAppId(sdkAppId);
        req.setSignName(signName);
        req.setTemplateId(templateId);
        req.setTemplateParamSet(new String[]{code});
        req.setPhoneNumberSet(new String[]{mobile});
        try {
            SendSmsResponse response = client.SendSms(req);
            // 成功状态码为 "Ok"
            String success = "Ok";
            for (SendStatus sendStatus : response.getSendStatusSet()) {
                // 不成功
                String responseCode = sendStatus.getCode();
                if (!success.equalsIgnoreCase(responseCode)) {
                    return "code: " + responseCode + ", message: " + sendStatus.getMessage();
                }
            }
            return null;
        } catch (TencentCloudSDKException e) {
            throw new RuntimeException(e);
        }
    }
}
