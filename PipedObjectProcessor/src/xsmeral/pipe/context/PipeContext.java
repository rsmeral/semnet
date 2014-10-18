package xsmeral.pipe.context;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.pipe.Pipe;

/**
 * A processing context with additional possibility of accessing the underlying {@link Pipe}
 * and the file system.
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class PipeContext implements ProcessingContext, FSContext {

    /**
     * The context parameter name for working directory
     */
    private static final String WORKING_DIR = "workingDir";
    /**
     * Default working directory
     */
    private static final String DEF_WORKING_DIR = ".";
    private final Map<String, Object> parameterMap;
    private Pipe pipe;

    /**
     * Creates empty parameter map and associates with a Pipe
     *
     * @param pipe The underlying {@link Pipe}
     */
    public PipeContext(Pipe pipe) {
        this.parameterMap = new TreeMap<String, Object>();
        this.pipe = pipe;
    }

    @Override
    public Set<String> getParameterNames() {
        return parameterMap.keySet();
    }

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException If name is null
     */
    @Override
    public Object getParameterValue(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        synchronized (parameterMap) {
            return parameterMap.get(name);
        }
    }

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException If name is null
     */
    @Override
    public void setParameterValue(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        synchronized (parameterMap) {
            parameterMap.put(name, value);
        }
    }

    /**
     * Sets the context parameter, overwriting any previous value (with a warning).
     *
     * @param name Parameter name
     * @param value Parameter value
     * @return The previous value associated with this name, or null if none existed
     * @throws IllegalArgumentException If name is null
     */
    @Override
    public Object setParameter(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        Object prev = null;
        synchronized (parameterMap) {
            prev = parameterMap.put(name, value);
        }
        // warn if overwriting existing value
        if (prev != null) {
            Logger.getLogger(PipeContext.class.getName()).log(Level.WARNING, "Context parameter ''{0}'' already existed", name);
        }
        return prev;
    }

    @Override
    public void removeParameter(String name) {
        parameterMap.remove(name);
    }

    /**
     * Returns the underlying {@link Pipe}
     */
    public Pipe getPipe() {
        return pipe;
    }

    @Override
    public void setWorkingDir(String path) {
        setParameter(WORKING_DIR, (path == null || path.isEmpty()) ? DEF_WORKING_DIR : path);
    }

    @Override
    public String getWorkingDir() {
        return (String) getParameterValue(WORKING_DIR);
    }

    @Override
    public File getFile(String path) {
        File pathFile = new File(path);
        if (pathFile.isAbsolute()) {
            return pathFile;
        } else {
            String workingDir = (String) getParameterValue(WORKING_DIR);
            return new File((workingDir != null ? workingDir : DEF_WORKING_DIR) + File.separator + path);
        }
    }
}
