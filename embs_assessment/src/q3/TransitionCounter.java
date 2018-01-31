//Y3606797
package q3;

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

/**
 * A Ptolemy actor which counts the number of bit transitions on a shared bus.
 */
public class TransitionCounter extends TypedAtomicActor {

    /**
     * Constant to define the width of the bus.
     */
    private static final int PROCESSOR_BUS_WIDTH = 16;

    /**
     * Per the paper attached to the assessment, this constant defines n/2 for determining when to invert the bits of the bus.
     */
    private static final int POWER_SAVE_INVERT_CONSTANT = PROCESSOR_BUS_WIDTH / 2;

    /**
     * Constant to define the initial bus state, a string of size
     * {@value #PROCESSOR_BUS_WIDTH} of 0's.
     */
    private static final String PROCESSOR_BUS_INITIAL_STATE = String.format("%0" + PROCESSOR_BUS_WIDTH + "d", 0);

    /**
     * Period of the clock as defined in the Ptolemy model.
     */
    private static final double CLOCK_PERIOD = 0.00000001;

    private TypedIOPort input;
    private TypedIOPort output;

    /**
     * A parameter to toggle the 9 bus-invert low power coding scheme.
     */
    private Parameter invert;

    private String prevNumber;

    /**
     * Running total of the number of transitions on the shared bus.
     */
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

            //determine value of the invert param
            boolean invertFlag = !invert.getExpression().isEmpty();
            totalTransitions += calcHammingDistance(curNumber, prevNumber, invertFlag);
            //update value of prevNumber for next iteration
            prevNumber = curNumber;
        }

        //fire on the clock period.
        scheduleFireTime(ptolemyTime.add(CLOCK_PERIOD));

        if (curNumber.isEmpty() && totalTransitions != -1) {
            output.send(0, new IntToken(totalTransitions));
        }
    }

    //returns the value of the hamming distance of two strings of bits.
    private int calcHammingDistance(String curNumber, String prevNumber) {
        return calcHammingDistance(curNumber, prevNumber, false);
    }

    /**
     * Overloaded method to determine the hamming distance of two strings of bits.
     * @param curNumber the current bus value.
     * @param prevNumber the previous bus value with which to compare the current.
     * @param invertFlag a boolean value indicating whether to use the low power invert coding scheme.
     * @return the hamming distance.
     */
    private int calcHammingDistance(String curNumber, String prevNumber, boolean invertFlag) {
        if (invertFlag) {
            curNumber += "0";
            prevNumber += "0";
        }

        //calculate hamming distance by simple comparing each string position.
        int hammingDistance = 0;
        if (curNumber.length() == prevNumber.length()) {
            for (int i = 0; i < curNumber.length(); i++) {
                if (curNumber.charAt(i) != prevNumber.charAt(i)) {
                    hammingDistance += 1;
                }
            }
        }

        //as per paper if hamming distance > n/2 we append a 1 bit to the bus and invert the value
        if (hammingDistance > POWER_SAVE_INVERT_CONSTANT && invertFlag) {
            prevNumber += "1";
            String invertedCurNumber = invertBitString(curNumber);
            invertedCurNumber += "1";
            return calcHammingDistance(invertedCurNumber, prevNumber);
        } else {
            return hammingDistance;
        }
    }

    /**
     * A method to flip the bit values in a string of bits.
     * @param bitString the input bit string.
     * @return the inverted bit string.
     */
    private String invertBitString(String bitString) {
        return bitString.replaceAll("0", "x").replaceAll("1", "0").replaceAll("x", "1");
    }

    private void scheduleFireTime(Time nextFireTime) throws IllegalActionException {
        getDirector().fireAt(this, nextFireTime);
    }
}
