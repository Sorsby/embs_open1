package q2mote;

import com.ibm.saguaro.system.*;
import q2.SourceNode;

/**
 * Implementation of a source node using the same logic as the ptolemy source node,
 * except applied to the IRIS Motes as seen in labs.
 */
public final class MySourceNode {
    private static final byte YELLOW_LED = (byte) 0;
    private static final byte GREEN_LED = (byte) 1;
    private static final byte RED_LED = (byte) 2;

    /**
     * Channel count.
     */
    private static final int NUM_CHANNELS = 3;

    /**
     * Instance of SourceNode, created for Ptolemy task but reused here with different channels and params.
     * For mote remember to change MIN_T and MIN_N.
     */
    private static final SourceNode sourceNode = new SourceNode(NUM_CHANNELS);

    /**
     * Constant arbitrary payload to transmit
     */
    private static final byte MOTE_PAYLOAD = 0x11;

    /**
     * Number to add to the channel id to get the PAN id
     */
    private static final int PAN_ID_OFFSET = 0x11;

    /**
     * The short address of this source node
     */
    private static final int SOURCE_NODE_SHORT_ADDR = 0x42;

    private static final Radio radio = new Radio();
    private static final Timer timer = new Timer();

    /**
     * Transmit buffer
     */
    private static final byte[] xmit = new byte[12];

    /**
     * Currently receiving if true.
     */
    private static boolean rxEnabled;

    //static constructor to init the mote source node.
    static {
        radio.open(Radio.DID, null, 0, 0);

        //set callback for the timer for reading beacons and firings.
        timer.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                timerCb(param, time);
            }
        });

        //set callback for the reception phase on the radio.
        radio.setRxHandler(new DevCallback(null) {
            @Override
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return readBeacon(flags, data, len, info, time);
            }
        });

        //set callback for the transmission phase on the radio.
        radio.setTxHandler(new DevCallback(null) {
            @Override
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return transmissionCb(flags, data, len, info, time);
            }
        });

        //radio setup
        xmit[0] = Radio.FCF_DATA;
        xmit[1] = Radio.FCA_SRC_SADDR | Radio.FCA_DST_SADDR;
        Util.set16le(xmit, 9, SOURCE_NODE_SHORT_ADDR);
        xmit[11] = MOTE_PAYLOAD;

        //switch to an initial channel
        switchChannel(sourceNode.getCurrentChannel());
        //initial firing
        fireSourceNode();

        //turn on yellow led to indicate the source node is enabled.
        LED.setState(YELLOW_LED, (byte)1);
    }

    private static int transmissionCb(int flags, byte[] data, int len, int info, long time) {
        fireSourceNode();
        return 0;
    }

    private static void timerCb(byte param, long time) {
        sourceNode.registerNextFire(Time.currentTime(Time.MILLISECS));
        fireSourceNode();
    }

    private static int readBeacon(int flags, byte[] data, int len, int info, long time) {
        if (data != null) {
            long fireTime = Time.currentTime(Time.MILLISECS);
            int n = data[11];

            sourceNode.readBeacon(fireTime, n);
            LED.setState(GREEN_LED, (byte) (1 - LED.getState(GREEN_LED)));

            sourceNode.registerNextFire(fireTime);
        } else {
            rxEnabled = false;
        }

        fireSourceNode();
        return 0;
    }

    private static void fireSourceNode() {
        timer.cancelAlarm();

        long nextFireTime = sourceNode.getNextFireTime();
        if (nextFireTime > 0)
            timer.setAlarmTime(Time.toTickSpan(Time.MILLISECS, nextFireTime));

        int sendChannel = sourceNode.getFireChannel();

        if (sendChannel != -1) {
            switchChannel(sendChannel);
            radio.transmit(Device.ASAP | Radio.TXMODE_POWER_MAX | Radio.TXMODE_CCA, xmit, 0, xmit.length, 0);
            LED.setState(RED_LED, (byte) (1 - LED.getState(RED_LED)));

        } else {
            int readChannel = sourceNode.getCurrentChannel();

            if (readChannel != -1) {
                switchChannel(readChannel);
                radio.startRx(Device.ASAP | Device.RX4EVER, 0, 0);
                rxEnabled = true;
            } else if (rxEnabled) {
                radio.stopRx();
                rxEnabled = false;
            }
        }
    }

    private static void switchChannel(int channel) {
        radio.setState(Device.S_OFF);

        Util.set16le(xmit, 3, PAN_ID_OFFSET + channel);
        Util.set16le(xmit, 5, PAN_ID_OFFSET + channel);
        Util.set16le(xmit, 7, PAN_ID_OFFSET + channel);

        radio.setChannel((byte) channel);
        radio.setPanId(PAN_ID_OFFSET + channel, true);
    }
}
