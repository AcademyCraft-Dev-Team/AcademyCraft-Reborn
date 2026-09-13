package org.academy.api.client.input;


public class MouseButtonEvent extends InputControlEvent {
    public int button;
    public int action;
    public int modifiers;

    public MouseButtonEvent(int newButton, int newAction, int newModifiers) {
        button = newButton;
        action = newAction;
        modifiers = newModifiers;
    }
}
