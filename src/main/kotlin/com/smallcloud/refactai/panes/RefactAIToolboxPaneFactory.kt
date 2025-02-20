package com.smallcloud.refactai.panes

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.aitoolbox.ToolboxPane
import com.smallcloud.refactai.panes.gptchat.ChatGPTPanes
import com.smallcloud.refactai.utils.getLastUsedProject

class RefactAIToolboxPaneFactory : ToolWindowFactory {
    override fun init(toolWindow: ToolWindow) {
        toolWindow.setIcon(Resources.Icons.LOGO_RED_13x13)
        super.init(toolWindow)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val toolbox = ToolboxPane(toolWindow.disposable)
        val toolboxContent: Content = contentFactory.createContent(
                toolbox.getComponent(),
                "Toolbox",
                false
        )
        toolboxContent.isCloseable = false
        toolboxContent.putUserData(toolboxKey, toolbox)
        toolWindow.contentManager.addContent(toolboxContent)

        val gptChatPanes = ChatGPTPanes(project, toolWindow.disposable)
        val content: Content = contentFactory.createContent(
                gptChatPanes.getComponent(),
                "Chat",
                false
        )
        content.isCloseable = false
        content.putUserData(panesKey, gptChatPanes)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        private val panesKey = Key.create<ChatGPTPanes>("refact.panes")
        private val toolboxKey = Key.create<ToolboxPane>("refact.toolbox")
        val chat: ChatGPTPanes?
            get() {
                val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
                return tw?.contentManager?.findContent("Chat")?.getUserData(panesKey)
            }

        fun focusToolbox() {
            val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
            val content = tw?.contentManager?.findContent("Toolbox") ?: return
            tw.contentManager.setSelectedContent(content, true)
            val toolbox = content.getUserData(toolboxKey)
            toolbox?.requestFocus()
        }
        fun isToolboxFocused(): Boolean {
            val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
            val toolbox =  tw?.contentManager?.findContent("Toolbox")?.getUserData(toolboxKey)
            return toolbox?.isFocused() ?: false
        }

        fun focusChat() {
            val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
            val content = tw?.contentManager?.findContent("Chat") ?: return
            tw.contentManager.setSelectedContent(content, true)
            val panes = content.getUserData(panesKey)
            panes?.requestFocus()
        }
    }
}