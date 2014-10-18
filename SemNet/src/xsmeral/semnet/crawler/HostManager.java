package xsmeral.semnet.crawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import xsmeral.semnet.crawler.model.CrawlerConfiguration;
import xsmeral.semnet.crawler.model.EntityDescriptor;
import xsmeral.semnet.crawler.model.HostDescriptor;
import xsmeral.semnet.util.URLUtil;
import xsmeral.semnet.util.Util;

/**
 * Host manager for {@link HTMLCrawler}.
 * Manages {@link HostDescriptor}s, {@link EntityDescriptor}s, mapping
 * of hosts to their IDs and persisting hosts in DB.
 * <br />
 * Mapping hosts to IDs is mainly a performance measure.
 * 
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class HostManager {

    private static final String ADD_HOST = "INSERT INTO {SCHEMA}host (address) VALUES (?)";
    private static final String GET_HOSTID = "SELECT hostid FROM {SCHEMA}host WHERE address=?;";
    private static final String REMOVE_HOSTS = "DELETE from {SCHEMA}host ";
    private static final String REMOVE_ID = " WHERE hostid=?";
    private RDBLayer dbLayer;
    private Connection dbConn;
    private PreparedStatement addHost;
    private PreparedStatement getHostId;
    private Map<Integer, HostDescriptor> hostMap;
    private Map<Integer, Map<Pattern, EntityDescriptor>> entityMap;
    private Map<Integer, Map<String, Pattern>> patternCache;
    private Mapper mapper;

    /**
     * Mapper is responsible for mapping hosts to IDs.
     */
    public static class Mapper implements HostMapper {

        private static final String GET_HOSTS = "SELECT hostid, address FROM {SCHEMA}host;";
        private PreparedStatement getHosts;
        private Map<String, Integer> hostIdLookup;
        private Map<Integer, String> hostNameLookup;
        private Connection conn;

        /**
         * Calls {@link #loadHosts(xsmeral.semnet.crawler.RDBLayer) loadHosts(db)}.
         * A mapper instance should be obtained using the {@link HostManager}.
         * {@link HostManager#getMapper(xsmeral.semnet.crawler.RDBLayer) getMapper()} method.
         */
        public Mapper(RDBLayer db) {
            loadHosts(db);
        }

        @Override
        public boolean containsHost(String url) {
            return hostIdLookup.containsKey(url);
        }

        @Override
        public boolean containsHost(int id) {
            return hostNameLookup.containsKey(id);
        }

        @Override
        public String getHostName(int hostid) {
            return hostNameLookup.get(hostid);
        }

        @Override
        public int getHostId(String url) {
            return hostIdLookup.get(url);
        }

        /**
         * {@inheritDoc}
         * It need not be called, since it is called in {@link #HostManager.Mapper(xsmeral.semnet.crawler.RDBLayer) constructor}.
         */
        @Override
        public final void loadHosts(RDBLayer db) {
            try {
                hostIdLookup = new HashMap<String, Integer>();
                hostNameLookup = new HashMap<Integer, String>();
                conn = db.getConnection();
                getHosts = conn.prepareStatement(GET_HOSTS.replaceAll("\\{SCHEMA\\}", db.getSchema()));
                ResultSet hostsRs = getHosts.executeQuery();
                while (hostsRs.next()) {
                    int id = hostsRs.getInt("hostid");
                    String address = hostsRs.getString("address");
                    mapHost(id, address);
                }
                getHosts.close();
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(Mapper.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        /**
         * Creates <tt>Host &lt;-&gt; ID</tt> mapping
         */
        private void mapHost(int id, String address) {
            hostIdLookup.put(address, id);
            hostNameLookup.put(id, address);
        }
    }

    /**
     * Creates manager instance for the specified DB layer.
     * @throws SQLException In case of a problem with the DB layer.
     */
    public HostManager(RDBLayer db) throws SQLException {
        this.dbLayer = db;
        dbConn = dbLayer.getConnection();
        hostMap = new HashMap<Integer, HostDescriptor>();
        patternCache = new HashMap<Integer, Map<String, Pattern>>();
        entityMap = new HashMap<Integer, Map<Pattern, EntityDescriptor>>();
        addHost = dbConn.prepareStatement(ADD_HOST.replaceAll("\\{SCHEMA\\}", db.getSchema()));
        getHostId = dbConn.prepareStatement(GET_HOSTID.replaceAll("\\{SCHEMA\\}", db.getSchema()));
        mapper = new Mapper(dbLayer);
    }

    /**
     * Initializes the manager with given set of hosts.
     * If a host in a descriptor is not yet managed, it is added to the DB.
     * (Which means it only does one way synchronization: Descriptors -&gt; DB).
     * @throws SQLException In case of a problem with the DB layer.
     */
    public void loadHosts(Collection<HostDescriptor> hosts) throws SQLException {
        Collection<HostDescriptor> hostDescriptors = hosts;
        int newHosts = 0;
        for (HostDescriptor desc : hostDescriptors) {
            String hostURL = "";
            try {
                hostURL = URLUtil.normalize(desc.getBaseURL()).toString();
                int hostId;
                if (!mapper.containsHost(hostURL)) {
                    newHosts++;
                    hostId = addHost(hostURL);
                } else {
                    hostId = mapper.getHostId(hostURL);
                }
                Map<Pattern, EntityDescriptor> descMap = new HashMap<Pattern, EntityDescriptor>();
                Map<String, Pattern> pattMap = new HashMap<String, Pattern>();
                for (EntityDescriptor entity : desc.getEntityDescriptors()) {
                    Pattern patt = entity.getUrlPattern();
                    descMap.put(patt, entity);
                    pattMap.put(patt.toString(), patt);
                }
                entityMap.put(hostId, descMap);
                patternCache.put(hostId, pattMap);
                hostMap.put(hostId, desc);
            } catch (PatternSyntaxException ex) {
                // skip desc with malformed pattern
                Logger.getLogger(HostManager.class.getName()).log(Level.SEVERE, "Configuration for host " + hostURL + " contains invalid regex pattern " + ex.getPattern(), ex);
            } catch (MalformedURLException ex) {
                // ignore file with malformed URL
                Logger.getLogger(HostManager.class.getName()).log(Level.SEVERE, hostURL + " is not a valid URL", ex);
            }
        }
        if (newHosts > 0) {
            mapper.loadHosts(dbLayer);
        }
    }

    /**
     * Returns a mapper instance for the specified DB.
     * @see HostManager.Mapper#HostManager.Mapper(xsmeral.semnet.crawler.RDBLayer) HostManager.Mapper(db)
     */
    public static HostMapper getMapper(RDBLayer db) {
        return new Mapper(db);
    }

    /**
     * Returns the mapper instance associated with this HostManager.
     */
    public HostMapper getMapper() {
        return mapper;
    }

    /**
     * Adds the host with the specified address to the DB.
     * Returns newly generated ID of the host.
     * @param address String containing the URL of the host
     * @return Generated ID of the added host or 0 if the host hasn't been added
     * @throws SQLException If a SQL command fails
     */
    public final int addHost(String address) throws SQLException {
        addHost.setString(1, address);
        addHost.executeUpdate();
        getHostId.setString(1, address);
        ResultSet rs = getHostId.executeQuery();
        if (rs.next()) {
            return rs.getInt("hostid");
        } else {
            return 0;
        }
    }

    /**
     * Returns descriptors of the managed hosts.
     */
    public Collection<HostDescriptor> getDescriptors() {
        return hostMap.values();
    }

    /**
     * Returns IDs of the managed hosts.
     */
    public Collection<Integer> getHostIds() {
        return mapper.hostNameLookup.keySet();
    }

    /**
     * Returns descriptor of the host associated with the given ID
     * or null if the ID is not mapped to any managed host.
     */
    public HostDescriptor getHostDescriptor(int id) {
        return hostMap.get(id);
    }

    /**
     * Returns the map between source URL patterns and their update frequencies
     * for the host with given ID, or null if the ID is not mapped to any managed host.
     * @see HostDescriptor
     */
    public Map<Pattern, Integer> getSourceURLMap(int hostId) {
        return hostMap.get(hostId).getSourceURLPatterns();
    }

    /**
     * Returns the map between entity URL patterns and their entity descriptors
     * for the host with given ID, or null if the ID is not mapped to any managed host.
     */
    public Map<Pattern, EntityDescriptor> getEntityDescriptorMap(int hostId) {
        return entityMap.get(hostId);
    }

    /**
     * Returns the first pattern from the list, that matches the supplied string
     * @param it Iterator over patterns
     * @param str The string to match
     * @return The matching pattern, or null if none match
     */
    private Pattern matchingPattern(Iterator<Pattern> it, String str) {
        boolean match = false;
        Pattern pattern = null;
        while (it.hasNext() && !match) {
            pattern = it.next();
            match = pattern.matcher(str).matches();
        }
        return match ? pattern : null;
    }

    /**
     * Returns the Pattern (entity or source) that matches the given relative URL
     * or null if no match is found.
     * @param hostId ID of the host to search for patterns.
     * @param relativeUrl URL to match
     * @see HostDescriptor
     * @see EntityDescriptor
     */
    public Pattern getPattern(int hostId, String relativeUrl) {
        // try entities first, then sources
        return Util.nonNull(
                matchingPattern(getEntityDescriptorMap(hostId).keySet().iterator(), relativeUrl),
                matchingPattern(getSourceURLMap(hostId).keySet().iterator(), relativeUrl));
    }

    /**
     * Indicates, whether the specified pattern represents an entity in the given host.
     * @param hostId ID of the given host
     * @param pattern The pattern to check
     * @see EntityDescriptor
     */
    public boolean isEntity(int hostId, Pattern pattern) {
        return entityMap.get(hostId).containsKey(pattern);
    }

    /**
     * Indicates, whether the specified pattern represents a source URL in the given host.
     * @param hostId ID of the given host
     * @param pattern The pattern to check
     * @see HostDescriptor
     */
    public boolean isSource(int hostId, Pattern pattern) {
        return hostMap.get(hostId).getSourceURLPatterns().containsKey(pattern);
    }

    /**
     * Returns the {@link EntityDescriptor} associated with this URL pattern
     * or null if the pattern does not represent an entity in the provided host
     * @param hostId ID of the host
     * @param pattern The URL pattern
     */
    public EntityDescriptor getEntityDescriptor(int hostId, String pattern) {
        return entityMap.get(hostId).get(patternCache.get(hostId).get(pattern));
    }

    /**
     * Completely resets crawler state, erasing all URLs from DB.
     */
    private void reset() throws SQLException {
        PreparedStatement resetStmt = dbConn.prepareStatement(REMOVE_HOSTS.replaceAll("\\{SCHEMA\\}", dbLayer.getSchema()));
        int count = resetStmt.executeUpdate();
        Logger.getLogger(HostManager.class.getName()).log(Level.INFO, "{0} entries removed", count);
        resetStmt.close();
    }

    /**
     * Removes selected host from DB. All entries referring to this host are removed
     * as well ({@code ON DELETE CASCADE}).
     */
    private void removeHost(int hostId) throws SQLException {
        PreparedStatement removeStmt = dbConn.prepareStatement((REMOVE_HOSTS + REMOVE_ID).replaceAll("\\{SCHEMA\\}", dbLayer.getSchema()));
        removeStmt.setInt(1, hostId);
        int count = removeStmt.executeUpdate();
        Logger.getLogger(HostManager.class.getName()).log(Level.INFO, "{0} entries removed", count);
        removeStmt.close();
    }

    /**
     * Closes the DB connection
     * @see RDBLayer
     */
    public void close() {
        try {
            getHostId.close();
            addHost.close();
            dbConn.close();
        } catch (SQLException ex) {
            Logger.getLogger(HostManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void printUsage() {
        System.err.println(
                "Usage: " + HostManager.class.getSimpleName() + " <conf> <operation> [arg]\n"
                + "where 'conf' is a crawler configuration file name and operation"
                + " is one of [help, list, remove, reset]\n\n"
                + "list - lists all available hosts and their IDs\n"
                + "remove <id> - removes the host with the specified ID\n"
                + "reset - completely resets the crawler state (removes all URLs and all hosts)");
    }

    /**
     * Provides a CLI for simple management of hosts.
     * Available methods are {@code list},{@code remove} and {@code reset}.
     * More details are provided upon running the class without arguments.
     */
    public static void main(String[] args) {
        final String LIST = "list", REMOVE = "remove", RESET = "reset";
        List<String> operations = Arrays.asList(new String[]{LIST, REMOVE, RESET});
        if (args.length >= 2) {
            String confFileName = args[0];
            File confFile = new File(confFileName);
            if (confFile.exists()) {
                String operation = args[1];
                if (operations.contains(operation)) {
                    try {
                        CrawlerConfiguration conf = Util.objectFromXml(confFileName, CrawlerConfiguration.class);
                        HostManager mgr = new HostManager(conf.getDBLayer());
                        if (LIST.equals(operation)) {
                            Collection<Integer> hostIds = mgr.getHostIds();
                            System.err.println("Managed hosts:");
                            for (Integer hostId : hostIds) {
                                System.err.println(hostId + ": " + mgr.getMapper().getHostName(hostId));
                            }
                        } else if (REMOVE.equals(operation)) {
                            if (args.length >= 3) {
                                String hostIdStr = args[2];
                                try {
                                    int hostId = Integer.parseInt(hostIdStr);
                                    String hostName = mgr.getMapper().getHostName(hostId);
                                    if (hostName != null) {
                                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                                        System.err.println("Do you really want to remove host " + hostName + "? (y/n)");
                                        String line = reader.readLine();
                                        if (line.trim().startsWith("y")) {
                                            mgr.removeHost(hostId);
                                        }
                                    } else {
                                        System.err.println("Invalid host ID: " + hostId);
                                    }
                                } catch (IOException ex) {
                                    System.err.println("Error: " + ex.getMessage());
                                } catch (NumberFormatException ex) {
                                    System.err.println("'" + hostIdStr + "' is not a valid host ID");
                                    printUsage();
                                }
                            } else {
                                System.err.println("No ID specified\n");
                                printUsage();
                            }
                        } else if (RESET.equals(operation)) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                            System.err.println("Do you really want to remove all URL entries of all hosts? (y/n)");
                            try {
                                String line = reader.readLine();
                                if (line.trim().startsWith("y")) {
                                    mgr.reset();
                                }
                            } catch (IOException ex) {
                                Logger.getLogger(HostManager.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        mgr.close();
                    } catch (SQLException ex) {
                        System.err.println("Error while working with DB: " + ex.getMessage());
                    } catch (ConfigurationException ex) {
                        System.err.println("Can't read crawler configuration: " + ex.getMessage());
                    }
                } else {
                    System.err.println("Invalid operation: '" + operation + "'");
                    printUsage();
                }
            } else {
                System.err.println("File '" + confFileName + "' does not exist");
            }
        } else {
            System.err.println("Operation not specified");
            printUsage();
        }
    }
}
