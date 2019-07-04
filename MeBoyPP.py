
# MeBoyPP is a quick-and-dirty preprocessor that creates GB-only and GBC-only
# versions of the Dmgcpu and GraphicsChip classes. The original classes are
# correct and functionally equivalent to specialized classes, but a couple of percent
# slower than especially the BW versions. The original classes are not included in
# the binary distribution, and are not used by default.

import re

# preprocess processes and saves a copy of a file. It recognizes commands
# of the form "//#if ([+-]key)+", where "key" is a keyword that must be defined if
# preced by a +, and must non be defined if preceded by a -. Any lines following
# the command (until the next command) will be included only if all keywords match
# (defined/nondefined with +/-). The line containing the command is never included
# (typically, it holds a control statement).
# preprocess can also perform literal string substitutions in the source.
def preprocess(inname, outname, defined, substitutions):
    fin = open(inname)
    fout = open(outname, 'w')
    
    echo = True;
    
    try:
        for line in fin:
            result = re.search('//#if', line)
            
            if (result):
                echo = True;
                for cond in re.split('[ \n]', line[result.end()+1:-1]):
                    if (len(cond) == 0):
                        continue;
                    
                    if (cond[0] == '+'):
                        echo = echo & (cond[1:] in defined)
                    elif cond[0] == '-':
                        echo = echo & (cond[1:] not in defined)
                    else:
                        raise Exception, line
                    
                continue #dont echo "//#if" lines
            
            if (echo):
                # perform substitutions
                for couple in substitutions:
                    match = re.search(couple[0], line)
                    if (match):
                        line = line[:match.start()] + couple[1] + line[match.end():]
                fout.write(line)
        
    finally:
        fin.close()
        fout.close()

# create GB (black/white) versions of the files
defined = []
substitutions = [['Dmgcpu', 'DmgcpuBW'], ['GraphicsChip', 'GraphicsChipBW']]
preprocess("Dmgcpu.java", "DmgcpuBW.java", defined, substitutions)
preprocess("GraphicsChip.java", "GraphicsChipBW.java", defined, substitutions)

# create color versions of the files
defined = ['gbc']
substitutions = [['Dmgcpu', 'DmgcpuColor'], ['GraphicsChip', 'GraphicsChipColor']]
preprocess("Dmgcpu.java", "DmgcpuColor.java", defined, substitutions)
preprocess("GraphicsChip.java", "GraphicsChipColor.java", defined, substitutions)

