package xsmeral.semnet.sink;

import java.util.Properties;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryException;

/**
 * Base class for Sesame repository factories.
 * <br />
 * It is configured with a Properties instance containing any implementation-specific
 * parameters.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @see Repository
 */
public abstract class RepositoryFactory {

    public static final String PROP_WORKING_DIR = "workingDir";
    private Repository repo;
    private Properties props;

    /**
     * Should be called only after initialization and return initialized repository.
     * @return Initialized sesame repository
     */
    public final Repository getRepository() {
        return repo;
    }

    /**
     * Called by the factory, sets the initialized repository.
     */
    protected void setRepository(Repository repo) {
        this.repo = repo;
    }

    /**
     * Returns the initialization Properties.
     */
    public final Properties getProperties() {
        return props;
    }

    /**
     * Sets the properties and calls {@link #initialize()}
     * @param props
     * @throws RepositoryException If the repository fails to initialize
     */
    public final void initialize(Properties props) throws RepositoryException {
        this.props = props;
        initialize();
    }

    /**
     * Instantiates and initializes the Repository.
     * Should call {@link #setRepository(org.openrdf.repository.Repository) setRepository(repo)}
     * @throws RepositoryException In case of any error with repository initialization
     */
    protected abstract void initialize() throws RepositoryException;

}
