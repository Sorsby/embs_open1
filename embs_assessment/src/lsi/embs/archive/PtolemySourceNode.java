package lsi.embs.archive;

import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.actor.Director;
import ptolemy.actor.util.Time;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.IntToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;

public class PtolemySourceNode extends TypedAtomicActor {
    private static final int CHANNELS = 5;
    private static final int EXT_CHANNEL_OFFSET = 11;
    private static final int PTOLEMY_PAYLOAD = 1337;

    private final TypedIOPort input, output;
    private final TypedIOPort setChannel;

    private final SourceController controller = new SourceController(CHANNELS);

    private int currentChannel;
    private Time timerTime;
    private boolean isSending;

    public PtolemySourceNode(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        input = new TypedIOPort(this, "input", true, false);
        setChannel = new TypedIOPort(this, "setChannel", false, true);
        output = new TypedIOPort(this, "output", false, true);

        input.setTypeEquals(BaseType.INT);
        setChannel.setTypeEquals(BaseType.INT);
        output.setTypeEquals(BaseType.INT);
    }

    private void setChannel(int channel) throws IllegalActionException {
        if (currentChannel != channel) {
            setChannel.send(0, new IntToken(EXT_CHANNEL_OFFSET + channel));
            currentChannel = channel;
        }
    }

    private void setWakeupTime(Time time) throws IllegalActionException {
        // Schedule next timer event
        timerTime = time;
        if (time != null)
            getDirector().fireAt(this, time);
    }

    private void setReading() throws IllegalActionException {
        // Set read channel and schedule next wakeup
        setChannel(controller.getReadChannel());
        setWakeupTime(new Time(getDirector(), ((double) controller.getNextWakeupTime()) / 1000));
    }

    private boolean sendPendingPacket() throws IllegalActionException {
        // Send any packets if necessary
        int sendChannel = controller.calcSendChannel();
        if (sendChannel >= 0) {
            setChannel(sendChannel);
            output.send(0, new IntToken(PTOLEMY_PAYLOAD));

            // Fire immediately
            setWakeupTime(getDirector().getModelTime());
            isSending = true;
            System.out.println(EXT_CHANNEL_OFFSET + sendChannel + " at " + getDirector().getModelTime());
            return true;
        }

        return false;
    }

    @Override
    public void initialize() throws IllegalActionException {
        // Reset the controller and start reading
        controller.reset();
        currentChannel = -1;
        timerTime = null;
        isSending = false;

        setReading();
    }

    @Override
    public void fire() throws IllegalActionException {
        Director director = getDirector();
        long time = (long) Math.ceil(director.getModelTime().getDoubleValue() * 1000);

        // Handle any received tokens
        //  We only accept the last token received
        int nValue = -1;

        while (input.hasToken(0))
            nValue = ((IntToken) input.get(0)).intValue();

        if (nValue >= 1) {
            controller.receiveBeacon(time, nValue);
        } else {
            // Ensure this is an intended timer event
            if (timerTime != null && !director.getModelTime().equals(timerTime))
                return;
        }

        // Tell the controller that we've woken up
        //  To ensure we don't get any sending loops, we do not do this if this
        //  was a sending wakeup
        if (!isSending)
            controller.wakeupEvent(time);

        isSending = false;

        // Send any packets if necessary
        if (!sendPendingPacket()) {
            // If nothing was sent, enter read mode
            setReading();
        }
    }
}