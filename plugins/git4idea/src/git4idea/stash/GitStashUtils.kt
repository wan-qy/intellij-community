/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("GitStashUtils")

package git4idea.stash

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import git4idea.GitUtil
import git4idea.commands.*
import git4idea.config.GitConfigUtil
import git4idea.merge.GitConflictResolver
import git4idea.ui.StashInfo
import git4idea.util.GitUntrackedFilesHelper
import git4idea.util.LocalChangesWouldBeOverwrittenHelper
import java.nio.charset.Charset

/**
 * Unstash the given root, handling common error scenarios.
 */
fun unstash(project: Project, root: VirtualFile, handler: GitLineHandler, conflictResolver: GitConflictResolver) {
  unstash(project, listOf(root), { handler }, conflictResolver)
}

/**
 * Unstash the given roots one by one, handling common error scenarios.
 *
 * If there's an error in one of the roots, stop and show the error.
 * If there's a conflict, show the merge dialog, and if the conflicts get resolved, continue with other roots.
 */
fun unstash(project: Project,
            roots: Collection<VirtualFile>,
            handlerProvider: (VirtualFile) -> GitLineHandler,
            conflictResolver: GitConflictResolver) {
  DvcsUtil.workingTreeChangeStarted(project, "Unstash").use {
    for (root in roots) {
      val handler = handlerProvider(root)

      val conflictDetector = GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT_ON_UNSTASH)
      val untrackedFilesDetector = GitUntrackedFilesOverwrittenByOperationDetector(root)
      val localChangesDetector = GitLocalChangesWouldBeOverwrittenDetector(root, GitLocalChangesWouldBeOverwrittenDetector.Operation.MERGE)
      handler.addLineListener(conflictDetector)
      handler.addLineListener(untrackedFilesDetector)
      handler.addLineListener(localChangesDetector)

      val result = Git.getInstance().runCommand { handler }

      VfsUtil.markDirtyAndRefresh(false, true, false, root)

      if (conflictDetector.hasHappened()) {
        val conflictsResolved = conflictResolver.merge()
        if (!conflictsResolved) return
      }
      else if (untrackedFilesDetector.wasMessageDetected()) {
        GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(project, root, untrackedFilesDetector.relativeFilePaths, "unstash", null)
        return
      }
      else if (localChangesDetector.wasMessageDetected()) {
        LocalChangesWouldBeOverwrittenHelper.showErrorNotification(project, root, "unstash", localChangesDetector.relativeFilePaths)
        return
      }
      else if (!result.success()) {
        VcsNotifier.getInstance(project).notifyError("Unstash Failed", result.errorOutputAsHtmlString)
        return
      }
    }
  }
}

@Deprecated("use the simpler overloading method which returns a list")
fun loadStashStack(project: Project, root: VirtualFile, consumer: Consumer<StashInfo>) {
  for (stash in loadStashStack(project, root)) {
    consumer.consume(stash)
  }
}

@Throws(VcsException::class)
fun loadStashStack(project: Project, root: VirtualFile): List<StashInfo> {
  val charset = Charset.forName(GitConfigUtil.getLogEncoding(project, root))

  val h = GitLineHandler(project, root, GitCommand.STASH.readLockingCommand())
  h.setSilent(true)
  h.addParameters("list")
  h.charset = charset
  val output = Git.getInstance().runCommand(h)
  output.throwOnError()

  val result = mutableListOf<StashInfo>()
  for (line in output.output) {
    val parts = line.split(':', limit = 3);
    if (parts.size < 2) {
      logger<GitUtil>().error("Can't parse stash record: ${line}")
    }
    else if (parts.size == 2) {
      result.add(StashInfo(parts[0], null, parts[1].trim()))
    }
    else {
      result.add(StashInfo(parts[0], parts[1].trim(), parts[2].trim()))
    }
  }
  return result
}
