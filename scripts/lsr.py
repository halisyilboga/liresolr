# -*- coding: utf-8 -*-
#!/usr/bin/env python

import os
import optparse

def run(folder):
    f = open('out.txt', 'w+')
    f.truncate()
    f.close()
    os.path.walk(folder, step, ('.jpg', 'out.txt'))

def step((ext, seed_path), dirname, names):
    ext = ext.lower()
    for name in names:
        if name.lower().endswith(ext):
            with open(seed_path, 'a+') as seed_file:
                seed_file.write('%s\n' % os.path.join(dirname, name))


if __name__ == "__main__" :
    parser = optparse.OptionParser()
    parser.add_option("-d", "--directory", metavar="DIR", help="Directory to scan for image files")

    opts, args = parser.parse_args()
    print 'scanning directory: ', opts.directory
    run(opts.directory)
