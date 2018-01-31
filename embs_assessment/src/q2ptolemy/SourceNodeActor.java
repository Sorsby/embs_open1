//Y3606797
package q2ptolemy;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.IntToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import q2.SinkNodeModel;
import q2.SourceNode;

/**
 * This class represents a Ptolemy actor responsible for syncing with sink nodes.
 */
public class SourceNodeActor extends TypedAtomicActor {

    /**
     * The channel count.
     */
    private static final int NUM_CHANNELS = 5;

    /**
     * An offset for determining the read/write channel in ptolemy, used to simplify the code when we work with arrays.
     */
    private static final int CHANNEL_OFFSET = 11;

    /**
     * Arbitrary payload constant.
     */
    private static final int PTOLEMY_PAYLOAD = 1337;

    private final TypedIOPort input, dataOutput;
    private final TypedIOPort channelOutput;

    private Time firingTime;
    private boolean isFiring;

    private SourceNode sourceNode;
    private int currentChannel;

    public SourceNodeActor(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        input = new TypedIOPort(this, "input", true, false);
        channelOutput = new TypedIOPort(this, "channelOutput", false, true);
        dataOutput = new TypedIOPort(this, "dataOutput", false, true);

        input.setTypeEquals(BaseType.INT);
        channelOutput.setTypeEquals(BaseType.INT);
        dataOutput.setTypeEquals(BaseType.INT);
    }

    @Override
    public void initialize() throws IllegalActionException {
        firingTime = null;
        isFiring = false;
        currentChannel = -1;

        sourceNode = new SourceNode(NUM_CHANNELS);

        startListening();
    }

    @Override
    public void fire() throws IllegalActionException {
        Time ptolemyTime = getDirector().getModelTime();
        //convert to milisecs because mote uses that.
        int currentTimeMs = (int) Math.ceil(ptolemyTime.getDoubleValue() * 1000);
        //init n to -1 se we can control the order of nodes different operations.
        int n = -1;

        if (input.hasToken(0))
            n = ((IntToken) input.get(0)).intValue();

        //if we have a valid beacon we read the beacon at this time and process it further to determine sink params.
        if (n >= SinkNodeModel.MIN_N) sourceNode.readBeacon(currentTimeMs, n);
        //if we don't have a valid n and we aren;t scheduled to fire at this time, just return
        else if (!ptolemyTime.equals(firingTime)) return;

        //if we're not currently firing, schedule the next fire for the channels we can calc the rx phase for.
        if (!isFiring) sourceNode.registerNextFire(currentTimeMs);
        isFiring = false;

        //if we aren't able to fire any output then start listening for beacons again.
        if (!firingDataOutput(ptolemyTime)) startListening();
    }

    /**
     * Switches channel to an appropriate one for reading beacons.
     * @throws IllegalActionException
     */
    private void startListening() throws IllegalActionException {
        switchToChannel(sourceNode.getCurrentChannel());
        scheduleFireTime(new Time(getDirector(), ((double) sourceNode.getNextFireTime()) / 1000));
    }

    /**
     * Switches to the channel specified as an argument.
     * @param channel the channel to switch to.
     * @throws IllegalActionException
     */
    private void switchToChannel(int channel) throws IllegalActionException {
        if (currentChannel != channel) {
            channelOutput.send(0, new IntToken(CHANNEL_OFFSET + channel));
            currentChannel = channel;
        }
    }

    /**
     * Schedule a fire at the time specified as an argument.
     * @param nextFireTime the next time to fire the actor.
     * @throws IllegalActionException
     */
    private void scheduleFireTime(Time nextFireTime) throws IllegalActionException {
        this.firingTime = nextFireTime;
        getDirector().fireAt(this, nextFireTime);
    }

    /**
     * Determines a channel for which we can fire an output,
     * switches to said channel and fires a payload of value {@value #PTOLEMY_PAYLOAD}
     * @param timeToFire The time at which to fire the actor.
     * @return a boolean indicating whether or not we have fired a channel.
     * @throws IllegalActionException
     */
    private boolean firingDataOutput(Time timeToFire) throws IllegalActionException {
        int fireChannel = sourceNode.getFireChannel();
        if (fireChannel == -1) return false;

        switchToChannel(fireChannel);
        dataOutput.send(0, new IntToken(PTOLEMY_PAYLOAD));
        scheduleFireTime(timeToFire);
        isFiring = true;
        System.out.println(CHANNEL_OFFSET + fireChannel + " at " + getDirector().getModelTime());
        return true;
    }

}