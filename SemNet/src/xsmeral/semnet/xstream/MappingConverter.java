package xsmeral.semnet.xstream;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.semnet.mapper.AssociationRole;
import xsmeral.semnet.mapper.Mapping;

/**
 * Converter for {@link Mapping}.
 * The marshalled file is sorted by entry keys.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class MappingConverter implements Converter {

    private class MapEntryComparator implements Comparator<Entry<String, String>> {

        @Override
        public int compare(Entry<String, String> o1, Entry<String, String> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    }

    private static final String ATTR_TARGET = "target";
    private static final String NODE_ENTRY = "entry";
    private static final String NODE_FROM = "from";
    private static final String NODE_TO = "to";

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Map<AssociationRole, Map<String, String>> src = ((Mapping) source).getMap();
        Map<Entry<String, String>, BitSet> inverseMap = new TreeMap<Entry<String, String>, BitSet>(new MapEntryComparator());
        for (AssociationRole a : src.keySet()) {
            Iterator<Entry<String, String>> it = src.get(a).entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, String> next = it.next();
                BitSet bs = inverseMap.get(next);
                if (bs == null) {
                    bs = new BitSet(AssociationRole.values().length);
                    inverseMap.put(next, bs);
                }
                bs.set(a.ordinal());
            }
        }
        Iterator<Entry<String, String>> it = inverseMap.keySet().iterator();
        while (it.hasNext()) {
            Entry<String, String> next = it.next();
            BitSet bs = inverseMap.get(next);
            String from = next.getKey();
            String to = next.getValue();
            String attrStr = "";
            for (AssociationRole ar : AssociationRole.values()) {
                if (bs.get(ar.ordinal())) {
                    if (!attrStr.isEmpty()) {
                        attrStr += ",";
                    }
                    attrStr += ar.name();
                }
            }
            writer.startNode(NODE_ENTRY);
            if (!attrStr.isEmpty()) {
                writer.addAttribute(ATTR_TARGET, attrStr);
            }
            writer.startNode(NODE_FROM);
            writer.setValue(from);
            writer.endNode();//from
            writer.startNode(NODE_TO);
            writer.setValue(to);
            writer.endNode();//to
            writer.endNode();//entry
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Mapping map = new Mapping();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if (NODE_ENTRY.equals(reader.getNodeName())) {
                String from = null, to = null;
                String attrStr = reader.getAttribute(ATTR_TARGET);
                String[] tokens = new String[]{};
                if (attrStr != null) {
                    tokens = attrStr.split("\\s*,\\s*");
                }
                while (reader.hasMoreChildren()) {
                    reader.moveDown();
                    String curNode = reader.getNodeName();
                    if (NODE_FROM.equals(curNode)) {
                        from = reader.getValue();
                    } else if (NODE_TO.equals(curNode)) {
                        to = reader.getValue();
                    }
                    reader.moveUp();
                }
                if (from != null && to != null) {
                    if (tokens.length != 0) {
                        for (String token : tokens) {
                            try {
                                AssociationRole ar = AssociationRole.valueOf(token);
                                map.addEntry(ar, from, to);
                            } catch (IllegalArgumentException ex) {
                                Logger.getLogger(MappingConverter.class.getName()).log(Level.WARNING, "Illegal value for AssociationRole: {0}", token);
                            }
                        }
                    } else {
                        for (AssociationRole ar : AssociationRole.values()) {
                            map.addEntry(ar, from, to);
                        }
                    }
                }
            }
            reader.moveUp();
        }
        return map;
    }

    @Override
    public boolean canConvert(Class type) {
        return Mapping.class.equals(type);
    }
}
