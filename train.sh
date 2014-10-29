# Format is:
# $1 = location of BioCreative 2 Gene Mention sentence file ('train.in')
# $2 = location of BioCreative 2 Gene Mention mention file ('GENE.eval')
# $3 = name & location of the model to output
# $4 (optional) = proportion of the training data to use. Specify e.g. 0.01 to quickly verify everything is working. Leave off to use all of the data.
#
# Note: the "-Xmx4G -Xms4G" specifies for the VM to use exactly 4 Gb of memory; this may need modification for your system
#
java -Xmx4G -Xms4G -cp temp/classes/:libs/dragontool.jar:libs/heptag.jar:libs/medpost.jar bc2.TrainModel banner.properties $1 $2 $3 $4

