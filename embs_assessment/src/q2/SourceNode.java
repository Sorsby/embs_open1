package q2;

/**
 * This class represents the logic of the source node, when to fire and to which channel etc.
 */
public class SourceNode {

    /**
     * A constant defining how long we listen on a particular channel before switched to another.
     */
    private static final int DEFAULT_LISTENING_TIME =
            SinkNodeModel.MAX_T + SinkNodeModel.MIN_T;

    /**
     * Stores instances of SourceNodeModel which contain the parameters and relevant information for sink nodes.
     */
    private final SinkNodeModel[] sinkNodes;

    private final byte[] fireQueue;
    private final long[] prevReceptionPhase;

    private int numChannels;

    private int currentChannel;
    private long nextFire;
    private long finishListeningTime;
    private boolean extendedListeningTime;

    public SourceNode(int numChannels) {
        this.numChannels = numChannels;
        this.sinkNodes = new SinkNodeModel[numChannels];

        for (int i = 0; i < numChannels; i++)
            this.sinkNodes[i] = new SinkNodeModel();

        this.fireQueue = new byte[numChannels];
        this.prevReceptionPhase = new long[numChannels];

        for (int i = 0; i < numChannels; i++) {
            fireQueue[i] = 0;
            prevReceptionPhase[i] = 0;
        }

        currentChannel = 0;
        nextFire = -1;
        finishListeningTime = DEFAULT_LISTENING_TIME;
        extendedListeningTime = false;
    }

    /**
     * Reads a beacon value on the current channel and forwards it to the SinkNodeModel.
     * Extends the time on the current channel if the beacon is valid and we haven't already extended the read time.
     * <p>
     * Changes channel if we have used all the allocated time for this channel at the moment.
     * @param time the time which the beacon was read.
     * @param n the payload of the beacon.
     */
    public void readBeacon(long time, int n) {
        sinkNodes[currentChannel].readBeacon(time, n);
        if (n != 1 && !extendedListeningTime) {
            finishListeningTime = time + SinkNodeModel.MAX_T;
            extendedListeningTime = true;
        } else {
            changeChannel(time);
        }
    }

    /**
     * Determines the times at which we next fire the actor on each channel.
     * If we can calculate the RX phase for a sink node we queue a fire time.
     * @param time the current time.
     */
    public void registerNextFire(long time) {
        nextFire = -1;

        for (int i = 0; i < getNumChannels(); i++) {
            long fireTime = sinkNodes[i].calcNextRxPhase(time);

            if (fireTime > 0) {
                if (fireTime <= time) {
                    //queue new fire time for node rx phase
                    if (fireTime != prevReceptionPhase[i]) {
                        fireQueue[i] = 1;
                        prevReceptionPhase[i] = fireTime;
                    }

                    long length = sinkNodes[i].totalProtocolLength();
                    if (length != 0) {
                        fireTime += length;
                    } else {
                        continue;
                    }
                }

                if (nextFire == -1 || fireTime < nextFire) {
                    nextFire = fireTime;
                }
            }
        }

        //change channel after the default listening period
        if (time >= finishListeningTime)
            changeChannel(time);
    }

    /**
     * Cycles through the defined channels looking for an appropriate one to read.
     * Sets the value of current channel to this appropriate channel.
     * @param time the current time.
     */
    private void changeChannel(long time) {
        int channel = currentChannel + 1;
        currentChannel = -1;

        for (int i = 0; i < getNumChannels(); i++) {
            int nextChannel = (channel + i) % getNumChannels();

            //if we don't know the parameters for a sink it is clearly of interest to us.
            if (!sinkNodes[nextChannel].hasT() || !sinkNodes[nextChannel].hasN()) {
                currentChannel = nextChannel;

                //if the channel in question is going to fire a beacon before the default listening time is up,
                // we can switch to this channel and break the search loop.
                if (sinkNodes[nextChannel].calcNextBeacon(time) < time + DEFAULT_LISTENING_TIME)
                    break;
            }
        }

        finishListeningTime = time + DEFAULT_LISTENING_TIME;
        extendedListeningTime = false;
    }

    private int getNumChannels() {
        return this.numChannels;
    }

    public int getCurrentChannel() {
        return this.currentChannel;
    }

    public long getNextFireTime() {
        long next = nextFire;
        if (next == -1 || finishListeningTime < next) {
            next = finishListeningTime;
        }
        return next;
    }

    /**
     * Determine which channel to fire from the queue of things to fire.
     * @return int Channel id to fire
     */
    public int getFireChannel() {
        for (int i = 0; i < getNumChannels(); i++) {
            //if the channel is waiting to be fired, return the channel id and zero the entry in the fireQueue array.
            if (fireQueue[i] != 0) {
                fireQueue[i] = 0;
                return i;
            }
        }
        return -1;
    }
}
