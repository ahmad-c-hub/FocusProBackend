package com.example.focuspro.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtp(String to, String otp) throws MessagingException {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, "utf-8");
        helper.setTo(to);
        helper.setSubject("Your LockedIn verification code");
        helper.setText(buildHtml(otp), true);
        mailSender.send(msg);
    }

    private String buildHtml(String otp) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
            </head>
            <body style="margin:0;padding:0;background:#F8F9FA;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0"
                     style="background:#F8F9FA;padding:40px 16px;">
                <tr><td align="center">
                  <table width="480" cellpadding="0" cellspacing="0"
                         style="background:#FFFFFF;border-radius:16px;
                                box-shadow:0 4px 20px rgba(0,0,0,0.07);
                                overflow:hidden;max-width:480px;">
                    <tr>
                      <td align="center" style="background:#012D1D;padding:28px 40px;">
                        <span style="font-size:30px;font-weight:900;color:#FFFFFF;
                                     letter-spacing:-0.5px;">Locked</span><span
                              style="font-size:30px;font-weight:900;color:#A0F4C8;
                                     letter-spacing:-0.5px;">In</span>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:40px;">
                        <p style="margin:0 0 8px;font-size:22px;font-weight:700;color:#012D1D;">
                          Verify your email
                        </p>
                        <p style="margin:0 0 32px;font-size:15px;color:#414844;line-height:1.6;">
                          Use the code below to complete your LockedIn sign-up.
                          It expires in <strong>10 minutes</strong>.
                        </p>
                        <div style="text-align:center;margin-bottom:32px;">
                          <div style="display:inline-block;background:#F3F4F5;
                                      border-radius:14px;padding:20px 44px;">
                            <span style="font-size:44px;font-weight:900;letter-spacing:14px;
                                         color:#012D1D;font-family:'Courier New',Courier,monospace;">
                              %s
                            </span>
                          </div>
                        </div>
                        <p style="margin:0;font-size:13px;color:#717973;text-align:center;">
                          If you didn't request this code, you can safely ignore this email.
                        </p>
                      </td>
                    </tr>
                    <tr>
                      <td style="background:#F3F4F5;padding:20px 40px;text-align:center;">
                        <p style="margin:0;font-size:12px;color:#717973;">
                          &copy; 2025 LockedIn &mdash; All rights reserved.
                        </p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(otp);
    }
}
