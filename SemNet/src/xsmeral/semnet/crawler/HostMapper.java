package xsmeral.semnet.crawler;

/**
 * HostMapper is responsible for mapping hosts to IDs.
 * It can be thought of as an internal analogy of DNS.
 */
public interface HostMapper {

    /**
     * Loads managed hosts from the specified DB.
     */
    public void loadHosts(RDBLayer db);

    /**
     * Returns host name for the specified ID.
     */
    public String getHostName(int hostid);

    /**
     * Returns ID of the host with the specified URL.
     */
    public int getHostId(String name);

    /**
     * Indicates, whether this manager contains a host with the specified URL.
     */
    public boolean containsHost(String name);

    /**
     * Indicates whether this manager contains a host with the specified ID.
     */
    public boolean containsHost(int id);
}
