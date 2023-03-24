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

package io.cdap.cdap.sourcecontrol;

import io.cdap.cdap.api.security.store.SecureStore;
import io.cdap.cdap.api.security.store.SecureStoreData;
import io.cdap.cdap.common.NotFoundException;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.utils.DirUtils;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.proto.sourcecontrol.AuthConfig;
import io.cdap.cdap.proto.sourcecontrol.AuthType;
import io.cdap.cdap.proto.sourcecontrol.PatConfig;
import io.cdap.cdap.proto.sourcecontrol.Provider;
import io.cdap.cdap.proto.sourcecontrol.RepositoryConfig;
import io.cdap.cdap.proto.sourcecontrol.RepositoryConfigValidationException;
import io.cdap.cdap.sourcecontrol.operationrunner.SourceControlException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link  RepositoryManager}.
 */
public class RepositoryManagerTest extends SourceControlTestBase {

  @Mock
  private SecureStore secureStore;
  private CConfiguration cConf;
  public LocalGitServer gitServer = getGitServer();
  @Rule
  public RuleChain chain = RuleChain.outerRule(baseTempFolder)
      .around(gitServer);

  @Before
  public void beforeEach() throws Exception {
    MockitoAnnotations.initMocks(this);
    Mockito.when(secureStore.get(NAMESPACE, PASSWORD_NAME))
        .thenReturn(new SecureStoreData(null,
            MOCK_TOKEN.getBytes(StandardCharsets.UTF_8)));
    cConf = CConfiguration.create();
    cConf.setInt(Constants.SourceControlManagement.GIT_COMMAND_TIMEOUT_SECONDS,
        GIT_COMMAND_TIMEOUT);
    cConf.set(
        Constants.SourceControlManagement.GIT_REPOSITORIES_CLONE_DIRECTORY_PATH,
        baseTempFolder.newFolder("repository").getAbsolutePath());
    // Disable hooks. In non-test environments, this is done by services on
    // start-up.
    SecureSystemReader.setAsSystemReader();
  }

  @Test
  public void testValidateIncorrectKeyName() throws Exception {
    String serverUrl = gitServer.getServerUrl();
    RepositoryConfig config =
        new RepositoryConfig.Builder()
            .setProvider(Provider.GITHUB)
            .setLink(serverUrl + "ignored")
            .setDefaultBranch("develop")
            .setAuth(new AuthConfig(AuthType.PAT,
                new PatConfig(PASSWORD_NAME + "invalid", null)))
            .build();
    SourceControlConfig sourceControlConfig = new SourceControlConfig(
        new NamespaceId(NAMESPACE),
        config, cConf);
    try {
      RepositoryManager.validateConfig(secureStore, sourceControlConfig);
      Assert.fail();
    } catch (RepositoryConfigValidationException e) {
      Assert.assertTrue(
          e.getMessage().contains("Failed to get authentication credentials"));
    }
  }

  @Test
  public void testValidateInvalidToken() throws Exception {
    SourceControlConfig sourceControlConfig = getSourceControlConfig();
    Mockito.when(secureStore.get(NAMESPACE, PASSWORD_NAME))
        .thenReturn(new SecureStoreData(null,
            "invalid-token".getBytes(StandardCharsets.UTF_8)));
    try {
      RepositoryManager.validateConfig(secureStore, sourceControlConfig);
      Assert.fail();
    } catch (RepositoryConfigValidationException e) {
      Assert.assertTrue(e.getMessage().contains("not authorized"));
    }
  }

  @Test
  public void testValidateInvalidBranchName() throws Exception {
    String serverUrl = gitServer.getServerUrl();
    RepositoryConfig config = new RepositoryConfig.Builder().setProvider(
            Provider.GITHUB)
        .setLink(serverUrl + "ignored")
        .setDefaultBranch("develop-invalid")
        .setAuth(AUTH_CONFIG)
        .build();
    SourceControlConfig sourceControlConfig = new SourceControlConfig(
        new NamespaceId(NAMESPACE),
        config, cConf);
    try {
      RepositoryManager.validateConfig(secureStore, sourceControlConfig);
      Assert.fail();
    } catch (RepositoryConfigValidationException e) {
      Assert.assertTrue(e.getMessage()
          .contains("Default branch not found in remote repository"));
    }
  }

  @Test
  public void testValidateNullBranchName() throws Exception {
    String serverUrl = gitServer.getServerUrl();
    RepositoryConfig config = new RepositoryConfig.Builder().setProvider(
            Provider.GITHUB)
        .setLink(serverUrl + "ignored")
        .setAuth(AUTH_CONFIG)
        .build();
    SourceControlConfig sourceControlConfig = new SourceControlConfig(
        new NamespaceId(NAMESPACE),
        config, cConf);
    RepositoryManager.validateConfig(secureStore, sourceControlConfig);
  }

