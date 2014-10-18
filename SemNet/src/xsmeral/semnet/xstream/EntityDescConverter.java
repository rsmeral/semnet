package xsmeral.semnet.xstream;

import java.util.Collection;
import java.util.regex.Pattern;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.HashSet;
import xsmeral.semnet.crawler.model.EntityDescriptor;
import xsmeral.semnet.crawler.model.URLEntry;
import xsmeral.semnet.manager.Configuration;

/**
 * Converter for {@link EntityDescriptor}.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class EntityDescConverter implements Converter {

    private static final String WEIGHT_ATTR = "weight";
    private static final String URLPATTERN_NODE = "pattern";
    private static final String UPDFREQ_ATTR = "update";
    private static final String SCRAPER_NODE = "scraper";

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        EntityDescriptor desc = (EntityDescriptor) source;

        writer.addAttribute(WEIGHT_ATTR, String.valueOf(desc.getWeight()));
        writer.startNode(URLPATTERN_NODE);
        writer.addAttribute(UPDFREQ_ATTR, String.valueOf(desc.getUpdateFreq() / URLEntry.DAY_SEC));
        writer.setValue(desc.getUrlPattern().toString());
        writer.endNode();

        Collection<Configuration> scrapers = desc.getScrapers();
        for (Configuration conf : scrapers) {
            writer.startNode(SCRAPER_NODE);
            context.convertAnother(conf);
            writer.endNode();
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Pattern patt = null;
        Integer updFreq = URLEntry.DEF_ENTITY_UPDATE;
        Collection<Configuration> scrapers = new HashSet<Configuration>();
        Integer weight = 1;
        if (reader.getAttributeCount() > 0) {
            String weightStr = reader.getAttribute(WEIGHT_ATTR);
            if (weightStr != null) {
                try {
                    weight = Math.max(Integer.valueOf(weightStr), 1);
                } catch (NumberFormatException ex) {
                    // keep default = 1
                }
            }
        }
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if (URLPATTERN_NODE.equals(reader.getNodeName())) {
                if (reader.getAttributeCount() > 0) {
                    updFreq = Integer.valueOf(reader.getAttribute(UPDFREQ_ATTR)) * URLEntry.DAY_SEC;
                }// else keep default
                patt = Pattern.compile(reader.getValue());
            } else if (SCRAPER_NODE.equals(reader.getNodeName())) {
                Configuration conf = (Configuration) context.convertAnother(context, Configuration.class);
                scrapers.add(conf);
            }
            reader.moveUp();
        }
        EntityDescriptor desc = new EntityDescriptor(patt, updFreq, scrapers, weight);
        return desc;
    }

    @Override
    public boolean canConvert(Class type) {
        return EntityDescriptor.class.equals(type);
    }
}
