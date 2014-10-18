package xsmeral.pipe.stats;

/**
 * Sum function.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class Sum implements StatFunction<Long> {

    private long value = 0;

    @Override
    public void add() {
        value++;
    }

    @Override
    public void add(Long value) {
        this.value += value;
    }

    @Override
    public Long getValue() {
        return value;
    }

    @Override
    public void reset() {
        value = 0;
    }
}
