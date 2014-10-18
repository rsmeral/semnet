package xsmeral.semnet.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.semnet.crawler.ConfigurationException;

/**
 * Various utility methods.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class Util {

    private static XStream xs;

    /**
     * Simplifies the following expression:<br />
     * <pre>  a != null ? a : b != null ? b : c</pre> to <br />
     * <pre>  nonNull(a, b, c)</pre>
     * @param <T> Any object type (or primitive (autoboxed))
     * @param objects The objects
     * @return First non-null object or null, if all are null
     */
    @SuppressWarnings("empty-statement")
    public static <T> T nonNull(T... objects) {
        int i = 0;
        while (i < objects.length && objects[i++] == null);
        return objects[i - 1];
    }

    /**
     * Non-generic version of {@link #nonNull(Object[]) nonNull(T...)}
     */
    public static Object nonNullObject(Object... objects) {
        return nonNull(objects);
    }

    /**
     * Deserializes an object from XML file, using <a href="http://xstream.codehaus.org/">XStream</a>
     *
     * @param xmlFileName Name of the XML file
     * @param cls The class of the object
     * @return The deserialized object, or null if the file is not found
     */
    public static <T> T objectFromXml(String xmlFileName, Class<T> cls) throws ConfigurationException {
        initXStream();
        xs.processAnnotations(cls);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(xmlFileName);
            Object obj = xs.fromXML(fis);
            return (T) obj;
        } catch (Exception ex) {
            throw new ConfigurationException("Can't unmarshal object from file " + xmlFileName + ": " + ex.getMessage());
        } finally {
            try {
                fis.close();
            } catch (IOException ex) {
                Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Serializes an object to XML file, using <a href="http://xstream.codehaus.org/">XStream</a>
     *
     * @param obj The object to serialize
     * @param xmlFileName Name of the XML file
     * @return True on success, false otherwise
     */
    public static boolean objectToXml(Object obj, String xmlFileName) {
        initXStream();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(xmlFileName);
            xs.toXML(obj, fos);
            return true;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Util.class.getName()).log(Level.SEVERE, "Can't marshal object to file " + xmlFileName, ex);
            return false;
        } catch (XStreamException ex) {
            Logger.getLogger(Util.class.getName()).log(Level.SEVERE, "Can't marshal object to file " + xmlFileName, ex);
            return false;
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Initializes the XStream instance used for XML (de)serialization
     */
    private static void initXStream() {
        if (xs == null) {
            xs = new XStream();
            xs.addDefaultImplementation(ArrayList.class, Collection.class);
            xs.setMode(XStream.NO_REFERENCES);
        }
        xs.autodetectAnnotations(true);
    }
}
