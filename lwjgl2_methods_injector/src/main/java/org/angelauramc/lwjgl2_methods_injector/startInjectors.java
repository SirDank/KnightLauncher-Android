package org.angelauramc.lwjgl2_methods_injector;

import java.lang.instrument.Instrumentation;

public class startInjectors {
    public static void premain(String args, Instrumentation inst) {
        ALC10Injector.premain(args, inst);
    }
}
