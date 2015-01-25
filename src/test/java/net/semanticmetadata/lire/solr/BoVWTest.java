package net.semanticmetadata.lire.solr;

import junit.framework.TestCase;
import net.semanticmetadata.lire.impl.VisualWordsImageSearcher;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.ImageSearchHits;
import net.semanticmetadata.lire.imageanalysis.SurfFeature;
import net.semanticmetadata.lire.imageanalysis.bovw.BOVWBuilder;
import net.semanticmetadata.lire.impl.ChainedDocumentBuilder;
import net.semanticmetadata.lire.impl.SiftDocumentBuilder;
import net.semanticmetadata.lire.impl.SurfDocumentBuilder;
import net.semanticmetadata.lire.indexing.parallel.ParallelIndexer;
import net.semanticmetadata.lire.utils.FileUtils;
import org.apache.lucene.document.Document;

/**
 * Created by ferdous on 1/23/15.
 */
public class BoVWTest extends TestCase {

    private static File indexPath;
    String queryImage = "/Users/ferdous/projects/digitalcandy/liresolr/testdata/cars/alpha-romeo/alfa_romeo_photo_147.jpg";
    private DocumentBuilder surfBuilder, siftBuilder;
    String pathName;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        pathName = "./bovw-test";
        indexPath = new File(pathName);
        surfBuilder = new SurfDocumentBuilder();
        siftBuilder = new SiftDocumentBuilder();
    }

//    public void testIndexingAndSearchSurf() throws IOException {
//        ParallelIndexer pin = new ParallelIndexer(8, pathName, "testdata") {
//            @Override
//            public void addBuilders(ChainedDocumentBuilder builder) {
//                builder.addBuilder(new SurfDocumentBuilder());
//            }
//        };
//        pin.run();
//        IndexReader ir = DirectoryReader.open(FSDirectory.open(indexPath));
//        BOVWBuilder sfh = new BOVWBuilder(ir, new SurfFeature(), 512, 256);
//        sfh.index();
//    }
    public void testSearchInIndexSurf() throws IOException {
        int[] docIDs = new int[]{1, 2};
        VisualWordsImageSearcher searcher = new VisualWordsImageSearcher(50,
                DocumentBuilder.FIELD_NAME_SURF + DocumentBuilder.FIELD_NAME_BOVW);
        IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
        for (int i : docIDs) {
            // let's take the first document for a query:
            Document doc = reader.document(i);
            System.out.println("looking for..." + doc.getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0]);
            ImageSearchHits hits = searcher.search(doc, reader);
            // show or analyze your results ....
            FileUtils.saveImageResultsToPng("bovw-" + i, hits, doc.getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0]);
        }
    }

}
