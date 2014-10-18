package xsmeral.pipe.stats;

/**
 * Arithmetic average.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class Average implements StatFunction<Double> {

    private int count = 0;
    private double avg = 0;

    @Override
    public void add() {
        add(1.0d);
    }

    @Override
    public void add(Double value) {
        final int oldCount = count++;
        avg = (avg*oldCount + value)/(double)count;
    }

    @Override
    public Double getValue() {
        return avg;
    }

    @Override
    public void reset() {
        avg = 0;
        count = 0;
    }

}
