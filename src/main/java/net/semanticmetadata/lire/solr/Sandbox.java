/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.semanticmetadata.lire.solr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.ImageSearchHits;
import net.semanticmetadata.lire.ImageSearcher;
import net.semanticmetadata.lire.ImageSearcherFactory;
import net.semanticmetadata.lire.filter.LsaFilter;
import net.semanticmetadata.lire.filter.RerankFilter;
import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.imageanalysis.bovw.SimpleFeatureBOVWBuilder;
import net.semanticmetadata.lire.impl.ChainedDocumentBuilder;
import net.semanticmetadata.lire.impl.GenericFastImageSearcher;
import net.semanticmetadata.lire.impl.SimpleBuilder;
import net.semanticmetadata.lire.impl.VisualWordsImageSearcher;
import net.semanticmetadata.lire.indexing.parallel.ParallelIndexer;
import net.semanticmetadata.lire.utils.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author ferdous
 */
public class Sandbox {

    private static final Logger LOG = Logger.getLogger(Sandbox.class.getName());
    private static File indexPath;
    private static final String queryImage = "/Users/ferdous/projects/digitalcandy/liresolr/testdata/ferrari/red/6822615599_18d9915317_b.jpg";
    static String bovw_index_path = "index";
    int sampleToCreateCodebook = 1000;
    int numberOfClusters = 100;

//    public void createSurfIndex() throws IOException {
//        ParallelIndexer pin = new ParallelIndexer(8, bovw_index_path, "testdata") {
//            @Override
//            public void addBuilders(ChainedDocumentBuilder builder) {
//                builder.addBuilder(new BOVWBuilder(reader, new SurfFeature(), samples, 500));
//            }
//        };
//        pin.run();
//        System.out.println("** SIMPLE BoVW using PHOG and Random");
//        SimpleFeatureBOVWBuilder simpleBovwBuilder = new SimpleFeatureBOVWBuilder(DirectoryReader.open(FSDirectory.open(new File(bovw_index_path))), new PHOG(), SimpleBuilder.KeypointDetector.Random, sampleToCreateCodebook, numberOfClusters);
//        simpleBovwBuilder.index();
//    }
    private static ImageSearcher getSearcher(int selectedIndex, int limit) {
        int numResults = 50;
        try {
            numResults = limit;
        } catch (Exception e) {
            // nothing to do ...
        }
        ImageSearcher searcher = ImageSearcherFactory.createColorLayoutImageSearcher(numResults);
        
        if (selectedIndex == 1) { // ScalableColor
            searcher = ImageSearcherFactory.createScalableColorImageSearcher(numResults);
        } else if (selectedIndex == 2) { // EdgeHistogram
            searcher = ImageSearcherFactory.createEdgeHistogramImageSearcher(numResults);
        } else if (selectedIndex == 3) { // AutoColorCorrelogram
            searcher = ImageSearcherFactory.createAutoColorCorrelogramImageSearcher(numResults);
        } else if (selectedIndex == 4) { // CEDD
            searcher = ImageSearcherFactory.createCEDDImageSearcher(numResults);
        } else if (selectedIndex == 5) { // FCTH
            searcher = ImageSearcherFactory.createFCTHImageSearcher(numResults);
        } else if (selectedIndex == 6) { // JCD
            searcher = ImageSearcherFactory.createJCDImageSearcher(numResults);
        } else if (selectedIndex == 7) { // SimpleColorHistogram
            searcher = ImageSearcherFactory.createColorHistogramImageSearcher(numResults);
        } else if (selectedIndex == 8) { // Tamura
            searcher = ImageSearcherFactory.createTamuraImageSearcher(numResults);
        } else if (selectedIndex == 9) { // Gabor
            searcher = ImageSearcherFactory.createGaborImageSearcher(numResults);
        } else if (selectedIndex == 10) { // JpegCoefficientHistogram
            searcher = ImageSearcherFactory.createJpegCoefficientHistogramImageSearcher(numResults);
        } else if (selectedIndex == 11) { // SURF
            searcher = new VisualWordsImageSearcher(numResults, DocumentBuilder.FIELD_NAME_SURF + DocumentBuilder.FIELD_NAME_BOVW);
        } else if (selectedIndex == 12) { // JointHistogram
            searcher = ImageSearcherFactory.createJointHistogramImageSearcher(numResults);
        } else if (selectedIndex == 13) { // OpponentHistogram
            searcher = ImageSearcherFactory.createOpponentHistogramSearcher(numResults);
        } else if (selectedIndex == 14) { // LuminanceLayout
            searcher = ImageSearcherFactory.createLuminanceLayoutImageSearcher(numResults);
        } else if (selectedIndex >= 15) { // PHOG
            searcher = ImageSearcherFactory.createPHOGImageSearcher(numResults);
        }
        return searcher;
    }

