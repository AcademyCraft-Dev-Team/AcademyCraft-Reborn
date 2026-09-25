package org.academy.api.client.gui.widget

import java.util.function.Consumer

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

        internalSelect(buttonToSelect)
    }

    private fun internalSelect(buttonToSelect: RadioButtonWidget?) {
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

        if ((selectionChanged || allowReselect) && onSelectionChanged != null && this.selectedButton != null) {
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
            internalSelect(null)
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
            internalSelect(null)
        }
        super.clearChildren()
    }
}
