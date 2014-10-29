/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


/**
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.classify;

import edu.umass.cs.mallet.base.types.InstanceList;

public class AccuracyEvaluator extends AbstractClassifierEvaluating {

  public boolean evaluate (Classifier classifier, boolean finishedTraining, int iteration, double cost,
                           InstanceList training, InstanceList validation, InstanceList testing)
  {
    if (shouldDoEvaluate (iteration, finishedTraining)) {
      System.out.print ("Iteration=" + iteration);
      System.out.print (" cost=" + cost);
      System.out.print (" Accuracy: training=" + classifier.getAccuracy (training));
      if (validation != null)
        System.out.print (" validation=" + classifier.getAccuracy (validation));
      if (testing != null)
        System.out.print (" testing=" + classifier.getAccuracy (testing));
      System.out.println ("");
      return true;
    } else {
      return true;
    }
  }
}
