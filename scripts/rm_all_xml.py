#!/usr/bin/env python

import os


def run(folder="/data/digitalcandy/ml/images/"):
    os.path.walk(folder, step, ('.xml', 'out.txt'))    
     
def step((ext, seed_path), dirname, names):
    ext = ext.lower() 
    for name in names:
        if name.lower().endswith(ext):
            print('%s\n' % os.path.join(dirname, name))
            os.remove(os.path.join(dirname, name))
 
    
if __name__ == "__main__" :
    run()  
