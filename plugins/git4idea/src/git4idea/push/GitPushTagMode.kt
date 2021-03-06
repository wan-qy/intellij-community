// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push

import com.intellij.openapi.components.BaseState

class GitPushTagMode : BaseState {
  var title by string()
  var argument by string()

  @Suppress("unused")
  constructor() : this(ALL.title!!, ALL.argument!!)

  constructor(title: String, argument: String) : super() {
    // must be in constructor, not as default value for field
    this.title = title
    this.argument = argument
  }

  companion object {
    @JvmField
    val ALL = GitPushTagMode("All", "--tags")
    @JvmField
    val FOLLOW = GitPushTagMode("Current Branch", "--follow-tags")

    @JvmStatic
    val values = arrayOf(ALL, FOLLOW)
  }
}
