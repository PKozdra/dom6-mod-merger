// src/main/kotlin/com/dominions/modmerger/ui/utils/NoWrapEditorKit.kt
package com.dominions.modmerger.ui.util

import javax.swing.SizeRequirements
import javax.swing.text.*

class NoWrapEditorKit : StyledEditorKit() {
    override fun getViewFactory(): ViewFactory {
        return NoWrapViewFactory()
    }
}

class NoWrapViewFactory : ViewFactory {
    override fun create(elem: Element): View {
        val kind = elem.name
        return when (kind) {
            AbstractDocument.ParagraphElementName -> NoWrapParagraphView(elem)
            AbstractDocument.SectionElementName -> BoxView(elem, View.Y_AXIS)
            else -> LabelView(elem)
        }
    }
}

class NoWrapParagraphView(elem: Element) : ParagraphView(elem) {
    override fun layout(width: Int, height: Int) {
        super.layout(Short.MAX_VALUE.toInt(), height)
    }

    override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
        val req = super.calculateMinorAxisRequirements(axis, r)
        req.minimum = req.preferred
        return req
    }

    override fun getMinimumSpan(axis: Int): Float {
        return super.getPreferredSpan(axis)
    }
}