  @Test
  public void testValidateSuccess() throws Exception {
    RepositoryManager.validateConfig(secureStore, getSourceControlConfig());
  }

  @Test
  public void testGetFileHashSuccess() throws Exception {
    String fileName = "pipeline.json";
    Path path = Paths.get(fileName);
    String fileContents = "{name: pipeline}";
    addFileToGit(path, fileContents, gitServer);
    RepositoryManager manager = getRepositoryManager();
    String commitId = manager.cloneRemote();
    String fileHash = manager.getFileHash(path, commitId);

    String expectedHash = getGitStyleHash(fileContents);
    Assert.assertEquals(expectedHash, fileHash);
    // Change the file, but don't commit. The hash should remain the same.
    Files.write(manager.getBasePath().resolve(fileName),
        "change 1".getBytes(StandardCharsets.UTF_8));
    fileHash = manager.getFileHash(path, manager.resolveHead().getName());
    Assert.assertEquals(expectedHash, fileHash);
    // Change the file contents and commit, the hash should also change.
    addFileToGit(path, "change 2", gitServer);
    commitId = manager.cloneRemote();
    Assert.assertNotEquals(manager.getFileHash(path, commitId), expectedHash);
    manager.close();
  }

  @Test(expected = NotFoundException.class)
  public void testGetFileHashInvalidPath() throws Exception {
    try (RepositoryManager manager = getRepositoryManager()) {
      String commitId = manager.cloneRemote();
      manager.getFileHash(Paths.get("data-pipelines", "pipeline.json"),
          commitId);
    }
  }

  @Test
  public void testGetFileHashChangedFile() throws Exception {
    // Add two files to git.
    String fileName1 = "file1.json";
    String fileContents1 = "abc";
    String fileContents2 = "def";
    Path path = Paths.get(fileName1);
    addFileToGit(path, fileContents1, gitServer);
    // check file hash of link.
    RepositoryManager manager = getRepositoryManager();
    manager.cloneRemote();
    String commitId = manager.resolveHead().getName();
    String hash = manager.getFileHash(path, commitId);
    // Change file contents, but don't commit.
    Path absoluteRepoPath = manager.getRepositoryRoot().resolve(path);
    Files.write(absoluteRepoPath,
        fileContents2.getBytes(StandardCharsets.UTF_8));
    // Check the file hash of the link, it should be unchanged.
    Assert.assertEquals(hash, manager.getFileHash(path, commitId));
    // Check that working directory changes are still present.
    Assert.assertEquals(new String(Files.readAllBytes(absoluteRepoPath),
            StandardCharsets.UTF_8),
        fileContents2);
    // Commit the symlink change.
    addFileToGit(path, fileContents2, gitServer);
    manager.cloneRemote();
    // Check the file hash at previous commit.
    Assert.assertEquals(hash, manager.getFileHash(path, commitId));
    // Check the file hash at head.
    Assert.assertNotEquals(hash,
        manager.getFileHash(path, manager.resolveHead().getName()));
    manager.close();
  }

  @Test(expected = IOException.class)
  public void testGetFileHashInvalidCommitId() throws Exception {
    String fileName = "pipeline.json";
    Path path = Paths.get(fileName);
    addFileToGit(path, "{name: pipeline}", gitServer);
    RepositoryManager manager = getRepositoryManager();
    manager.cloneRemote();
    try {
      manager.getFileHash(path, "7edf45b8cab741caf657e71bd30464860fa56789");
    } finally {
      manager.close();
    }
  }

  @Test
  public void testCloneUntrackedFiles() throws Exception {
    try (RepositoryManager manager = getRepositoryManager()) {
      manager.cloneRemote();
      Path path = manager.getRepositoryRoot().resolve("my-file.txt");
      Files.write(path, "abc".getBytes(StandardCharsets.UTF_8));
      Assert.assertTrue(Files.exists(path));
      manager.cloneRemote();
      Assert.assertFalse(Files.exists(path));
    }
  }

  @Test
  public void testFetchNewCommitForFileHash() throws Exception {
    try (RepositoryManager manager = getRepositoryManager()) {
      manager.cloneRemote();
      String fileName = "pipeline.json";
      Path path = Paths.get(fileName);
      String contents = "{name: pipeline}";
      RevCommit commit = addFileToGit(path, contents, gitServer);
      Assert.assertEquals(manager.getFileHash(path, commit.getName()),
          getGitStyleHash(contents));
    }
  }

