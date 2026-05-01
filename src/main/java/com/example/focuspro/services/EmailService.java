package com.example.focuspro.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class EmailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendOtp(String to, String otp) throws IOException, InterruptedException {
        String html = buildHtml(otp);

        // Escape the HTML so it can be safely embedded as a JSON string value
        String escapedHtml = html
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n")
                .replace("\t", "\\t");

        String jsonBody = "{"
                + "\"sender\":{\"name\":\"LockedIn\",\"email\":\"abdelrahmansamad65@gmail.com\"},"
                + "\"to\":[{\"email\":\"" + to + "\"}],"
                + "\"subject\":\"Your LockedIn verification code\","
                + "\"htmlContent\":\"" + escapedHtml + "\""
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Brevo API error " + response.statusCode() + ": " + response.body());
        }
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
                                         color:#012D1D;font-family:Courier New,Courier,monospace;">
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
