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

    private static final Radio RADIO = new Radio();
    private static final Timer TIMER = new Timer();

    /**
     * Transmit buffer
     */
    private static final byte[] XMIT = new byte[12];

    /**
     * Currently receiving if true.
     */
    private static boolean rxEnabled;

    //static constructor to init the mote source node.
    static {
        RADIO.open(Radio.DID, null, 0, 0);

        //set callback for the TIMER for reading beacons and firings.
        TIMER.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                timerCb(param, time);
            }
        });

        //set callback for the reception phase on the RADIO.
        RADIO.setRxHandler(new DevCallback(null) {
            @Override
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return readBeacon(flags, data, len, info, time);
            }
        });

        //set callback for the transmission phase on the RADIO.
        RADIO.setTxHandler(new DevCallback(null) {
            @Override
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return transmissionCb(flags, data, len, info, time);
            }
        });

        //RADIO setup
        XMIT[0] = Radio.FCF_DATA;
        XMIT[1] = Radio.FCA_SRC_SADDR | Radio.FCA_DST_SADDR;
        Util.set16le(XMIT, 9, SOURCE_NODE_SHORT_ADDR);
        XMIT[11] = MOTE_PAYLOAD;

        //switch to an initial channel
        switchChannel(sourceNode.getCurrentChannel());
        //initial firing
        handleNextAction();

        //turn on yellow led to indicate the source node is enabled.
        LED.setState(YELLOW_LED, (byte) 1);
    }

    /**
     * Delegate method for our transmit callbacks.
     * When we finish trasnmitting, call {@link #handleNextAction()} to handle the next action for the mote.
     */
    private static int transmissionCb(int flags, byte[] data, int len, int info, long time) {
        handleNextAction();
        return 0;
    }

    /**
     * Delegate method for our timer callbacks.
     * Just register the next firing time and call {@link #handleNextAction()} to handle the next action for the mote.
     */
    private static void timerCb(byte param, long time) {
        sourceNode.registerNextFire(Time.currentTime(Time.MILLISECS));
        handleNextAction();
    }

    /**
     * Delegate method for the receive callback.
     * When we receive a beacon, forward it to our {@link #sourceNode} and process as normal.
     * Finally call {@link #handleNextAction()} to handle the next action for the mote.
     */
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

        handleNextAction();
        return 0;
    }

    /**
     * Handles timer callbacks and performing the correct SourceNode action at any time.
     * Such as receiving beacons or transmitting to a channel.
     */
    private static void handleNextAction() {
        TIMER.cancelAlarm();

        long nextFireTime = sourceNode.getNextFireTime();
        if (nextFireTime > 0)
            TIMER.setAlarmTime(Time.toTickSpan(Time.MILLISECS, nextFireTime));

        int sendChannel = sourceNode.getFireChannel();

        if (sendChannel != -1) {
            //we have a channel to transmit on.
            switchChannel(sendChannel);
            RADIO.transmit(Device.ASAP | Radio.TXMODE_POWER_MAX | Radio.TXMODE_CCA, XMIT, 0, XMIT.length, 0);
            LED.setState(RED_LED, (byte) (1 - LED.getState(RED_LED)));
        } else {
            //read beacons because no transmit needed.
            int currentChannel = sourceNode.getCurrentChannel();

            if (currentChannel != -1) {
                switchChannel(currentChannel);
                RADIO.startRx(Device.ASAP | Device.RX4EVER, 0, 0);
                rxEnabled = true;
            } else if (rxEnabled) {
                RADIO.stopRx();
                rxEnabled = false;
            }
        }
    }

    /**
     * Method to switch the current wireless channel.
     * Turns off the radio before perfomring the channel switch.
     * @param channel the channel to switch to.
     */
    private static void switchChannel(int channel) {
        RADIO.setState(Device.S_OFF);

        Util.set16le(XMIT, 3, PAN_ID_OFFSET + channel);
        Util.set16le(XMIT, 5, PAN_ID_OFFSET + channel);
        Util.set16le(XMIT, 7, PAN_ID_OFFSET + channel);

        RADIO.setChannel((byte) channel);
        RADIO.setPanId(PAN_ID_OFFSET + channel, true);
    }
}
