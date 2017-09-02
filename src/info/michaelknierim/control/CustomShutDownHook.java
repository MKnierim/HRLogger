package info.michaelknierim.control;

import info.michaelknierim.HRBeltListener;

/**
 * Created by michaelknierim on 23.01.17.
 */
public class CustomShutDownHook extends Thread {

    HRBeltListener hrBeltListener;

    /**
     * ...
     */
    @Override
    public void run() {
        // Close BLED112 connection
        if (hrBeltListener != null && hrBeltListener.getConnection() >= 0)
            hrBeltListener.disconnectBLED112();
    }

    public void setHRBeltListener(HRBeltListener hrBeltListener) {
        this.hrBeltListener = hrBeltListener;
    }
}
