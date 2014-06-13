#!/usr/bin/env python

import os

def run(folder_name="/Users/ferdous/projects/digitalcandy/liresolr/data/flickr-10k-negative/"):
    out_file = open('/Users/ferdous/projects/digitalcandy/liresolr/out.txt', 'wb')
    for item in os.listdir(folder_name):
        if not item.endswith("xml"):
            line = folder_name + item + '\n'
            print line
            out_file.write(line)

    out_file.close()
    
if __name__ == "__main__" :
    run()  