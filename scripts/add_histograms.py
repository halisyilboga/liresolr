#!/usr/bin/env python

import json

__author__ = 'ferdous'

import os
import urllib2
import xml.etree.ElementTree as ET
from urllib import quote_plus

def run(folder_name="/data/digitalcandy/ml/images/"):
    for file_name in os.listdir(folder_name):
        if file_name.endswith("xml"):
            save(folder_name, file_name)
        else:
            print '[not an xml file] ', file_name
        
def save(folder_name, file_name):
        xml_file = folder_name + file_name
        try:
            tree = ET.parse(xml_file)
            fields = tree.findall("field")
            params = [{'id': file_name, 'title': file_name, 'url': getPbsUrl(quote_plus(file_name[:-9]))}]

            for field in fields:
              key = field.get('name')
              params[0][key] = field.text
            url = 'http://54.235.24.244:8888/solr/Media/update/json'
            data = json.dumps(params)
            print data
            req = urllib2.Request(url)
            req.add_header('Content-type', 'application/json')
            req.data = data
            r = urllib2.urlopen(req)
            print r.read()
            r.close()
        except Exception, ex:
            pass


def getPbsUrl(file_name="none"):
    if file_name.startswith("Twitter"):
        segment = file_name.split('___')[-1]
        url = 'https://pbs.twimg.com/media/' + segment
        return url
    else:
        return 'http://54.235.24.244/images/' + file_name
    

if __name__ == "__main__":
    run()
