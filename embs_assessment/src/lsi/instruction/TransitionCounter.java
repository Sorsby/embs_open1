package lsi.instruction;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.IntToken;
import ptolemy.data.StringToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class TransitionCounter extends TypedAtomicActor {

    private static final int PROCESSOR_BUS_WIDTH = 16;
    private static final int POWER_SAVE_INVERT_CONSTANT = PROCESSOR_BUS_WIDTH / 2;
    private static final String PROCESSOR_BUS_INITIAL_STATE = String.format("%0" + PROCESSOR_BUS_WIDTH + "d", 0);
    private static final double CLOCK_PERIOD = 0.00000001;

    private TypedIOPort input;
    private TypedIOPort output;

    private Parameter invert;

    private String prevNumber;
    private int totalTransitions;

    public TransitionCounter(CompositeEntity container, String name)
            throws NameDuplicationException, IllegalActionException {
        super(container, name);

        input = new TypedIOPort(this, "input", true, false);
        output = new TypedIOPort(this, "output", false, true);

        input.setTypeEquals(BaseType.STRING);
        output.setTypeEquals(BaseType.INT);

        invert = new Parameter(this,"invert");
        invert.setExpression("");
    }

    @Override
    public void initialize() throws IllegalActionException {
        prevNumber = PROCESSOR_BUS_INITIAL_STATE;
        totalTransitions = 0;
        //fire immediately
        scheduleFireTime(getDirector().getModelTime());
    }

    @Override
    public void fire() throws IllegalActionException {
        Time ptolemyTime = getDirector().getModelTime();
        String curNumber = "";

        while (input.hasToken(0)) {
            curNumber = ((StringToken) input.get(0)).stringValue();

            boolean invertFlag = !invert.getExpression().isEmpty();
            totalTransitions += calcHammingDistance(curNumber, prevNumber, invertFlag);
            prevNumber = curNumber;
        }

        scheduleFireTime(ptolemyTime.add(CLOCK_PERIOD));

        if (curNumber.isEmpty() && totalTransitions != -1) {
            output.send(0, new IntToken(totalTransitions));
        }
    }

    private int calcHammingDistance(String curNumber, String prevNumber) {
        return calcHammingDistance(curNumber, prevNumber, false);
    }

    private int calcHammingDistance(String curNumber, String prevNumber, boolean invertFlag) {
        if (invertFlag) {
            curNumber += "0";
            prevNumber += "0";
        }

        int hammingDistance = 0;
        if (curNumber.length() == prevNumber.length()) {
            for (int i = 0; i < curNumber.length(); i++) {
                if (curNumber.charAt(i) != prevNumber.charAt(i)) {
                    hammingDistance += 1;
                }
            }
        }

        if (hammingDistance > POWER_SAVE_INVERT_CONSTANT && invertFlag) {
            prevNumber += "1";
            String invertedCurNumber = invertBitString(curNumber);
            invertedCurNumber += "1";
            return calcHammingDistance(invertedCurNumber, prevNumber);
        } else {
            return hammingDistance;
        }
    }

    private String invertBitString(String bitString) {
        return bitString.replaceAll("0", "x").replaceAll("1", "0").replaceAll("x", "1");
    }

    private void scheduleFireTime(Time nextFireTime) throws IllegalActionException {
        getDirector().fireAt(this, nextFireTime);
    }
}
