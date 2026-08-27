package com.dreamspace.api.service;

import com.dreamspace.api.common.ApiException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailSender implements EmailSender {
  private final Supplier<JavaMailSender> sender;
  private final String from;

  public SmtpEmailSender(org.springframework.beans.factory.ObjectProvider<JavaMailSender> provider,
      com.dreamspace.common.persistence.config.DreamSpaceProperties properties) {
    this.sender = provider::getIfAvailable;
    this.from = properties.auth().emailFrom();
  }

  @Override
  public void sendRegistrationCode(String email, String code) {
    JavaMailSender mailSender = sender.get();
    if (mailSender == null || from == null || from.isBlank()) {
      throw unavailable();
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(email);
      message.setSubject("造梦空间注册验证码");
      message.setText("你的造梦空间注册验证码是 " + code + "，10 分钟内有效。如非本人操作请忽略此邮件。");
      mailSender.send(message);
    } catch (RuntimeException error) {
      throw unavailable();
    }
  }

  private static ApiException unavailable() {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
        "AUTH_EMAIL_PROVIDER_UNAVAILABLE", "邮箱验证码服务暂不可用");
  }
}
