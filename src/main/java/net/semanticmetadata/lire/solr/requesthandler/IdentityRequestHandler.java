package net.semanticmetadata.lire.solr.requesthandler;

import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.ColorLayout;
import net.semanticmetadata.lire.imageanalysis.SurfFeature;
import net.semanticmetadata.lire.impl.SimpleResult;
import net.semanticmetadata.lire.impl.SurfDocumentBuilder;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.solr.SurfInterestPoint;
import net.semanticmetadata.lire.solr.utils.PropertiesUtils;
import net.semanticmetadata.lire.solr.utils.QueryImageUtils;
import net.semanticmetadata.lire.solr.utils.SurfUtils;
import net.semanticmetadata.lire.utils.ImageUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.*;
import org.apache.lucene.search.ScoreDoc;
import org.apache.solr.common.util.NamedList;

public class IdentityRequestHandler extends RequestHandlerBase {

    static final String defaultAlgorithmField = "cl_ha";
    volatile long numErrors;
    volatile long numRequests;
    volatile long totalTime;

    @Override
    public NamedList<Object> getStatistics() {
        NamedList<Object> statistics = super.getStatistics();
        statistics.add("Number of Requests", numRequests);
        statistics.add("Number of Errors", numErrors);
        statistics.add("totalTime(ms)", "" + totalTime);
        return statistics;
    }

    @Override
    public String getVersion() {
        return "0.9.5-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "LIRE Request Handler determines if exists indentical image, which is equaled to the queried image.";
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
        // Create hashes from the image.
        BufferedImage image = ImageIO.read(new URL(url).openStream());
        image = ImageUtils.trimWhiteSpace(image);
        // Use ColorLayout to extract feature from the image.
        ColorLayout feature = new ColorLayout();
        feature.extract(image);
        // Create hashes
        BitSampling.readHashFunctions();
        int[] hashes = BitSampling.generateHashes(feature.getDoubleHistogram());
        // Create query, we use only 50% of hashes.
        BooleanQuery query = createQuery(hashes, "cl_ha", 0.5d);
        // Getting searcher and reader instances
        SolrIndexSearcher searcher = req.getSearcher();
        IndexReader reader = searcher.getIndexReader();
        Properties properties = PropertiesUtils.getProperties(searcher.getCore());
        // Read candidateResultNumber from config.properties file.
        int numColorLayoutImages = Integer.parseInt(properties.getProperty("numColorLayoutImages"));
        // Taking the time of search for statistical purposes.
        long time = System.currentTimeMillis();
        TopDocs docs = searcher.search(query, numColorLayoutImages);
        time = System.currentTimeMillis() - time;
        res.add("RawDocsCount", docs.scoreDocs.length + "");
        res.add("RawDocsSearchTime", time + "");
        // re-rank
        time = System.currentTimeMillis();
        LinkedList<SimpleResult> resultScoreDocs = new LinkedList<>();
        float tmpDistance;
        float threshold1 = Float.parseFloat(properties.getProperty("thresholdCLIdentity1"));
        float threshold2 = Float.parseFloat(properties.getProperty("thresholdCLIdentity2"));

        BinaryDocValues binaryValues = MultiDocValues.getBinaryValues(reader, "cl_hi"); // ***  #
        BytesRef bytesRef = new BytesRef();
        for (ScoreDoc scoreDoc : docs.scoreDocs) {
            // using DocValues to retrieve the field values ...
            binaryValues.get(scoreDoc.doc, bytesRef);
            // Create feature
            ColorLayout tmpFeauture = new ColorLayout();
            tmpFeauture.setByteArrayRepresentation(bytesRef.bytes, bytesRef.offset, bytesRef.length);
            //BytesRef binaryValue = doc.getField("cl_hi").binaryValue();
            // compute a distance
            tmpDistance = feature.getDistance(tmpFeauture);
            // compare distance with the threshold
            if (tmpDistance < threshold1) {
                resultScoreDocs.add(new SimpleResult(tmpDistance, searcher.doc(scoreDoc.doc), scoreDoc.doc));
            }
        }
        time = System.currentTimeMillis() - time;
        res.add("ReRankSearchTime", time + "");
        // Surf re-rank
        time = System.currentTimeMillis();

        if (resultScoreDocs.size() == 1 && resultScoreDocs.get(0).getDistance() < threshold2) {
            res.add("identity", true);
            HashMap<String, String> map = new HashMap<>(2);
            map.put("id", resultScoreDocs.get(0).getDocument().get("id"));
            map.put("d", String.format("%.2f", resultScoreDocs.get(0).getDistance()));
            map.put("title", resultScoreDocs.get(0).getDocument().get("title"));
            res.add("doc", map);
        } else if (resultScoreDocs.size() >= 1) {
            SimpleResult surfIdentityResult = surfIdentityCheck(image, resultScoreDocs, properties);
            if (surfIdentityResult != null) {
                res.add("identity", true);
                HashMap<String, String> map = new HashMap<>(2);
                map.put("id", surfIdentityResult.getDocument().get("id"));
                map.put("title", surfIdentityResult.getDocument().get("title"));
                map.put("d", String.format("%.2f", surfIdentityResult.getDistance()));
                res.add("doc", map);
            } else {
                res.add("identity", false);
            }
        } else {
            res.add("identity", false);
        }
        time = System.currentTimeMillis() - time;
        res.add("ReRankSurfTime", time + "");
        totalTime += System.currentTimeMillis() - startTime;
        /*LinkedList<HashMap<String, String>> result = new LinkedList<HashMap<String, String>>();
         for (SimpleResult r : resultScoreDocs) {
         HashMap<String, String> map = new HashMap<String, String>(2);
         map.put("id", r.getDocument().get("id"));
         map.put("d", "" + r.getDistance());
         result.add(map);
         }
         res.add("docs", result);*/
    }

