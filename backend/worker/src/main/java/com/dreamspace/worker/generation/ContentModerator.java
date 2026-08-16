package com.dreamspace.worker.generation;

public interface ContentModerator {
  Decision moderateInput(WorkerTaskSnapshot task);
  Decision moderateOutput(WorkerTaskSnapshot task, ProviderImage image);
  record Decision(boolean approved, String code) {}
}
