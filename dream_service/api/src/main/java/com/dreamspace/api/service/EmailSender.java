package com.dreamspace.api.service;

public interface EmailSender {
  void sendRegistrationCode(String email, String code);
}
