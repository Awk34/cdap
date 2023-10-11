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

package io.cdap.cdap.internal.operation;

import io.cdap.cdap.proto.operationrun.OperationError;
import io.cdap.cdap.proto.operationrun.OperationMeta;

/**
 * Publishes operation state messages.
 */
public interface OperationStatePublisher {

  /**
   * Publishes message with the current metadata. The operation status should be RUNNING
   *
   * @param meta Current metadata for the operation.
   */
  void publishMetaUpdate(OperationMeta meta);

  /**
   * Publishes the current operation status as RUNNING.
   */
  void publishRunning();

  /**
   * Publishes the current operation status as FAILED.
   */
  void publishFailed(OperationError error);

  /**
   * Publishes the current operation status as SUCCEEDED.
   */
  void publishSuccess();

  /**
   * Publishes the current operation status as STOPPED.
   */
  void publishStopped();
}
