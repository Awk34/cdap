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

package io.cdap.cdap.messaging.store.spanner;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.InstanceId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import io.cdap.cdap.storage.spanner.SpannerStructuredTableAdmin;
import io.cdap.cdap.storage.spanner.SpannerTransactionRunner;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

public class SpannerUtil {

  public static String instanceId = "spanner-instance";

  public static String databaseId = "test-692-spanner";

  public static DatabaseClient getSpannerDbClient() {

    Spanner spanner =
        SpannerOptions.newBuilder().setProjectId("ardekani-cdf-sandbox2").build().getService();
    DatabaseId db = DatabaseId.of("ardekani-cdf-sandbox2", instanceId, databaseId);

    DatabaseClient dbClient = spanner.getDatabaseClient(db);

    return dbClient;
  }

  public static DatabaseAdminClient getSpannerDbAdminClient() {

    Spanner spanner =
        SpannerOptions.newBuilder().setProjectId("ardekani-cdf-sandbox2").build().getService();
    DatabaseId db = DatabaseId.of("ardekani-cdf-sandbox2", instanceId, databaseId);

    return spanner.getDatabaseAdminClient();
  }
}
