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

package io.cdap.cdap.common.http;

import com.google.inject.Inject;
import io.cdap.cdap.api.auditlogging.AuditLogWriter;
import io.cdap.cdap.api.metrics.MetricsCollectionService;
import io.cdap.cdap.common.conf.CConfiguration;

/**
 * Factory to create {@link CommonNettyHttpServiceBuilder} using {@link CConfiguration} and {@link
 * MetricsCollectionService}
 */
public class CommonNettyHttpServiceFactory {

  private final CConfiguration cConf;
  private final MetricsCollectionService metricsCollectionService;
  private final AuditLogWriter auditLogWriter;

  @Inject
  public CommonNettyHttpServiceFactory(CConfiguration cConf,
                                       MetricsCollectionService metricsCollectionService,
                                        AuditLogWriter auditLogWriter) {
    this.cConf = cConf;
    this.metricsCollectionService = metricsCollectionService;
    this.auditLogWriter = auditLogWriter;
  }

  /**
   * Creates a {@link CommonNettyHttpServiceBuilder} with serviceName
   *
   * @param serviceName Name of the service passed to {@link CommonNettyHttpServiceBuilder}
   * @return {@link CommonNettyHttpServiceBuilder}
   */
  public CommonNettyHttpServiceBuilder builder(String serviceName) {
    return new CommonNettyHttpServiceBuilder(cConf, serviceName, metricsCollectionService, auditLogWriter);
  }
}
