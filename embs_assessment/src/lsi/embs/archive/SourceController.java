package lsi.embs.archive;

/**
 * The main controller for a source node
 * <p>
 * Among other things, this class controls the "math" side of:
 * - Owning the SinkSyncData objects for multiple sinks
 * - Channel hopping (which channels to receive beacons on)
 * - When to send packets
 */
public class SourceController {
    /**
     * Default time before hopping to another channel
     */
    private static final long TIME_HOP = 2000;

    /**
     * Extra time to give a channel if data is received on it
     */
    private static final long TIME_HOP_DATA = SinkSyncData.DELTA_T_UPPER + 50;

    /**
     * The SinkSyncData objects for each sink
     */
    private final SinkSyncData[] sinkData;

    /**
     * If 1 for a channel, a packet should be sent asap
     * <p>
     * This is a byte array since moterunner does not support boolean arrays.
     */
    private final byte[] sendPending;

    /**
     * The last reception phase a packet was sent on
     */
    private final long[] lastReceptionPhase;

    /**
     * The next time the source should wakeup for a send event. This is -1 if nothing is pending.
     */
    private long nextSendWakeup;

    /**
     * Current channel to listen for beacons on
     * <p>
     * Channels are numbered from 0. If readChannel == -1, nothing is to be read.
     */
    private int readChannel;

    /**
     * Expiry time for the current channel hop.
     */
    private long hopExpiry;

    /**
     * If true, the hop time has already been extended.
     */
    private boolean hopExtended;

    SourceController(int channels) {
        sinkData = new SinkSyncData[channels];
        sendPending = new byte[channels];
        lastReceptionPhase = new long[channels];

        for (int i = 0; i < channels; i++)
            sinkData[i] = new SinkSyncData();

        reset();
    }

    private int getChannelCount() {
        return sinkData.length;
    }

    public int getReadChannel() {
        return readChannel;
    }

    public int calcSendChannel() {
        for (int i = 0; i < getChannelCount(); i++) {
            if (sendPending[i] != 0) {
                sendPending[i] = 0;
                return i;
            }
        }

        return -1;
    }

    public long getNextWakeupTime() {
        long lowest = nextSendWakeup;

        if (lowest == -1 || hopExpiry < lowest)
            lowest = hopExpiry;

        return lowest;
    }

    public void reset() {
        // Reset per sink data
        for (int i = 0; i < getChannelCount(); i++) {
            sinkData[i].reset();
            sendPending[i] = 0;
            lastReceptionPhase[i] = 0;
        }

        nextSendWakeup = -1;

        // Reset the read channel
        readChannel = 0;

        // Initialize hop timer
        hopExpiry = TIME_HOP;
        hopExtended = false;
    }

    public void receiveBeacon(long absoluteTime, int n) {
        // Ignore spurious receive
        if (readChannel < 0)
            return;

        // Forward to sink object
        sinkData[readChannel].receiveBeacon(absoluteTime, n);

        // If n is not 1, and this was the first beacon, extend the hop time
        //  to try and immediately get another beacon
        if (n != 1 && !hopExtended) {
            // Extend the hop if it was the first
            hopExpiry = absoluteTime + TIME_HOP_DATA;
            hopExtended = true;
        } else {
            // Otherwise there is no point staying on this channel at the moment
            changeChannel(absoluteTime);
        }
    }

    private void changeChannel(long absoluteTime) {
        int firstChannel = readChannel + 1;
        readChannel = -1;

        // Find another suitable channel
        for (int i = 0; i < getChannelCount(); i++) {
            int channel = (firstChannel + i) % getChannelCount();

            if (!sinkData[channel].hasGoodDeltaT() || !sinkData[channel].hasGoodN()) {
                // We must ensure that SOME channel is selected if any remaining channels
                //  need some data
                readChannel = channel;

                // Break if this channel is definitely suitable
                if (sinkData[channel].nextInterestingBeacon(absoluteTime) < absoluteTime + TIME_HOP)
                    break;
            }
        }

        // Reset timer
        hopExpiry = absoluteTime + TIME_HOP;
        hopExtended = false;
    }

    public void wakeupEvent(long absoluteTime) {
        nextSendWakeup = -1;

        // Recalculate all the pending packet sends
        for (int i = 0; i < getChannelCount(); i++) {
            long sendTime = sinkData[i].calcReceptionPhase(absoluteTime);

            if (sendTime > 0) {
                if (sendTime <= absoluteTime) {
                    // Only signal a wakeup if we haven't already handled this reception phase
                    if (sendTime != lastReceptionPhase[i]) {
                        sendPending[i] = 1;
                        lastReceptionPhase[i] = sendTime;
                    }

                    // Try and wakeup in exactly one iteration if possible
                    long iterationLength = sinkData[i].getIterationLength();
                    if (iterationLength != 0)
                        sendTime += iterationLength;
                    else
                        continue;
                }

                if (nextSendWakeup == -1 || sendTime < nextSendWakeup)
                    nextSendWakeup = sendTime;
            }
        }

        // Change channel if the hop timer has expired
        if (absoluteTime >= hopExpiry)
            changeChannel(absoluteTime);
    }
}