  @Test
  public void testGitHooksEnabled()
      throws IOException, GitAPIException, NoChangesToPushException,
      AuthenticationConfigException {
    try (RepositoryManager manager = getRepositoryManager()) {
      SecureSystemReader secureSystemReader = (SecureSystemReader) SystemReader.getInstance();
      SystemReader.setInstance(secureSystemReader.getDelegate());
      // Re-enable hooks to ensure test environment is correct and hooks
      // are set up correctly.
      manager.cloneRemote();
      // Install a git hook in the repository.
      installHook(manager);
      // commit a change.
      commitFileToLocalRepository(
          Paths.get("abc.txt"),
          "init file abc", manager, new CommitMeta(
              "user-1", "user-2",
              System.currentTimeMillis(),
              "first commit"));
      // Verify the hook was executed.
      Assert.assertTrue(manager
          .getRepositoryRoot()
          .resolve("hook_executed")
          .toFile()
          .exists());
    }
  }

  @Test
  public void testGitHooksDisabled()
      throws IOException, GitAPIException, NoChangesToPushException,
      AuthenticationConfigException {
    try (RepositoryManager manager = getRepositoryManager()) {
      manager.cloneRemote();
      // Install a git hook in the repository.
      installHook(manager);
      // commit a change.
      commitFileToLocalRepository(
          Paths.get("abc.txt"),
          "init file abc", manager, new CommitMeta(
              "user-1", "user-2",
              System.currentTimeMillis(),
              "first commit"));
      // Verify the hook wasn't executed.
      Assert.assertFalse(manager
          .getRepositoryRoot()
          .resolve("hook_executed")
          .toFile()
          .exists());
    }
  }

  @Test
  public void testCommitAndPushSuccess() throws Exception {
    String serverUrl = gitServer.getServerUrl();
    RepositoryConfig config = new RepositoryConfig.Builder().setProvider(
            Provider.GITHUB)
        .setLink(serverUrl + "ignored")
        .setDefaultBranch("develop")
        .setAuth(AUTH_CONFIG)
        .build();

    try (RepositoryManager manager = new RepositoryManager(secureStore, cConf,
        new NamespaceId(NAMESPACE), config)) {
      manager.cloneRemote();
      CommitMeta commitMeta = new CommitMeta("author", "committer", 100,
          "message");
      Path filePath = manager.getBasePath().resolve("file1");
      String fileContent = "content";

      Files.write(filePath, fileContent.getBytes(StandardCharsets.UTF_8));
      manager.commitAndPush(commitMeta,
          manager.getBasePath().relativize(filePath));
      verifyCommit(filePath, fileContent, commitMeta);
    }
  }

  @Test(expected = SourceControlException.class)
  public void testCommitAndPushSourceControlFailure() throws Exception {
    String serverUrl = gitServer.getServerUrl();
    RepositoryConfig config = new RepositoryConfig.Builder().setProvider(
            Provider.GITHUB)
        .setLink(serverUrl + "ignored")
        .setDefaultBranch("develop")
        .setAuth(AUTH_CONFIG)
        .build();

    try (RepositoryManager manager = new RepositoryManager(secureStore, cConf,
        new NamespaceId(NAMESPACE), config)) {
      manager.cloneRemote();
      CommitMeta commitMeta = new CommitMeta("author", "committer", 100,
          "message");
      Path filePath = manager.getBasePath().resolve("file1");
      String fileContent = "content";

      Files.write(filePath, fileContent.getBytes(StandardCharsets.UTF_8));
      manager.commitAndPush(commitMeta, Paths.get("fileThatDoesNotExist.json"));
    }
  }

  @Test(expected = NoChangesToPushException.class)
  public void testCommitAndPushNoChangeSuccess() throws Exception {
    String pathPrefix = "prefix";
    String serverUrl = gitServer.getServerUrl();
    RepositoryConfig config = new RepositoryConfig.Builder().setProvider(
            Provider.GITHUB)
        .setLink(serverUrl + "ignored")
        .setDefaultBranch("develop")
        .setPathPrefix(pathPrefix)
        .setAuth(AUTH_CONFIG)
        .build();

    try (RepositoryManager manager = new RepositoryManager(secureStore, cConf,
        new NamespaceId(NAMESPACE), config)) {
      manager.cloneRemote();
      CommitMeta commitMeta = new CommitMeta("author", "committer", 100,
          "message");
      manager.commitAndPush(commitMeta, Paths.get(pathPrefix));

      verifyNoCommit();
    }
  }

