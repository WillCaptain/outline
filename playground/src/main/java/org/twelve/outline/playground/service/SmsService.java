package org.twelve.outline.playground.service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Sends OTP codes via Alibaba Cloud SMS (阿里云短信).
 *
 * Configuration (application.properties):
 *   aliyun.sms.access-key-id      — AccessKey ID from RAM console
 *   aliyun.sms.access-key-secret  — AccessKey Secret
 *   aliyun.sms.sign-name          — SMS signature (审核通过的签名名称)
 *   aliyun.sms.template-code      — Template code (SMS_xxxxxxxxx), body must contain ${code}
 *   aliyun.sms.enabled            — true = call Aliyun; false = dev mock (code shown in UI)
 */
@Service
public class SmsService {

    @Value("${aliyun.sms.access-key-id}")       private String accessKeyId;
    @Value("${aliyun.sms.access-key-secret}")   private String accessKeySecret;
    @Value("${aliyun.sms.sign-name}")           private String signName;
    @Value("${aliyun.sms.template-code}")       private String templateCode;
    @Value("${aliyun.sms.enabled:false}")       private boolean enabled;

    private Client client;

    @PostConstruct
    public void init() {
        if (!enabled) return;
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);
            config.endpoint = "dysmsapi.aliyuncs.com";
            client = new Client(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise Aliyun SMS client", e);
        }
    }

    /** True when real Aliyun SMS is active; false in dev/mock mode. */
    public boolean isEnabled() { return enabled; }

    /**
     * Dispatches the OTP to the given phone number.
     * In dev mode (aliyun.sms.enabled=false) only logs the code to the server console.
     */
    public void send(String phone, String code) {
        if (!enabled) {
            System.out.printf("[SMS-DEV] phone=%s  OTP=%s  (set aliyun.sms.enabled=true for real SMS)%n",
                    phone, code);
            return;
        }
        try {
            SendSmsRequest req = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            var resp = client.sendSms(req);
            if (!"OK".equals(resp.body.code)) {
                System.err.printf("[SMS-ERROR] phone=%s  result=%s  message=%s%n",
                        phone, resp.body.code, resp.body.message);
            } else {
                System.out.printf("[SMS-OK] phone=%s  requestId=%s%n", phone, resp.body.requestId);
            }
        } catch (Exception e) {
            System.err.printf("[SMS-EXCEPTION] phone=%s  error=%s%n", phone, e.getMessage());
        }
    }
}
