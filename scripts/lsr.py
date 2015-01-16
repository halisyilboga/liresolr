#!/usr/bin/env python

import os


def run(folder="/data/digitalcandy/ml/images/"):
    os.path.walk(folder, step, ('.jpg', 'out.txt'))    
     
def step((ext, seed_path), dirname, names):
    ext = ext.lower() 
    for name in names:
        if name.lower().endswith(ext):
            with open(seed_path, 'a') as seed_file:
                seed_file.write('%s\n' % os.path.join(dirname, name))
 
    
if __name__ == "__main__" :
    run('/Users/ferdous/code/java/lire-trunk/samples/liredemo/flickrphotos')  
