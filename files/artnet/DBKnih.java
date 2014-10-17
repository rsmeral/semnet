package xsmeral.artnet.scraper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.htmlcleaner.TagNode;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import xsmeral.semnet.crawler.model.EntityDocument;
import xsmeral.semnet.scraper.AbstractScraper;
import xsmeral.semnet.scraper.onto.EntityClass;
import xsmeral.semnet.scraper.onto.Term;
import xsmeral.semnet.util.XPathUtil;

public class DBKnih {

    public static class Kniha extends AbstractScraper {

        private static final Pattern PATT_ISBN = Pattern.compile("ISBN:\\s*([\\d-]+[\\d])");
        private static final Pattern PATT_YEAR = Pattern.compile("Rok vydání:\\s*(\\d{4})");
        public static final String NAMESPACE = "http://www.databazeknih.cz#";
        @Term
        public static final URI TYPE = RDF.TYPE;
        @Term
        @EntityClass
        public static final URI BOOK = f.createURI(NAMESPACE, "book");
        @Term
        public static final URI TITLE = RDFS.LABEL;
        @Term("Has author")
        public static final URI AUTHOR = f.createURI(NAMESPACE, "author");
        @Term
        public static final URI YEAR = f.createURI(NAMESPACE, "year");
        @Term
        public static final URI ISBN = f.createURI(NAMESPACE, "isbn");

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }

