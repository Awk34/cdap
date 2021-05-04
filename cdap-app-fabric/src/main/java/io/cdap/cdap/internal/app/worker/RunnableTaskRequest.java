/*
 * Copyright © 2021 Cask Data, Inc.
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

package io.cdap.cdap.internal.app.worker;

import io.cdap.cdap.common.id.Id;

import javax.annotation.Nullable;

/**
 * Request for launching a runnable task.
 */
public class RunnableTaskRequest {
  @Nullable
  Id.Artifact artifactId;

  String className;
  String param;

  public RunnableTaskRequest(Id.Artifact artifactId, String className, String param) {
    this.artifactId = artifactId;
    this.className = className;
    this.param = param;
  }

  public Id.Artifact getArtifactId() {
    return artifactId;
  }

  public String getClassName() {
    return className;
  }

  public String getParam() {
    return param;
  }
}
