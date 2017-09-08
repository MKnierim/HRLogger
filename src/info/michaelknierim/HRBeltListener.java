package info.michaelknierim;

import gnu.io.NoSuchPortException;
import gnu.io.SerialPort;
import org.thingml.bglib.*;
import org.thingml.bglib.gui.*;

import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;
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

    // Set defaults for BLED112 options (scanning, connecting, package lookup)
    private final String PORT_NAME = "/dev/tty.usbmodem1";
    Boolean DEBUG = false;

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

    // HR Service & Attribute Parameters
    int att_handle_measurement = 0;
    int att_handle_measurement_config = 0;
    private byte[] uuid_service = new byte[]{0x28, 0x00};           // 0x2800
    private byte[] uuid_client_characteristic_configuration = new byte[]{0x29, 0x02}; // 0x2902
    private byte[] uuid_hr_service = new byte[]{0x18, 0x0D};        // 0x180D
    private byte[] uuid_hr_characteristic = new byte[]{0x2A, 0x37}; // 0x2A37

    public HRBeltListener() {
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
            resetBLED112Status();

            // Start scanning for BLE devices and connect to HR belt when it is available.
            discoverAndConnect();
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
     * ...
     */
    private void resetBLED112Status() {
        // Disconnect if connected already.
        bgapi.send_connection_disconnect(connection);

        // Stop advertising if advertising already.
        bgapi.send_gap_set_mode(0, 0);

        // Stop scanning if scanning already.
        bgapi.send_gap_end_procedure();
    }

    /**
     * This starts scanning for available BLE devices.
     */
    private void discoverAndConnect() {
        devList.clear();
        bgapi.send_gap_set_scan_parameters(10, 250, 1);
        bgapi.send_gap_discover(1);
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
     *
     * @return
     */
    private void connectToHRBelt(BLEDevice hrDevice, int hrDeviceAddressType) {
        bledevice = hrDevice;

        if (bledevice != null) {
            logger.info("Trying to connect to HR belt now.");

            bgapi.send_gap_connect_direct(BDAddr.fromString(bledevice.getAddress()), hrDeviceAddressType, 0x3C, 0x3C, 0x64, 0);
        }
    }

    /**
     * Here the data collection process from the HR belt is handled.
     */
    private void readOutHRBeltData() {
        // Perform service discovery
        discovery_state = SERVICES;     // TODO: Not sure if I want to keep this.
        logger.info("Performing service discovery now.");

        bgapi.send_attclient_read_by_group_type(connection, 0x0001, 0xFFFF, getReverseByteArray(uuid_service));
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
        if (flags != 0) {
            logger.info("Connection status received.");
            bledevice = devList.getFromAddress(address.toString());
            connection = conn;

            // TODO: This if-clause is probably not required in this case.
            // If connected, perform service discovery
            if (bledevice.getAddress().equals(HR_BELT_MAC)) {
                isHRBeltConnected = true;
                // Start reading out data packets
                readOutHRBeltData();
            }
        } else {
            logger.info("Connection was lost!");
            connection = -1;
            bledevice = null;
        }
    }

    // Callbacks for class attclient (index = 4)
    @Override
    public void receive_attributes_value(int connection, int reason, int handle, int offset, byte[] value) {
        System.out.println("Attribute Value att=" + Integer.toHexString(handle) + " val = " + bytesToString(value));
    }

    @Override
    public void receive_attclient_procedure_completed(int connection, int result, int chrhandle) {
        if (discovery_state != IDLE && bledevice != null) {
            if (discovery_state == SERVICES) { // services have been discovered
                discovery_it = bledevice.getServices().values().iterator();
                discovery_state = ATTRIBUTES;
            }
            // Find information on service attributes
            if (discovery_state == ATTRIBUTES) {
                if (discovery_it.hasNext()) {
                    discovery_srv = discovery_it.next();
                    logger.info("Attribute discorvey started.");

                    // This scans for all attributes of available services.
                    // TODO: If I want to shorten this to only look for specific attributes I do this here by passing the attribute of interest as last param.
                    bgapi.send_attclient_find_information(connection, discovery_srv.getStart(), discovery_srv.getEnd());
                } else {
                    logger.info("Service discovery completed.");
                    logger.info(bledevice.getGATTDescription());
                    discovery_state = IDLE;

                    // TODO: Integrate this with rest of the code later.
                    // Subscribe listener to specific attribute of a service (here: HR).
                    bgapi.send_attclient_attribute_write(connection, att_handle_measurement_config, new byte[]{0x01, 0x00});

                    // TODO: Use this to read out device descriptions, etc.
                    // Alternative approach to read by handle.
                    // bgapi.send_attclient_read_by_handle(connection, 0x18);
                    // bgapi.send_attributes_read(0x180D, 0);
                }
            }
        }
        if (result != 0) {
            System.err.println("ERROR: Attribute Procedure Completed with error code 0x" + Integer.toHexString(result));
        }
    }

    @Override
    public void receive_attclient_group_found(int connection, int start, int end, byte[] uuid) {
        // Collect available services.
        if (bledevice != null) {
            BLEService srv = new BLEService(uuid, start, end);
            bledevice.getServices().put(srv.getUuidString(), srv);
        }
    }

    @Override
    public void receive_attclient_find_information_found(int connection, int chrhandle, byte[] uuid) {
        // Collect available attributes of a service.
        if (discovery_state == ATTRIBUTES && discovery_srv != null) {
            BLEAttribute att = new BLEAttribute(uuid, chrhandle);
            discovery_srv.getAttributes().add(att);

            // Check for heart rate measurement characteristic.
            if (Arrays.equals(uuid, getReverseByteArray(uuid_hr_characteristic))) {
                logger.info("Changes att_handle_measurement to: " + chrhandle);
                att_handle_measurement = chrhandle;
            }
            // Check for subsequent client characteristic configuration
            else if (Arrays.equals(uuid, getReverseByteArray(uuid_client_characteristic_configuration))
                    && att_handle_measurement > 0) {
                logger.info("Changes att_handle_measurement_config to: " + chrhandle);
                att_handle_measurement_config = chrhandle;
            }
        }
    }

    @Override
    public void receive_attclient_attribute_value(int connection, int atthandle, int type, byte[] value) {
        // Check for a new value from the connected peripheral's heart rate measurement attribute.
        if (isHRBeltConnected) {
            int hr_flags = value[0];    // TODO: Not sure if needed or correct...
            int hr_value = value[1];    // TODO: Not sure if needed or correct...

            // System.out.println("Attclient Value att=" + Integer.toHexString(atthandle) + " val = " + bytesToString(value));
            System.out.println("HR value: " + hr_value);
        }
    }

    // Callbacks for class gap (index = 6)
    @Override
    public void receive_gap_scan_response(int rssi, int packet_type, BDAddr sender, int address_type, int bond, byte[] data) {

        // TODO: This weirdly fails sometimes. Maybe need a way to restart the scan if there is no response being received...
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

                // Print all values for the heart belt monitor
                // printHRDeviceConnectionData(rssi, packet_type, sender, address_type, bond, data);

                if (!isHRBeltConnected) connectToHRBelt(d, address_type);
            }
        }
    }

    /**
     * Turns byte array values into a String.
     *
     * @param bytes
     * @return
     */
    public String bytesToString(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        result.append("[ ");
        for (byte b : bytes) result.append(Integer.toHexString(b & 0xFF) + " ");
        result.append("]");
        return result.toString();
    }

    /**
     * Gets the current connection status.
     *
     * @return
     */
    public Integer getConnection() {
        return connection;
    }

    /**
     * ...
     */
    private void printHRDeviceConnectionData(int rssi, int packet_type, BDAddr sender, int address_type, int bond, byte[] data) {
        System.out.println("Rssi: " + rssi);
        System.out.println("Packet type: " + packet_type);
        System.out.println("BDAddr: " + sender);
        System.out.println("Address Type: " + address_type);
        System.out.println("Bond: " + bond);
        for (byte b : data) {
            System.out.println("Data " + b);
        }
    }

    /**
     * ...
     *
     * @param inputArray
     * @return
     * @throws IndexOutOfBoundsException
     */
    private byte[] getReverseByteArray(byte[] inputArray) throws IndexOutOfBoundsException {
        byte[] outputArray = new byte[2];

        // This is used so that only the arrays of length 2 are switched.
        if (inputArray.length != 2) throw new IndexOutOfBoundsException();
        else {
            outputArray[0] = inputArray[1];
            outputArray[1] = inputArray[0];
        }

        return outputArray;
    }
}
