#!/usr/bin/env python

import json

__author__ = 'ferdous'

import os
import urllib2
import xml.etree.ElementTree as ET
from urllib import pathname2url

HOSTNAME = 'localhost'
# HOSTNAME = '107.20.76.134'

SOLR_HOSTNAME = 'localhost'
#SOLR_HOSTNAME = '54.235.24.244'

def run(folder="/data/digitalcandy/ml/images/"):
    os.path.walk(folder, step, ('.xml', 'out2.txt'))
    for line in open('out2.txt'):
        save(line.rstrip())
     
def step((ext, seed_path), dirname, names):
    ext = ext.lower() 
    for name in names:
        if name.lower().endswith(ext):
            with open(seed_path, 'a') as seed_file:
                seed_file.write('%s\n' % os.path.join(dirname, name))


def save(xml_file):
    try:
        tree = ET.parse(xml_file)
        fields = tree.findall('doc/field', 'add')
        params = [{'id': xml_file, 'title': xml_file, 'url': getPbsUrl(xml_file[21:-9])}]

        for field in fields:
          key = field.get('name')
          params[0][key] = field.text
        url = 'http://' + SOLR_HOSTNAME + ':8983/solr/collection1/update?wt=json&commitWithin=1000&overwrite=true'
        data = json.dumps(params)
        #print data
        req = urllib2.Request(url)
        req.add_header('Content-type', 'application/json')
        req.data = data
        r = urllib2.urlopen(req)
        print r.read()
        r.close()
    except Exception, ex:
        print ex
        pass


def getPbsUrl(file_name="none"):
    return 'http://' + HOSTNAME + pathname2url(file_name)


if __name__ == "__main__":
    run()
