package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.LireFeature;

/**
 * Created by ferdous on 5/17/14.
 */
class ResultItem {

    private final LireFeature feature;
    private final String id;

    ResultItem(LireFeature feature, String id) {
        this.feature = feature;
        this.id = id;
    }
}
