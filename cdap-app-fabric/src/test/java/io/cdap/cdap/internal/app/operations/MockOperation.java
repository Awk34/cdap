/*
 * Copyright © 2023 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.internal.app.operations;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.cdap.cdap.common.operation.LongRunningOperation;
import io.cdap.cdap.proto.operationrun.OperationError;
import io.cdap.cdap.proto.operationrun.OperationMeta;
import java.lang.reflect.Type;
import java.util.function.Consumer;

public class MockOperation implements LongRunningOperation<MockOperationRequest> {

  @Override
  public Type getRequestType() {
    return MockOperationRequest.class;
  }

  @Override
  public ListenableFuture<OperationError> run(MockOperationRequest request,
      Consumer<OperationMeta> updateMetadata) throws Exception {
    SettableFuture<OperationError> error = SettableFuture.create();
    if (request.isThrowException()) {
      throw new RuntimeException();
    }
    if (request.getReturnError() != null) {
      error.set(request.getReturnError());
      return error;
    }
    while (request.getCount().incrementAndGet() <= request.getMaxCount()) {
      Thread.sleep(request.getWaitTimeInMillis());
    }
    error.set(null);
    return error;
  }
}
