curl http://localhost:8983/solr/media_shard1_replica1/update  -H "Content-Type: text/xml" --data-binary @add0.xml
curl http://localhost:8983/solr/media_shard1_replica1/update  -H "Content-Type: text/xml" --data-binary @add1.xml
curl http://localhost:8983/solr/media_shard1_replica1/update  -H "Content-Type: text/xml" --data-binary @add2.xml
curl http://localhost:8983/solr/media_shard1_replica1/update  -H "Content-Type: text/xml" --data-binary @add3.xml
curl http://localhost:8983/solr/media_shard1_replica1/update  -H "Content-Type: text/xml" --data-binary "<commit/>"