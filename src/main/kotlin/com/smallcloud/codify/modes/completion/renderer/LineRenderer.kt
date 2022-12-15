package com.smallcloud.codify.modes.completion.renderer

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

class LineRenderer(private val editor: Editor, private val suffix: String, private val deprecated: Boolean) :
    EditorCustomElementRenderer {
    private var color: Color? = null
    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        return editor.contentComponent
            .getFontMetrics(RenderHelper.getFont(editor, deprecated)).stringWidth(suffix)
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics,
        targetRegion: Rectangle,
        textAttributes: TextAttributes
    ) {
        color = color ?: RenderHelper.color
        g.color = color
        g.font = RenderHelper.getFont(editor, deprecated)
        g.drawString(suffix, targetRegion.x, targetRegion.y + editor.ascent)
    }
}
