package xsmeral.semnet.mapper;

import java.io.File;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import xsmeral.pipe.LocalObjectFilter;
import xsmeral.pipe.ProcessorStoppedException;
import xsmeral.pipe.context.FSContext;
import xsmeral.pipe.interfaces.ObjectProcessorInterface;
import xsmeral.pipe.interfaces.Param;
import xsmeral.semnet.crawler.ConfigurationException;
import xsmeral.semnet.util.Util;

/**
 * An object processor, a filter, that maps URIs in subject or predicate to different URIs.
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @see Mapping
 * @init mapping Name of the file containing a Mapping
 */
@ObjectProcessorInterface(in = Statement.class, out = Statement.class)
public class StatementMapper extends LocalObjectFilter<Statement, Statement> {

    @Param("mapping")
    private String mappingFileName;
    private Mapping mapping;
    private ValueFactory f;

    public StatementMapper() {
        f = ValueFactoryImpl.getInstance();
    }

    public StatementMapper(Mapping mapping) {
        this();
        if (mapping != null) {
            this.mapping = mapping;
        } else {
            failStart("Invalid Mapping");
        }
    }

    @Override
    public void initPostContext() {
        try {
            File mappingFile = ((FSContext) getContext()).getFile(mappingFileName);
            Mapping loadedMapping = Util.objectFromXml(mappingFile.getAbsolutePath(), Mapping.class);
            setMapping(loadedMapping);
        } catch (ConfigurationException ex) {
            failStart("Can't load mapping: " + ex.getMessage());
        }
    }

    @Override
    protected void process() throws ProcessorStoppedException {
        write(mapStatement(read()));
    }

    private Statement mapStatement(Statement st) {
        boolean changed = false;
        String mapped = null;
        Resource sub = st.getSubject();
        URI pre = st.getPredicate();
        Value obj = st.getObject();

        // subject
        if (sub instanceof URI) {
            mapped = mapping.getEntry(AssociationRole.SUBJECT, sub.stringValue());
            if (mapped != null) {
                sub = f.createURI(mapped);
                changed = true;
            }
        }

        // predicate
        mapped = mapping.getEntry(AssociationRole.PREDICATE, pre.stringValue());
        if (mapped != null) {
            pre = f.createURI(mapped);
            changed = true;
        }

        // object
        if (obj instanceof URI) {
            mapped = mapping.getEntry(AssociationRole.OBJECT, obj.stringValue());
            if (mapped != null) {
                obj = f.createURI(mapped);
                changed = true;
            }
        }
        return changed ? f.createStatement(sub, pre, obj) : st;
    }

    public Mapping getMapping() {
        return mapping;
    }

    public void setMapping(Mapping mapping) {
        this.mapping = mapping;
    }
}
