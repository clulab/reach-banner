/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.pipe.iterator;

import edu.umass.cs.mallet.base.pipe.iterator.PipeInputIterator;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Label;
import edu.umass.cs.mallet.base.types.LabelAlphabet;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Dirichlet;
import edu.umass.cs.mallet.base.types.Multinomial;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.FeatureVector;
import edu.umass.cs.mallet.base.types.Label;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.util.Random;
import java.net.URI;
import java.util.logging.*;

public class RandomFeatureVectorIterator extends AbstractPipeInputIterator
{
	private static Logger logger = MalletLogger.getLogger(RandomFeatureVectorIterator.class.getName());

	Random r;
	Dirichlet classCentroidDistribution;
	double classCentroidAvergeAlphaMean;
	double classCentroidAvergeAlphaVariance;
	double featureVectorSizePoissonLambda;
	double classInstanceCountPoissonLamba;
	String[] classNames;

	int[] numInstancesPerClass;						// indexed over classes
	Dirichlet[] classCentroid;						// indexed over classes
	int currentClassIndex;
	int currentInstanceIndex;
	
	public RandomFeatureVectorIterator (Random r,
																			// the generator of all random-ness used here
																			Dirichlet classCentroidDistribution,
																			// includes a Alphabet
																			double classCentroidAvergeAlphaMean,
																			// Gaussian mean on the sum of alphas
																			double classCentroidAvergeAlphaVariance,
																			// Gaussian variance on the sum of alphas
																			double featureVectorSizePoissonLambda,
																			double classInstanceCountPoissonLamba,
																			String[] classNames)
	{
		this.r = r;
		this.classCentroidDistribution = classCentroidDistribution;
		assert (classCentroidDistribution.getAlphabet() instanceof Alphabet);
		this.classCentroidAvergeAlphaMean = classCentroidAvergeAlphaMean;
		this.classCentroidAvergeAlphaVariance = classCentroidAvergeAlphaVariance;
		this.featureVectorSizePoissonLambda = featureVectorSizePoissonLambda;
		this.classInstanceCountPoissonLamba = classInstanceCountPoissonLamba;
		this.classNames = classNames;
		this.numInstancesPerClass = new int[classNames.length];
		this.classCentroid = new Dirichlet[classNames.length];
		for (int i = 0; i < classNames.length; i++) {
			logger.fine ("classCentroidAvergeAlphaMean = "+classCentroidAvergeAlphaMean);
			double aveAlpha = r.nextGaussian (classCentroidAvergeAlphaMean,
																				classCentroidAvergeAlphaVariance);
			logger.fine ("aveAlpha = "+aveAlpha);
			classCentroid[i] = classCentroidDistribution.randomDirichlet (r, aveAlpha);
			//logger.fine ("Dirichlet for class "+classNames[i]);	classCentroid[i].print();
		}
		reset ();
	}

	public RandomFeatureVectorIterator (Random r, Alphabet vocab, String[] classnames)
	{
		this (r, new Dirichlet(vocab, 2.0),
					30, 0,
					10, 20, classnames);
	}

	public Alphabet getAlphabet () { return classCentroidDistribution.getAlphabet(); }

	private static Alphabet dictOfSize (int size)
	{
		Alphabet ret = new Alphabet ();
		for (int i = 0; i < size; i++)
			ret.lookupIndex ("feature"+i);
		return ret;
	}

	private static String[] classNamesOfSize (int size)
	{
		String[] ret = new String[size];
		for (int i = 0; i < size; i++)
			ret[i] = "class"+i;
		return ret;
	}

	public RandomFeatureVectorIterator (Random r, int vocabSize, int numClasses)
	{
		this (r, new Dirichlet(dictOfSize(vocabSize), 2.0),
					30, 0,
					10, 20, classNamesOfSize(numClasses));
	}

	public void reset ()
	{
		for (int i = 0; i < classNames.length; i++) {
			this.numInstancesPerClass[i] = r.nextPoisson (classInstanceCountPoissonLamba);
			logger.fine ("Class "+classNames[i]+" will have "
									 +numInstancesPerClass[i]+" instances.");
		}
		this.currentClassIndex = classNames.length - 1;
		this.currentInstanceIndex = numInstancesPerClass[currentClassIndex] - 1;
	}

	public LabelAlphabet getLabelAlphabet () { return null; }
	
	public Instance nextInstance ()
	{
		if (currentInstanceIndex < 0) {
			if (currentClassIndex <= 0)
				throw new IllegalStateException ("No next FeatureVector.");
			currentClassIndex--;
			currentInstanceIndex = numInstancesPerClass[currentClassIndex] - 1;
		}
		URI uri = null;
		try { uri = new URI ("random:" + classNames[currentClassIndex] + "/" + currentInstanceIndex); }
		catch (Exception e) {e.printStackTrace(); throw new IllegalStateException (); }
		//xxx Producing small numbers? int randomSize = r.nextPoisson (featureVectorSizePoissonLambda);
		int randomSize = (int)featureVectorSizePoissonLambda;
		FeatureVector fv = classCentroid[currentClassIndex].randomFeatureVector (r, randomSize);
		//logger.fine ("FeatureVector "+currentClassIndex+" "+currentInstanceIndex); fv.print();
		currentInstanceIndex--;
		return new Instance (fv, classNames[currentClassIndex], uri, null);
	}

	public boolean hasNext ()	{	return ! (currentClassIndex == 0 && currentInstanceIndex == 0);	}
	
}
