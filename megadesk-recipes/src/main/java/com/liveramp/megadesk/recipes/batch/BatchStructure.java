package com.liveramp.megadesk.recipes.batch;

import com.google.common.collect.ImmutableList;
import com.liveramp.megadesk.core.transaction.Executor;
import com.liveramp.megadesk.recipes.pipeline.DriverFactory;

public class BatchStructure<VALUE> {

  private final Batch<VALUE> batch;
  private Executor executor;

  public BatchStructure(Batch<VALUE> batch, Executor executor) {
    this.batch = batch;
    this.executor = executor;
  }

  public static <VALUE> BatchStructure<VALUE> getByName(String name, DriverFactory factory, Executor executor) {
    return new BatchStructure<VALUE>(
        new Batch<VALUE>(factory.<ImmutableList>get(name + "-input"), factory.<ImmutableList>get(name + "-output")), executor);
  }

  public void append(VALUE value) {
    try {
      executor.execute(batch.getAppendTransaction(value));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public ImmutableList<VALUE> readBatch() {
    try {
      executor.execute(batch.getTransferTransaction());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return batch.getOutput().persistence().read();
  }

  public void popBatch(){
    try {
      executor.execute(batch.getEraseTransaction());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public boolean batchAvailable(){
    try {
      return executor.execute(batch.getCheckForDataTransaction());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}