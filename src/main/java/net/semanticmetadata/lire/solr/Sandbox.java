package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.impl.SimpleResult;
import net.semanticmetadata.lire.impl.SurfDocumentBuilder;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.solr.utils.PropertiesUtils;
import net.semanticmetadata.lire.solr.utils.QueryImageUtils;
import net.semanticmetadata.lire.solr.utils.SurfUtils;
import net.semanticmetadata.lire.utils.ImageUtils;
import net.semanticmetadata.lire.utils.LuceneUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class Sandbox {
    private static HashMap<String, Class> fieldToClass = new HashMap<String, Class>(5);
    private long time = 0;
    private int countRequests = 0;
    private int defaultNumberOfResults = 60;
    private int defaultStartValue = 0;

    /**
     * number of candidate results retrieved from the index. The higher this number, the slower,
     * the but more accurate the retrieval will be.
     */
    private int candidateResultNumber = 2000;

    static {
        fieldToClass.put("cl_ha", ColorLayout.class);
        fieldToClass.put("ph_ha", PHOG.class);
        fieldToClass.put("oh_ha", OpponentHistogram.class);
        fieldToClass.put("eh_ha", EdgeHistogram.class);
        fieldToClass.put("jc_ha", JCD.class);
        fieldToClass.put("su_ha", SurfSolrFeature.class);

        // one time hash function read ...
        try {
            BitSampling.readHashFunctions();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
//		SolrQueryRequest req = new SolrQueryRequest();
//		SolrQueryResponse rsp = new SolrQueryResponse();

        String paramUrl = "http://localhost/images/im190056.jpg";
        String paramField = "cl_ha";

        readHistogramBy(paramUrl, "cl_ha");
        readHistogramBy(paramUrl, "ph_ha");
        readHistogramBy(paramUrl, "oh_ha");
        readHistogramBy(paramUrl, "eh_ha");
        readHistogramBy(paramUrl, "jc_ha");
        //readHistogramBy(paramUrl, "su_ha");
    }

    private static void readHistogramBy(String paramUrl, String paramField) {
        LireFeature feat = null;
        try {
            BufferedImage img = ImageIO.read(new URL(paramUrl).openStream());
            img = ImageUtils.trimWhiteSpace(img);
            // getting the right feature per field:
            if (paramField == null) feat = new EdgeHistogram();
            else {
                if (paramField.equals("cl_ha")) feat = new ColorLayout();
                else if (paramField.equals("jc_ha")) feat = new JCD();
                else if (paramField.equals("ph_ha")) feat = new PHOG();
                else if (paramField.equals("oh_ha")) feat = new OpponentHistogram();
                else if (paramField.equals("su_ha")) feat = new SurfSolrFeature();
                else feat = new EdgeHistogram();
            }
            feat.extract(img);
            String histogram = Base64.encodeBase64String(feat.getByteArrayRepresentation());
            int[] hashes = BitSampling.generateHashes(feat.getDoubleHistogram());

            System.out.println(histogram);
            System.out.println(hashes);

        } catch (Exception e) {
            //System.err.println("Error reading image from URL: " + paramUrl + ": " + e.getMessage());
            e.printStackTrace();
        } finally {

        }
    }

    /**
     * Handles three types of requests.
     * <ol>
     * <li>search by already extracted images.</li>
     * <li>search by an image URL.</li>
     * <li>Random results.</li>
     * </ol>
     *
     * @param req
     * @param rsp
     * @throws Exception
     */
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        // (1) check if the necessary parameters are here
        if (req.getParams().get("hashes") != null) { // we are searching for hashes ...
            handleHashSearch(req, rsp);
        } else if (req.getParams().get("url") != null) { // we are searching for an image based on an URL
            handleUrlSearch(req, rsp);
        } else if (req.getParams().get("id") != null) { // we are searching for an image based on an URL
            handleIdSearch(req, rsp);
        } else if (req.getParams().get("extract") != null) { // we are trying to extract from an image URL.
            handleExtract(req, rsp);
        } else { // lets return random results.
            handleRandomSearch(req, rsp);
        }
    }

    private void surfUrlSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, ParseException {
        String url = req.getParams().get("url");
        int rows = req.getParams().getInt("rows", 20);
        int start = req.getParams().getInt("start", 0);
        String mode = req.getParams().get("mode");
        SolrIndexSearcher searcher = req.getSearcher();
        searcher.setSimilarity(new BM25Similarity());
        IndexReader reader = searcher.getIndexReader();
        QueryParser qp = new QueryParser(LuceneUtils.LUCENE_VERSION, "su_ha", new WhitespaceAnalyzer(LuceneUtils.LUCENE_VERSION));
        BooleanQuery.setMaxClauseCount(10000);
        Properties properties = PropertiesUtils.getProperties(searcher.getCore());

        SurfDocumentBuilder sb = new SurfDocumentBuilder();
        Document query = sb.createDocument(QueryImageUtils.resizeQueryImage(ImageIO.read(new URL(url).openStream()), properties), "image");

        if ("vw".equals(mode)) {
            SolrSurfFeatureHistogramBuilder sh = new SolrSurfFeatureHistogramBuilder(null);
            sh.setClusterFile(req.getCore().getDataDir() + "/clusters-surf.dat");
            // Get Visual Words
            query = sh.getVisualWords(query);

            String queryString = query.getValues(DocumentBuilder.FIELD_NAME_SURF_VISUAL_WORDS)[0];
            Query tq = qp.parse(queryString);
            TopDocs docs = searcher.search(tq, rows);

            LinkedList<HashMap<String, Comparable>> list = new LinkedList<HashMap<String, Comparable>>();

            for (int i = start; i < docs.scoreDocs.length; i++) {
                float d = 1f / docs.scoreDocs[i].score;
                HashMap<String, Comparable> m = new HashMap<String, Comparable>(2);
                m.put("d", d);
                m.put("id", reader.document(docs.scoreDocs[i].doc).get("id"));
                list.add(m);
            }

            rsp.add("docs", list);
        } else {
            // load interest points from document
            ArrayList<SurfInterestPoint> queryPoints = new ArrayList<SurfInterestPoint>();
            IndexableField[] queryFields = query.getFields(DocumentBuilder.FIELD_NAME_SURF);
            for (IndexableField queryField : queryFields) {
                SurfSolrFeature feature = new SurfSolrFeature();
                feature.setByteArrayRepresentation(queryField.binaryValue().bytes, queryField.binaryValue().offset, queryField.binaryValue().length);
                SurfInterestPoint sip = new SurfInterestPoint(feature.getDoubleHistogram());
                queryPoints.add(sip);
            }
            // sort for faster compare
            Collections.sort(queryPoints);

            SolrSurfFeatureHistogramBuilder sh = new SolrSurfFeatureHistogramBuilder(null);
            sh.setClusterFile(req.getCore().getDataDir() + "/clusters-surf.dat");
            // Get Visual Words
            query = sh.getVisualWords(query);

            String queryString = query.getValues(DocumentBuilder.FIELD_NAME_SURF_VISUAL_WORDS)[0];
            Query tq = qp.parse(queryString);
            TopDocs docs = searcher.search(tq, Integer.parseInt(properties.getProperty("numVisualWordsImages")));

            TreeSet<SimpleResult> resultScoreDocs = new TreeSet<SimpleResult>();
            float maxDistance = -1f;
            float tmpScore;

            for (int i = start; i < docs.scoreDocs.length; i++) {
                Document doc = reader.document(docs.scoreDocs[i].doc);

                // load interest points from document
                ArrayList<SurfInterestPoint> docPoints = new ArrayList<SurfInterestPoint>();
                IndexableField[] docFields = doc.getFields("su_hi");
                for (IndexableField docField : docFields) {
                    SurfSolrFeature feature = new SurfSolrFeature();
                    feature.setByteArrayRepresentation(docField.binaryValue().bytes, docField.binaryValue().offset, docField.binaryValue().length);
                    SurfInterestPoint sip = new SurfInterestPoint(feature.getDoubleHistogram());
                    docPoints.add(sip);
                }

                tmpScore = SurfUtils.getDistance(docPoints, queryPoints);
                if (resultScoreDocs.size() < rows) {
                    resultScoreDocs.add(new SimpleResult(tmpScore, searcher.doc(docs.scoreDocs[i].doc), docs.scoreDocs[i].doc));
                    maxDistance = resultScoreDocs.last().getDistance();
                } else if (tmpScore < maxDistance) {
//                    if it is nearer to the sample than at least one of the current set:
//                    remove the last one ...
                    resultScoreDocs.remove(resultScoreDocs.last());
//                    add the new one ...
                    resultScoreDocs.add(new SimpleResult(tmpScore, searcher.doc(docs.scoreDocs[i].doc), docs.scoreDocs[i].doc));
//                    and set our new distance border ...
                    maxDistance = resultScoreDocs.last().getDistance();
                }
            }

            LinkedList<HashMap<String, Comparable>> list = new LinkedList<HashMap<String, Comparable>>();

            for (SimpleResult result : resultScoreDocs) {
                HashMap<String, Comparable> m = new HashMap<String, Comparable>(2);
                m.put("d", result.getDistance());
                m.put("id", result.getDocument().get("id"));
                list.add(m);
            }

            rsp.add("docs", list);
        }

    }

    private void surfIdSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, ParseException {
        String id = req.getParams().get("id");
        int rows = req.getParams().getInt("rows");
        int start = req.getParams().getInt("start", 0);
        String mode = req.getParams().get("mode");
        SolrIndexSearcher searcher = req.getSearcher();
        searcher.setSimilarity(new BM25Similarity());
        IndexReader reader = searcher.getIndexReader();
        QueryParser qp = new QueryParser(LuceneUtils.LUCENE_VERSION, "su_ha", new WhitespaceAnalyzer(LuceneUtils.LUCENE_VERSION));
        BooleanQuery.setMaxClauseCount(10000);
        Properties properties = PropertiesUtils.getProperties(searcher.getCore());

        TopDocs hits = searcher.search(new TermQuery(new Term("id", id)), 1);

        if (hits.scoreDocs.length > 0) {
            Document queriedDoc = reader.document(hits.scoreDocs[0].doc);

            Document query = new Document();
            query.add(new StringField(DocumentBuilder.FIELD_NAME_IDENTIFIER, queriedDoc.getValues("id")[0], Field.Store.YES));

            IndexableField[] features = queriedDoc.getFields("su_hi");
            for (IndexableField field : features) {
                query.add(new StoredField(DocumentBuilder.FIELD_NAME_SURF, field.binaryValue()));
            }

            if ("vw".equals(mode)) {
                SolrSurfFeatureHistogramBuilder sh = new SolrSurfFeatureHistogramBuilder(null);
                sh.setClusterFile(req.getCore().getDataDir() + "/clusters-surf.dat");
                // Get Visual Words
                query = sh.getVisualWords(query);

                String queryString = query.getValues(DocumentBuilder.FIELD_NAME_SURF_VISUAL_WORDS)[0];
                Query tq = qp.parse(queryString);
                TopDocs docs = searcher.search(tq, rows);

                LinkedList<HashMap<String, Comparable>> list = new LinkedList<HashMap<String, Comparable>>();

                for (int i = start; i < docs.scoreDocs.length; i++) {
                    float d = 1f / docs.scoreDocs[i].score;
                    HashMap<String, Comparable> m = new HashMap<String, Comparable>(2);
                    m.put("d", d);
                    m.put("id", reader.document(docs.scoreDocs[i].doc).get("id"));
                    list.add(m);
                }

                rsp.add("docs", list);
            } else {
                // load interest points from document
                ArrayList<SurfInterestPoint> queryPoints = new ArrayList<SurfInterestPoint>();
                IndexableField[] queryFields = query.getFields(DocumentBuilder.FIELD_NAME_SURF);
                for (IndexableField queryField : queryFields) {
                    SurfSolrFeature feature = new SurfSolrFeature();
                    feature.setByteArrayRepresentation(queryField.binaryValue().bytes, queryField.binaryValue().offset, queryField.binaryValue().length);
                    SurfInterestPoint sip = new SurfInterestPoint(feature.getDoubleHistogram());
                    queryPoints.add(sip);
                }
                // sort for faster compare
                Collections.sort(queryPoints);

                SolrSurfFeatureHistogramBuilder sh = new SolrSurfFeatureHistogramBuilder(null);
                sh.setClusterFile(req.getCore().getDataDir() + "/clusters-surf.dat");
                // Get Visual Words
                query = sh.getVisualWords(query);

                String queryString = query.getValues(DocumentBuilder.FIELD_NAME_SURF_VISUAL_WORDS)[0];
                Query tq = qp.parse(queryString);
                TopDocs docs = searcher.search(tq, Integer.parseInt(properties.getProperty("numVisualWordsImages")));

                TreeSet<SimpleResult> resultScoreDocs = new TreeSet<SimpleResult>();
                float maxDistance = -1f;
                float tmpScore;

                for (int i = start; i < docs.scoreDocs.length; i++) {
                    Document doc = reader.document(docs.scoreDocs[i].doc);

                    // load interest points from document
                    ArrayList<SurfInterestPoint> docPoints = new ArrayList<SurfInterestPoint>();
                    IndexableField[] docFields = doc.getFields("su_hi");
                    for (IndexableField docField : docFields) {
                        SurfSolrFeature feature = new SurfSolrFeature();
                        feature.setByteArrayRepresentation(docField.binaryValue().bytes, docField.binaryValue().offset, docField.binaryValue().length);
                        SurfInterestPoint sip = new SurfInterestPoint(feature.getDoubleHistogram());
                        docPoints.add(sip);
                    }

                    tmpScore = SurfUtils.getDistance(docPoints, queryPoints);
                    if (resultScoreDocs.size() < rows) {
                        resultScoreDocs.add(new SimpleResult(tmpScore, searcher.doc(docs.scoreDocs[i].doc), docs.scoreDocs[i].doc));
                        maxDistance = resultScoreDocs.last().getDistance();
                    } else if (tmpScore < maxDistance) {
//                        if it is nearer to the sample than at least one of the current set:
//                        remove the last one ...
                        resultScoreDocs.remove(resultScoreDocs.last());
//                        add the new one ...
                        resultScoreDocs.add(new SimpleResult(tmpScore, searcher.doc(docs.scoreDocs[i].doc), docs.scoreDocs[i].doc));
//                        and set our new distance border ...
                        maxDistance = resultScoreDocs.last().getDistance();
                    }
                }

                LinkedList<HashMap<String, Comparable>> list = new LinkedList<HashMap<String, Comparable>>();

                for (SimpleResult result : resultScoreDocs) {
                    HashMap<String, Comparable> m = new HashMap<String, Comparable>(2);
                    m.put("d", result.getDistance());
                    m.put("id", result.getDocument().get("id"));
                    list.add(m);
                }

                rsp.add("docs", list);
            }

    		/*SolrSurfFeatureHistogramBuilder sh = new SolrSurfFeatureHistogramBuilder(null);
            sh.setClusterFile(req.getCore().getDataDir() + "/clusters-surf.dat");
        	// Get Visual Words
        	query = sh.getVisualWords(query);

        	String queryString = query.getValues(DocumentBuilder.FIELD_NAME_SURF_VISUAL_WORDS)[0];
        	Query tq = qp.parse(queryString);
        	TopDocs docs = searcher.search(tq, rows);

        	LinkedList<HashMap<String, Comparable>> list = new LinkedList<HashMap<String, Comparable>>();

    		for (int i = 0; i < docs.scoreDocs.length; i++) {
                float d = 1f / docs.scoreDocs[i].score;
                HashMap<String, Comparable> m = new HashMap<String, Comparable>(2);
                m.put("d", d);
                m.put("id", reader.document(docs.scoreDocs[i].doc).get("id"));
                list.add(m);
            }

            rsp.add("docs", list);*/
        }
    }

    /**
     * Handles the get parameters id, field and rows.
     *
     * @param req
     * @param rsp
     * @throws IOException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ParseException
     */
    private void handleIdSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException, ParseException {
        SolrIndexSearcher searcher = req.getSearcher();
        String paramId = req.getParams().get("id");
        String paramField = "cl_ha";
        int paramRows = defaultNumberOfResults;
        int paramStarts = defaultStartValue;

        if (req.getParams().get("field") != null)
            paramField = req.getParams().get("field");

        if (req.getParams().getInt("rows") != null)
            paramRows = req.getParams().getInt("rows");

        if (req.getParams().getInt("start") != null)
            paramStarts = req.getParams().getInt("start");

        // SURF method
        if ("su_ha".equals(paramField)) {
            surfIdSearch(req, rsp);
            return;
        }

        try {
            TopDocs hits = searcher.search(new TermQuery(new Term("id", paramId)), 1);

            LireFeature queryFeature = (LireFeature) fieldToClass.get(paramField).newInstance();
            rsp.add("QueryField", paramField);
            rsp.add("QueryFeature", queryFeature.getClass().getName());

            if (hits.scoreDocs.length > 0) {
                // Using DocValues to get the actual data from the index.
                BinaryDocValues binaryValues = MultiDocValues.getBinaryValues(searcher.getIndexReader(), paramField.replace("_ha", "_hi")); // ***  #
                if (binaryValues == null)
                    System.err.println("Could not find the DocValues of the query document. Are they in the index?");
                BytesRef bytesRef = new BytesRef();
                binaryValues.get(hits.scoreDocs[0].doc);
//                Document d = searcher.getIndexReader().document(hits.scoreDocs[0].doc);
                String histogramFieldName = paramField.replace("_ha", "_hi");
                queryFeature.setByteArrayRepresentation(bytesRef.bytes, bytesRef.offset, bytesRef.length);
                // Re-generating the hashes to save space (instead of storing them in the index)
                int[] hashes = BitSampling.generateHashes(queryFeature.getDoubleHistogram());
                // just use 50% of the hashes for search ...
                BooleanQuery query = createQuery(hashes, paramField, 0.5d);
                doSearch(rsp, searcher, paramField, paramStarts, paramRows, query, queryFeature);
            } else {
                rsp.add("Error", "Did not find an image with the given id " + req.getParams().get("id"));
            }
        } catch (Exception e) {
            rsp.add("Error", "There was an error with your search for the image with the id " + req.getParams().get("id")
                    + ": " + e.getMessage());
        }
    }

    /**
     * Returns a random set of documents from the index. Mainly for testing purposes.
     *
     * @param req
     * @param rsp
     * @throws IOException
     */
    private void handleRandomSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException {
        SolrIndexSearcher searcher = req.getSearcher();
        DirectoryReader indexReader = searcher.getIndexReader();
        double maxDoc = indexReader.maxDoc();
        int paramRows = defaultNumberOfResults;
        if (req.getParams().getInt("rows") != null)
            paramRows = req.getParams().getInt("rows");
        LinkedList list = new LinkedList();
        while (list.size() < paramRows) {
            HashMap m = new HashMap(2);
            Document d = indexReader.document((int) Math.floor(Math.random() * maxDoc));
            m.put("id", d.getValues("id")[0]);
            //m.put("title", d.getValues("title")[0]);
            list.add(m);
        }
        rsp.add("docs", list);
    }

    /**
     * Searches for an image given by an URL. Note that (i) extracting image features takes time and
     * (ii) not every image is readable by Java.
     *
     * @param req
     * @param rsp
     * @throws IOException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ParseException
     */
    private void handleUrlSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException, ParseException {
        SolrParams params = req.getParams();
        String paramUrl = params.get("url");

        String paramField = "cl_ha";
        if (req.getParams().get("field") != null)
            paramField = req.getParams().get("field");

        int paramRows = defaultNumberOfResults;
        if (params.get("rows") != null)
            paramRows = params.getInt("rows");

        int paramStarts = defaultStartValue;
        if (params.get("start") != null)
            paramStarts = params.getInt("start");

        // SURF method
        if ("su_ha".equals(paramField)) {
            surfUrlSearch(req, rsp);
            return;
        }

        LireFeature feat = null;
        BooleanQuery query = null;
        // wrapping the whole part in the try
        try {
            BufferedImage img = ImageIO.read(new URL(paramUrl).openStream());
            img = ImageUtils.trimWhiteSpace(img);
            // getting the right feature per field:
            if (paramField == null) feat = new EdgeHistogram();
            else {
                if (paramField.equals("cl_ha")) feat = new ColorLayout();
                else if (paramField.equals("jc_ha")) feat = new JCD();
                else if (paramField.equals("ph_ha")) feat = new PHOG();
                else if (paramField.equals("oh_ha")) feat = new OpponentHistogram();
                else if (paramField.equals("su_ha")) feat = new SurfSolrFeature();
                else feat = new EdgeHistogram();
            }
            feat.extract(img);
            int[] hashes = BitSampling.generateHashes(feat.getDoubleHistogram());
            // just use 50% of the hashes for search ...
            query = createQuery(hashes, paramField, 0.5d);
        } catch (Exception e) {
            rsp.add("Error", "Error reading image from URL: " + paramUrl + ": " + e.getMessage());
            e.printStackTrace();
        }
        // search if the feature has been extracted.
        if (feat != null) doSearch(rsp, req.getSearcher(), paramField, paramStarts, paramRows, query, feat);
    }

    private void handleExtract(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException {
        SolrParams params = req.getParams();
        String paramUrl = params.get("extract");
        String paramField = "cl_ha";
        if (req.getParams().get("field") != null)
            paramField = req.getParams().get("field");
//        int paramRows = defaultNumberOfResults;
//        if (params.get("rows") != null)
//            paramRows = params.getInt("rows");
        LireFeature feat = null;
//        BooleanQuery query = null;
        // wrapping the whole part in the try
        try {
            BufferedImage img = ImageIO.read(new URL(paramUrl).openStream());
            img = ImageUtils.trimWhiteSpace(img);
            // getting the right feature per field:
            if (paramField == null) feat = new EdgeHistogram();
            else {
                if (paramField.equals("cl_ha")) feat = new ColorLayout();
                else if (paramField.equals("jc_ha")) feat = new JCD();
                else if (paramField.equals("ph_ha")) feat = new PHOG();
                else if (paramField.equals("oh_ha")) feat = new OpponentHistogram();
                else if (paramField.equals("su_ha")) feat = new SurfSolrFeature();
                else feat = new EdgeHistogram();
            }
            feat.extract(img);
            rsp.add("histogram", Base64.encodeBase64String(feat.getByteArrayRepresentation()));
//            int[] hashes = BitSampling.generateHashes(feat.getDoubleHistogram());
//            just use 50% of the hashes for search ...
//            query = createQuery(hashes, paramField, 0.5d);
        } catch (Exception e) {
//            rsp.add("Error", "Error reading image from URL: " + paramUrl + ": " + e.getMessage());
            e.printStackTrace();
        }
        // search if the feature has been extracted.
//        if (feat != null) doSearch(rsp, req.getSearcher(), paramField, paramRows, query, feat);
    }

    /**
     * Search based on the given image hashes.
     *
     * @param req
     * @param rsp
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private void handleHashSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, IllegalAccessException, InstantiationException {
        SolrParams params = req.getParams();
        SolrIndexSearcher searcher = req.getSearcher();
        // get the params needed:
        // hashes=x y z ...
        // feature=<base64>
        // field=<cl_ha|ph_ha|...>

        String[] hashes = params.get("hashes").trim().split(" ");
        byte[] featureVector = Base64.decodeBase64(params.get("feature"));
        String paramField = "cl_ha";
        if (req.getParams().get("field") != null)
            paramField = req.getParams().get("field");

        int paramRows = defaultNumberOfResults;
        if (params.getInt("rows") != null)
            paramRows = params.getInt("rows");

        int paramStarts = defaultStartValue;
        if (params.get("start") != null)
            paramStarts = params.getInt("start");

        // create boolean query:
//        System.out.println("** Creating query.");
        BooleanQuery query = new BooleanQuery();
        for (int i = paramStarts; i < hashes.length; i++) {
            // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
            hashes[i] = hashes[i].trim();
            if (hashes[i].length() > 0) {
                query.add(new BooleanClause(new TermQuery(new Term(paramField, hashes[i].trim())), BooleanClause.Occur.SHOULD));
//                System.out.println("** " + field + ": " + hashes[i].trim());
            }
        }
//        System.out.println("** Doing search.");

        // query feature
        LireFeature queryFeature = (LireFeature) fieldToClass.get(paramField).newInstance();
        queryFeature.setByteArrayRepresentation(featureVector);

        // get results:
        doSearch(rsp, searcher, paramField, paramStarts, paramRows, query, queryFeature);
    }

    /**
     * Actual search implementation based on (i) hash based retrieval and (ii) feature based re-ranking.
     *
     * @param rsp
     * @param searcher
     * @param field
     * @param maximumHits
     * @param query
     * @param queryFeature
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private void doSearch(SolrQueryResponse rsp, SolrIndexSearcher searcher, String field, int paramStarts, int maximumHits, BooleanQuery query, LireFeature queryFeature) throws IOException, IllegalAccessException, InstantiationException {
        // temp feature instance
        LireFeature tmpFeature = queryFeature.getClass().newInstance();
        // Taking the time of search for statistical purposes.
        time = System.currentTimeMillis();
        TopDocs docs = searcher.search(query, candidateResultNumber);
        time = System.currentTimeMillis() - time;
        rsp.add("RawDocsCount", docs.scoreDocs.length + "");
        rsp.add("RawDocsSearchTime", time + "");
        // re-rank
        time = System.currentTimeMillis();
        TreeSet<SimpleResult> resultScoreDocs = new TreeSet<SimpleResult>();
        float maxDistance = -1f;
        float tmpScore;

        String name = field.replace("_ha", "_hi");
        Document d;
        // iterating and re-ranking the documents.
        BinaryDocValues binaryValues = MultiDocValues.getBinaryValues(searcher.getIndexReader(), name); // ***  #
        BytesRef bytesRef = new BytesRef();

        for (int i = paramStarts; i < docs.scoreDocs.length; i++) {
            // using DocValues to retrieve the field values ...
            binaryValues.get(docs.scoreDocs[i].doc);
            tmpFeature.setByteArrayRepresentation(bytesRef.bytes, bytesRef.offset, bytesRef.length);
            // Getting the document from the index.
            // This is the slow step based on the field compression of stored fields.
//            tmpFeature.setByteArrayRepresentation(d.getBinaryValue(name).bytes, d.getBinaryValue(name).offset, d.getBinaryValue(name).length);
            tmpScore = queryFeature.getDistance(tmpFeature);
            if (resultScoreDocs.size() < maximumHits) {
                resultScoreDocs.add(new SimpleResult(tmpScore, searcher.doc(docs.scoreDocs[i].doc), docs.scoreDocs[i].doc));
                maxDistance = resultScoreDocs.last().getDistance();
            } else if (tmpScore < maxDistance) {
//                if it is nearer to the sample than at least one of the current set:
//                remove the last one ...
                resultScoreDocs.remove(resultScoreDocs.last());
//                add the new one ...
                resultScoreDocs.add(new SimpleResult(tmpScore, searcher.doc(docs.scoreDocs[i].doc), docs.scoreDocs[i].doc));
//                and set our new distance border ...
                maxDistance = resultScoreDocs.last().getDistance();
            }
        }
//        System.out.println("** Creating response.");
        time = System.currentTimeMillis() - time;
        rsp.add("ReRankSearchTime", time + "");
        LinkedList list = new LinkedList();
        for (Iterator<SimpleResult> it = resultScoreDocs.iterator(); it.hasNext(); ) {
            SimpleResult result = it.next();
            HashMap m = new HashMap(2);
            m.put("d", result.getDistance());
            m.put("id", result.getDocument().get("id"));
            m.put("title", result.getDocument().get("title"));
//            m.put(field, result.getDocument().get(field));
//            m.put(field.replace("_ha", "_hi"), result.getDocument().getBinaryValue(field));
            list.add(m);
        }
        rsp.add("docs", list);
        // rsp.add("Test-name", "Test-val");
    }

    private BooleanQuery createQuery(int[] hashes, String paramField, double size) {
        List<Integer> hList = new ArrayList<Integer>(hashes.length);
        for (int i = 0; i < hashes.length; i++) {
            hList.add(hashes[i]);
        }
        Collections.shuffle(hList);
        BooleanQuery query = new BooleanQuery();
        int numHashes = (int) Math.min(hashes.length, Math.floor(hashes.length * size));
        if (numHashes < 5) numHashes = hashes.length;
        for (int i = 0; i < numHashes; i++) {
            // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
            query.add(new BooleanClause(new TermQuery(new Term(paramField, Integer.toHexString(hashes[i]))), BooleanClause.Occur.SHOULD));
        }
        return query;
    }

}
