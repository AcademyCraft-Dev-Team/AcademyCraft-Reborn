package org.academy.api.client.gui.event

class CharTypedEvent(val codePoint: Int) : InputEvent(EventType.CHAR_TYPED)