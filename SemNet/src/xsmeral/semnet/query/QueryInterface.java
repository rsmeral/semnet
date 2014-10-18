package xsmeral.semnet.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.BindingSet;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.rdfxml.util.RDFXMLPrettyWriter;
import org.openrdf.sail.memory.MemoryStore;
import xsmeral.semnet.crawler.ConfigurationException;
import xsmeral.semnet.sink.RepositoryFactory;
import xsmeral.semnet.sink.SesameWriter;

/**
 * Simple interface for querying of the Sesame Database.
 * Configuration is the same as in {@link SesameWriter}.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class QueryInterface {

    private class TupleQueryWrapper {

        private TupleQuery q;
        private TupleQueryResult r;

        public TupleQueryWrapper(TupleQuery q) {
            this.q = q;
        }

        public TupleQueryWrapper bind(String name, Value val) {
            q.setBinding(name, val);
            return this;
        }

        public List<BindingSet> getBindingSets() throws QueryEvaluationException {
            r = q.evaluate();
            List<BindingSet> out = new ArrayList<BindingSet>();
            while (r.hasNext()) {
                BindingSet bindingSet = r.next();
                out.add(bindingSet);
            }
            r.close();
            return out;
        }

        public List<Value> getValues(String bindName) throws QueryEvaluationException {
            r = q.evaluate();
            List<Value> out = new ArrayList<Value>();
            while (r.hasNext()) {
                Value val = r.next().getBinding(bindName).getValue();
                out.add(val);
            }
            r.close();
            return out;
        }
    }

    public static final String QUERY_DESCRIBE = "SELECT ?prop ?hasValue ?isValueOf WHERE {{?x ?prop ?hasValue} UNION {?isValueOf ?prop ?x}} ORDER BY ?prop";// SPARQL
    public static final String QUERY_FULLTYPE = "SELECT ?cls WHERE {?x a ?cls}";// SPARQL
    public static final String QUERY_DIRECTTYPE = "SELECT cls FROM {x} rdf:type {cls} WHERE NOT EXISTS(SELECT otherCls FROM {x} rdf:type {otherCls} rdfs:subClassOf {cls} WHERE otherCls!=cls)";// SeRQL
    public static final String QUERY_WHAT = "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> SELECT ?node WHERE {?node rdfs:label ?label}";// SPARQL
    private TupleQuery directTypeQuery;
    private TupleQuery fullTypeQuery;
    private TupleQuery descQuery;
    private TupleQuery whatQuery;
    private Properties props;
    private Repository repo;
    private ValueFactory f;
    private RepositoryConnection conn;

    /**
     * Initializes the repository from the given configuration.
     */
    public QueryInterface(Properties props) throws ConfigurationException, MalformedQueryException {
        try {
            this.props = props;
            Class factory = Class.forName(props.getProperty("connFactory"));
            RepositoryFactory rf = (RepositoryFactory) factory.newInstance();
            rf.initialize(props);
            this.repo = rf.getRepository();
            this.conn = repo.getConnection();
            f = conn.getValueFactory();
        } catch (RepositoryException ex) {
            throw new ConfigurationException("Can't connect to repository: " + ex.getMessage());
        } catch (ClassNotFoundException ex) {
            throw new ConfigurationException("Can't create repository factory: " + ex.getMessage());
        } catch (InstantiationException ex) {
            throw new ConfigurationException("Can't create repository factory: " + ex.getMessage());
        } catch (IllegalAccessException ex) {
            throw new ConfigurationException("Can't create repository factory: " + ex.getMessage());
        }
    }

    /**
     * Returns a value factory instance.
     */
    public ValueFactory getValueFactory() {
        return f;
    }

    /**
     * Returns the underlying repository.
     */
    public Repository getRepository() {
        return repo;
    }

    /**
     * Returns a connection to the underlying repository.
     */
    public RepositoryConnection getConnection() {
        return conn;
    }

    /**
     * Indicates, whether a statement with the given String literal as object is
     * in the repository.
     */
    public boolean containsLiteral(String literal) throws RepositoryException {
        return conn.hasStatement(null, null, f.createLiteral(literal), false);
    }

    /**
     * Returns number of explicit statements in the repository.
     */
    public long count() throws RepositoryException {
        return conn.size();
    }

    /**
     * Returns direct type of the resource represented by the given URI.
     * A resource X is of direct type Y iff:
     * <ol>
     * <li>X rdf:type Y.</li>
     * <li>There is no class Z (Z != Y) such that X rdf:type Z and Z rdfs:subClassOf Y.</li>
     * </ol>
     */
    public Collection<Value> type(URI uri) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        if (directTypeQuery == null) {
            directTypeQuery = conn.prepareTupleQuery(QueryLanguage.SERQL, QUERY_DIRECTTYPE);
        }
        return new TupleQueryWrapper(directTypeQuery).bind("x", uri).getValues("cls");
    }

    /**
     * Returns full type of the resource represented by the given URI.
     * <br />
     * That is every resource Y such that X rdf:type Y.
     */
    public Collection<Value> fullType(URI uri) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        if (fullTypeQuery == null) {
            fullTypeQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_FULLTYPE);
        }
        return new TupleQueryWrapper(fullTypeQuery).bind("x", uri).getValues("cls");
    }

    /**
     * Similarly to {@code DESCRIBE} in SPARQL, this method returns collection
     * of statements (as binding sets) in which the given URI is either a subject
     * or an object.
     */
    public Collection<BindingSet> describe(URI uri) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        if (descQuery == null) {
            descQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_DESCRIBE);
        }
        return new TupleQueryWrapper(descQuery).bind("x", uri).getBindingSets();
    }

    /**
     * Returns such resource X for which holds X rdfs:label Y, where Y is the given label.
     */
    public Collection<Value> what(String label) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        if (whatQuery == null) {
            whatQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_WHAT);
        }
        return new TupleQueryWrapper(whatQuery).bind("label", f.createLiteral(label)).getValues("node");
    }

    /**
     * Evaluates a supplied SeRQL tuple query and returns resulting binding sets.
     */
    public List<BindingSet> query(String query) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SERQL, query);
        return new TupleQueryWrapper(tupleQuery).getBindingSets();
    }

    /**
     * Closes connection and shuts down repository.
     */
    public void close() throws RepositoryException {
        conn.close();
        repo.shutDown();
    }

    public static void printUsage() {
        System.err.println("Usage: " + QueryInterface.class.getSimpleName() + " <conf> <operation> [args]\n"
                + "where <conf> is a RepositoryFactory configuration (Properties file) and operation is "
                + "one of [has, what, type, fulltype, describe, count, tquery]\n\n"
                + "has - takes a rdfs:label and returns a boolean indicating presence of a resource with given label\n"
                + "what - takes a rdfs:label and returns a list of resources with given label\n"
                + "type - takes a URI and returns a direct type of the given resource\n"
                + "fulltype - takes a URI and returns all types of the given resource, including those inferred from the class hierarchy\n"
                + "describe - takes a URI and returns all statements pertaining to the given resource\n"
                + "count - returns the number of explicit statements currently present in the repository\n"
                + "tquery - takes a SeRQL tuple query and returns binding sets\n"
                + "gquery - takes a SeRQL graph query and a file name and evaluates the query to the file as RDF\n"
                + "rdfdump - takes a file name and dumps contents of the database as RDF");
    }

    public static void main(String[] args) {
        final String HAS = "has", TYPE = "type", FULLTYPE = "fulltype", DESCRIBE = "describe", COUNT = "count", WHAT = "what", TQUERY = "tquery", GQUERY = "gquery", RDFDUMP = "rdfdump";
        final String FIX = "fix";//DEBUG, remove
        List<String> operations = Arrays.asList(new String[]{HAS, COUNT, DESCRIBE, TYPE, FULLTYPE, WHAT, TQUERY, GQUERY, RDFDUMP, /*FIX*/});
        if (args.length >= 2) {
            String propsFileName = args[0];
            File propsFile = new File(propsFileName);
            if (propsFile.exists()) {
                String operation = args[1];
                if (operations.contains(operation)) {
                    QueryInterface qif = null;
                    try {
                        Properties props = new Properties();
                        props.load(new FileReader(propsFile));
                        props.setProperty(RepositoryFactory.PROP_WORKING_DIR, propsFile.getAbsoluteFile().getParent());
                        qif = new QueryInterface(props);
                        if (HAS.equals(operation)) {
                            if (args.length >= 3) {
                                String literal = args[2];
                                System.out.println(qif.containsLiteral(literal));
                            } else {
                                System.err.println("Missing argument");
                            }
                        } else if (COUNT.equals(operation)) {
                            System.out.println(qif.count() + " explicit statements");
                        } else if (DESCRIBE.equals(operation)) {
                            if (args.length >= 3) {
                                String uriStr = args[2];
                                try {
                                    URI uri = qif.getValueFactory().createURI(uriStr);
                                    Collection<BindingSet> describe = qif.describe(uri);
                                    for (BindingSet bindingSet : describe) {
                                        Value prop = bindingSet.getValue("prop");
                                        Value hasValue = bindingSet.getValue("hasValue");
                                        Value isValueOf = bindingSet.getValue("isValueOf");
                                        if (isValueOf == null) {
                                            System.out.println(prop + ", " + hasValue);
                                        } else {
                                            System.out.println(isValueOf + ", " + prop);
                                        }
                                    }
                                } catch (Exception ex) {
                                    System.err.println(ex.getMessage());
                                }
                            } else {
                                System.err.println("Missing argument");
                            }
                        } else if (FULLTYPE.equals(operation)) {
                            if (args.length >= 3) {
                                String uriStr = args[2];
                                try {
                                    URI uri = qif.getValueFactory().createURI(uriStr);
                                    Collection<Value> types = qif.fullType(uri);
                                    for (Value value : types) {
                                        System.out.println(value);
                                    }
                                } catch (Exception ex) {
                                    System.err.println(ex.getMessage());
                                }
                            } else {
                                System.err.println("Missing argument");
                            }
                        } else if (TYPE.equals(operation)) {
                            if (args.length >= 3) {
                                String uriStr = args[2];
                                try {
                                    URI uri = qif.getValueFactory().createURI(uriStr);
                                    Collection<Value> types = qif.type(uri);
                                    for (Value value : types) {
                                        System.out.println(value);
                                    }
                                } catch (Exception ex) {
                                    System.err.println(ex.getMessage());
                                }
                            } else {
                                System.err.println("Missing argument");
                            }
                        } else if (WHAT.equals(operation)) {
                            if (args.length >= 3) {
                                String label = args[2];
                                try {
                                    Collection<Value> nodes = qif.what(label);
                                    for (Value value : nodes) {
                                        System.out.println(value);
                                    }
                                } catch (Exception ex) {
                                    System.err.println(ex.getMessage());
                                }
                            } else {
                                System.err.println("Missing argument");
                            }
                        } else if (TQUERY.equals(operation)) {
                            if (args.length >= 3) {
                                String queryFile = args[2];
                                StringBuilder query = new StringBuilder();
                                BufferedReader reader = new BufferedReader(new FileReader(new File(queryFile)));
                                String line = null;
                                while ((line = reader.readLine()) != null) {
                                    query.append(line);
                                    query.append(" ");
                                }
                                reader.close();
                                try {
                                    List<BindingSet> result = qif.query(query.toString());
                                    for (BindingSet bindingSet : result) {
                                        System.out.println(bindingSet);
                                    }
                                } catch (Exception ex) {
                                    System.err.println(ex.getMessage());
                                }
                            } else {
                                System.err.println("Missing argument");
                            }
                        } else if (GQUERY.equals(operation)) {
                            if (args.length >= 4) {
                                String queryFileName = args[2];
                                String outFileName = args[3];

                                StringBuilder query = new StringBuilder();
                                BufferedReader reader = new BufferedReader(new FileReader(new File(queryFileName)));
                                String line = null;
                                while ((line = reader.readLine()) != null) {
                                    query.append(line);
                                    query.append(" ");
                                }
                                reader.close();
                                GraphQuery gquery = qif.getConnection().prepareGraphQuery(QueryLanguage.SERQL, query.toString());
                                try {
                                    gquery.evaluate(new RDFXMLPrettyWriter(new FileOutputStream(outFileName)));
                                } catch (QueryEvaluationException ex) {
                                    Logger.getLogger(QueryInterface.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            } else {
                                System.err.println("Missing argument");
                            }
                        } else if (RDFDUMP.equals(operation)) {
                            if (args.length >= 3) {
                                String dumpFileName = args[2];
                                qif.getConnection().export(new RDFXMLPrettyWriter(new FileOutputStream(dumpFileName)));

                            } else {
                                System.err.println("Missing argument");
                            }
                        } else if (FIX.equals(operation)) {
                            qif.close();
                            if (args.length >= 3) {
                                File inFile = new File(args[2]);
                                Repository repo = new SailRepository(new MemoryStore());
                                repo.initialize();
                                RepositoryConnection conn = repo.getConnection();
                                System.err.println("Loading '" + args[2] + "'");
                                conn.add(inFile, "", RDFFormat.RDFXML);
                                System.err.println(conn.size() + " statements loaded");

                                int l = 1913, u = 2012, i;
                                List<Statement> bad_st = new ArrayList<Statement>();
                                // iterate over years in interval
                                // for each year -> select * from (m origin year)
                                for (int year = l; year <= u; year++) {
                                    GraphQuery query = conn.prepareGraphQuery(QueryLanguage.SERQL, "CONSTRUCT * FROM {m} csfd:origin {\"" + year + "\"} USING NAMESPACE csfd = <http://www.csfd.cz#>");
                                    GraphQueryResult result = query.evaluate();
                                    while (result.hasNext()) {
                                        Statement st = result.next();
                                        bad_st.add(st);
                                    }
                                    result.close();
                                }

                                System.err.println(bad_st.size() + " wrong statements found.");
                                ValueFactory vf = repo.getValueFactory();
                                List<Statement> new_st = new ArrayList<Statement>();
                                for (Statement st : bad_st) {
                                    Resource sub = st.getSubject();
                                    URI pred = vf.createURI("http://www.csfd.cz#", "year");
                                    Value obj = vf.createLiteral(Integer.parseInt(st.getObject().stringValue()));
                                    new_st.add(vf.createStatement(sub, pred, obj));
                                }
                                conn.setAutoCommit(false);

                                // REMOVE
                                System.err.println("Removing...");
                                i = 0;
                                for (Statement st : bad_st) {
                                    conn.remove(st);
                                    i++;
                                    if (i % 100 == 0) {
                                        System.err.println(i);
                                    }
                                }
                                System.err.println(i);
                                conn.commit();

                                // ADD
                                System.err.println("Adding...");
                                i = 0;
                                for (Statement st : new_st) {
                                    conn.add(st);
                                    i++;
                                    if (i % 100 == 0) {
                                        System.err.println(i);
                                    }
                                }
                                System.err.println(i);
                                conn.commit();

                                String origName = inFile.getName();
                                String newFileName = origName.substring(0, origName.lastIndexOf(".")) + "-fixed.rdf";

                                System.err.println(conn.size() + " statements in repo after fix");
                                System.err.println("Exporting to " + newFileName);

                                conn.export(new RDFXMLPrettyWriter(new FileOutputStream(newFileName)));
                                conn.close();
                                repo.shutDown();
                            } else {
                                System.err.println("Missing argument");
                            }
                        }
                    } catch (RDFParseException ex) {
                        Logger.getLogger(QueryInterface.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (QueryEvaluationException ex) {
                        Logger.getLogger(QueryInterface.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (RDFHandlerException ex) {
                        Logger.getLogger(QueryInterface.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (MalformedQueryException ex) {
                        System.err.println("Malformed query: " + ex.getMessage());
                    } catch (RepositoryException ex) {
                        System.err.println("Error while working with repository: " + ex.getMessage());
                    } catch (ConfigurationException ex) {
                        System.err.println("Can't open query interface: " + ex.getMessage());
                    } catch (IOException ex) {
                        System.err.println("Can't read file: " + ex.getMessage());
                    } finally {
                        if (qif != null) {
                            try {
                                qif.close();
                            } catch (RepositoryException ex) {
                                System.err.println("Failed to close repository: " + ex.getMessage());
                            }
                        }
                    }
                } else {
                    System.err.println("Invalid operation: '" + operation + "'");
                    printUsage();
                }
            } else {
                System.err.println("File '" + propsFileName + "' does not exist");
            }
        } else {
            System.err.println("Operation not specified");
            printUsage();
        }
    }
}
