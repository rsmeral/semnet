package xsmeral.semnet.crawler;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Relational DB Layer for Crawler.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */

@XStreamAlias("RDBLayer")
public class RDBLayer {

    @XStreamOmitField
    private static final boolean DEF_AUTO_COMMIT = true;
    private Class driver;
    private String url;
    private String user;
    private String password;
    private String schema;
    private boolean autoCommit;

    /**
     * Constructs the DB layer with parameters specified in the given Properties instance.
     * The parameters are:
     * <br />
     * <ul>
     *  <li><tt>driver</tt> - full name of the JDBC driver class</li>
     *  <li><tt>url</tt> - JDBC URL of the DB</li>
     *  <li><tt>user</tt></li>
     *  <li><tt>password</tt></li>
     *  <li><tt>schema</tt> - the schema to use for all operations</li>
     *  <li><tt>autoCommit</tt> - (optional) indicates, whether all operations should be automatically commited</li>
     * </ul>
     */
    public RDBLayer(Properties dbProps) throws ConfigurationException {
        this(
                dbProps.getProperty("driver"),
                dbProps.getProperty("url"),
                dbProps.getProperty("user"),
                dbProps.getProperty("password"),
                dbProps.getProperty("schema"),
                dbProps.getProperty("autoCommit") == null ? DEF_AUTO_COMMIT : Boolean.parseBoolean(dbProps.getProperty("autoCommit")));

    }

    public RDBLayer(String dbDriverClassName, String dbURL, String dbUser, String dbPassword, String dbSchema, boolean autoCommit) throws ConfigurationException {
        try {
            this.driver = Class.forName(dbDriverClassName);
            this.url = dbURL;
            this.user = dbUser;
            this.password = dbPassword;
            this.schema = dbSchema;
            this.autoCommit = autoCommit;
            if (dbDriverClassName == null || dbDriverClassName.isEmpty() || dbURL == null || dbURL.isEmpty() || dbUser == null || dbPassword == null) {
                throw new ConfigurationException("Bad DB configuration");
            }
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(RDBLayer.class.getName()).log(Level.SEVERE, null, ex);
            throw new ConfigurationException("Can't initialize DB driver", ex);
        }
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public Class getDriver() {
        return driver;
    }

    public String getPassword() {
        return password;
    }

    public String getURL() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getSchema() {
        return (schema != null && !schema.isEmpty()) ? schema + "." : "";
    }

    /**
     * Returns a new connection to the database.
     * After getting a connection, the caller is responsible to close it as well.
     * This class doesn't take care of the connections after creation.
     * @return A new connection
     * @throws SQLException If the driver manager fails to return a connection
     */
    public Connection getConnection() throws SQLException {
        Connection dbConn = DriverManager.getConnection(url, user, password);
        dbConn.setAutoCommit(autoCommit);
        return dbConn;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RDBLayer other = (RDBLayer) obj;
        if ((this.url == null) ? (other.url != null) : !this.url.equals(other.url)) {
            return false;
        }
        if ((this.user == null) ? (other.user != null) : !this.user.equals(other.user)) {
            return false;
        }
        if ((this.password == null) ? (other.password != null) : !this.password.equals(other.password)) {
            return false;
        }
        if ((this.schema == null) ? (other.schema != null) : !this.schema.equals(other.schema)) {
            return false;
        }
        if (this.autoCommit != other.autoCommit) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + (this.url != null ? this.url.hashCode() : 0);
        hash = 37 * hash + (this.user != null ? this.user.hashCode() : 0);
        hash = 37 * hash + (this.password != null ? this.password.hashCode() : 0);
        hash = 37 * hash + (this.schema != null ? this.schema.hashCode() : 0);
        hash = 37 * hash + (this.autoCommit ? 1 : 0);
        return hash;
    }
}
