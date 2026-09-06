package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationSlotStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the database transaction after a v2 provider call has produced an image.
 * Object storage is deliberately handled by the caller so a rolled back transaction
 * can remove an object that never became a visible result.
 */
@Service
public class GenerationV2SettlementService {
  private final GenerationMapper generation;
  private final GenerationV2Mapper v2;
  private final QuotaTransactionService quota;
  private final TransactionTemplate transactions;
  private final ObjectMapper json;

  public GenerationV2SettlementService(GenerationMapper generation, GenerationV2Mapper v2,
      QuotaTransactionService quota, PlatformTransactionManager transactionManager, ObjectMapper json) {
    this.generation = generation;
    this.v2 = v2;
    this.quota = quota;
    this.transactions = new TransactionTemplate(transactionManager);
    this.json = json;
  }

  /**
   * Atomically publishes one slot result and consumes exactly one unit of quota.
   * A false return means another worker/cancellation already owns the slot; the
   * caller must clean up the just-written object and must not retry the settlement.
   */
  public boolean publishSlot(String executionId, String taskId, int slotIndex,
      StoredGenerationResult result, int unitCost) {
    if (result == null || result.index() != slotIndex) {
      throw new IllegalArgumentException("generation result does not match its slot");
    }
    Boolean committed = transactions.execute(status -> {
      GenerationTaskRecord task = lockV2Task(taskId);
      var execution = v2.lockExecution(executionId);
      var slot = v2.lockResultSlot(taskId, slotIndex);
      if (execution == null || slot == null || task == null
          || !taskId.equals(execution.taskId())
          || execution.status() != GenerationExecutionStatus.GENERATING
          || slot.status() != GenerationSlotStatus.GENERATING
          || !executionId.equals(slot.activeExecutionId())) return false;
      if (unitCost != task.unitCost()
          || execution.reservedAmount() - execution.consumedAmount() - execution.releasedAmount() < unitCost) {
        throw new IllegalStateException("generation slot settlement amount is invalid");
      }
      if (generation.insertResult(result.id(), taskId, slotIndex, result.imagePath(), result.objectKey(),
          result.thumbnailObjectKey(), result.checksumSha256(), result.width(), result.height(), result.mimeType(),
          result.byteSize(), result.thumbnailWidth(), result.thumbnailHeight(), result.thumbnailByteSize(), true) != 1) {
        return false;
      }
      String consumeKey = "consume:" + taskId + ":slot:" + slotIndex;
      if (!quota.settle(task.userId(), taskId, unitCost, "CONSUME", consumeKey, executionId, slotIndex)) {
        throw new IllegalStateException("quota consume failed for generation slot");
      }
      if (v2.addExecutionConsumed(executionId, unitCost) != 1
          || v2.addTaskConsumed(taskId, unitCost) != 1
          || v2.finishResultSlot(taskId, slotIndex, "SUCCEEDED", result.id(), null, null, executionId) != 1) {
        throw new IllegalStateException("generation slot settlement state update failed");
      }
      generation.insertEvent(taskId, "task.result.succeeded", GenerationTaskStatus.GENERATING.name(),
          write(Map.of("executionId", executionId, "slotIndex", slotIndex, "resultId", result.id(),
              "consumedCost", unitCost)));
      return true;
    });
    return Boolean.TRUE.equals(committed);
  }

  /** Finish an execution only after every task slot has reached a terminal state. */
  public boolean completeExecution(String executionId, String taskId, int resultCount) {
    Boolean committed = transactions.execute(status -> {
      GenerationTaskRecord task = lockV2Task(taskId);
      var execution = v2.lockExecution(executionId);
      if (execution == null || task == null
          || !taskId.equals(execution.taskId())
          || (execution.status() != GenerationExecutionStatus.GENERATING
              && execution.status() != GenerationExecutionStatus.QUEUED)) return false;
      // Aggregate only after every slot has reached a terminal state. A failed
      // slot is independent from its siblings; release the remaining reserve
      // once, then derive the task status from authoritative slot counts.
      int succeeded = v2.countSucceededSlots(taskId);
      int failed = v2.countFailedSlots(taskId);
      if (v2.countIncompleteSlots(taskId) != failed || succeeded + failed != task.imageCount()) return false;
      int release = execution.reservedAmount() - execution.consumedAmount() - execution.releasedAmount();
      if (release < 0) throw new IllegalStateException("generation execution settlement invariant violated");
      if (release > 0 && !quota.settle(task.userId(), taskId, release, "RELEASE", "release:" + executionId,
          executionId, null)) throw new IllegalStateException("quota release failed for generation execution");
      String taskStatus = succeeded == task.imageCount() ? "SUCCEEDED" : succeeded == 0 ? "FAILED" : "PARTIALLY_SUCCEEDED";
      String executionStatus = failed == 0 ? "SUCCEEDED" : "FAILED";
      String executionError = failed == 0 ? null : "SLOT_GENERATION_FAILED";
      if (v2.finishExecution(executionId, release, executionStatus, executionError) != 1
          || v2.setTaskV2Status(taskId, taskStatus, failed == 0 ? null : "SLOT_GENERATION_FAILED",
              failed == 0 ? null : "部分图片生成失败", true) != 1) {
        throw new IllegalStateException("generation execution completion update failed");
      }
      task = generation.findTask(taskId);
      generation.insertEvent(taskId, "SUCCEEDED".equals(taskStatus) ? "task.succeeded"
          : "PARTIALLY_SUCCEEDED".equals(taskStatus) ? "task.partially_succeeded" : "task.failed", taskStatus,
          write(Map.of("executionId", executionId, "resultCount", resultCount,
              "succeededCount", succeeded, "failedCount", failed,
              "consumedCost", task.consumedCost(), "releasedCost", release)));
      return true;
    });
    return Boolean.TRUE.equals(committed);
  }

