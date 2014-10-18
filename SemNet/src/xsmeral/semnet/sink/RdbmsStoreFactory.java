package xsmeral.semnet.sink;

import java.util.Properties;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.rdbms.RdbmsStore;

/**
 * Factory of {@link RdbmsStore} repositories.
 * <br />
 * Takes parameters corresponding to RdbmsStore {@linkplain RdbmsStore#RdbmsStore(java.lang.String, java.lang.String, java.lang.String, java.lang.String) constructor}:
 * <ul>
 *  <li><code>driver</code> - FQN of JDBC driver</li>
 *  <li><code>url</code> - JDBC URL</li>
 *  <li><code>user</code> - DB user name</li>
 *  <li><code>password</code> - DB password</li>
 * </ul>
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class RdbmsStoreFactory extends RepositoryFactory {

    @Override
    public void initialize() throws RepositoryException {
        Properties props = getProperties();
        String jdbcDriver = props.getProperty("driver");
        String url = props.getProperty("url");
        String user = props.getProperty("user");
        String pwd = props.getProperty("password");
        if (jdbcDriver == null || url == null || user == null || pwd == null) {
            throw new RepositoryException("Invalid parameters for repository");
        } else {
            Repository repo = new SailRepository(new RdbmsStore(jdbcDriver, url, user, pwd));
            repo.initialize();
            setRepository(repo);
        }
    }

}
