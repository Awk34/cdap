/*
 * Copyright © 2016 Cask Data, Inc.
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
package io.cdap.cdap.messaging.spanner;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.Credentials;
import com.google.cloud.ByteArray;
import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.spanner.admin.database.v1.UpdateDatabaseDdlMetadata;
import io.cdap.cdap.api.dataset.lib.CloseableIterator;
import io.cdap.cdap.api.messaging.TopicAlreadyExistsException;
import io.cdap.cdap.api.messaging.TopicNotFoundException;
import io.cdap.cdap.messaging.spi.MessageFetchRequest;
import io.cdap.cdap.messaging.spi.MessagingService;
import io.cdap.cdap.messaging.spi.MessagingServiceContext;
import io.cdap.cdap.messaging.spi.RawMessage;
import io.cdap.cdap.messaging.spi.RollbackDetail;
import io.cdap.cdap.messaging.spi.StoreRequest;
import io.cdap.cdap.messaging.spi.TopicMetadata;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.proto.id.TopicId;
import io.cdap.cdap.security.spi.authorization.UnauthorizedException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpannerMessagingService implements MessagingService {

  private static final Logger LOG = LoggerFactory.getLogger(SpannerMessagingService.class);
  public static final String PAYLOAD_FIELD = "payload";
  public static final String PUBLISH_TS_FIELD = "publish_ts";
  public static final String PAYLOAD_SEQUENCE_ID = "payload_sequence_id";
  public static final String SEQUENCE_ID_FIELD = "sequence_id";
  public static final String TOPIC_METADATA_TABLE = "topic_metadata";
  public static final String TOPIC_ID_FIELD = "topic_id";
  public static final String PROPERTIES_FIELD = "properties";
  public static final String NAMESPACE_FIELD = "namespace";
  public static final String TOPIC_TABLE_PREFIX = "messaging";

  private DatabaseClient client;

  private DatabaseAdminClient adminClient;

  private String instanceId;

  private String databaseId;

  private int publishBatchSize;

  private int publishBatchTimeoutMillis;

  private int publishDelayMillis;

  private final ConcurrentLinkedQueue<StoreRequest> batch = new ConcurrentLinkedQueue<>();

  @Override
  public void initialize(MessagingServiceContext context) throws IOException {
    Map<String, String> cConf = context.getProperties();
    this.databaseId = SpannerUtil.getDatabaseID(cConf);
    this.instanceId = SpannerUtil.getInstanceID(cConf);
    String projectID = SpannerUtil.getProjectID(cConf);
    Credentials credentials = SpannerUtil.getCredentials(cConf);

    this.publishBatchSize = Integer.parseInt(cConf.get(SpannerUtil.PUBLISH_BATCH_SIZE));
    this.publishBatchTimeoutMillis = Integer.parseInt(
        cConf.get(SpannerUtil.PUBLISH_BATCH_TIMEOUT_MILLIS));
    this.publishDelayMillis = Integer.parseInt(cConf.get(SpannerUtil.PUBLISH_BATCH_POLL_MILLIS));

    Spanner spanner = SpannerUtil.getSpannerService(projectID, credentials);
    this.client = SpannerUtil.getSpannerDbClient(projectID, instanceId, databaseId, spanner);
    this.adminClient = SpannerUtil.getSpannerDbAdminClient(spanner);
    LOG.info("Spanner messaging service started.");

    createTopics(context.getSystemTopics());
  }

  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void createTopic(TopicMetadata topicMetadata)
      throws TopicAlreadyExistsException, IOException, UnauthorizedException {
    createTopics(ImmutableList.of(topicMetadata));
    LOG.trace("Created topic : {}", topicMetadata.getTopicId().getTopic());
  }

  private void createTopics(List<TopicMetadata> topics) throws IOException {
    if (topics.isEmpty()) {
      LOG.info("No topics were created initially.");
      return;
    }
    List<String> ddlStatements = new ArrayList<>();
    ddlStatements.add(getCreateTopicMetadataDDLStatement());
    for (TopicMetadata topic : topics) {
      LOG.info("Creating topic : {}", topic.getTopicId().getTopic());
      ddlStatements.add(getCreateTopicDDLStatement(topic.getTopicId()));
    }
    executeCreateDDLStatements(ddlStatements);
    updateTopicMetadataTable(topics);
    LOG.info("Created all system topics.");
  }

  private String getCreateTopicMetadataDDLStatement() {
    return String.format(
        "CREATE TABLE IF NOT EXISTS %s ( %s STRING(MAX) NOT NULL, %s STRING(MAX), %s JSON ) PRIMARY KEY(%s)",
        TOPIC_METADATA_TABLE, TOPIC_ID_FIELD, NAMESPACE_FIELD, PROPERTIES_FIELD, TOPIC_ID_FIELD);
  }

  /**
   * <p>Key features of the schema:
   *     <ul>
   *         <li>**`sequence_id`:** An arbitrary unique number (0-99) assigned by the publisher.
   *             This helps to distribute writes across the Spanner cluster (avoiding hotspots)
   *             and allows for efficient batching of up to 100 messages per transaction.
   *             The optimal range for `sequence_id` may need to be adjusted based on performance
   *             testing. Smaller ranges generally lead to faster reads but may increase the risk of hotspots.
   *         </li>
   *         <li>**`payload_sequence_id`:** Used to identify message chunks when a message is
   *             larger than 10MB and needs to be split across multiple rows.
   *         </li>
   *         <li>**`publish_ts`:** The commit timestamp obtained from the TrueTime API.
   *             Guaranteed to be monotonically increasing and unique across transactions
   *             that modify the same fields.
   *         </li>
   *         <li>**`payload`:** The message body.</li>
   *         <li>Message durability is currently set to 7 days. This means that Messaging service
   *             allows consumers to fetch messages as old as 7 days.</li>
   *     </ul>
   * </p>
   */
  private String getCreateTopicDDLStatement(TopicId topicId) {
    return String.format("CREATE TABLE IF NOT EXISTS %s ( %s INT64, %s INT64, %s"
            + " TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true), %s BYTES(MAX) )"
            + " PRIMARY KEY (%s, %s, %s), ROW DELETION POLICY" + " (OLDER_THAN(%s, INTERVAL 7 DAY))",
        getTableName(topicId), SEQUENCE_ID_FIELD, PAYLOAD_SEQUENCE_ID, PUBLISH_TS_FIELD,
        PAYLOAD_FIELD, SEQUENCE_ID_FIELD, PAYLOAD_SEQUENCE_ID, PUBLISH_TS_FIELD, PUBLISH_TS_FIELD);
  }

  private void updateTopicMetadataTable(List<TopicMetadata> topics) throws IOException {
    List<Mutation> mutations = new ArrayList<>();
    for (TopicMetadata topic : topics) {
      Gson gson = new Gson();
      String jsonString = gson.toJson(topic.getProperties());
      Mutation mutation = Mutation.newInsertOrUpdateBuilder(TOPIC_METADATA_TABLE)
          .set(TOPIC_ID_FIELD).to(getTableName(topic.getTopicId())).set(PROPERTIES_FIELD)
          .to(Value.json(jsonString)).set(NAMESPACE_FIELD).to(topic.getTopicId().getNamespace())
          .build();
      mutations.add(mutation);
    }
    try {
      client.write(mutations);
    } catch (SpannerException e) {
      throw new IOException(e);
    }
  }

  private void executeCreateDDLStatements(List<String> ddlStatements) throws IOException {
    try {
      OperationFuture<Void, UpdateDatabaseDdlMetadata> future = adminClient.updateDatabaseDdl(
          this.instanceId, this.databaseId, ddlStatements, null);
      future.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
  }

  public static String getTableName(TopicId topicId) {
    return String.join("_", TOPIC_TABLE_PREFIX, topicId.getNamespace(), topicId.getTopic());
  }

  @Override
  public void updateTopic(TopicMetadata topicMetadata)
      throws TopicNotFoundException, IOException, UnauthorizedException {
    updateTopicMetadataTable(ImmutableList.of(topicMetadata));
  }

  @Override
  public void deleteTopic(TopicId topicId)
      throws TopicNotFoundException, IOException, UnauthorizedException {
    try {
      String topicTableName = getTableName(topicId);
      String deleteTopicTableSQL = String.format("DROP TABLE IF EXISTS %s", topicTableName);
      OperationFuture<Void, UpdateDatabaseDdlMetadata> future = adminClient.updateDatabaseDdl(
          this.instanceId, this.databaseId, Collections.singleton(deleteTopicTableSQL), null);
      future.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }

    Mutation mutation = Mutation.delete(TOPIC_METADATA_TABLE, Key.of(topicId.getTopic()));
    try {
      client.write(Collections.singletonList(mutation));
    } catch (SpannerException e) {
      throw new IOException(e);
    }
  }

  @Override
  public Map<String, String> getTopicMetadataProperties(TopicId topicId)
      throws TopicNotFoundException, IOException, UnauthorizedException {

    try (ResultSet resultSet = client.singleUse()
        .read(TOPIC_METADATA_TABLE, KeySet.singleKey(Key.of(getTableName(topicId))),
            Collections.singletonList(PROPERTIES_FIELD))) {
      if (resultSet.next()) {
        String propertiesJson = resultSet.getJson(PROPERTIES_FIELD);
        Gson gson = new Gson();
        return gson.fromJson(propertiesJson, new TypeToken<Map<String, String>>() {
        }.getType());
      } else {
        throw new TopicNotFoundException(topicId.getNamespace(), topicId.getTopic());
      }
    }
  }

  @Override
  public List<TopicId> listTopics(NamespaceId namespaceId)
      throws IOException, UnauthorizedException {

    List<TopicId> topics = new ArrayList<>();
    String namespace = namespaceId.getNamespace();
    String topicSQL = String.format("SELECT %s FROM %s WHERE %s = '%s'", TOPIC_ID_FIELD,
        TOPIC_METADATA_TABLE, NAMESPACE_FIELD, namespace);

    try (ResultSet resultSet = client.singleUse().executeQuery(Statement.of(topicSQL))) {
      while (resultSet.next()) {
        String topicId = resultSet.getString(TOPIC_ID_FIELD);
        topics.add(new TopicId(namespace, topicId));
      }
    }
    return ImmutableList.copyOf(topics);
  }

  /**
   * Please refer {@link #getCreateTopicDDLStatement(TopicId)} for schema details. Following table
   * shows how messages would be persisted in the topic tables.
   *
   * <pre>
   * TXN     sequence_id  payload_sequence_id  publish_ts  payload
   * TXN1    0            0                    ts1          msg1
   * TXN1    1            0                    ts1          msg2_p0
   * TXN1    1            1                    ts1          msg2_p1
   * TXN2    0            0                    ts2          msg3
   * TXN3    0            0                    ts3          msg4
   * </pre>
   */
  @Nullable
  @Override
  public RollbackDetail publish(StoreRequest request)
      throws TopicNotFoundException, IOException, UnauthorizedException {
    long start = System.currentTimeMillis();

    batch.add(request);
    if (!batch.isEmpty()) {
      int sequenceId = 0;
      List<Mutation> batchCopy = new ArrayList<>(batch.size());
      // We need to batch less than fetch limit since we read for publish_ts >= last_message.publish_ts
      // see fetch for explanation of why we read for publish_ts >= last_message.publish_ts and
      // not publish_ts > last_message.publish_ts
      while (!batch.isEmpty()) {
        StoreRequest headRequest = batch.poll();
        for (byte[] payload : headRequest) {
          //TODO: [CDAP-21094] Breakdown messages with larger payload into parts.
          Mutation mutation = Mutation.newInsertBuilder(getTableName(headRequest.getTopicId()))
              .set(SEQUENCE_ID_FIELD).to(sequenceId++).set(PAYLOAD_SEQUENCE_ID).to(0)
              .set(PUBLISH_TS_FIELD)
              .to("spanner.commit_timestamp()").set(PAYLOAD_FIELD).to(ByteArray.copyFrom(payload))
              .build();
          batchCopy.add(mutation);
        }

        if (batch.isEmpty() && (sequenceId < publishBatchSize
            || System.currentTimeMillis() - start < publishBatchTimeoutMillis)) {
          try {
            Thread.sleep(publishDelayMillis);
          } catch (InterruptedException e) {
            throw new IOException(e);
          }
        }
      }
      if (!batchCopy.isEmpty()) {
        try {
          client.write(batchCopy);
        } catch (SpannerException e) {
          throw new IOException(e);
        }
      }
    }
    //TODO: Add a RollbackDetail implementation that throws exceptions if any of the methods is called.
    return null;
  }

  @Override
  public void storePayload(StoreRequest request)
      throws TopicNotFoundException, IOException, UnauthorizedException {
    throw new IOException("NOT IMPLEMENTED");
  }

  @Override
  public void rollback(TopicId topicId, RollbackDetail rollbackDetail)
      throws TopicNotFoundException, IOException, UnauthorizedException {
    throw new IOException("NOT IMPLEMENTED");
  }

  @Override
  public CloseableIterator<RawMessage> fetch(MessageFetchRequest messageFetchRequest)
      throws TopicNotFoundException, IOException {
    throw new IOException("NOT IMPLEMENTED");
  }
}