    private static void searchForDocument(Document d) {
        final Document myDoc = d;
        Thread t = new Thread() {
            public void run() {
                try {
                    IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
                    ImageSearcher searcher = getSearcher(99, 12);
                    System.out.println(searcher.getClass().getName() + " " + searcher.toString());
                    ImageSearchHits hits = searcher.search(myDoc, reader);
                    reader.close();
//                    hits = lsa(hits, myDoc);
                    FileUtils.saveImageResultsToHtml("filtertest", hits, myDoc.getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0]);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
    }

    private static void searchForImage(String imagePath) throws FileNotFoundException, IOException {
        System.out.println("---< searching >-------------------------");
        IndexReader reader = IndexReader.open(FSDirectory.open(indexPath));
//        for (int i = 0; i < reader.numDocs(); i++) {
//            Document doc = reader.document(i);
//            String fileName = doc.getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0];
//            System.out.println(i + ": \t" + fileName);
//        }
//        Document document = reader.document(139);
//        System.out.print(document.getFields().toString());

        Document document = reader.document(474);
        reader.close();
        searchForDocument(document);
//        System.out.print(document.getFields().toString());
//        String path = document.getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0];
//
//        ImageSearcher searcher = getSearcher(11, 100);
//        ImageSearchHits hits = searcher.search(document, reader);
//        for (int i = 0; i < hits.length(); i++) {
//            String fileName = hits.doc(i).getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0];
//            System.out.println(hits.score(i) + ": \t" + fileName);
//        }
////        hits = rerank(hits, document);
////        hits = lsa(hits, document);
//        FileUtils.saveImageResultsToHtml("filtertest", hits, path);
    }

    private static ImageSearchHits lsa(ImageSearchHits hits, Document document) {
        System.out.println("---< LSA filtering >-------------------------");
        LsaFilter filter = new LsaFilter(CEDD.class, DocumentBuilder.FIELD_NAME_CEDD);
        hits = filter.filter(hits, document);
        return hits;
    }

    private static ImageSearchHits rerank(ImageSearchHits hits, Document document) {
        // rerank
        System.out.println("---< filtering >-------------------------");
        RerankFilter filter = new RerankFilter(ColorLayout.class, DocumentBuilder.FIELD_NAME_COLORLAYOUT);
        hits = filter.filter(hits, document);
        return hits;
    }

    public void findByUrl() throws IOException {
        String path = "/Users/ferdous/projects/digitalcandy/liresolr/testdata/cars/trucks/pickup-white.jpg";
        searchForImage(path);

//        for (int i = 0; i < hits.length(); i++) {
//            String fileName = hits.doc(i).getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0];
//            System.out.println(hits.score(i) + ": \t" + fileName);
//        }
//        FileUtils.saveImageResultsToPng("bovw", hits, path);
//        FileUtils.saveImageResultsToHtml("bovw", hits, path);
    }

    public Sandbox() {
        indexPath = new File(bovw_index_path);
    }

    public static void main(String[] args) {
        try {
            Sandbox sandbox = new Sandbox();
//            sandbox.createSurfIndex();
            sandbox.findByUrl();
        } catch (IOException ex) {
            LOG.info(ex.getMessage());
        }
    }

}
