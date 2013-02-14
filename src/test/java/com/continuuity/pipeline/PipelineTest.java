/*
 * Copyright 2012-2013 Continuuity,Inc. All Rights Reserved.
 */

package com.continuuity.pipeline;

import com.continuuity.internal.pipeline.PipelineFactory;
import com.google.common.reflect.TypeToken;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests simple pipelining
 */
public class PipelineTest {

  public static final class HowStage extends AbstractStage<String> {
    public HowStage() {
      super(TypeToken.of(String.class));
    }

    @Override
    public void process(String msg) {
        msg += ", how ";
        emit(msg);
    }
  }

  public static class AreStage extends AbstractStage<String> {
    public AreStage() {
      super(TypeToken.of(String.class));
    }

    @Override
    public void process(String msg) {
      msg += " are ";
      emit(msg);
    }
  }

  public static class YouStage extends AbstractStage<String> {
    public YouStage() {
      super(TypeToken.of(String.class));
    }

    @Override
    public void process(String msg) {
      msg += " you";
      emit(msg);
    }
  }

  @Test
  public void testSimplePipeline() throws Exception {
    Pipeline pipeline = PipelineFactory.newSynchronousPipeline();
    pipeline.addLast(new HowStage());
    pipeline.addLast(new AreStage());
    pipeline.addLast(new YouStage());
    pipeline.execute("Hi");
    String s = (String)pipeline.getResult();
    Assert.assertTrue(s.equals("Hi, how  are  you"));
  }



}
