package com.ujcms.commons.sms;


import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;

/**
 * 阿里云工具类
 * <p>
 * 发送短信：https://next.api.aliyun.com/api/Dysmsapi/2017-05-25/SendSms
 * 错误码：https://help.aliyun.com/document_detail/101346.html
 *
 * @author PONY
 */
public class AliyunUtils {
    private static Client createClient(String accessKeyId, String accessKeySecret) throws Exception {
        Config config = new Config()
                // 您的 AccessKey ID
                .setAccessKeyId(accessKeyId)
                // 您的 AccessKey Secret
                .setAccessKeySecret(accessKeySecret);
        // 访问的域名
        config.endpoint = "dysmsapi.aliyuncs.com";
        return new Client(config);
    }

    /**
     * @return 返回 {@code null} 代表成功，否则返回错误信息
     */
    public static String sendSms(String accessKeyId, String accessKeySecret, String signName, String templateCode,
                                 String codeName, String code, String mobile) {
        try {
            Client client = createClient(accessKeyId, accessKeySecret);
            SendSmsRequest sendSmsRequest = new SendSmsRequest()
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setPhoneNumbers(mobile)
                    .setTemplateParam("{\"" + codeName + "\":\"" + code + "\"}");
            RuntimeOptions runtime = new RuntimeOptions();
            SendSmsResponse response = client.sendSmsWithOptions(sendSmsRequest, runtime);
            // 成功状态码
            String success = "OK";
            String responseCode = response.getBody().getCode();
            // 不成功
            if (!success.equalsIgnoreCase(responseCode)) {
                String message = response.getBody().getMessage();
                return "code: " + responseCode + ", message: " + message;
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
