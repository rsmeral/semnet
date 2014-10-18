package xsmeral.semnet.xstream;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import xsmeral.semnet.crawler.model.EntityDescriptor;
import xsmeral.semnet.crawler.model.HostDescriptor;
import xsmeral.semnet.crawler.model.URLEntry;

/**
 * Converter for {@link HostDescriptor}.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class HostDescConverter implements Converter {

    private static final String BASEURL_NODE = "baseURL";
    private static final String NAME_NODE = "name";
    private static final String CHARSET_NODE = "charset";
    private static final String CRAWLDELAY_NODE = "crawlDelay";
    private static final String SOURCEFIRST_NODE = "sourceFirst";
    private static final String SOURCEPATTERNS_NODE = "source";
    private static final String PATTERN_NODE = "pattern";
    private static final String ENTITIES_NODE = "entities";
    private static final String UPDFREQ_ATTR = "update";

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        HostDescriptor desc = (HostDescriptor) source;

        String baseURL = desc.getBaseURL();
        if (baseURL != null) {
            writer.startNode(BASEURL_NODE);
            writer.setValue(baseURL);
            writer.endNode();
        }

        String name = desc.getName();
        if (name != null) {
            writer.startNode(NAME_NODE);
            writer.setValue(name);
            writer.endNode();
        }

        String charset = desc.getCharset();
        if (charset != null) {
            writer.startNode(CHARSET_NODE);
            context.convertAnother(charset);
            writer.endNode();
        }

        Integer crawlDelay = desc.getCrawlDelay();
        if (crawlDelay != null) {
            writer.startNode(CRAWLDELAY_NODE);
            writer.setValue(Integer.toString(crawlDelay));
            writer.endNode();
        }

        boolean sourceFirst = desc.isSourceFirst();
        writer.startNode(SOURCEFIRST_NODE);
        writer.setValue(Boolean.toString(sourceFirst));
        writer.endNode();

        Map<Pattern, Integer> sourceURLPatterns = desc.getSourceURLPatterns();
        if (sourceURLPatterns != null && !sourceURLPatterns.isEmpty()) {
            writer.startNode(SOURCEPATTERNS_NODE);
            for (Pattern sourceURLPattern : sourceURLPatterns.keySet()) {
                writer.startNode(PATTERN_NODE);
                writer.addAttribute(UPDFREQ_ATTR, String.valueOf(sourceURLPatterns.get(sourceURLPattern) / URLEntry.DAY_SEC));
                writer.setValue(sourceURLPattern.toString());
                writer.endNode();
            }
            writer.endNode();
        }

        Collection<EntityDescriptor> entities = desc.getEntityDescriptors();
        if (!entities.isEmpty()) {
            writer.startNode(ENTITIES_NODE);
            context.convertAnother(entities);
            writer.endNode();
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        HostDescriptor desc = new HostDescriptor();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            String curNode = reader.getNodeName();
            if (BASEURL_NODE.equals(curNode)) {
                desc.setBaseURL(reader.getValue());
            } else if (NAME_NODE.equals(curNode)) {
                desc.setName(reader.getValue());
            } else if (CHARSET_NODE.equals(curNode)) {
                desc.setCharset(reader.getValue());
            } else if (SOURCEFIRST_NODE.equals(curNode)) {
                desc.setSourceFirst(Boolean.valueOf(reader.getValue()));
            } else if (CRAWLDELAY_NODE.equals(curNode)) {
                try {
                    Integer crawlDelay = Integer.parseInt(reader.getValue());
                    desc.setCrawlDelay(crawlDelay);
                } catch (NumberFormatException ex) {
                    Logger.getLogger(HostDescConverter.class.getName()).log(Level.SEVERE, "Invalid crawl delay value, ignoring", ex);
                }
            } else if (SOURCEPATTERNS_NODE.equals(curNode)) {
                while (reader.hasMoreChildren()) {
                    reader.moveDown();
                    if (PATTERN_NODE.equals(reader.getNodeName())) {
                        int updFreq;
                        if (reader.getAttributeCount() > 0) {
                            updFreq = Integer.valueOf(reader.getAttribute(UPDFREQ_ATTR)) * URLEntry.DAY_SEC;
                        } else {
                            updFreq = URLEntry.DEF_SOURCE_UPDATE;
                        }
                        String pattern = reader.getValue();
                        desc.addSourceURLPattern(Pattern.compile(pattern), updFreq);
                    }
                    reader.moveUp();
                }
            } else if (ENTITIES_NODE.equals(curNode)) {
                Collection<EntityDescriptor> entities = (Collection<EntityDescriptor>) context.convertAnother(desc, Collection.class);
                desc.setEntityDescriptors(entities);
            }
            reader.moveUp();
        }
        return desc;
    }

    @Override
    public boolean canConvert(Class type) {
        return HostDescriptor.class.equals(type);
    }
}
