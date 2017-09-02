package info.michaelknierim;

import info.michaelknierim.control.CustomShutDownHook;

public class HRLoggerMain {
    public static void main(String[] args) {
        HRBeltListener hrBeltListener = new HRBeltListener();

        // Register shutdown hook to save collected data and ensure clean exit
        CustomShutDownHook shutDownHook = new CustomShutDownHook();
        shutDownHook.setHRBeltListener(hrBeltListener);
        Runtime.getRuntime().addShutdownHook(shutDownHook);
    }
}
