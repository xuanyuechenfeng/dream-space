package com.dreamspace.worker.generation;

public interface GenerationStage<I, O> {
  String name();
  O execute(I input, StageContext context);
}
