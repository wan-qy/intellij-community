// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions.history

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsDiffUtil
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.util.ObjectUtils.notNull
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsLog
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.history.FileHistoryUtil
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import java.awt.event.KeyEvent

class CompareRevisionsFromFolderHistoryActionProvider : AnActionExtensionProvider {

  private fun getFilePath(e: AnActionEvent): FilePath? {
    return e.getData(VcsDataKeys.FILE_PATH)
  }

  override fun isActive(e: AnActionEvent): Boolean {
    if (e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) == null) return false
    val filePath = getFilePath(e)
    return filePath != null && filePath.isDirectory
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val logData = e.getData(VcsLogInternalDataKeys.LOG_DATA)
    val log = e.getData(VcsLogDataKeys.VCS_LOG)
    val filePath = getFilePath(e)
    if (log == null || project == null || logData == null || filePath == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    updateActionText(e, log)

    e.presentation.isVisible = true

    if (e.inputEvent is KeyEvent) {
      e.presentation.isEnabled = true
      return
    }

    if (log.selectedCommits.size >= 2) {
      val handler = e.getData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER)
      e.presentation.isEnabledAndVisible = handler != null
    }
    else {
      e.presentation.isEnabled = log.selectedCommits.isNotEmpty()
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val logData = e.getRequiredData(VcsLogInternalDataKeys.LOG_DATA)
    val log = e.getRequiredData(VcsLogDataKeys.VCS_LOG)
    val filePath = getFilePath(e)!!

    VcsLogUsageTriggerCollector.triggerUsage(e, this)

    val commits = log.selectedCommits
    if (commits.size >= 2) {
      val handler = e.getRequiredData(VcsLogInternalDataKeys.LOG_DIFF_HANDLER)
      handler.showDiff(commits[1].root, filePath, commits[1].hash, filePath, commits[0].hash)
      return
    }

    val commitIds = ContainerUtil.map(commits) { c -> logData.getCommitIndex(c.hash, c.root) }
    logData.commitDetailsGetter.loadCommitsData(commitIds, { details ->
      val detail = notNull(ContainerUtil.getFirstItem(details))
      val changes = FileHistoryUtil.collectRelevantChanges(detail,
                                                           Condition { change -> FileHistoryUtil.affectsDirectory(change, filePath) })
      VcsDiffUtil.showChangesDialog(project, "Changes in " + detail.id.toShortString() + " for " + filePath.name, changes.toMutableList())
    }, { t -> VcsBalloonProblemNotifier.showOverChangesView(project, "Could not load selected commits: " + t.message,
                                                            MessageType.ERROR) }, null)
  }

  companion object {
    private const val COMPARE_TEXT = "Compare"
    private const val COMPARE_DESCRIPTION = "Compare selected versions"
    private const val DIFF_TEXT = "Show Diff"
    private const val DIFF_DESCRIPTION = "Show diff with previous version"

    @JvmStatic
    fun updateActionText(e: AnActionEvent, log: VcsLog) {
      if (log.selectedCommits.size >= 2) {
        e.presentation.text = COMPARE_TEXT
        e.presentation.description = COMPARE_DESCRIPTION
      }
      else {
        e.presentation.text = DIFF_TEXT
        e.presentation.description = DIFF_DESCRIPTION
      }
    }
  }
}
