package net.semanticmetadata.lire.solr;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * This file is part of LIRE, a Java library for content based image retrieval.
 *
 * @author Mathias Lux, mathias@juggle.at, 10.12.2014
 */
public class BenchmarkTest extends TestCase {

    String[] ids = new String[]{
        "/data/digitalcandy/ml/images/im190001.jpg",
        "/data/digitalcandy/ml/images/im190002.jpg",
        "/data/digitalcandy/ml/images/im190003.jpg",
        "/data/digitalcandy/ml/images/im190004.jpg",
        "/data/digitalcandy/ml/images/im190005.jpg",
        "/data/digitalcandy/ml/images/im190006.jpg",
        "/data/digitalcandy/ml/images/im190007.jpg",
        "/data/digitalcandy/ml/images/im190008.jpg",
        "/data/digitalcandy/ml/images/im190009.jpg",
        "/data/digitalcandy/ml/images/im190010.jpg",
        "/data/digitalcandy/ml/images/im190011.jpg",
        "/data/digitalcandy/ml/images/im190012.jpg",
        "/data/digitalcandy/ml/images/im190013.jpg",
        "/data/digitalcandy/ml/images/im190014.jpg",
        "/data/digitalcandy/ml/images/im190015.jpg",
        "/data/digitalcandy/ml/images/im190016.jpg",
        "/data/digitalcandy/ml/images/im190017.jpg",
        "/data/digitalcandy/ml/images/im190018.jpg",
        "/data/digitalcandy/ml/images/im190019.jpg",
        "/data/digitalcandy/ml/images/im190020.jpg",
        "/data/digitalcandy/ml/images/im190021.jpg",
        "/data/digitalcandy/ml/images/im190022.jpg",
        "/data/digitalcandy/ml/images/im190023.jpg",
        "/data/digitalcandy/ml/images/im190024.jpg",
        "/data/digitalcandy/ml/images/im190025.jpg",
        "/data/digitalcandy/ml/images/im190026.jpg",
        "/data/digitalcandy/ml/images/im190027.jpg",
        "/data/digitalcandy/ml/images/im190028.jpg",
        "/data/digitalcandy/ml/images/im190029.jpg",
        "/data/digitalcandy/ml/images/im190030.jpg",
        "/data/digitalcandy/ml/images/im190031.jpg",
        "/data/digitalcandy/ml/images/im190032.jpg",
        "/data/digitalcandy/ml/images/im190033.jpg",
        "/data/digitalcandy/ml/images/im190034.jpg",
        "/data/digitalcandy/ml/images/im190035.jpg",
        "/data/digitalcandy/ml/images/im190036.jpg",
        "/data/digitalcandy/ml/images/im190037.jpg",
        "/data/digitalcandy/ml/images/im190038.jpg",
        "/data/digitalcandy/ml/images/im190039.jpg",
        "/data/digitalcandy/ml/images/im190040.jpg",
        "/data/digitalcandy/ml/images/im190041.jpg",
        "/data/digitalcandy/ml/images/im190042.jpg",
        "/data/digitalcandy/ml/images/im190043.jpg",
        "/data/digitalcandy/ml/images/im190044.jpg",
        "/data/digitalcandy/ml/images/im190045.jpg",
        "/data/digitalcandy/ml/images/im190046.jpg",
        "/data/digitalcandy/ml/images/im190047.jpg",
        "/data/digitalcandy/ml/images/im190048.jpg",
        "/data/digitalcandy/ml/images/im190049.jpg",
        "/data/digitalcandy/ml/images/im190050.jpg",
        "/data/digitalcandy/ml/images/im190051.jpg",
        "/data/digitalcandy/ml/images/im190052.jpg",
        "/data/digitalcandy/ml/images/im190053.jpg",
        "/data/digitalcandy/ml/images/im190054.jpg",
        "/data/digitalcandy/ml/images/im190055.jpg",
        "/data/digitalcandy/ml/images/im190056.jpg",
        "/data/digitalcandy/ml/images/im190057.jpg",
        "/data/digitalcandy/ml/images/im190058.jpg",
        "/data/digitalcandy/ml/images/im190059.jpg",
        "/data/digitalcandy/ml/images/im190060.jpg",
        "/data/digitalcandy/ml/images/im190061.jpg",
        "/data/digitalcandy/ml/images/im190062.jpg",
        "/data/digitalcandy/ml/images/im190063.jpg",
        "/data/digitalcandy/ml/images/im190064.jpg",
        "/data/digitalcandy/ml/images/im190065.jpg",
        "/data/digitalcandy/ml/images/im190066.jpg",
        "/data/digitalcandy/ml/images/im190067.jpg",
        "/data/digitalcandy/ml/images/im190068.jpg",
        "/data/digitalcandy/ml/images/im190069.jpg",
        "/data/digitalcandy/ml/images/im190070.jpg"
    };
    String[] fields = {"cl_ha", "ce_ha", "eh_ha"};
    String[] acc = {"0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0"};
    // media_shard1_replica1/lireq?rows=30&id=images02/20/im193713.jpg&field=ce_ha&accuracy=0.15
    String baseUrl = "http://localhost:8983/solr/";

    public void testGetRandom() throws Exception {
        String data = getData(baseUrl + "media_shard1_replica1/lireq?rows=100");
        System.out.println(data);
    }

    public void testConnection() throws Exception {
        System.out.println("Field\tAcc\tQTime\tSearch\tRank");
        for (int j = 0; j < acc.length; j++) {
            for (int i = 0; i < fields.length; i++) {
                double qtime = 0d, searchTime = 0d, rankTime = 0d;
                for (int k = 0; k < ids.length; k++) {
                    String data = getData(baseUrl + "media_shard1_replica1/lireq?rows=30&id=" + ids[k] + "&field=" + fields[i] + "&accuracy=" + acc[j]);
                    qtime += getValue("QTime", data);
                    searchTime += getValue("RawDocsSearchTime", data);
                    rankTime += getValue("ReRankSearchTime", data);

                }
                System.out.printf("%s\t%s\t%4.2f\t%4.2f\t%4.2f\n", fields[i], acc[j], qtime / (double) ids.length, searchTime / (double) ids.length, rankTime / (double) ids.length);
            }
        }
//        String data = getData(baseUrl + "media_shard1_replica1/lireq?rows=30");
//        System.out.println(data);
    }

    public int getValue(String name, String data) {
        String value = data.substring(data.indexOf(name) + name.length() + 2);
        value = value.substring(0, value.indexOf(','));
        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }
        return Integer.parseInt(value);
    }

    public String getData(String url) throws Exception {
        StringBuilder ab = new StringBuilder();
        URL oracle = new URL(url);
        URLConnection yc = oracle.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                yc.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            ab.append(inputLine);
        }
        in.close();
        return ab.toString();
    }
}
