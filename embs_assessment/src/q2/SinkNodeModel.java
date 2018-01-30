package q2;

/**
 * This class handles computing the sink node parameters given sequences of beacons.
 * It then uses the parameters to calculate the respective reception phases on each firing of the node.
 */
public class SinkNodeModel {

    public static final int MAX_T = 1500;
    public static final int MIN_T = 500;
    public static final int MIN_N = 1;
    private static final int MAX_N = 10;
    /**
     * Two vars to store the previous values of n and t.
     */
    private int prevN;
    private int prevTime;
    private int bestN;
    private int bestT;
    private boolean correctT;
    private boolean correctN;

    public SinkNodeModel() {
        prevN = 0;
        prevTime = 0;
        bestN = MIN_N;
        bestT = 0;
        correctN = false;
        correctT = false;
    }

    public void readBeacon(int time, int n) {
        int timeGap = time - prevTime;

        if (n > bestN) {
            bestN = n;
        }

        int nDiff = prevN - n;
        if (prevN != 0 && timeGap > 0) {

            int minProtocolLength = (11 + bestN) * MIN_T;
            if (nDiff > 0 && timeGap < minProtocolLength) {
                bestT = timeGap / nDiff;
                correctT = true;
            } else if (!correctT) {

                if (nDiff > 0 && timeGap < nDiff * MAX_T) {
                    bestT = timeGap / nDiff;
                } else if (bestN == 1) {
                    calcTForNEqual1(timeGap);
                }
            }

            if (correctT) {
                calcN(timeGap, nDiff);
            }
        }
        prevN = n;
        prevTime = time;
    }


    private void calcN(int timeGap, int nDiff) {
        int beaconDiff = (timeGap / bestT) - nDiff;
        if (11 + bestN <= beaconDiff && beaconDiff <= 11 + MAX_N) {
            bestN = beaconDiff - 11;
            correctN = true;
        }
    }

    /**
     * Calculate t when n=1 by dividing the time gap by 12
     * @param timeGap the time between the current beacon being read and the prevTime.
     */
    private void calcTForNEqual1(int timeGap) {
        int t = timeGap / 12;
        if ((bestT == 0 || t < bestT) && (t > MIN_T && t < MAX_T))
            bestT = t;
    }

    public boolean hasN() {
        return correctN;
    }

    public boolean hasT() {
        return correctT;
    }

    private static int nextRxPhaseStart(int protocolLength, int n) {
        return (n + protocolLength) / protocolLength * protocolLength;
    }

    public int calcNextRxPhase(int time) {
        int rxPhaseTime = 0;

        if (prevN != 0 && bestT != 0) {
            //
            int currentPhase = prevTime + prevN * bestT + 20;
            int phaseLength = bestT - 20;
            int protocolLength = (11 + bestN) * bestT;

            if (currentPhase + phaseLength > time) {
                rxPhaseTime = currentPhase;
            } else if (correctN) {
                rxPhaseTime = currentPhase + nextRxPhaseStart(protocolLength, (time - currentPhase - phaseLength));
            }
        }
        return rxPhaseTime;
    }

    public int totalProtocolLength() {
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
    public int calcNextBeacon(int time) {
        if (correctN && correctT)
            return 0;

        if (!correctT)
            return time;

        int nextBeacon = prevTime + (11 + prevN) * bestT - 500;
        if (time >= nextBeacon)
            return time;

        return nextBeacon;
    }
}
