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

package io.cdap.cdap.internal.app.sourcecontrol;

import io.cdap.cdap.proto.sourcecontrol.RepositoryConfig;
import io.cdap.cdap.sourcecontrol.CommitMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Information required by {@link SourceControlOperationRunner}
 * to push applications' specification to linked repository.
 */
public class PushApplicationsContext {
  private final List<AppDetailsToPush> appsToPush;
  private final CommitMeta commitDetails;
  private final RepositoryConfig repositoryConfig;

  public PushApplicationsContext(List<AppDetailsToPush> appsToPush,
                                 CommitMeta commitDetails,
                                 RepositoryConfig repositoryConfig) {
    this.appsToPush = Collections.unmodifiableList(new ArrayList<>(appsToPush));
    this.commitDetails = commitDetails;
    this.repositoryConfig = repositoryConfig;
  }

  public CommitMeta getCommitDetails() {
    return commitDetails;
  }

  public RepositoryConfig getRepositoryConfig() {
    return repositoryConfig;
  }

  public List<AppDetailsToPush> getAppsToPush() {
    return appsToPush;
  }
}
