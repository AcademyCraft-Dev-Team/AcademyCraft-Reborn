package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.MouseEvent

/**
 * A universal radio button that can contain any widget as its content.
 * It extends ButtonWidget, inheriting all button behaviors, and adds selection state management.
 * It is designed to be used within a [RadioGroupWidget].
 */
open class RadioButtonWidget : ButtonWidget {
    override var isSelected: Boolean = false

    var radioGroup: RadioGroupWidget? = null
    var id: Int = -1
        protected set

    /**
     * Creates an empty radio button.
     */
    constructor()

    /**
     * Creates a radio button with the given widget as its content.
     * @param content The widget to be displayed inside the radio button.
     */
    constructor(content: Widget) : super(content)

    fun setId(id: Int): RadioButtonWidget {
        this.id = id
        return this
    }

    override fun onMousePressed(event: MouseEvent) {
        super.onMousePressed(event)
        if (event.isConsumed && radioGroup != null) {
            radioGroup!!.selectButton(this)
        }
    }
}