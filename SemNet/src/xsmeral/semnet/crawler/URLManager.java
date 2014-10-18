package xsmeral.semnet.crawler;

import java.sql.Connection;
import xsmeral.semnet.crawler.model.URLEntry;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * URL Manager for {@link HTMLCrawler}.
 * Responsible for persistence of URLs. Contains methods for querying, locking,
 * updating, adding.
 *
 * @see HTMLCrawler
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class URLManager {

    //private static final String GET_FOR_HOST = "SELECT urlid, hostid, path, last_visited, visit_count, update_freq, entity, pattern, working, score FROM {SCHEMA}url WHERE ? - update_freq > last_visited AND hostid = ? AND NOT EXISTS(SELECT urlid FROM {SCHEMA}url_lock WHERE url_lock.urlid=url.urlid) LIMIT ?;";
    private static final String UPDATE_ENTRY = "UPDATE {SCHEMA}url SET last_visited=?, visit_count=?, update_freq=?, entity=?, pattern=?, working=?, score=? WHERE urlid=?";
    private static final String ADD_ENTRY = "INSERT INTO {SCHEMA}url (hostid, path, last_visited, visit_count, update_freq, entity, pattern, working, score) SELECT ?, ?, 0, 0, ?, ?, ?, true, 0 WHERE NOT EXISTS (SELECT * FROM {SCHEMA}url WHERE path=?)";// conditional insert = preserves uniqueness
    private static final String LOCK_URL = "INSERT INTO {SCHEMA}url_lock (owner, urlid, time) VALUES (?, ?, localtimestamp)";
    private static final String UNLOCK_URL = "DELETE FROM {SCHEMA}url_lock ";
    private static final String UNLOCK_CONDITION = " WHERE urlid=?";
    //private final PreparedStatement getEntry;
    private final PreparedStatement updEntry;
    private final PreparedStatement addEntry;
    private final PreparedStatement lockUrl;
    private final PreparedStatement unlockUrl;
    private final PreparedStatement unlockAll;
    private PreparedStatement listLocked;
    private PreparedStatement listBroken;
    //
    private final RDBLayer dbLayer;
    private final HostMapper hostMapper;
    private Connection dbConn;

    //<editor-fold desc="Query Builder">
    // Polymorphic Builder
    /**
     * Builder of queries.
     */
    public interface QueryBuilder extends WhereClause {

        /**
         * Appends condition for locked URLs.
         */
        public QueryBuilder locked();
    }

    /**
     * The {@code WHERE} clause of SQL statement.
     */
    public interface WhereClause extends OrderClause {

        /**
         * Appends condition for URLs not locked.
         */
        public WhereClause notLocked();

        /**
         * Appends condition for URLs that need updating (where
         * <tt>[current_time]-update_freq &gt; last_visited</tt>).
         */
        public WhereClause current();

        /**
         * Appends condition for the given host.
         * @param hostId If null, a wildcard (?) is used
         */
        public WhereClause forHost(Integer hostId);

        /**
         * Appends condition for given pattern.
         * @param pattern If null, a wildcard (?) is used
         */
        public WhereClause forPattern(String pattern);

        /**
         * Appends condition for URLs that represent entities/sources.
         * @param entity If null, a wildcard (?) is used
         */
        public WhereClause entity(Boolean entity);

        /**
         * Appends condition for URLs that are (not) working.
         * @param working If null, a wildcard (?) is used
         */
        public WhereClause working(Boolean working);
    }

    /**
     * The {@code ORDER BY} clause of SQL statement.
     */
    public interface OrderClause extends LimitClause {

        /**
         * Appends {@code ORDER BY [order]}.
         * @param order The full SQL-equivalent argument to {@code ORDER BY} clause.
         * If null, a wildcard (?) is used.
         */
        public LimitClause orderBy(String order);

        /**
         * Orders by {@code entity} field, so that source URLs are returned first.
         */
        public LimitClause sourceFirst();
    }

    /**
     * The {@code LIMIT} clause of SQL statement.
     */
    public interface LimitClause extends Query {

        /**
         * Appends {@code LIMIT} with the given argument.
         * @param count If null, a wildcard (?) is used.
         */
        public Query limit(Integer count);
    }

    /**
     * Complete SQL query.
     */
    public interface Query {

        /**
         * Constructs the query. Must be called before {@link #getStatement() getStatement()}.
         */
        public Query getQuery() throws ConfigurationException;

        /**
         * Returns the constructed SQL prepared statement. The 
         * {@link #getQuery() getQuery()} must be called before calling this method.
         */
        public PreparedStatement getStatement();
    }

    /**
     * Implementation of QueryBuilder for URL entries.
     */
    public class QueryBuilderImpl implements QueryBuilder {

        private static final String SELECT = "SELECT urlid, hostid, path, last_visited, visit_count, update_freq, entity, pattern, working, score FROM {SCHEMA}url ";
        private static final String LOCKED = " NATURAL JOIN {SCHEMA}url_lock ";
        private static final String NOT_LOCKED = " NOT EXISTS(SELECT urlid FROM {SCHEMA}url_lock WHERE url_lock.urlid=url.urlid) ";
        private static final String CURRENT = " {now} > last_visited + update_freq";
        private static final String FOR_HOST = " hostid = {id} ";
        private static final String FOR_PATTERN = " pattern = {pattern} ";
        private static final String ENTITY = " entity = {entity} ";
        private static final String WORKING = " working = {working} ";
        private static final String ORDER = " ORDER BY {order}";
        private static final String LIMIT = " LIMIT {count} ";
        private static final String WHERE = " WHERE ";
        private static final String AND = " AND ";
        //
        private StringBuilder q = new StringBuilder(SELECT.replaceAll("\\{SCHEMA\\}", dbLayer.getSchema()));
        private PreparedStatement pst;
        private boolean isWhere = false;
        //

        /**
         * Replaces <tt>{SCHEMA}</tt> wildcards with schema used by this URLManager.
         */
        private String withSchema(String clause) {
            return clause.replaceAll("\\{SCHEMA\\}", dbLayer.getSchema());
        }

        /**
         * Creates {@code WHERE} clause. Appends either {@code WHERE}, if called
         * the first time, otherwis appends {@code AND}.
         */
        private void where(String condition) {
            if (isWhere) {
                q.append(AND);
            } else {
                q.append(WHERE);
                isWhere = true;
            }
            q.append("(").append(condition).append(")");
        }

        @Override
        public QueryBuilder locked() {
            q.append(withSchema(LOCKED));
            return this;
        }

        @Override
        public WhereClause notLocked() {
            where(withSchema(NOT_LOCKED));
            return this;
        }

        @Override
        public WhereClause current() {
            where(CURRENT.replaceAll("\\{now\\}", String.valueOf(new Date().getTime() / 1000)));
            return this;
        }

        @Override
        public WhereClause entity(Boolean entity) {
            where(ENTITY.replaceAll("\\{entity\\}", (entity == null ? "?" : entity.toString())));
            return this;
        }

        @Override
        public WhereClause forHost(Integer hostId) {
            where(FOR_HOST.replaceAll("\\{id\\}", (hostId == null ? "?" : hostId.toString())));
            return this;
        }

        @Override
        public WhereClause forPattern(String pattern) {
            where(FOR_PATTERN.replaceAll("\\{pattern\\}", (pattern == null ? "?" : "'" + pattern.toString() + "'")));
            return this;
        }

        @Override
        public WhereClause working(Boolean working) {
            where(WORKING.replaceAll("\\{working\\}", (working == null ? "?" : working.toString())));
            return this;
        }

        @Override
        public LimitClause orderBy(String order) {
            where(ORDER.replaceAll("\\{order\\}", (order == null ? "?" : order.toString())));
            return this;
        }

        @Override
        public LimitClause sourceFirst() {
            where(ORDER.replaceAll("\\{order\\}", "entity"));
            return this;
        }

        @Override
        public Query limit(Integer count) {
            q.append(LIMIT.replaceAll("\\{count\\}", (count == null ? "?" : count.toString())));
            return this;
        }

        @Override
        public Query getQuery() throws ConfigurationException {
            try {
                if (pst == null) {
                    pst = dbConn.prepareStatement(q.toString());
                }
                return this;
            } catch (SQLException ex) {
                throw new ConfigurationException("Not a valid statement: " + ex.getMessage());
            }
        }

        @Override
        public PreparedStatement getStatement() {
            return pst;
        }
    }

    /**
     * Returns a Query for host with given ID.
     */
    public WhereClause getQueryForHost(int hostId) {
        return new QueryBuilderImpl().forHost(hostId);
    }
    //</editor-fold>

    /**
     * Creates an instance for the specified DB layer.
     * @throws SQLException If an error occurs when connecting to DB or preparing statements.
     */
    public URLManager(RDBLayer db) throws SQLException {
        dbLayer = db;
        hostMapper = HostManager.getMapper(dbLayer);
        dbConn = dbLayer.getConnection();
        dbConn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        //
        //getEntry = dbConn.prepareStatement(GET_FOR_HOST.replaceAll("\\{SCHEMA\\}", dbLayer.getSchema()));
        updEntry = dbConn.prepareStatement(UPDATE_ENTRY.replaceAll("\\{SCHEMA\\}", dbLayer.getSchema()));
        addEntry = dbConn.prepareStatement(ADD_ENTRY.replaceAll("\\{SCHEMA\\}", dbLayer.getSchema()));
        lockUrl = dbConn.prepareStatement(LOCK_URL.replaceAll("\\{SCHEMA\\}", dbLayer.getSchema()));
        unlockUrl = dbConn.prepareStatement((UNLOCK_URL + UNLOCK_CONDITION).replaceAll("\\{SCHEMA\\}", dbLayer.getSchema()));
        unlockAll = dbConn.prepareStatement(UNLOCK_URL.replaceAll("\\{SCHEMA\\}", dbLayer.getSchema()));
    }

    /**
     * Retrieves URLs based on the given query.
     * Locks the entries so that no other thread can get the same URLs at the same time.
     * Therefore every URL retrieved should also be returned
     * (and thusly unlocked) by calling {@link #returnEntry(xsmeral.semnet.crawler.model.URLEntry) returnEntry(entry)}.<br />
     * @param q The Query to use
     * @param ownerId An identification of the entity that is retrieving and locking this URL
     */
    public Collection<URLEntry> fetchEntries(Query q, int ownerId) {
        try {
            dbConn.setAutoCommit(false);// fetch atomically
            ResultSet rs = q.getStatement().executeQuery();
            Collection<URLEntry> entries = resultSetToCollection(rs);
            lockUrls(ownerId, entries);
            dbConn.commit();
            return entries;
        } catch (SQLException ex) {
            Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, ex.getNextException().getMessage(), ex);
            return null;
        } finally {
            try {
                dbConn.setAutoCommit(true);
            } catch (SQLException ex) {
                Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, ex.getNextException().getMessage(), ex);
            }
        }
    }

    /**
     * Updates and unlocks the given entry in DB.
     */
    public void returnEntry(URLEntry entry) {
        updateEntry(entry);
        unlockUrl(entry);
    }

    /**
     * Adds given entry to DB.
     * @return True, if the entry was added (did not exist).
     * @see #addEntries(java.util.Collection)
     */
    public boolean addEntry(URLEntry entry) {
        return addEntries(Collections.singletonList(entry)) > 0;
    }

    /**
     * Adds given entries to DB.
     * Any entries already present in DB are ignored.
     * @return Number of modified rows (added entries).
     */
    public int addEntries(Collection<URLEntry> entries) {
        synchronized (addEntry) {
            int added = 0;
            try {
                for (URLEntry entry : entries) {
                    populateStatement(addEntry,
                            hostMapper.getHostId(entry.getHost()),
                            entry.getPath(),
                            entry.getUpdateFreq(),
                            entry.isEntity(),
                            entry.getPattern(),
                            entry.getPath()).addBatch();
                }

                int[] result = addEntry.executeBatch();
                // return count of modified rows
                for (int i : result) {
                    added += i;
                }
                return added;
            } catch (SQLException ex) {
                Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, ex.getNextException().getMessage(), ex);
                return added;
            }
        }
    }

    /**
     * Updates the given entry in DB.
     */
    public void updateEntry(URLEntry entry) {
        synchronized (updEntry) {
            try {
                populateStatement(updEntry,
                        (int) (entry.getLastVisited().getTime() / 1000),
                        entry.getVisitCount(),
                        entry.getUpdateFreq(),
                        entry.isEntity(),
                        entry.getPattern(),
                        entry.isWorking(),
                        entry.getScore(),
                        entry.getId()).executeUpdate();
            } catch (SQLException ex) {
                Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, ex.getNextException().getMessage(), ex);
            }
        }
    }

    /**
     * Closes the DB connection and all prepared statements.
     */
    public void close() {
        try {
            if (!dbConn.getAutoCommit()) {
                dbConn.commit();
            }
            //getEntry.close();
            updEntry.close();
            addEntry.close();
            lockUrl.close();
            unlockUrl.close();
            unlockAll.close();
            if (listLocked != null) {
                listLocked.close();
            }
            if (listBroken != null) {
                listBroken.close();
            }
            dbConn.close();
        } catch (SQLException ex) {
            Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, ex.getNextException().getMessage(), ex);
        }
    }

    /**
     * Locks the URL specified by given entry for use by the given owner.
     * All calls to this method should be matched by a call to {@link #unlockUrl(xsmeral.semnet.crawler.model.URLEntry) unlockUrl}.
     * @param ownerId An identification of the entity that is retrieving and locking this URL
     * @param entry The entry to lock
     * @return True, if the URL was successfully locked
     */
    private boolean lockUrl(int ownerId, URLEntry entry) {
        return lockUrls(ownerId, Collections.singletonList(entry));
    }

    /**
     * See {@link #lockUrl(int, xsmeral.semnet.crawler.model.URLEntry) lockUrl}.
     */
    private boolean lockUrls(int ownerId, Collection<URLEntry> entries) {
        synchronized (lockUrl) {
            try {
                for (URLEntry entry : entries) {
                    populateStatement(lockUrl, ownerId, entry.getId()).addBatch();
                }
                if (!entries.isEmpty()) {
                    lockUrl.executeBatch();
                }
                return true;
            } catch (SQLException ex) {
                Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, ex.getNextException().getMessage(), ex);
                return false;
            }
        }
    }

    /**
     * Unlocks the URL specified by given entry.
     * @return True, if the URL was successfully unlocked
     */
    public boolean unlockUrl(URLEntry entry) {
        return unlockUrls(Collections.singletonList(entry));
    }

    /**
     * See {@link #unlockUrl(xsmeral.semnet.crawler.model.URLEntry) unlockUrl}
     */
    public boolean unlockUrls(Collection<URLEntry> entries) {
        synchronized (unlockUrl) {
            try {
                for (URLEntry entry : entries) {
                    populateStatement(unlockUrl, entry.getId()).addBatch();
                }
                if (!entries.isEmpty()) {
                    unlockUrl.executeBatch();
                }
                return true;
            } catch (SQLException ex) {
                Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, ex.getNextException().getMessage(), ex);
                return false;
            }
        }
    }

    /**
     * Returns list of locked entries.
     */
    public Collection<URLEntry> listLocked() {
        try {
            if (listLocked == null) {
                listLocked = new QueryBuilderImpl().locked().getQuery().getStatement();
            }
            synchronized (listLocked) {
                ResultSet rs = listLocked.executeQuery();
                Collection<URLEntry> entries = resultSetToCollection(rs);
                return entries;
            }
        } catch (SQLException ex) {
            Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, "Can''t list locked URLs: {0}", ex.getMessage());
            return null;
        } catch (ConfigurationException ex) {
            Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, "Can''t list locked URLs: {0}", ex.getMessage());
            return null;
        }
    }

    /**
     * Returns list of URL marked as not working.
     */
    public Collection<URLEntry> listBroken() {
        try {
            if (listBroken == null) {
                listBroken = new QueryBuilderImpl().locked().getQuery().getStatement();
            }
            synchronized (listBroken) {
                ResultSet rs = listBroken.executeQuery();
                Collection<URLEntry> entries = resultSetToCollection(rs);
                return entries;
            }
        } catch (SQLException ex) {
            Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, "Can''t list broken URLs: {0}", ex.getMessage());
            return null;
        } catch (ConfigurationException ex) {
            Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, "Can''t list broken URLs: {0}", ex.getMessage());
            return null;
        }
    }

    /**
     * Unlocks all locked URLs.
     * Should only be called in case of a crash, where not all locked URLs
     * have been unlocked.
     * @see #lockUrl(int, xsmeral.semnet.crawler.model.URLEntry) lockUrl
     * @see #unlockUrl(xsmeral.semnet.crawler.model.URLEntry) unlockUrl
     * @return True, if all URLs have been successfully unlocked
     */
    public boolean unlockAll() {
        synchronized (unlockAll) {
            try {
                unlockAll.executeUpdate();
                return true;
            } catch (SQLException ex) {
                Logger.getLogger(URLManager.class.getName()).log(Level.SEVERE, ex.getNextException().getMessage(), ex);
                return false;
            }
        }
    }

    /**
     * Populates given statements with supplied objects
     * @return The populated statement
     * @throws SQLException If there are more objects than wildcards in the
     * statement, or the type does not match
     */
    private PreparedStatement populateStatement(PreparedStatement st, Object... objs) throws SQLException {
        st.clearParameters();
        for (int i = 0; i < objs.length; i++) {
            st.setObject(i + 1, objs[i]);
        }
        return st;
    }

    /**
     * Transforms ResultSet to collection of URLEntry.
     */
    private Collection<URLEntry> resultSetToCollection(ResultSet rs) throws SQLException {
        Collection<URLEntry> entries = new ArrayList<URLEntry>();
        while (rs.next()) {
            entries.add(new URLEntry(
                    rs.getInt("urlid"),
                    hostMapper.getHostName(rs.getInt("hostid")),
                    rs.getString("path"),
                    new Date(rs.getInt("last_visited") * 1000),
                    rs.getInt("visit_count"),
                    rs.getInt("update_freq"),
                    rs.getBoolean("entity"),
                    rs.getString("pattern"),
                    rs.getBoolean("working"),
                    rs.getShort("score")));
        }
        return entries;
    }
}
