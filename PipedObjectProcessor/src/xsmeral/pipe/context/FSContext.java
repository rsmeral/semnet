package xsmeral.pipe.context;

import java.io.File;

/**
 * A file system context, based on the notion of a single working directory.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public interface FSContext {

    /**
     * Sets the working directory to the given path (relative or absolute)
     */
    public void setWorkingDir(String path);

    /**
     * Returns the working directory
     */
    public String getWorkingDir();

    /**
     * Returns a file with the specified path.
     * If the path is relative, it is resolved against the working directory.
     */
    public File getFile(String path);

}
