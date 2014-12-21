package net.semanticmetadata.lire.solr.requesthandler;

import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.ColorLayout;
import net.semanticmetadata.lire.imageanalysis.SurfFeature;
import net.semanticmetadata.lire.impl.SimpleResult;
import net.semanticmetadata.lire.impl.SurfDocumentBuilder;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.solr.SolrSurfFeatureHistogramBuilder;
import net.semanticmetadata.lire.solr.SurfInterestPoint;
import net.semanticmetadata.lire.solr.utils.PropertiesUtils;
import net.semanticmetadata.lire.solr.utils.QueryImageUtils;
import net.semanticmetadata.lire.solr.utils.SurfUtils;
import net.semanticmetadata.lire.utils.ImageUtils;
import net.semanticmetadata.lire.utils.LuceneUtils;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.*;

public class SimilarRequestHandler extends RequestHandlerBase {

    static final String defaultAlgorithmField = "cl_ha";
    volatile long numErrors;
    volatile long numRequests;
    volatile long totalTime;
    static int defaultNumberOfResults = 60;
    static int defaultStartValue = 0;


    @Override
    public NamedList<Object> getStatistics() {
        NamedList<Object> statistics = super.getStatistics();
        statistics.add("requests", numRequests);
        statistics.add("errors", numErrors);
        statistics.add("totalTime", "" + totalTime);
        return statistics;
    }

    @Override
    public String getVersion() {
        return "0.9.5-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "LIRE Request Handler finds similar images.";
    }

    @Override
    public String getSource() {
        return "http://github.com/dynamicguy/liresolr";
    }