  @Test(expected = GitAPIException.class)
  public void testCommitAndPushFails() throws Exception {
    String serverUrl = gitServer.getServerUrl();
    RepositoryConfig config = new RepositoryConfig.Builder().setProvider(
            Provider.GITHUB)
        .setLink(serverUrl + "ignored")
        .setDefaultBranch("develop")
        .setAuth(AUTH_CONFIG)
        .build();

    try (RepositoryManager manager = new RepositoryManager(secureStore, cConf,
        new NamespaceId(NAMESPACE), config)) {
      manager.cloneRemote();
      CommitMeta commitMeta = new CommitMeta("author", "committer", 100,
          "message");
      Path filePath = manager.getBasePath().resolve("file1");
      String fileContent = "content";

      Files.write(filePath, fileContent.getBytes(StandardCharsets.UTF_8));
      gitServer.after();
      manager.commitAndPush(commitMeta,
          manager.getBasePath().relativize(filePath));
    }
  }

  private void verifyNoCommit() throws GitAPIException, IOException {
    Path tempDirPath = baseTempFolder.newFolder("temp-local-git-verify")
        .toPath();
    Git localGit = getClonedGit(tempDirPath, gitServer);
    List<RevCommit> commits =
        StreamSupport.stream(localGit.log().all().call().spliterator(), false)
            .collect(Collectors.toList());
    Assert.assertEquals(1, commits.size());
  }

  private void verifyCommit(Path pathFromRepoRoot, String expectedContent,
      CommitMeta commitMeta)
      throws
      GitAPIException, IOException {
    Path tempDirPath = baseTempFolder.newFolder("temp-local-git-verify")
        .toPath();
    Git localGit = getClonedGit(tempDirPath, gitServer);
    Path filePath = tempDirPath.resolve(pathFromRepoRoot);

    String actualContent = new String(Files.readAllBytes(filePath),
        StandardCharsets.UTF_8);
    Assert.assertEquals(expectedContent, actualContent);

    List<RevCommit> commits =
        StreamSupport.stream(localGit.log().all().call().spliterator(), false)
            .collect(Collectors.toList());
    Assert.assertEquals(2, commits.size());
    RevCommit latestCommit = commits.get(0);

    Assert.assertEquals(latestCommit.getFullMessage(), commitMeta.getMessage());
    Assert.assertEquals(commitMeta.getAuthor(),
        latestCommit.getAuthorIdent().getName());
    Assert.assertEquals(commitMeta.getCommitter(),
        latestCommit.getCommitterIdent().getName());

    localGit.close();
    DirUtils.deleteDirectoryContents(tempDirPath.toFile());
  }

  private RepositoryConfig.Builder getRepositoryConfigBuilder() {
    return new RepositoryConfig.Builder().setProvider(Provider.GITHUB)
        .setLink(gitServer.getServerUrl() + "ignored")
        .setDefaultBranch("develop")
        .setAuth(AUTH_CONFIG);
  }

  /**
   * Creates a valid {@link SourceControlConfig}.
   *
   * @return the {@link SourceControlConfig}.
   */
  private SourceControlConfig getSourceControlConfig() {
    return new SourceControlConfig(new NamespaceId(NAMESPACE),
        getRepositoryConfigBuilder().build(),
        cConf);
  }

  /**
   * Creates a valid {@link RepositoryManager}.
   *
   * @return the {@link RepositoryManager}.
   */
  private RepositoryManager getRepositoryManager() {
    return new RepositoryManager(secureStore, cConf, new NamespaceId(NAMESPACE),
        getRepositoryConfigBuilder().build());
  }

  private void commitFileToLocalRepository(Path filePath, String contents,
      RepositoryManager manager, CommitMeta commitMeta)
      throws IOException, NoChangesToPushException, GitAPIException {
    Files.write(manager.getRepositoryRoot().resolve(filePath),
        contents.getBytes(StandardCharsets.UTF_8));
    manager.commitAndPush(commitMeta, filePath);
  }

  private void installHook(RepositoryManager manager) throws IOException {
    Path gitHookPath = manager
        .getRepositoryRoot()
        .resolve(".git/hooks/pre-commit");
    Files.createDirectories(gitHookPath.getParent());
    Files.createFile(gitHookPath, PosixFilePermissions.asFileAttribute(
        PosixFilePermissions.fromString("rwxr--r--")));
    Files.write(gitHookPath, "touch hook_executed\nexit 0".getBytes(
        StandardCharsets.UTF_8));
  }
}
