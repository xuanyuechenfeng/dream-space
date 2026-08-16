package com.dreamspace.worker.generation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!real")
public class DeterministicMockContentModerator implements ContentModerator {
  @Override public Decision moderateInput(WorkerTaskSnapshot task) {
    return task.prompt().contains("[mock-reject-input]") ? new Decision(false, "MOCK_INPUT_REJECTED") : new Decision(true, null);
  }
  @Override public Decision moderateOutput(WorkerTaskSnapshot task, ProviderImage image) {
    return task.prompt().contains("[mock-reject-output]") ? new Decision(false, "MOCK_OUTPUT_REJECTED") : new Decision(true, null);
  }
}