  /** Records one terminal slot failure without ending the shared execution. */
  public boolean recordSlotFailure(String executionId, String taskId, int slotIndex, String code, String message) {
    Boolean committed = transactions.execute(status -> {
      GenerationTaskRecord task = lockV2Task(taskId);
      var execution = v2.lockExecution(executionId);
      var slot = v2.lockResultSlot(taskId, slotIndex);
      if (task == null || execution == null || slot == null
          || !taskId.equals(execution.taskId())
          || (execution.status() != GenerationExecutionStatus.GENERATING
              && execution.status() != GenerationExecutionStatus.QUEUED)
          || slot.status() != GenerationSlotStatus.GENERATING
          || !executionId.equals(slot.activeExecutionId())) return false;
      if (v2.finishResultSlot(taskId, slotIndex, "FAILED", null, code, message, executionId) != 1) return false;
      generation.insertEvent(taskId, "task.slot.failed", GenerationTaskStatus.GENERATING.name(),
          write(Map.of("executionId", executionId, "slotIndex", slotIndex, "errorCode", code)));
      return true;
    });
    return Boolean.TRUE.equals(committed);
  }

  /** Mark the current slot failed and release only this execution's unconsumed reserve. */
  public boolean failExecution(String executionId, String taskId, int slotIndex, String code, String message) {
    Boolean committed = transactions.execute(status -> {
      GenerationTaskRecord task = lockV2Task(taskId);
      var execution = v2.lockExecution(executionId);
      if (execution == null || task == null
          || !taskId.equals(execution.taskId())
          || task.status() != GenerationTaskStatus.QUEUED
              && task.status() != GenerationTaskStatus.GENERATING
          || execution.status() != GenerationExecutionStatus.QUEUED
              && execution.status() != GenerationExecutionStatus.GENERATING) return false;
      if (slotIndex >= 0) {
        var slot = v2.lockResultSlot(taskId, slotIndex);
        if (slot == null || slot.status() != GenerationSlotStatus.GENERATING
            || !executionId.equals(slot.activeExecutionId())
            || v2.finishResultSlot(taskId, slotIndex, "FAILED", null, code, message, executionId) != 1) return false;
      }
      int release = execution.reservedAmount() - execution.consumedAmount() - execution.releasedAmount();
      if (release < 0) throw new IllegalStateException("generation execution settlement invariant violated");
      if (release > 0 && !quota.settle(task.userId(), taskId, release, "RELEASE", "release:" + executionId,
          executionId, null)) {
        throw new IllegalStateException("quota release failed for generation execution");
      }
      if (v2.finishExecution(executionId, release, "FAILED", code) != 1) {
        throw new IllegalStateException("generation execution failure update failed");
      }
      String taskStatus = task.consumedCost() > 0 ? "PARTIALLY_SUCCEEDED" : "FAILED";
      if (v2.setTaskV2Status(taskId, taskStatus, code, message, true) != 1) {
        throw new IllegalStateException("generation task failure update failed");
      }
      generation.insertEvent(taskId, "task.slot.failed", GenerationTaskStatus.GENERATING.name(),
          write(Map.of("executionId", executionId, "slotIndex", slotIndex, "errorCode", code,
              "releasedCost", release)));
      generation.insertEvent(taskId, "task.execution.released", GenerationTaskStatus.GENERATING.name(),
          write(Map.of("executionId", executionId, "releasedCost", release)));
      String terminalType = "PARTIALLY_SUCCEEDED".equals(taskStatus)
          ? "task.partially_succeeded" : "task.failed";
      generation.insertEvent(taskId, terminalType, taskStatus,
          write(Map.of("executionId", executionId, "consumedCost", task.consumedCost(), "releasedCost", release)));
      return true;
    });
    return Boolean.TRUE.equals(committed);
  }

  private GenerationTaskRecord lockV2Task(String taskId) {
    GenerationTaskRecord task = generation.findTask(taskId);
    return task == null ? null : v2.lockTask(task.userId(), taskId);
  }

  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception error) { throw new IllegalStateException("failed to serialize generation event", error); }
  }
}
