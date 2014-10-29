#!/bin/sh

BC2=/Users/mihais/corpora/bc2geneMention # change this to the location of the BioCreative 2 dataset!
TRAIN=$BC2/train/train.in
GENE=$BC2/train/GENE.eval
ALTGENE=$BC2/train/ALTGENE.eval
MODEL=banner_data/banner_model.dat
PROPORTION=0.01

CLASSPATH=target/scala-2.10/classes/:lib/dragontool.jar:lib/heptag.jar

mkdir -p tmp

# Train (take out $PROPORTION to train on the whole dataset!)
java -Xmx4G -Xms4G -cp $CLASSPATH bc2.TrainModel banner_data/banner.properties $TRAIN $GENE $ALTGENE $MODEL $PROPORTION

# Test 
java -Xmx4G -Xms4G -cp $CLASSPATH bc2.TestModel banner_data/banner.properties $TRAIN $GENE $ALTGENE $MODEL tmp 