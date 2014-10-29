# Format is:
# $1 = location of BioCreative 2 Gene Mention sentence file ('train.in')
# $2 = location of BioCreative 2 Gene Mention mention file ('GENE.eval')
# $3 = location of BioCreative 2 Gene Mention alternate mention file ('ALTGENE.eval'). If your application has no alternate mentions, use an empty file
# $4 = name & location of the trained model created earlier
# $5 = location to put the output files
#
# Note: the "-Xmx4G -Xms4G" specifies for the VM to use exactly 4 Gb of memory; this may need modification for your system
#
java -Xmx4G -Xms4G -cp temp/classes/:libs/dragontool.jar:libs/heptag.jar:libs/medpost.jar bc2.TestModel banner.properties $1 $2 $3 $4 $5