    @Override
    public void init(NamedList args) {
        super.init(args);

        // Caching off by default
        httpCaching = false;
        if (args != null) {
            Object caching = args.get("httpCaching");
            if (caching != null) {
                httpCaching = Boolean.parseBoolean(caching.toString());
            }
        }
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse res)
            throws Exception {

        numRequests++;
        long startTime = System.currentTimeMillis();

        String url = req.getParams().get("url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("You have to specify the url parameter.");
        }

        SolrIndexSearcher searcher = req.getSearcher();
        searcher.setSimilarity(new BM25Similarity());
        IndexReader reader = searcher.getIndexReader();
        QueryParser qp = new QueryParser(LuceneUtils.LUCENE_VERSION, "su_ha", new WhitespaceAnalyzer(LuceneUtils.LUCENE_VERSION));
        BooleanQuery.setMaxClauseCount(10000);

        // Read properties
        Properties properties = PropertiesUtils.getProperties(searcher.getCore());
        int numColorLayoutImages = Integer.parseInt(properties.getProperty("numColorLayoutImages"));
        int numColorLayoutSimImages = Integer.parseInt(properties.getProperty("numColorLayoutSimImages"));
        int numSurfSimImages = Integer.parseInt(properties.getProperty("numSurfSimImages"));
        int numSimImages = Integer.parseInt(properties.getProperty("numSimilarImages"));

        // Load image
        BufferedImage image = ImageIO.read(new URL(url).openStream());
        image = ImageUtils.trimWhiteSpace(image);

        // Extract image information
        /* CL */
        ColorLayout clFeat = new ColorLayout();
        clFeat.extract(image);
        // Create hashes
        BitSampling.readHashFunctions();
        int[] clHash = BitSampling.generateHashes(clFeat.getDoubleHistogram());

        /* SURF */
        SurfDocumentBuilder sb = new SurfDocumentBuilder();
        Document suFeat = sb.createDocument(QueryImageUtils.resizeQueryImage(ImageIO.read(new URL(url).openStream()), properties), "image");
        SolrSurfFeatureHistogramBuilder sh = new SolrSurfFeatureHistogramBuilder(null);
        sh.setClusterFile(req.getCore().getDataDir() + "/clusters-surf.dat");

        // load interest points from document
        // has to be loaded before getVisualWords (this method delete surf interest points)
        ArrayList<SurfInterestPoint> suPoints = new ArrayList<>();
        IndexableField[] queryFields = suFeat.getFields(DocumentBuilder.FIELD_NAME_SURF);
        for (IndexableField queryField : queryFields) {
            SurfFeature feature = new SurfFeature();
            feature.setByteArrayRepresentation(queryField.binaryValue().bytes, queryField.binaryValue().offset, queryField.binaryValue().length);
            SurfInterestPoint sip = new SurfInterestPoint(feature.getDoubleHistogram());
            suPoints.add(sip);
        }
        // sort for faster compare
        Collections.sort(suPoints);

        // Get Visual Words
        suFeat = sh.getVisualWords(suFeat);

        // Create queries
        /* CL */
        BooleanQuery clQuery = createQuery(clHash, "cl_ha", 0.5d);
        /* SURF */
        Query suQuery = qp.parse(suFeat.getValues(DocumentBuilder.FIELD_NAME_SURF_VISUAL_WORDS)[0]);

        // Searching..
        // Taking the time of search for statistical purposes.
        long time = System.currentTimeMillis();
        // CL
        TopDocs clDocs = searcher.search(clQuery, numColorLayoutImages);
        // Surf
        TopDocs suDocs = searcher.search(suQuery, numSurfSimImages);

        time = System.currentTimeMillis() - time;
        res.add("RawDocsCount", clDocs.scoreDocs.length + suDocs.scoreDocs.length + "");
        res.add("RawDocsSearchTime", time + "");
        // re-rank
        time = System.currentTimeMillis();
        totalTime += System.currentTimeMillis() - startTime;
        // Re-rank color layout
        TreeSet<SimpleResult> clScoreDocs = new TreeSet<>();
        ColorLayout clTmpFeature = new ColorLayout();
        float clTmpDistance;
        float maxClDistance = -1;

        BinaryDocValues binaryValues = MultiDocValues.getBinaryValues(searcher.getIndexReader(), "cl_hi"); // ***  #
        BytesRef bytesRef = new BytesRef();
        for (ScoreDoc scoreDoc : clDocs.scoreDocs) {
            // using DocValues to retrieve the field values ...
            binaryValues.get(scoreDoc.doc);
            clTmpFeature.setByteArrayRepresentation(bytesRef.bytes, bytesRef.offset, bytesRef.length);
            // Getting the document from the index.
            // This is the slow step based on the field compression of stored fields.
            clTmpDistance = clFeat.getDistance(clTmpFeature);
            if (clScoreDocs.size() < numColorLayoutSimImages) {
                clScoreDocs.add(new SimpleResult(clTmpDistance, searcher.doc(scoreDoc.doc), scoreDoc.doc));
                maxClDistance = clScoreDocs.last().getDistance();
            } else if (clTmpDistance < maxClDistance) {
                // if it is nearer to the sample than at least one of the current set:
                // remove the last one ...
                clScoreDocs.remove(clScoreDocs.last());
                // add the new one ...
                clScoreDocs.add(new SimpleResult(clTmpDistance, searcher.doc(scoreDoc.doc), scoreDoc.doc));
                // and set our new distance border ...
                maxClDistance = clScoreDocs.last().getDistance();
            }
        }

        // Re-rank by surf method
        TreeSet<SimpleResult> resultScoreDocs = new TreeSet<>();

        // Re-rank color layout
        for (SimpleResult r : clScoreDocs) {
            rerank(suPoints, r.getDocument(), r.getIndexNumber(), resultScoreDocs, numSimImages);
        }

        // Re-rank surf (visual words)
        for (ScoreDoc scoreDoc : suDocs.scoreDocs) {
            Document doc = reader.document(scoreDoc.doc);
            rerank(suPoints, doc, scoreDoc.doc, resultScoreDocs, numSimImages);
        }

        time = System.currentTimeMillis() - time;
        res.add("ReRankSearchTime", time + "");

        SolrDocumentList results = new SolrDocumentList();
        float maxScore = 0.0F;
        int numFound = 0;
        List<SolrDocument> slice = new ArrayList<SolrDocument>();
        SolrParams params = req.getParams();
        int paramRows = defaultNumberOfResults;
        if (params.getInt("rows") != null) {
            paramRows = params.getInt("rows");
        }

        int paramStarts = defaultStartValue;
        if (params.getInt("start") != null) {
            paramStarts = params.getInt("start");
        }


        for (SimpleResult sdoc : resultScoreDocs) {

            Float score = sdoc.getDistance();
            if (maxScore < score) {
                maxScore = score;
            }
            if (numFound >= paramStarts && numFound < paramStarts + paramRows) {
                SolrDocument solrDocument = new SolrDocument();
                solrDocument.setField("id", sdoc.getDocument().get("id"));
                solrDocument.setField("title", sdoc.getDocument().get("title"));
                solrDocument.setField("url", sdoc.getDocument().get("url"));
                solrDocument.setField("score", score);
                slice.add(solrDocument);
            }
            numFound++;
        }


        results.clear();
        results.addAll(slice);
        results.setNumFound(resultScoreDocs.size());
        results.setMaxScore(maxScore);
        results.setStart(paramStarts);
        res.add("response", results);

//
//
//        LinkedList<HashMap<String, String>> result = new LinkedList<>();
//        for (SimpleResult r : resultScoreDocs) {
//            HashMap<String, String> map = new HashMap<>(2);
//            map.put("id", r.getDocument().get("id"));
//            map.put("title", r.getDocument().get("title"));
//            map.put("d", String.format("%.2f", r.getDistance()));
//            result.add(map);
//        }
//        res.add("docs", result);
//        res.add("params", req.getParams().toNamedList());
    }

