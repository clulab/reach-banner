banner
======

A fork of the Banner Named Entity Recognizer from Arizona State.
This is based on a CVS snapshot from 11/27/2014.

Changes to the original code
----------------------------

The following changes were made to the original Banner code:
* Removed the following packages from src: test, edu.umass.cs.mallet.projects. They are not actually used.
* Moved the code from src/ to src/main/java/
* Moved all data resources (dict/, nlpdata/, regex.txt, banner.properties) under the new banner_data/ directory. Adjusted banner.properties to point to the new locations.
* Trained a model using all BC2 training data, and saved it as banner_data/banner_model.data.
* Added the BannerWrapper class, which creates a Banner NER with default options, based on the banner_data directory.

