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

package io.cdap.cdap.internal.app.worker.sidecar;

import com.google.gson.annotations.SerializedName;

/**
 * Serializable GCP Token Response.
 */
public class GCPTokenResponse {
  @SerializedName("access_token")
  public final String accessToken;
  @SerializedName("expires_in")
  public final int expiresIn;
  @SerializedName("token_type")
  public final String tokenType;

  public GCPTokenResponse(String accessToken, int expiresIn, String tokenType) {
    this.accessToken = accessToken;
    this.expiresIn = expiresIn;
    this.tokenType = tokenType;
  }

}
