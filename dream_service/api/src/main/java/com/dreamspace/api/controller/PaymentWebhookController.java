package com.dreamspace.api.controller;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.service.BillingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/billing")
public class PaymentWebhookController {
  private final BillingService billing;
  private final String webhookToken;
  public PaymentWebhookController(BillingService billing, @Value("${dream-space.billing.webhook-token:}") String webhookToken) { this.billing = billing; this.webhookToken = webhookToken; }
  @PostMapping("/webhooks/{provider}") BillingService.OrderView webhook(@PathVariable String provider, @RequestHeader(value = "X-Dreamspace-Webhook-Token", required = false) String token, @RequestBody(required = false) WebhookRequest body) {
    if (webhookToken.isBlank() || token == null || !java.security.MessageDigest.isEqual(webhookToken.getBytes(java.nio.charset.StandardCharsets.UTF_8), token.getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw new ApiException(HttpStatus.UNAUTHORIZED, "PAYMENT_WEBHOOK_UNAUTHORIZED", "支付回调未授权");
    if (body == null || provider == null || provider.isBlank() || body.orderNo() == null || body.transactionId() == null || body.eventId() == null || body.currency() == null)
      throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_WEBHOOK_INVALID", "支付回调参数无效");
    return billing.paymentWebhook(provider, body.orderNo(), body.transactionId(), body.eventId(), body.amountMinor(), body.currency(), body.signatureVerified());
  }
  public record WebhookRequest(String orderNo, String transactionId, String eventId, long amountMinor, String currency, boolean signatureVerified) {}
}
