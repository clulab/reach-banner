/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.fst;

import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Sequence;
import edu.umass.cs.mallet.base.util.MalletLogger;

import java.io.PrintStream;
import java.util.logging.Logger;

/**
 * Reports the percentage of instances for which the entire predicted sequence was
 *  correct.
 *
 * Created: May 12, 2004
 * 
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: InstanceAccuracyEvaluator.java,v 1.1 2011/07/29 09:11:45 bleaman Exp $
 */
public class InstanceAccuracyEvaluator extends TransducerEvaluator {
  
  private static final Logger logger = MalletLogger.getLogger (InstanceAccuracyEvaluator.class.getName());

  public void test (Transducer crf, InstanceList data, String description, PrintStream viterbiOutputStream)
  {
    int correct = 0;
    for (int i = 0; i < data.size(); i++) {
      Instance instance = data.getInstance(i);
      Sequence input = (Sequence) instance.getData();
      Sequence trueOutput = (Sequence) instance.getTarget();
      assert (input.size() == trueOutput.size());
      Sequence predOutput = crf.transduce (input);
      assert (predOutput.size() == trueOutput.size());
      if (sequencesMatch (trueOutput, predOutput))
        correct++;
      }
    double acc = ((double)correct) / data.size();
    logger.info (description+" Num instances = "+data.size()+"  Num correct = "+correct);
    logger.info (description+" Per-instance accuracy = "+acc);
  }

  private boolean sequencesMatch (Sequence trueOutput, Sequence predOutput)
  {
    for (int j = 0; j < trueOutput.size(); j++) {
      Object tru = trueOutput.get(j);
      Object pred = predOutput.get(j);
      if (!tru.toString().equals (pred.toString())) {
        return false;
      }
    }
    return true;
  }

}
