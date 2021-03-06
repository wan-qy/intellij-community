// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 * <p/>
 * Delegates to the VcsLogManager.
 */
public class VcsLogContentProvider implements ChangesViewContentProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogContentProvider.class);
  @SuppressWarnings("StaticNonFinalField") //might be change in other IDEs
  public static String TAB_NAME = "Log";

  @NotNull private final VcsProjectLog myProjectLog;
  @NotNull private final JPanel myContainer = new JBPanel(new BorderLayout());
  @Nullable private Consumer<? super VcsLogUiImpl> myOnCreatedListener;

  @Nullable private volatile VcsLogUiImpl myUi;

  public VcsLogContentProvider(@NotNull Project project, @NotNull VcsProjectLog projectLog) {
    myProjectLog = projectLog;

    MessageBusConnection connection = project.getMessageBus().connect(projectLog);
    connection.subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, new VcsProjectLog.ProjectLogListener() {
      @Override
      public void logCreated(@NotNull VcsLogManager logManager) {
        addMainUi(logManager);
      }

      @Override
      public void logDisposed(@NotNull VcsLogManager logManager) {
        disposeMainUi();
      }
    });

    VcsLogManager manager = myProjectLog.getLogManager();
    if (manager != null) {
      addMainUi(manager);
    }

    if (Registry.is("show.log.as.editor.tab")) {
      ChangesViewContentI changesViewManager = ChangesViewContentManager.getInstance(project);
      if (changesViewManager instanceof ChangesViewContentManager) {
        ((ChangesViewContentManager)changesViewManager).adviseSelectionChanged(new ContentManagerAdapter() {
          @Override
          public void selectionChanged(@NotNull ContentManagerEvent event) {
            if (myUi != null) {
              if (event.getContent().getDisplayName().equals(TAB_NAME) &&
                  event.getOperation() == ContentManagerEvent.ContentOperation.add) {
                VirtualFile graphFile = myUi.getMainFrame().tryGetGraphViewFile();
                if (graphFile != null) {
                  FileEditorManager.getInstance(project).openFile(graphFile, true);
                }
              }
            }
          }
        });
      }
    }
  }

  @Nullable
  public VcsLogUiImpl getUi() {
    return myUi;
  }

  @CalledInAwt
  private void addMainUi(@NotNull VcsLogManager logManager) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (myUi == null) {
      myUi = logManager.createLogUi(VcsLogProjectTabsProperties.MAIN_LOG_ID, true);
      VcsLogPanel panel = createPanel(logManager, myUi);
      myContainer.add(panel, BorderLayout.CENTER);
      DataManager.registerDataProvider(myContainer, panel);

      if (myOnCreatedListener != null) myOnCreatedListener.consume(myUi);
      myOnCreatedListener = null;
    }
  }

  @NotNull
  protected VcsLogPanel createPanel(@NotNull VcsLogManager logManager, AbstractVcsLogUi ui) {
    return new VcsLogPanel(logManager, ui);
  }

  @CalledInAwt
  private void disposeMainUi() {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    myContainer.removeAll();
    DataManager.removeDataProvider(myContainer);
    myOnCreatedListener = null;
    if (myUi != null) {
      VcsLogUiImpl ui = myUi;
      myUi = null;
      Disposer.dispose(ui);
    }
  }

  @Override
  public JComponent initContent() {
    myProjectLog.createLogInBackground(true);
    return myContainer;
  }

  /**
   * Executes a consumer when a main log ui is created. If main log ui already exists, executes it immediately.
   * Overwrites any consumer that was added previously: only the last one gets executed.
   *
   * @param consumer consumer to execute.
   */
  @CalledInAwt
  public void executeOnMainUiCreated(@NotNull Consumer<? super VcsLogUiImpl> consumer) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    if (myUi == null) {
      myOnCreatedListener = consumer;
    }
    else {
      consumer.consume(myUi);
    }
  }

  @Override
  public void disposeContent() {
    disposeMainUi();
  }

  @Nullable
  public static VcsLogContentProvider getInstance(@NotNull Project project) {
    for (ChangesViewContentEP ep : ChangesViewContentEP.EP_NAME.getExtensions(project)) {
      if (ep.getClassName().equals(VcsLogContentProvider.class.getName())) {
        return (VcsLogContentProvider)ep.getCachedInstance();
      }
    }
    return null;
  }

  public static class VcsLogVisibilityPredicate implements NotNullFunction<Project, Boolean> {
    @NotNull
    @Override
    public Boolean fun(Project project) {
      VcsRoot[] roots = ProjectLevelVcsManager.getInstance(project).getAllVcsRoots();
      return !VcsLogManager.findLogProviders(Arrays.asList(roots), project).isEmpty();
    }
  }
}
