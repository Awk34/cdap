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

package io.cdap.cdap.common.encryption;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.cdap.cdap.common.encryption.guice.DataStorageAeadEncryptionModule;
import io.cdap.cdap.common.guice.ConfigModule;
import org.junit.Test;

/**
 * Tests for {@link DataStorageAeadEncryptionModule}.
 */
public class DataStorageAeadEncryptionModuleTest {

  @Test
  public void testBinding() {
    Injector injector = Guice
        .createInjector(new ConfigModule(), new DataStorageAeadEncryptionModule());
    injector.getInstance(Key.get(AeadCipher.class,
        Names.named(DataStorageAeadEncryptionModule.DATA_STORAGE_ENCRYPTION)));
  }
}
