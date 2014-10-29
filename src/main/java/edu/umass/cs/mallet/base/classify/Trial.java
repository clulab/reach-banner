/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




package edu.umass.cs.mallet.base.classify;

import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Label;
import edu.umass.cs.mallet.base.types.FeatureVector;
import edu.umass.cs.mallet.base.pipe.Pipe;
import java.util.ArrayList;

/**
 * A convenience class for running an instance list through
 * a classifier and storing the instancelist, the classifier,
 * and the resulting classifications of each instance.
 *
 * Also has methods for computing f1 and accuracy over
 * the classifications.
 *
 * Some similar functionality is in {@link edu.umass.cs.mallet.base.classify.Classifier}
 * itself.
 * @see InstanceList
 * @see Classifier
 * @see Classification
 *
 *        @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class Trial
{
	ArrayList classifications;
	Classifier classifier;
	InstanceList ilist;

	public Trial (Classifier c, InstanceList ilist)
	{
		this.classifier = c;
		this.ilist = ilist;
		classifications = c.classify (ilist);
	}

	public int size ()
	{
		return classifications.size();
	}

	public final Classification getClassification (int i)
	{
		return (Classification) classifications.get(i);
	}

	public Classifier getClassifier () {
		return classifier;
	}

	public ArrayList toArrayList ()
	{
		//return classifications.clone();  ??
		return classifications;
	}
	
	public double labelF1 (String label)
	{
		int numCorrect = 0;
		int numMissed = 0;
		int numInLabel = 0;
		double recall = 0;
		double precision = 0;
                
		for (int i = 0; i < classifications.size(); i++) {
			if (getClassification(i).getLabeling().getBestLabel().toString().equals(label)) {
				if (getClassification(i).bestLabelIsCorrect()) {
					numCorrect++;
				} else {
					numMissed++;
				}
			}
			if (getClassification(i).getInstance().getLabeling().getBestLabel().toString().equals(label))
				numInLabel++;
		}
		if (numInLabel > 0) {
			recall = ((double)numCorrect / (double)numInLabel);
		}
		int numAnswered = numCorrect + numMissed;
		precision = ((double)numCorrect / (double)numAnswered);
		if ((recall + precision) > 0) {
			return (2 * recall * precision) / (recall + precision);  // f-measure
		}
		else {
			return 0;
		}
	}

	public double accuracy ()
	{
		int numCorrect = 0;
		for (int i = 0; i < classifications.size(); i++) {
			if (getClassification(i).bestLabelIsCorrect())
				numCorrect++;
		}
		return ((double)numCorrect/classifications.size());
	}
}
