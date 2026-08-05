package org.academy.agent;

import com.sun.tools.attach.VirtualMachine;

public final class AttachMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: <pid> <agent-jar> [agent-args]");
        var machine = VirtualMachine.attach(args[0]);
        try {
            machine.loadAgent(args[1], args.length >= 3 ? args[2] : "");
        } finally {
            machine.detach();
        }
    }

    private AttachMain() {
    }
}
