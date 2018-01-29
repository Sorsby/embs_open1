package lsi.q2ptolemy;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.IntToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/**
 * This class represents a Ptolemy actor responsible for syncing with sink nodes.
 */
public class SourceNodeActor extends TypedAtomicActor {

    private static final int NUM_CHANNELS = 5;
    private static final int CHANNEL_OFFSET = 11;
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
        int currentTimeMs = (int) Math.ceil(ptolemyTime.getDoubleValue() * 1000);
        int n = -1;

        if (input.hasToken(0))
            n = ((IntToken) input.get(0)).intValue();

        if (n >= SinkNodeModel.MIN_N) sourceNode.readBeacon(currentTimeMs, n);
        else if (!ptolemyTime.equals(firingTime)) return;

        if (!isFiring) sourceNode.registerNextFire(currentTimeMs);
        isFiring = false;

        if (!firingDataOutput(ptolemyTime)) startListening();
    }

    private void startListening() throws IllegalActionException {
        switchToChannel(sourceNode.getCurrentChannel());
        scheduleFireTime(new Time(getDirector(), ((double) sourceNode.getNextFireTime()) / 1000));
    }

    private void switchToChannel(int channel) throws IllegalActionException {
        if (currentChannel != channel) {
            channelOutput.send(0, new IntToken(CHANNEL_OFFSET + channel));
            currentChannel = channel;
        }
    }

    private void scheduleFireTime(Time nextFireTime) throws IllegalActionException {
        this.firingTime = nextFireTime;
        getDirector().fireAt(this, nextFireTime);
    }

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