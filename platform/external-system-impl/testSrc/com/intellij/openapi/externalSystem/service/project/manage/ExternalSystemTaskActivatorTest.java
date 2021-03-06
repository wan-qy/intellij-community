// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.test.TestExternalProjectSettings;
import com.intellij.openapi.externalSystem.test.TestExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.test.TestExternalSystemManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.task.*;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.concurrency.Semaphore;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator.Phase.*;
import static com.intellij.openapi.externalSystem.test.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX;
import static java.util.Collections.emptyList;

public class ExternalSystemTaskActivatorTest extends HeavyPlatformTestCase {

  private static final Key<StringBuilder> TASKS_TRACE = KeyWithDefaultValue.create("tasks trace", StringBuilder::new);
  private static final String TEST_MODULE_NAME = "MyModule";

  @Override
  public void setUp() throws Exception {
    edt(() -> super.setUp());
    TestExternalSystemManager testExternalSystemManager = new MyTestExternalSystemManager();
    List<ExternalSystemManager<?, ?, ?, ?, ?>> externalSystemManagers =
      StreamEx.of(ExternalSystemManager.EP_NAME.extensions()).append(testExternalSystemManager).toList();
    ExtensionTestUtil.maskExtensions(ExternalSystemManager.EP_NAME, externalSystemManagers, getTestRootDisposable());
    ExtensionTestUtil.maskExtensions(ConfigurationType.CONFIGURATION_TYPE_EP,
                                     Collections.<ConfigurationType>singletonList(new TestTaskConfigurationType()), getTestRootDisposable());
    ExtensionTestUtil.maskExtensions(ProjectTaskRunner.EP_NAME, emptyList(), getTestRootDisposable());
    Registry.addKey(TEST_EXTERNAL_SYSTEM_ID.getId() + USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX, "", true, false);

    String projectPath = "/project/path";
    TestExternalProjectSettings projectSettings = new TestExternalProjectSettings();
    projectSettings.setExternalProjectPath(projectPath);
    ExternalSystemUtil
      .linkExternalProject(TEST_EXTERNAL_SYSTEM_ID, projectSettings, myProject, null, false, ProgressExecutionMode.MODAL_SYNC);
  }

  @Override
  public void tearDown() throws Exception {
    edt(() -> super.tearDown());
  }

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  public void testBeforeAfterBuildTasks() {
    Module module = ModuleManager.getInstance(myProject).findModuleByName(TEST_MODULE_NAME);
    ExternalProjectsManagerImpl.getInstance(myProject).init();
    addTaskTrigger("beforeBuildTask1", BEFORE_COMPILE, module);
    addTaskTrigger("beforeBuildTask2", BEFORE_COMPILE, module);
    addTaskTrigger("afterBuildTask1", AFTER_COMPILE, module);
    addTaskTrigger("afterBuildTask2", AFTER_COMPILE, module);
    addTaskTrigger("beforeReBuildTask1", BEFORE_REBUILD, module);
    addTaskTrigger("beforeReBuildTask2", BEFORE_REBUILD, module);
    addTaskTrigger("afterReBuildTask1", AFTER_REBUILD, module);
    addTaskTrigger("afterReBuildTask2", AFTER_REBUILD, module);
    build(module);
    assertEquals("beforeBuildTask1,beforeBuildTask2,afterBuildTask1,afterBuildTask2", TASKS_TRACE.get(myProject).toString());

    TASKS_TRACE.get(myProject).setLength(0);
    rebuild(module);
    assertEquals("beforeReBuildTask1,beforeReBuildTask2,afterReBuildTask1,afterReBuildTask2", TASKS_TRACE.get(myProject).toString());
  }

  private void addTaskTrigger(String taskName, ExternalSystemTaskActivator.Phase phase, Module module) {
    String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    ExternalSystemTaskActivator taskActivator = ExternalProjectsManagerImpl.getInstance(myProject).getTaskActivator();
    taskActivator.addTask(new ExternalSystemTaskActivator.TaskActivationEntry(TEST_EXTERNAL_SYSTEM_ID, phase, projectPath, taskName));
  }

  private static void build(@NotNull Module module) {
    Semaphore semaphore = new Semaphore(1);
    ProjectTaskManager.getInstance(module.getProject()).build(new Module[]{module}, new ProjectTaskNotification() {
      @Override
      public void finished(@NotNull ProjectTaskContext context, @NotNull ProjectTaskResult executionResult) {
        semaphore.up();
      }
    });
    semaphore.waitFor();
  }

  private static void rebuild(@NotNull Module module) {
    Semaphore semaphore = new Semaphore(1);
    ProjectTaskManager.getInstance(module.getProject()).rebuild(new Module[]{module}, new ProjectTaskNotification() {
      @Override
      public void finished(@NotNull ProjectTaskContext context, @NotNull ProjectTaskResult executionResult) {
        semaphore.up();
      }
    });
    semaphore.waitFor();
  }

  private static class TestTaskConfigurationType extends AbstractExternalSystemTaskConfigurationType {
    private TestTaskConfigurationType() {super(TEST_EXTERNAL_SYSTEM_ID);}
  }

  private class MyTestExternalSystemManager extends TestExternalSystemManager {
    private MyTestExternalSystemManager() {super(ExternalSystemTaskActivatorTest.this.myProject);}

    @NotNull
    @Override
    public Class<? extends ExternalSystemProjectResolver<TestExternalSystemExecutionSettings>> getProjectResolverClass() {
      return TestProjectResolver.class;
    }

    @Override
    public Class<? extends ExternalSystemTaskManager<TestExternalSystemExecutionSettings>> getTaskManagerClass() {
      return TestTaskManager.class;
    }
  }

  public static class TestProjectResolver implements ExternalSystemProjectResolver<TestExternalSystemExecutionSettings> {

    @Nullable
    @Override
    public DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                                    @NotNull String projectPath,
                                                    boolean isPreviewMode,
                                                    @Nullable TestExternalSystemExecutionSettings settings,
                                                    @NotNull ExternalSystemTaskNotificationListener listener)
      throws ExternalSystemException, IllegalArgumentException, IllegalStateException {
      DataNode<ProjectData> projectDataDataNode = new DataNode<>(
        ProjectKeys.PROJECT, new ProjectData(TEST_EXTERNAL_SYSTEM_ID, "MyProject", "", projectPath), null);
      projectDataDataNode.createChild(
        ProjectKeys.MODULE, new ModuleData("my-module", TEST_EXTERNAL_SYSTEM_ID, "myModuleType", TEST_MODULE_NAME, "", projectPath));
      return projectDataDataNode;
    }

    @Override
    public boolean cancelTask(@NotNull ExternalSystemTaskId taskId, @NotNull ExternalSystemTaskNotificationListener listener) {
      return false;
    }
  }

  public static class TestTaskManager implements ExternalSystemTaskManager<TestExternalSystemExecutionSettings> {

    @Override
    public void executeTasks(@NotNull ExternalSystemTaskId id,
                             @NotNull List<String> taskNames,
                             @NotNull String projectPath,
                             @Nullable TestExternalSystemExecutionSettings settings,
                             @Nullable String jvmParametersSetup,
                             @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
      StringBuilder builder = TASKS_TRACE.get(id.findProject());
      if (builder.length() != 0) {
        builder.append(",");
      }
      builder.append(StringUtil.join(taskNames, ","));
    }

    @Override
    public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener)
      throws ExternalSystemException {
      return false;
    }
  }
}