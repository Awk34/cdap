/*
 * Copyright 2014 Continuuity, Inc.
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

package com.continuuity.explore.client;

import com.continuuity.explore.service.ColumnDesc;
import com.continuuity.explore.service.Explore;
import com.continuuity.explore.service.ExploreException;
import com.continuuity.explore.service.Handle;
import com.continuuity.explore.service.HandleNotFoundException;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * Statement execution future implementation. The overridden methods use an {@link ExploreHttpClient}
 * instance internally.
 */
class StatementExecutionFutureImpl extends AbstractFuture<ExploreExecutionResult> implements StatementExecutionFuture {
  private static final Logger LOG = LoggerFactory.getLogger(StatementExecutionFutureImpl.class);

  private final Explore exploreClient;
  private final ListenableFuture<Handle> futureHandle;

  StatementExecutionFutureImpl(Explore exploreClient, ListenableFuture<Handle> futureHandle) {
    this.exploreClient = exploreClient;
    this.futureHandle = futureHandle;
  }

  @Override
  public List<ColumnDesc> getResultSchema() throws ExploreException {
    try {
      Handle handle = futureHandle.get();
      return exploreClient.getResultSchema(handle);
    } catch (InterruptedException e) {
      LOG.error("Caught exception", e);
      throw Throwables.propagate(e);
    } catch (ExecutionException e) {
      LOG.error("Caught exception when retrieving results schema", e);
      Throwable t = Throwables.getRootCause(e);
      if (t instanceof ExploreException) {
        throw (ExploreException) t;
      }
      throw new ExploreException(t);
    } catch (HandleNotFoundException e) {
      LOG.error("Caught exception when retrieving results schema", e);
      throw new ExploreException(e);
    } catch (SQLException e) {
      LOG.error("Caught exception when retrieving results schema", e);
      throw new ExploreException(e);
    }
  }

  @Override
  public boolean setException(Throwable throwable) {
    return super.setException(throwable);
  }

  @Override
  public boolean set(@Nullable ExploreExecutionResult value) {
    return super.set(value);
  }
}
