package q2;

/**
 * This class handles computing the sink node parameters given sequences of beacons.
 * It then uses the parameters to calculate the respective reception phases on each firing of the node.
 */
public class SinkNodeModel {

    /**
     * Constants defining the parameters of the sink nodes as defined in the assessment brief.
     */
    public static final int MAX_T = 1500;
    public static final int MIN_T = 250;
    public static final int MIN_N = 2;
    private static final int MAX_N = 10;

    /**
     * The previous read beacon value for the sink node.
     */
    private int prevN;

    /**
     * The previous time a beacon was read.
     */
    private long prevTime;

    /**
     * The current best value for n.
     */
    private int bestN;

    /**
     * The current best value for t.
     */
    private long bestT;

    /**
     * If true we have the correct value for t.
     */
    private boolean correctT;

    /**
     * If true we have the correct value for n.
     */
    private boolean correctN;

    public SinkNodeModel() {
        prevN = 0;
        prevTime = 0;
        bestN = 1;
        bestT = 0;
        correctN = false;
        correctT = false;
    }

    /**
     * Read a beacon payload at a certain time and use those values to determine the sink node parameters.
     * @param time the time at which the beacon was read.
     * @param n the beacon payload.
     */
    public void readBeacon(long time, int n) {
        long timeGap = time - prevTime;

        if (n > bestN) {
            bestN = n;
        }

        int nDiff = prevN - n;
        //if the previous n is not 0 and timeGap is > 0 then we have enough information to compute some parameters.
        if (prevN != 0 && timeGap > 0) {

            int minProtocolLength = (11 + bestN) * MIN_T;
            //if the beacons are in descending order of value and have arrived in the same protocol iteration, we can determine t.
            if (nDiff > 0 && timeGap < minProtocolLength) {
                bestT = timeGap / nDiff;
                correctT = true;
            } else if (!correctT) {

                if (nDiff > 0 && timeGap < nDiff * MAX_T) {
                    bestT = timeGap / nDiff;
                    //special case for n=1
                } else if (bestN == 1) {
                    calcTForNEqual1(timeGap);
                }
            }

            //when we know t we can work out n
            if (correctT) {
                calcN(timeGap, nDiff);
            }
        }
        prevN = n;
        prevTime = time;
    }


    /**
     * Calculate n using the difference of beacon values between two different protocol iterations.
     * @param timeGap the time gap between beacon reads.
     * @param nDiff the difference in beacon value.
     */
    private void calcN(long timeGap, int nDiff) {
        int beaconDiff = (int)(timeGap / bestT) - nDiff;

        if (11 + bestN <= beaconDiff && beaconDiff <= 11 + MAX_N) {
            bestN = beaconDiff - 11;
            correctN = true;
        }
    }

    /**
     * Calculate t when n=1 by dividing the time gap by 12
     * @param timeGap the time between the current beacon being read and the prevTime.
     */
    private void calcTForNEqual1(long timeGap) {
        long t = timeGap / 12;
        if ((bestT == 0 || t < bestT) && (t > MIN_T && t < MAX_T))
            bestT = t;
    }

    public boolean hasN() {
        return correctN;
    }

    public boolean hasT() {
        return correctT;
    }

    /**
     * Determine whether the RX phase is happening now or has already happened.
     * If already happened and we know the value of n, we can compute the next RX phase immediately.
     * @param protocolLength the length of an iteration of the protocol.
     * @param n the time of the last RX phase.
     * @return the start of the next RX phase.
     */
    private long nextRxPhaseStart(long protocolLength, long n) {
        return (n + protocolLength) / protocolLength * protocolLength;
    }

    public long calcNextRxPhase(long time) {
        long rxPhaseTime = 0;

        if (prevN != 0 && bestT != 0) {
            long currentPhase = prevTime + prevN * bestT + 20;
            long phaseLength = bestT - 20;
            long protocolLength = (11 + bestN) * bestT;

            if (currentPhase + phaseLength > time) {
                rxPhaseTime = currentPhase;
            } else if (correctN) {
                rxPhaseTime = currentPhase + nextRxPhaseStart(protocolLength, (time - currentPhase - phaseLength));
            }
        }
        return rxPhaseTime;
    }

    public long totalProtocolLength() {
        return (11 + bestN) * bestT;
    }

    /**
     * This method is used when selecting which channel to switch to.
     * <p>
     * If we can calculate when the next beacon is to arrive as a minimum,
     * we can switch to that channel with high confidence it will produce a beacon soon.
     * @param time current time.
     * @return an integer representing either the current time or the time a beacon could arrive as a minimum.
     */
    public long calcNextBeacon(long time) {
        //if we know the params we don't need another beacon
        if (correctN && correctT)
            return 0;

        if (!correctT)
            return time;

        long nextBeacon = prevTime + (11 + prevN) * bestT - 500;
        if (time >= nextBeacon)
            return time;

        return nextBeacon;
    }
}
