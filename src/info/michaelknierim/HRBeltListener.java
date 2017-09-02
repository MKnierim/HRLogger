package info.michaelknierim;

import gnu.io.NoSuchPortException;
import gnu.io.SerialPort;
import org.thingml.bglib.*;
import org.thingml.bglib.gui.*;

import javax.swing.*;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HRBeltListener extends BGAPIDefaultListener {

    // The Logger.
    private final Logger logger = Logger.getLogger(getClass().getName());

    // BGAPI objects.
    protected BGAPI bgapi;
    protected SerialPort port;
    protected BGAPIPacketLogger bgapiLogger = new BGAPIPacketLogger();

    // Available BLE Devices.
    protected BLEDeviceList devList = new BLEDeviceList();
    protected BLEDevice bledevice;
    private final String HR_BELT_MAC = "0:18:31:f0:ee:be";
    private final String VALERT_MAC = "d0:39:72:c5:1:e"; // Just included for dev purposes

    // GATT Discovery (from BLEExplorerDialog.java)
    private static final int IDLE = 0;
    private static final int SERVICES = 1;
    private static final int ATTRIBUTES = 2;
    private Iterator<BLEService> discovery_it = null;
    private BLEService discovery_srv = null;
    private int discovery_state = IDLE;

    // Program state variables.
    private Integer connection = -1;
    private Boolean isHRBeltAvailable = false;
    private Boolean isHRBeltConnected = false;
    private Boolean receiveBLEData = false;

    // Set defaults for options (scanning, connecting, package lookup)
    private final String PORT_NAME = "/dev/tty.usbmodem1";
    Integer BAUD = 115200;
    Boolean PACKET = false;
    Boolean DEBUG = false;

    public HRBeltListener() {
        // This is called first in BLEExplorerFrame.java too. Maybe important? Didn't seem to make a change though...
        BLED112.initRXTX();

        // Approach to connect to BLE devices through the provided BLEExplorer GUI by bglib.
        // runExlorerFrame();

        // New version adapted from bglib_test_hr_collector.py and BLEExplorerDialog.java
        runHRBeltDiscovery();
    }

    /**
     * This merely instantiates the example provided by the bglib library, to connect to a BLED112, scan for BLE devices and connect to them.
     */
    private void runExlorerFrame() {
        JFrame frame = new BLEExplorerFrame();
        frame.setVisible(true);
    }

    /**
     * ...
     */
    private void runHRBeltDiscovery() {

        // Create and setup BGLIB object
        if (connectToBLED112(PORT_NAME)) {

            // Add debugger if desired.
            if (DEBUG) bgapi.getLowLevelDriver().addListener(bgapiLogger);

            // Reset.
            // TODO: Disconnect if connected already
            // TODO: Stop advertising if advertising already
            bgapi.send_gap_end_procedure();     // Stop scanning if scanning already

            // Start scanning for BLE devices and connect to HR belt when it is available.
            runDiscovery();

            // Run Gatt Discovery // TODO: Not sure if this is needed.
            runGATTDiscovery();

            // Continuously check incoming data (in loop) // TODO: Not sure if this is needed.
//        while (receiveBLEData) {
//            // In python example code this calls: ble.check_activity(ser); time.sleep(0.01)
//        }
        }

    }

    /**
     * Establish connection to the BLED112 dongle on a pre-defined port.
     *
     * @throws NoSuchPortException
     */
    private Boolean connectToBLED112(String portName) {
        Boolean isConnected = false;

        logger.info("Connecting BLED112 Dongle...");

        // Create serial port object
        port = org.thingml.bglib.gui.BLED112.connectSerial(portName);

        if (port != null) {
            try {
                bgapi = new BGAPI(new BGAPITransport(port.getInputStream(), port.getOutputStream()));
                bgapi.addListener(this);
                Thread.sleep(250);
                bgapi.send_system_get_info();

                isConnected = true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } catch (NullPointerException npe) {
                npe.printStackTrace();
                return false;
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, null, e);
                return false;
            }
        }

        return isConnected;
    }

    /**
     * This starts scanning for available BLE devices.
     */
    private void runDiscovery() {
        devList.clear();
        bgapi.send_gap_set_scan_parameters(10, 250, 1);
        bgapi.send_gap_discover(1);
    }

    // TODO: Not sure if this should be an own method. Was integrated in main code in BLEExplorerDialog.java
    private void runGATTDiscovery() {
        // TODO
    }

    /**
     * Disconnect the BLED112 dongle.
     */
    public void disconnectBLED112() {
        devList.clear();

        if (connection >= 0) {
            bgapi.send_connection_disconnect(connection);
        }
        connection = -1;

        if (bgapi != null) {
            bgapi.removeListener(this);
            bgapi.getLowLevelDriver().removeListener(bgapiLogger);
            logger.info("BLE: Reset BLED112 Dongle");
            bgapi.send_system_reset(0);
            bgapi.disconnect();
        }
        if (port != null) {
            port.close();
        }
        bgapi = null;
        port = null;
    }

    /**
     * ...
     * @return
     */
    private Boolean connectToHRBelt(BLEDevice hrDevice) {
        Boolean isHRBeltConnected = false;

        bledevice = hrDevice;
        if (bledevice != null) {
            logger.info("Trying to connect to HR belt now.");
            // TODO: Should I adapt these parameters?
            bgapi.send_gap_connect_direct(BDAddr.fromString(bledevice.getAddress()), 1, 0x3C, 0x3C, 0x64,0);
            isHRBeltConnected = true;
        }

        // TODO: This return logic is probably not needed. Should be in response method to the connection call.
        return isHRBeltConnected;
    }

    // TODO: Not sure if I understood this correctly...
    // Most of this code was adapted from BLEExplorerDialog.java
    private void disconnectBLEDevice() {
        bledevice = null;

        if (connection >= 0) {
            bgapi.send_connection_disconnect(connection);
        }
        connection = -1;    // TODO: Is connection now for the BLED112 or a single device?
    }

    // Callbacks for class system (index = 0)
    @Override
    public void receive_system_get_info(int major, int minor, int patch, int build, int ll_version, int protocol_version, int hw) {
        System.out.println("Connected BLED112: " + major + "." + minor + "." + patch + " (" + build + ") " + "ll=" + ll_version + " hw=" + hw);
    }

    // Callbacks for class connection (index = 3)
    @Override
    public void receive_connection_status(int conn, int flags, BDAddr address, int address_type, int conn_interval, int timeout, int latency, int bonding) {
        logger.info("Receiving ble device connection status.");  // TODO: Remove later.

        if (flags != 0) {
            logger.info("Connection status received.");
            bledevice = devList.getFromAddress(address.toString());
            connection = conn;
        } else {
            logger.info("Connection was lost!");
            connection = -1;
            bledevice = null;
        }
    }

    // Callbacks for class attclient (index = 4)
    // From BLEExplorerDialog.java // TODO: Decide on integration.
    @Override
    public void receive_attributes_value(int connection, int reason, int handle, int offset, byte[] value) {
        System.out.println("Attribute Value att=" + Integer.toHexString(handle) + " val = " + bytesToString(value));
    }

    // From BLEExplorerDialog.java // TODO: Decide on integration.
    @Override
    public void receive_attclient_procedure_completed(int connection, int result, int chrhandle) {
        logger.info("Receive callback.");       // TODO: Remove later.

        if (discovery_state != IDLE && bledevice != null) {
            if (discovery_state == SERVICES) { // services have been discovered
                discovery_it = bledevice.getServices().values().iterator();
                discovery_state = ATTRIBUTES;
            }
            if (discovery_state == ATTRIBUTES) {
                if (discovery_it.hasNext()) {
                    discovery_srv = discovery_it.next();
                    bgapi.send_attclient_find_information(connection, discovery_srv.getStart(), discovery_srv.getEnd());
                } else { // Discovery is done
                    System.out.println("Discovery completed:");
                    System.out.println(bledevice.getGATTDescription());
                    discovery_state = IDLE;
                }
            }
        }
        if (result != 0) {
            System.err.println("ERROR: Attribute Procedure Completed with error code 0x" + Integer.toHexString(result));
        }
    }

    // From BLEExplorerDialog.java // TODO: Decide on integration.
    @Override
    public void receive_attclient_group_found(int connection, int start, int end, byte[] uuid) {
        logger.info("Receive callback.");       // TODO: Remove later.

        if (bledevice != null) {
            BLEService srv = new BLEService(uuid, start, end);
            bledevice.getServices().put(srv.getUuidString(), srv);
        }
    }

    // From BLEExplorerDialog.java // TODO: Decide on integration.
    @Override
    public void receive_attclient_find_information_found(int connection, int chrhandle, byte[] uuid) {
        logger.info("Receive callback.");       // TODO: Remove later.

        if (discovery_state == ATTRIBUTES && discovery_srv != null) {
            BLEAttribute att = new BLEAttribute(uuid, chrhandle);
            discovery_srv.getAttributes().add(att);
        }
    }

    // From BLEExplorerDialog.java // TODO: Decide on integration.
    @Override
    public void receive_attclient_attribute_value(int connection, int atthandle, int type, byte[] value) {
        System.out.println("Attclient Value att=" + Integer.toHexString(atthandle) + " val = " + bytesToString(value));
    }

    // Callbacks for class gap (index = 6)
    @Override
    public void receive_gap_scan_response(int rssi, int packet_type, BDAddr sender, int address_type, int bond, byte[] data) {

        // TODO: This weirdly fails sometimes. Maybe need a way to restart the scan if there is no response being received...
        // From BLEExplorerDialog.java
        BLEDevice d = devList.getFromAddress(sender.toString());
        if (d == null) {
            d = new BLEDevice(sender.toString());
            devList.add(d);

            String name = new String(data).trim();
            if (d.getName().length() < name.length()) d.setName(name);
            d.setRssi(rssi);
            devList.changed(d);

            System.out.println("Create device: " + d.toString());

            // When the HR belt is found, update the state variable
            if (sender.toString().equals(HR_BELT_MAC)) {
                isHRBeltAvailable = true;
                if (!isHRBeltConnected) connectToHRBelt(d);
            }
        }
    }

    public String bytesToString(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        result.append("[ ");
        for (byte b : bytes) result.append(Integer.toHexString(b & 0xFF) + " ");
        result.append("]");
        return result.toString();
    }

    public Integer getConnection() {
        return connection;
    }
}
