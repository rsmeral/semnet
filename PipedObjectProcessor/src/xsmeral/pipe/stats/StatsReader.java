package xsmeral.pipe.stats;

/**
 * Interface for access to statistics stored in processing context.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public interface StatsReader {

    /**
     * Returns value of the context parameter with the supplied name as a Double.
     * @param statName Full name of the context parameter
     */
    public Double getDouble(String statName);

    /**
     * Returns value of the statistic specified by group and name as a Double.
     * @param group Group name
     * @param stat Stat name
     */
    public Double getDouble(String group, String stat);

    /**
     * Returns value of the context parameter with the supplied name as a Long.
     * @param statName Full name of the context parameter
     */
    public Long getLong(String statName);

    /**
     * Returns value of the statistic specified by group and name as a Long.
     * @param group Group name
     * @param stat Stat name
     */
    public Long getLong(String group, String stat);
}