    private void rerank(ArrayList<SurfInterestPoint> query, Document doc, int indexNumber, TreeSet<SimpleResult> resultScoreDocs, int numSimImages) {

        float maxDistance = resultScoreDocs.isEmpty() ? -1 : resultScoreDocs.last().getDistance();

        // load interest points from document
        ArrayList<SurfInterestPoint> docPoints = new ArrayList<>();
        IndexableField[] docFields = doc.getFields("su_hi");
        for (IndexableField docField : docFields) {
            SurfFeature feature = new SurfFeature();
            feature.setByteArrayRepresentation(docField.binaryValue().bytes, docField.binaryValue().offset, docField.binaryValue().length);
            SurfInterestPoint sip = new SurfInterestPoint(feature.getDoubleHistogram());
            docPoints.add(sip);
        }

        float tmpScore = SurfUtils.getDistance(docPoints, query);
        if (resultScoreDocs.size() < numSimImages) {
            resultScoreDocs.add(new SimpleResult(tmpScore, doc, indexNumber));
        } else if (tmpScore < maxDistance) {
            // if it is nearer to the sample than at least one of the current set:
            // remove the last one ...
            resultScoreDocs.remove(resultScoreDocs.last());
            // add the new one ...
            resultScoreDocs.add(new SimpleResult(tmpScore, doc, indexNumber));
            // and set our new distance border ...
            maxDistance = resultScoreDocs.last().getDistance();
        }
    }

    private BooleanQuery createQuery(int[] hashes, String paramField, double size) {
        List<Integer> hList = new ArrayList<>(hashes.length);
        for (int i = 0; i < hashes.length; i++) {
            hList.add(hashes[i]);
        }
        Collections.shuffle(hList);
        BooleanQuery query = new BooleanQuery();
        int numHashes = (int) Math.min(hashes.length, Math.floor(hashes.length * size));
        if (numHashes < 5) {
            numHashes = hashes.length;
        }
        for (int i = 0; i < numHashes; i++) {
            // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
            query.add(new BooleanClause(new TermQuery(new Term(paramField, Integer.toHexString(hashes[i]))), BooleanClause.Occur.SHOULD));
        }
        return query;
    }

}
