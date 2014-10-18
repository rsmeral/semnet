package xsmeral.pipe.stats;

/**
 * Average time interval between calls to {@link #add() add()}.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class TimeInterval implements StatFunction<Double> {

    private double avg = 0;
    private int count = 0;
    private long last = 0;

    @Override
    public void add() {
        if (last == 0) {
            last = System.currentTimeMillis();
        } else {
            long now = System.currentTimeMillis();
            add((double) (now - last));
            last = now;
        }
    }

    @Override
    public void add(Double value) {
        final int oldCount = count++;
        avg = (avg * oldCount + value) / (double) count;
    }

    @Override
    public Double getValue() {
        return avg;
    }

    @Override
    public void reset() {
        avg = 0;
        count = 0;
        last = 0;
    }
}