        @Override
        protected void scrape(EntityDocument doc) throws Exception {
            TagNode bookNode = (TagNode) doc.getDocument().evaluateXPath("//*[@id='book']")[0];

            // state type of this entity
            fact(TYPE, BOOK);

            // main name
            String bookName = XPathUtil.queryText(bookNode, "h1[@class='bookname']");
            fact(TITLE, lit(bookName));

            // original name
            String otherName = XPathUtil.queryText(bookNode, "h2[@class='otitle']");
            if (otherName != null) {
                fact(TITLE, lit(otherName));
            }

            // author name(s)
            for (String author : XPathUtil.queryTextNodes(bookNode, "h2[@class='jmenaautoru']/a/@href")) {
                fact(AUTHOR, uri(author));
            }

            // year of publishing, ISBN
            List<TagNode> bookInfoNodes = XPathUtil.queryNodes(bookNode, "span[@class='h5']");
            if (bookInfoNodes.size() > 0) {
                String bookInfo = bookInfoNodes.get(0).getText().toString();
                Matcher m = PATT_YEAR.matcher(bookInfo);
                if (m.find()) {
                    String year = m.group(1);
                    if (year != null) {
                        fact(YEAR, f.createLiteral(Integer.parseInt(year)));
                    }
                }
                m = PATT_ISBN.matcher(bookInfo);
                if (m.find()) {
                    String isbn = m.group(1);
                    if (isbn != null) {
                        fact(ISBN, lit(isbn));
                    }
                }
            }
        }
    }

    public static class Povidka extends AbstractScraper {

        private static final Pattern PATT_YEAR = Pattern.compile("Rok vydání originálu:\\s*(\\d{4})");
        public static final String NAMESPACE = "http://www.databazeknih.cz#";
        @Term
        public static final URI TYPE = RDF.TYPE;
        @Term
        @EntityClass
        public static final URI SHORT_STORY = f.createURI(NAMESPACE, "shortStory");
        @Term
        public static final URI PART_OF = f.createURI(NAMESPACE, "partOf");
        @Term
        public static final URI TITLE = RDFS.LABEL;
        @Term("Has author")
        public static final URI AUTHOR = f.createURI(NAMESPACE, "author");
        @Term
        public static final URI YEAR = f.createURI(NAMESPACE, "year");

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }

        @Override
        protected void scrape(EntityDocument doc) throws Exception {
            TagNode storyNode = (TagNode) doc.getDocument().evaluateXPath("//*[@id='tale']")[0];
            
            // state type of this entity
            fact(TYPE, SHORT_STORY);

            // story name
            String storyName = XPathUtil.queryText(storyNode, "h1[@class='bookname']");
            fact(TITLE, lit(storyName));

            // original name
            String otherName = XPathUtil.queryText(storyNode, "h2[@class='otitle']");
            if (otherName != null) {
                fact(TITLE, lit(otherName));
            }

            // year, authors, containing book
            List<TagNode> storyInfoNodes = XPathUtil.queryNodes(storyNode, "h2[@class='jmenaautoru']");
            for (TagNode storyInfoNode : storyInfoNodes) {
                List firstChildNodeList = storyInfoNode.getChildren();
                if (firstChildNodeList.size() > 0) {
                    String firstChild = XPathUtil.getText(firstChildNodeList.get(0));
                    if (firstChild != null && !firstChild.isEmpty()) {
                        if (firstChild.startsWith("Kniha:")) {
                            String href = XPathUtil.queryText(storyInfoNode, "a/@href");
                            if (href != null) {
                                fact(PART_OF, uri(href));
                            }
                        } else if (firstChild.startsWith("Autor:")) {
                            String href = XPathUtil.queryText(storyInfoNode, "a/@href");
                            if (href != null) {
                                fact(AUTHOR, uri(href));
                            }
                        } else if (firstChild.startsWith("Rok vydání originálu:")) {
                            Matcher m = PATT_YEAR.matcher(firstChild);
                            if (m.find()) {
                                String yearStr = m.group(1);
                                fact(YEAR, f.createLiteral(Integer.parseInt(yearStr)));
                            }
                        }
                    }
                }
            }
        }
    }

    public static class Autor extends AbstractScraper {
        // 1 - birth, 2 - death, 3 - country
        private static final Pattern PATT_ORIGIN = Pattern.compile("\\((\\d{4})(?:\\s*-\\s*(\\d{4}))?\\),?\\s*(.*)");
        public static final String NAMESPACE = "http://www.databazeknih.cz#";
        @Term
        public static final URI TYPE = RDF.TYPE;
        @Term
        @EntityClass
        public static final URI WRITER = f.createURI(NAMESPACE, "writer");
        @Term
        public static final URI NAME = RDFS.LABEL;
        @Term
        public static final URI PSEUDONYM = f.createURI(NAMESPACE, "pseudonym");
        @Term
        public static final URI BIRTH_DATE = f.createURI(NAMESPACE, "birthDate");
        @Term
        public static final URI DEATH_DATE = f.createURI(NAMESPACE, "deathDate");
        @Term
        public static final URI COUNTRY = f.createURI(NAMESPACE, "country");

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }

        @Override
        protected void scrape(EntityDocument doc) throws Exception {
            TagNode authorNode = (TagNode) doc.getDocument().evaluateXPath("//*[@id='author']")[0];

            // state type of this entity
            fact(TYPE, WRITER);

            // author name
            String authorName = XPathUtil.queryText(authorNode, "h1[1]");
            fact(NAME, lit(authorName));

            //pseudonym
            String pseudonym = XPathUtil.queryText(authorNode, "h2[@class='pseudonym']");
            if (pseudonym != null) {
                fact(PSEUDONYM, lit(pseudonym));
            }
            // dates and origin
            String datesAndOrigin = XPathUtil.queryText(authorNode, "h2[@class='bookname']");
            if (datesAndOrigin != null) {
                Matcher m = PATT_ORIGIN.matcher(datesAndOrigin);
                if(m.find()) {
                    String birth = m.group(1);
                    String death = m.group(2);
                    String country = m.group(3);
                    if (birth != null) {
                        fact(BIRTH_DATE, f.createLiteral(Integer.parseInt(birth)));
                    }
                    if (death != null) {
                        fact(DEATH_DATE, f.createLiteral(Integer.parseInt(death)));
                    }
                    if (country != null) {
                        fact(COUNTRY, lit(country));
                    }
                }
            }
        }
    }
}
