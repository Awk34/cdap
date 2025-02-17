/*
 * Copyright © 2016-2018 Cask Data, Inc.
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

package io.cdap.cdap.security.server;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.utils.Networks;
import java.net.InetAddress;
import java.net.URL;
import org.junit.Assert;


/**
 * Base test class for LDAP Based ExternalAuthenticationServer.
 */
public abstract class ExternalLdapAuthenticationServerTestBase extends ExternalAuthenticationServerTestBase {

  private static InMemoryDirectoryServer ldapServer;
  protected static int ldapPort = Networks.getRandomPort();
  protected static InMemoryListenerConfig ldapListenerConfig;

  /**
   * LDAP server and related handler configurations.
   */
  protected CConfiguration getConfiguration(CConfiguration cConf) {
    String configBase = Constants.Security.AUTH_HANDLER_CONFIG_BASE;

    // Use random port for testing
    cConf.setInt(Constants.Security.AUTH_SERVER_BIND_PORT, 0);
    cConf.setInt(Constants.Security.AuthenticationServer.SSL_PORT, 0);

    cConf.set(Constants.Security.AUTH_HANDLER_CLASS, LdapAuthenticationHandler.class.getName());
    cConf.set(Constants.Security.LOGIN_MODULE_CLASS_NAME, LdapLoginModule.class.getName());
    cConf.set(configBase.concat("debug"), "true");
    cConf.set(configBase.concat("hostname"), InetAddress.getLoopbackAddress().getHostName());
    cConf.set(configBase.concat("port"), Integer.toString(ldapPort));
    cConf.set(configBase.concat("userBaseDn"), "dc=example,dc=com");
    cConf.set(configBase.concat("userRdnAttribute"), "cn");
    cConf.set(configBase.concat("userObjectClass"), "inetorgperson");

    URL keytabUrl = ExternalAuthenticationServerTestBase.class.getClassLoader().getResource("test.keytab");
    Assert.assertNotNull(keytabUrl);
    cConf.set(Constants.Security.CFG_CDAP_MASTER_KRB_KEYTAB_PATH, keytabUrl.getPath());
    cConf.set(Constants.Security.CFG_CDAP_MASTER_KRB_PRINCIPAL, "test_principal");
    return cConf;
  }

  protected void startExternalAuthenticationServer() throws Exception {
    InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=com");
    config.setListenerConfigs(ldapListenerConfig);

    Entry defaultEntry = new Entry(
      "dn: dc=example,dc=com",
      "objectClass: top",
      "objectClass: domain",
      "dc: example");
    Entry userEntry = new Entry(
      "dn: uid=user,dc=example,dc=com",
      "objectClass: inetorgperson",
      "cn: admin",
      "sn: User",
      "uid: user",
      "userPassword: realtime");

    ldapServer = new InMemoryDirectoryServer(config);
    ldapServer.addEntries(defaultEntry, userEntry);
    ldapServer.startListening();
  }

  protected void stopExternalAuthenticationServer() {
    ldapServer.shutDown(true);
  }
}
