#!/bin/sh

mkdir -p tmp
#./train.sh ../bc2geneMention/train/train.in ../bc2geneMention/train/GENE.eval banner_model.dat 0.01
./test.sh ../bc2geneMention/train/train.in ../bc2geneMention/train/GENE.eval ../bc2geneMention/train/ALTGENE.eval banner_model.dat tmp
