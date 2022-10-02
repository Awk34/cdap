/*
 * Copyright © 2022 Cask Data, Inc.
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

package io.cdap.cdap.proto.artifact;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Change Summary returned in the app response.
 */
public class ChangeSummaryResponse {
  @Nullable
  private final String description;
  @Nullable
  private final String author;
  private final long creationTimeMillis;

  public ChangeSummaryResponse(@Nullable String description, @Nullable String author, long creationTimeMillis) {
    this.description = description;
    this.author = author;
    this.creationTimeMillis = creationTimeMillis;
  }

  /**
   * @return The creation time of the update in millis seconds.
   */
  public long getCreationTimeMillis() {
    return creationTimeMillis;
  }

  /**
   * @return Author(user name) of the change.
   */
  @Nullable
  public String getAuthor() {
    return author;
  }

  /**
   * @return Description of the change.
   */
  @Nullable
  public String getDescription() {
    return description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ChangeSummaryResponse that = (ChangeSummaryResponse) o;

    return creationTimeMillis == that.creationTimeMillis &&
      Objects.equals(description, that.description) &&
      Objects.equals(author, that.author);
  }

  @Override
  public int hashCode() {
    return Objects.hash(description, author, creationTimeMillis);
  }

  @Override
  public String toString() {
    return "ChangeSummaryResponse{" +
      "description='" + description + '\'' +
      ", author='" + author + '\'' +
      ", creationTimeMillis=" + creationTimeMillis +
      '}';
  }
}
