package xsmeral.semnet.xstream;

import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import xsmeral.semnet.manager.Configuration;

/**
 * Converter for {@link Configuration}.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class ConfigurationConverter implements Converter {

    private static final String NODE_PROCESSOR = "processor";

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Configuration conf = (Configuration) source;
        Map<String, String> params = conf.getParams();
        if (params != null) {
            Iterator<String> it = params.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                writer.addAttribute(key, params.get(key));
            }
        }
        writer.setValue(conf.getClazz().getName());
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Class cls = null;
        Map<String, String> params = new HashMap<String, String>();
        Iterator it = reader.getAttributeNames();
        while (it.hasNext()) {
            String name = (String) it.next();
            String val = reader.getAttribute(name);
            params.put(name, val);
        }
        String clsName = reader.getValue();
        try {
            cls = Class.forName(clsName);
        } catch (ClassNotFoundException ex) {
            throw new XStreamException("Class " + clsName + " not found");
        }
        return new Configuration(cls, params);
    }

    @Override
    public boolean canConvert(Class type) {
        return Configuration.class.equals(type);
    }
}
