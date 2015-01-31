# -*- coding: utf-8 -*-
#!/usr/bin/env python

import os
import optparse
import json

__author__ = 'ferdous'

import os
import urllib2
import xml.etree.ElementTree as ET
from urllib import quote_plus
HOSTNAME = 'localhost'

def run(folder_name="/data/digitalcandy/ml/images/"):    
    for file_name in os.listdir(folder_name):
        if file_name.endswith("xml") and file_name.startswith("Twitter"):
            save(folder_name, file_name)
            exit()
        else:
            print '[not an xml file] ', file_name
        
def getSolrMediaDocs(limit=20):
    url = "http://localhost:8888/solr/Media/select?rows="+str(limit)+"&wt=python&q=*:*&fq=url%3Dpbs.twitter.com"
    req = urllib2.Request(url)
    # req.add_header('Content-type', 'application/json')
    # req.data = data
    r = urllib2.urlopen(req)
    result =  r.read()
    r.close()    
    return eval(result)
    

def save(folder_name, file_name):
    mid = quote_plus(file_name[:-9])
    print mid
    solrdoc = eval(getSolrMediaDoc(mid))
    print solrdoc.get('doc').get('url')
    #
    # xml_file = folder_name + file_name
    # try:
    #     tree = ET.parse(xml_file)
    #     fields = tree.findall("field")
    #     params = [{'id': file_name, 'title': file_name, 'url': getPbsUrl(mid)}]
    #
    #     for field in fields:
    #       key = field.get('name')
    #       params[0][key] = field.text
    #     url = 'http://' + HOSTNAME + ':8888/solr/Media/update/json'
    #     data = json.dumps(params)
    #
    #     #print data
    #
    #
    #
    #     # req = urllib2.Request(url)
    #     # req.add_header('Content-type', 'application/json')
    #     # req.data = data
    #     # r = urllib2.urlopen(req)
    #     # print r.read()
    #     # r.close()
    # except Exception, ex:
    #     pass


def getPbsUrl(file_name="none"):
    if file_name.startswith("Twitter"):
        segment = file_name.split('___')[-1]
        url = 'https://pbs.twimg.com/media/' + segment
        return url
    else:
        return 'http://' + HOSTNAME + '/images/' + file_name
    

if __name__ == "__main__":
    docs = getSolrMediaDocs().get('response').get('docs')
    for item in docs:
        print item.get('url')
    # run()
