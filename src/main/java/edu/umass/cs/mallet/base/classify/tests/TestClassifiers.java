/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.classify.tests;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.classify.*;
import edu.umass.cs.mallet.base.pipe.*;
import edu.umass.cs.mallet.base.util.*;
//import edu.umass.cs.mallet.base.pipe.SerialPipe;
import edu.umass.cs.mallet.base.pipe.iterator.ArrayIterator;
import edu.umass.cs.mallet.base.pipe.iterator.PipeInputIterator;
import edu.umass.cs.mallet.base.pipe.iterator.RandomTokenSequenceIterator;
import junit.framework.*;
import java.net.URI;

public class TestClassifiers extends TestCase
{
	public TestClassifiers (String name)
	{
		super (name);
	}

	private static Alphabet dictOfSize (int size)
	{
		Alphabet ret = new Alphabet ();
		for (int i = 0; i < size; i++)
			ret.lookupIndex ("feature"+i);
		return ret;
	}

	public void testRandomTrained ()
	{
		ClassifierTrainer[] trainers = new ClassifierTrainer[3];
		trainers[0] = new NaiveBayesTrainer();
		trainers[1] = new MaxEntTrainer();
		trainers[2] = new DecisionTreeTrainer();

		Alphabet fd = dictOfSize (3);
		String[] classNames = new String[] {"class0", "class1", "class2"};

		InstanceList ilist = new InstanceList (new Random(1), fd, classNames, 200);

		InstanceList lists[] = ilist.split (new java.util.Random(2), new double[] {.5, .5});
		//System.out.println ("Training set size = "+lists[0].size());
		//System.out.println ("Testing set size = "+lists[1].size());
		
		Classifier[] classifiers = new Classifier[trainers.length];
		for (int i = 0; i < trainers.length; i++)
			classifiers[i] = trainers[i].train (lists[0]);

		System.out.println ("Accuracy on training set:");
		for (int i = 0; i < trainers.length; i++)
			System.out.println (classifiers[i].getClass().getName()
													+ ": " + new Trial (classifiers[i], lists[0]).accuracy());

		System.out.println ("Accuracy on testing set:");
		for (int i = 0; i < trainers.length; i++)
			System.out.println (classifiers[i].getClass().getName()
													+ ": " + new Trial (classifiers[i], lists[1]).accuracy());
	}

  public void tetsNewFeatures ()
  {
    ClassifierTrainer[] trainers = new ClassifierTrainer[1];
    trainers[0] = new MaxEntTrainer();

    Alphabet fd = dictOfSize (3);
    String[] classNames = new String[] {"class0", "class1", "class2"};

    Random r = new Random(1);
    InstanceList training = new InstanceList (r, fd, classNames, 50);
    expandDict (fd, 25);

    Classifier[] classifiers = new Classifier[trainers.length];
    for (int i = 0; i < trainers.length; i++)
      classifiers[i] = trainers[i].train (training);

    System.out.println ("Accuracy on training set:");
    for (int i = 0; i < trainers.length; i++)
      System.out.println (classifiers[i].getClass().getName()
                          + ": " + new Trial (classifiers[i], training).accuracy());

    InstanceList testing = new InstanceList (training.getPipe ());
    PipeInputIterator iter = new RandomTokenSequenceIterator (
      r,  new Dirichlet (fd, 2.0),
      30, 0,
      10, 50,
      classNames);
    testing.add (iter);

    for (int i = 0; i < testing.size (); i++) {
      Instance inst = testing.getInstance (i);
      System.out.println ("DATA:"+inst.getData());
    }

    System.out.println ("Accuracy on testing set:");
    for (int i = 0; i < trainers.length; i++)
      System.out.println (classifiers[i].getClass().getName()
                          + ": " + new Trial (classifiers[i], testing).accuracy());
  }

  private void expandDict (Alphabet fd, int size)
  {
    fd.startGrowth ();
    for (int i = 0; i < size; i++)
      fd.lookupIndex ("feature"+i, true);
  }

  public static Test suite ()
	{
		return new TestSuite (TestClassifiers.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
