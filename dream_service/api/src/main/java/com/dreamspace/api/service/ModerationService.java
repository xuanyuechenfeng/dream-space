package com.dreamspace.api.service;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.common.AdminPrincipal;
import com.dreamspace.common.persistence.moderation.ModerationAppealRecord;
import com.dreamspace.common.persistence.moderation.ModerationAuditEventRecord;
import com.dreamspace.common.persistence.moderation.ModerationMapper;
import com.dreamspace.common.persistence.moderation.ModerationReviewCaseRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {
  private final ModerationMapper mapper;
  private final ObjectMapper json;

  public ModerationService(ModerationMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  public record UserCase(String id, String taskId, String stage, String status, String reasonCode,
      Instant createdAt, Instant resolvedAt, Appeal appeal) {}
  public record Appeal(String id, String reason, String status, Instant createdAt, Instant resolvedAt) {}
  public record AdminPage(List<AdminCase> items, long total, int page, int pageSize) {}
  public record AdminCase(String id, String taskId, String userId, String stage, String status,
      String reasonCode, String model, String modelVersion, Instant createdAt, Instant resolvedAt) {}
  public record AdminDetail(AdminCase reviewCase, Appeal appeal, List<Audit> audit) {}
  public record Audit(String id, String actorId, String actorType, String action,
      Object before, Object after, Instant createdAt) {}

  public List<UserCase> userCases(String userId) {
    return mapper.listOwned(userId, 50).stream().map(this::userCase).toList();
  }

  @Transactional
  public UserCase appeal(String userId, String caseId, String reason) {
    String text = bounded(reason, 2000);
    ModerationReviewCaseRecord review = owned(caseId, userId);
    if (!"REJECTED".equals(review.status())) throw bad("MODERATION_APPEAL_INVALID", "当前审核状态不能申诉");
    if (mapper.findAppeal(review.id()) != null) throw bad("MODERATION_APPEAL_EXISTS", "该审核案件已经提交申诉");
    mapper.insertAppeal(UUID.randomUUID().toString(), review.id(), userId, text);
    if (mapper.markAppealed(review.id(), userId) != 1) throw bad("MODERATION_APPEAL_CONFLICT", "审核案件状态已变化");
    audit(review.id(), userId, "USER", "APPEAL_SUBMITTED", review, mapper.findCase(review.id()));
    return userCase(mapper.findCase(review.id()));
  }

  public AdminPage list(String status, int page, int pageSize) {
    if (page < 1 || page > 1_000_000 || pageSize < 1 || pageSize > 100) throw bad("VALIDATION_ERROR", "分页参数无效");
    String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    if (normalized != null && !List.of("PENDING", "REJECTED", "APPEALED", "APPROVED").contains(normalized))
      throw bad("MODERATION_STATUS_INVALID", "审核状态无效");
    int offset = (page - 1) * pageSize;
    return new AdminPage(mapper.listCases(normalized, pageSize, offset).stream().map(this::adminCase).toList(),
        mapper.countCases(normalized), page, pageSize);
  }

  public AdminDetail detail(String caseId) {
    ModerationReviewCaseRecord review = required(caseId);
    ModerationAppealRecord appeal = mapper.findAppeal(review.id());
    return new AdminDetail(adminCase(review), appeal == null ? null : appeal(appeal),
        mapper.listAudit(review.id()).stream().map(this::audit).toList());
  }

  @Transactional
  public AdminDetail resolve(AdminPrincipal principal, String caseId, String outcome, String note) {
    String normalized = outcome == null ? "" : outcome.trim().toUpperCase(Locale.ROOT);
    if (!List.of("APPROVED", "REJECTED").contains(normalized)) throw bad("MODERATION_OUTCOME_INVALID", "审核结论无效");
    ModerationReviewCaseRecord before = required(caseId);
    if (mapper.resolveCase(before.id(), before.version(), normalized) != 1)
      throw bad("MODERATION_CONFLICT", "审核案件已被其他管理员处理");
    mapper.resolveAppeal(before.id(), normalized);
    ModerationReviewCaseRecord after = mapper.findCase(before.id());
    audit(before.id(), principal.id(), "ADMIN", "CASE_RESOLVED", before,
        java.util.Map.of("case", after, "note", boundedOptional(note)));
    return detail(after.id());
  }

  private ModerationReviewCaseRecord owned(String id, String userId) {
    ModerationReviewCaseRecord value = mapper.findOwnedCase(requiredId(id), userId);
    if (value == null) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "审核案件不存在");
    return value;
  }

  private ModerationReviewCaseRecord required(String id) {
    ModerationReviewCaseRecord value = mapper.findCase(requiredId(id));
    if (value == null) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "审核案件不存在");
    return value;
  }

  private void audit(String caseId, String actorId, String actorType, String action, Object before, Object after) {
    try {
      mapper.insertAudit(UUID.randomUUID().toString(), caseId, actorId, actorType, action,
          json.writeValueAsString(before), json.writeValueAsString(after));
    } catch (Exception error) {
      throw new IllegalStateException("moderation audit serialization failed", error);
    }
  }

  private UserCase userCase(ModerationReviewCaseRecord value) {
    ModerationAppealRecord appeal = mapper.findAppeal(value.id());
    return new UserCase(value.id(), value.taskId(), value.stage(), value.status(), value.reasonCode(),
        value.createdAt(), value.resolvedAt(), appeal == null ? null : appeal(appeal));
  }

  private AdminCase adminCase(ModerationReviewCaseRecord value) {
    return new AdminCase(value.id(), value.taskId(), value.userId(), value.stage(), value.status(),
        value.reasonCode(), value.model(), value.modelVersion(), value.createdAt(), value.resolvedAt());
  }

  private Appeal appeal(ModerationAppealRecord value) {
    return new Appeal(value.id(), value.reason(), value.status(), value.createdAt(), value.resolvedAt());
  }

  private Audit audit(ModerationAuditEventRecord value) {
    return new Audit(value.id(), value.actorId(), value.actorType(), value.action(), value.beforeJson(),
        value.afterJson(), value.createdAt());
  }

  private static String requiredId(String id) {
    if (id == null || id.isBlank() || id.length() > 100) throw bad("ID_INVALID", "ID 无效");
    return id.trim();
  }

  private static String bounded(String value, int max) {
    if (value == null || value.trim().isEmpty()) throw bad("MODERATION_APPEAL_REASON_REQUIRED", "申诉理由不能为空");
    String normalized = value.trim();
    if (normalized.length() > max) throw bad("VALIDATION_ERROR", "申诉理由过长");
    return normalized;
  }

  private static String boundedOptional(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.length() > 2000 ? normalized.substring(0, 2000) : normalized;
  }

  private static ApiException bad(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }
}