    private SimpleResult surfIdentityCheck(BufferedImage queryImage, LinkedList<SimpleResult> candidates, Properties properties) {
        SurfDocumentBuilder sb = new SurfDocumentBuilder();
        Document query = sb.createDocument(QueryImageUtils.resizeQueryImage(queryImage, properties), "image");

        float threshold = Float.parseFloat(properties.getProperty("thresholdSUIdentity"));

        // load interest points from document
        ArrayList<SurfInterestPoint> queryPoints = new ArrayList<>();
        IndexableField[] queryFields = query.getFields(DocumentBuilder.FIELD_NAME_SURF);
        for (IndexableField queryField : queryFields) {
            SurfFeature feature = new SurfFeature();
            feature.setByteArrayRepresentation(queryField.binaryValue().bytes, queryField.binaryValue().offset, queryField.binaryValue().length);
            SurfInterestPoint sip = new SurfInterestPoint(feature.descriptor);
            queryPoints.add(sip);
        }
        // sort for faster compare
        Collections.sort(queryPoints);

        Document minDistanceDoc = null;
        int minDistanceDocIndexNumber = 0;
        float minDistance = Float.MAX_VALUE;

        for (SimpleResult candidate : candidates) {
            Document doc = candidate.getDocument();
            // load interest points from document
            ArrayList<SurfInterestPoint> docPoints = new ArrayList<>();
            IndexableField[] docFields = doc.getFields("su_hi");
            for (IndexableField docField : docFields) {
                SurfFeature feature = new SurfFeature();
                feature.setByteArrayRepresentation(docField.binaryValue().bytes, docField.binaryValue().offset, docField.binaryValue().length);
                SurfInterestPoint sip = new SurfInterestPoint(feature.descriptor);
                docPoints.add(sip);
            }
            float tmpDistance = SurfUtils.getDistance(docPoints, queryPoints);
            if (tmpDistance >= threshold) {
                continue;
            }
            tmpDistance *= candidate.getDistance();
            if (tmpDistance < minDistance) {
                minDistance = tmpDistance;
                minDistanceDoc = doc;
                minDistanceDocIndexNumber = candidate.getIndexNumber();
            }
        }

        if (minDistanceDoc != null) {
            return new SimpleResult(minDistance, minDistanceDoc, minDistanceDocIndexNumber);
        } else {
            return null;
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
