package org.academy.api.client.gui.widget

import java.util.function.Consumer

/**
 * A container that acts as both a logical controller and a visual layout
 * for a group of [RadioButtonWidget]s, ensuring only one can be selected at a time.
 * It inherits from LinearLayoutWidget to automatically arrange its children.
 */
open class RadioGroupWidget : LinearLayoutWidget() {
    var selectedButton: RadioButtonWidget? = null
        protected set
    var onSelectionChanged: Consumer<RadioButtonWidget>? = null
    var allowReselect: Boolean = false
    protected var idCounter: Int = 0

    fun selectButton(buttonToSelect: RadioButtonWidget?) {
        if (!allowReselect && buttonToSelect == selectedButton) {
            return
        }

        internalSelect(buttonToSelect, true)
    }

    private fun internalSelect(buttonToSelect: RadioButtonWidget?, triggerCallback: Boolean) {
        if (buttonToSelect != null && !buttonToSelect.isEnabled) {
            return
        }

        val selectionChanged = selectedButton != buttonToSelect

        if (selectedButton != null) {
            selectedButton!!.isSelected = false
        }

        selectedButton = buttonToSelect
        if (selectedButton != null) {
            selectedButton!!.isSelected = true
        }

        if (triggerCallback && (selectionChanged || allowReselect) && onSelectionChanged != null && this.selectedButton != null) {
            onSelectionChanged!!.accept(this.selectedButton!!)
        }
    }

    override fun addChild(name: String, child: Widget) {
        if (child is RadioButtonWidget) {
            child.radioGroup = this
            child.setId(idCounter++)
        }
        super.addChild(name, child)
    }

    override fun removeChild(name: String) {
        val removedWidget = children[name]
        if (removedWidget === selectedButton) {
            internalSelect(null, true)
        }

        if (removedWidget is RadioButtonWidget) {
            removedWidget.radioGroup = null
        }

        super.removeChild(name)
    }

    override fun clearChildren() {
        for (child in children.values) {
            if (child is RadioButtonWidget) {
                child.radioGroup = null
            }
        }
        if (selectedButton != null) {
            internalSelect(null, true)
        }
        super.clearChildren()
    }
}