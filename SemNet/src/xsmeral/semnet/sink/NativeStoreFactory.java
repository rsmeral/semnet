package xsmeral.semnet.sink;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.NotifyingSail;
import org.openrdf.sail.Sail;
import org.openrdf.sail.nativerdf.NativeStore;

/**
 * Factory of {@link NativeStore} repositories.
 * <br />
 * Takes parameters corresponding to NativeStore {@linkplain NativeStore#NativeStore(java.io.File, java.lang.String) constructor}:
 * <ul>
 *  <li><code>indexes</code> - 4-letter strings separated by comma, consisting of letters <code>[s, o, p, c]</code>, e.g. "sopc,ospc"</li>
 *  <li><code>dataDir</code> - name of folder for storing data</li>
 *  <li><code>inferencer</code> - (Optional) FQN of Sesame inferencer class</li>
 * </ul>
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class NativeStoreFactory extends RepositoryFactory {

    private static final String DEF_WORKING_DIR = ".";

    @Override
    public void initialize() throws RepositoryException {
        Properties props = getProperties();
        String dataDirName = props.getProperty("dataDir");
        String indexes = props.getProperty("indexes");
        String inferencerClassName = props.getProperty("inferencer");
        if (dataDirName == null || indexes == null) {
            throw new RepositoryException("Invalid parameters for repository");
        } else {
            File dataDirFile = new File(dataDirName);
            if (!dataDirFile.isAbsolute()) {
                String workingDir = props.getProperty(RepositoryFactory.PROP_WORKING_DIR);
                dataDirFile = new File((workingDir != null ? workingDir : DEF_WORKING_DIR) + File.separator + dataDirName);
            }
            NotifyingSail nativeStore = new NativeStore(dataDirFile, indexes);
            Sail sail = nativeStore;
            if (inferencerClassName != null && !inferencerClassName.isEmpty()) {
                try {
                    Class infCls = Class.forName(inferencerClassName);
                    Constructor infCons = infCls.getConstructor(NotifyingSail.class);
                    sail = (Sail) infCons.newInstance(nativeStore);
                } catch (InstantiationException ex) {
                    Logger.getLogger(NativeStoreFactory.class.getName()).log(Level.WARNING, "Inferencing NOT USED, failed to instantiate inferencer class: {0}", ex.getMessage());
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(NativeStoreFactory.class.getName()).log(Level.WARNING, "Inferencing NOT USED, failed to instantiate inferencer class: {0}", ex.getMessage());
                } catch (IllegalArgumentException ex) {
                    Logger.getLogger(NativeStoreFactory.class.getName()).log(Level.WARNING, "Inferencing NOT USED, failed to instantiate inferencer class: {0}", ex.getMessage());
                } catch (InvocationTargetException ex) {
                    Logger.getLogger(NativeStoreFactory.class.getName()).log(Level.WARNING, "Inferencing NOT USED, failed to instantiate inferencer class: {0}", ex.getMessage());
                } catch (NoSuchMethodException ex) {
                    Logger.getLogger(NativeStoreFactory.class.getName()).log(Level.WARNING, "Inferencing NOT USED, failed to instantiate inferencer class: {0}", ex.getMessage());
                } catch (SecurityException ex) {
                    Logger.getLogger(NativeStoreFactory.class.getName()).log(Level.WARNING, "Inferencing NOT USED, failed to instantiate inferencer class: {0}", ex.getMessage());
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(NativeStoreFactory.class.getName()).log(Level.WARNING, "Inferencing NOT USED, failed to instantiate inferencer class: {0}", ex.getMessage());
                }
            }
            Repository repo = new SailRepository(sail);
            repo.initialize();
            setRepository(repo);
        }
    }
}
