package xsmeral.semnet.sink;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openrdf.model.Statement;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParseException;
import xsmeral.pipe.LocalObjectSink;
import xsmeral.pipe.ProcessorStoppedException;
import xsmeral.pipe.context.FSContext;
import xsmeral.pipe.interfaces.ObjectProcessorInterface;
import xsmeral.pipe.interfaces.Param;
import xsmeral.pipe.stats.StatFunction;
import xsmeral.pipe.stats.Stats;
import xsmeral.pipe.stats.Sum;

/**
 * Writes Statements to a Sesame database.
 * The connection to database is configured using a configured RepositoryFactory
 * implementation.<br />
 * The configuration is read from a Properties file that should contain fields:
 * <ul>
 *  <li><code>connFactory</code> - with a value of fully-qualified name of a
 *      RepositoryFactory implementation</li>
 *  <li>other implementation-specific parameters</li>
 * </ul>
 * In the Properties, a field with a name equal to {@link RepositoryFactory#PROP_WORKING_DIR}
 * is set to the value of current working directory (used by implementations
 * that use file system to resolve relative file names).
 * <br />
 * <h4>Bootstrapping</h4>
 * There is an option to bootstrap the database with statements from a file.
 * The file names and types have to be specified in the {@code bootstrap}
 * initialization parameter, like in the following example:
 * <pre>
 *     &lt;processor ...bootstrap=&quot;<b style="color: blue;">file1.rdf:RDFXML,file2.n3:N3</b>&quot;...&gt;...
 * </pre>
 * or more generally
 * <pre>
 *     <b><em>file[:type][,file[:type]...]</em></b>
 * </pre>
 * where {@code type} refers to a constant defined in Sesame's {@link RDFFormat}.
 * If the type is not specified, it is guessed from the file name extension.
 *
 * @init conf Name of Properties file containing configuration of Repository
 * @init bootstrap (Optional) Names of files (comma-separated) containing statements that should be added
 *  to the database prior to running. Format for the parameter specified in description
 *  of this class.
 * @init bootBase (Optional) Only applies if bootstrap is used; specifies the base
 *  URI for any relative URIs in the bootstrapped files. Default is a reference to 
 *  runtime directory.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @see RepositoryFactory
 */
@ObjectProcessorInterface(in = Statement.class)
public class SesameWriter extends LocalObjectSink<Statement> {

    public static final String BOOTSTRAP_OLD_SUFFIX = ".old";
    @Param("conf")
    private String propsFileName;
    @Param("bootstrap")
    private String bootstrapParam = "";
    @Param
    private String bootBase = "";
    RepositoryConnection conn;
    StatFunction<Long> count;

    public SesameWriter() {
    }

    /**
     * Initializes with the specified connection.
     */
    public SesameWriter(RepositoryConnection conn) {
        if (conn != null) {
            this.conn = conn;
        } else {
            failStart("Invalid Sesame connection");
        }
    }

    /**
     * Reads the supplied configuration (Properties) file, sets working directory, initializes repository factory.
     * @see RepositoryFactory
     */
    @Override
    protected void initPostContext() {
        try {
            Properties props = new Properties();
            File propsFile = ((FSContext) getContext()).getFile(propsFileName);
            props.load(new FileReader(propsFile));
            props.setProperty(RepositoryFactory.PROP_WORKING_DIR, propsFile.getAbsoluteFile().getParent());
            Class factory = Class.forName(props.getProperty("connFactory"));
            RepositoryFactory rf = (RepositoryFactory) factory.newInstance();
            rf.initialize(props);
            this.conn = rf.getRepository().getConnection();
            if (conn == null) {
                failStart("Invalid Sesame connection");
            } else {
                doBootstrap();
            }
            count = new Stats(this).newFunction("stat.count", Sum.class);
        } catch (RepositoryException ex) {
            failStart("Can't open repository connection", ex);
        } catch (ClassNotFoundException ex) {
            failStart("Repository factory class not found", ex);
        } catch (FileNotFoundException ex) {
            failStart("Properties file for repository factory not found", ex);
        } catch (IOException ex) {
            failStart("Can't read properties file for repository factory", ex);
        } catch (InstantiationException ex) {
            failStart("Can't instantiate repository factory", ex);
        } catch (IllegalAccessException ex) {
            failStart("Can't instantiate repository factory", ex);
        }
    }

    /**
     * Adds a read statement to the database.
     */
    @Override
    protected void process() throws ProcessorStoppedException {
        try {
            conn.add(read());
            count.add();
        } catch (RepositoryException ex) {
            Logger.getLogger(SesameWriter.class.getName()).log(Level.SEVERE, "Error while adding statement, stopping", ex);
            stop();
        }
    }

    /**
     * Closes the connection.
     */
    @Override
    protected void postRun() {
        try {
            conn.close();
        } catch (RepositoryException ex) {
            Logger.getLogger(SesameWriter.class.getName()).log(Level.SEVERE, "Failed to close repository connection", ex);
        }
    }

    private void doBootstrap() {
        if (bootstrapParam != null && !bootstrapParam.isEmpty()) {
            String[] filesAndTypes = bootstrapParam.split("\\s*,\\s*");
            for (String string : filesAndTypes) {
                String[] fileAndType = string.split(":");
                if (fileAndType.length >= 1) {
                    String fileName = fileAndType[0];
                    String fileFormat = fileAndType.length == 2 ? fileAndType[1] : null;
                    RDFFormat format;
                    if (fileFormat != null && !fileFormat.isEmpty()) {
                        format = RDFFormat.valueOf(fileFormat);
                    } else {
                        format = RDFFormat.forFileName(fileName);
                    }
                    if (format != null) {
                        File file = ((FSContext) getContext()).getFile(fileName);
                        if (file.exists()) {
                            String baseURI = (bootBase != null && !bootBase.isEmpty()) ? bootBase : null;
                            try {
                                long statementsBefore = conn.size();
                                long fileSize = file.length();
                                Logger.getLogger(SesameWriter.class.getName()).log(Level.INFO, "Bootstrapping file {0} ({1} KB), might take a long time", new Object[]{fileName, fileSize / 1024});
                                conn.add(file, baseURI, format);
                                file.renameTo(new File(file.getPath() + BOOTSTRAP_OLD_SUFFIX));
                                Logger.getLogger(SesameWriter.class.getName()).log(Level.INFO, "File {0} parsed, {1} statements added", new Object[]{fileName, conn.size() - statementsBefore});
                            } catch (IOException ex) {
                                Logger.getLogger(SesameWriter.class.getName()).log(Level.WARNING, "Can''t add statements to repository: {0}", ex.getMessage());
                            } catch (RDFParseException ex) {
                                Logger.getLogger(SesameWriter.class.getName()).log(Level.WARNING, "Can''t add statements to repository: {0}", ex.getMessage());
                            } catch (RepositoryException ex) {
                                Logger.getLogger(SesameWriter.class.getName()).log(Level.WARNING, "Can''t add statements to repository: {0}", ex.getMessage());
                            }
                        } else {
                            Logger.getLogger(SesameWriter.class.getName()).log(Level.WARNING, "Bootstrap file ''{0}'' doesn''t exist", fileName);
                        }
                    } else {
                        Logger.getLogger(SesameWriter.class.getName()).log(Level.WARNING, "Invalid RDFFormat specified");
                    }
                } else {
                    Logger.getLogger(SesameWriter.class.getName()).log(Level.WARNING, "Invalid format of bootstrap parameter");
                }
            }
        }
    }
}
