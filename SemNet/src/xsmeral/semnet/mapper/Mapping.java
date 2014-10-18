package xsmeral.semnet.mapper;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import xsmeral.semnet.xstream.MappingConverter;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple container for a map, with custom converter.
 * @see MappingConverter
 * @see StatementMapper
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@XStreamAlias("mapping")
@XStreamConverter(MappingConverter.class)
public class Mapping {

    private Map<AssociationRole, Map<String, String>> map;

    public Mapping() {
        map = new EnumMap<AssociationRole, Map<String, String>>(AssociationRole.class);
        for (AssociationRole r : AssociationRole.values()) {
            map.put(r, new HashMap<String, String>());
        }
    }

    public Mapping(Map<AssociationRole, Map<String, String>> map) {
        this.map = map;
    }

    public Map<AssociationRole, Map<String, String>> getMap() {
        return map;
    }

    public void addEntry(AssociationRole target, String key, String value) {
        map.get(target).put(key, value);
    }

    public String getEntry(AssociationRole target, String key) {
        return map.get(target).get(key);
    }

    public boolean containsKey(AssociationRole target, String key) {
        return map.get(target).containsKey(key);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Mapping other = (Mapping) obj;
        if (this.map != other.map && (this.map == null || !this.map.equals(other.map))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + (this.map != null ? this.map.hashCode() : 0);
        return hash;
    }
}
