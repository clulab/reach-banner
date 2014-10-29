/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.fst;

import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.types.*;

import java.util.logging.Logger;
import java.util.Arrays;
import java.io.PrintStream;
import java.text.DecimalFormat;

/**
 * @author Charles Sutton
 * @version $Id: PerClassAccuracyEvaluator.java,v 1.1 2011/07/29 09:11:45 bleaman Exp $
 */
public class PerClassAccuracyEvaluator extends TransducerEvaluator {

  private static Logger logger = MalletLogger.getLogger(TokenAccuracyEvaluator.class.getName());


  public PerClassAccuracyEvaluator(boolean printViterbiPath)
  {
    viterbiOutput = printViterbiPath;
  }


  public PerClassAccuracyEvaluator()
  {
    this(false);
  }


  public boolean evaluate(Transducer crf, boolean finishedTraining, int iteration,
                          boolean converged, double cost,
                          InstanceList training, InstanceList validation, InstanceList testing)
  {
    logger.info("Iteration=" + iteration + " Cost=" + cost);
    InstanceList[] lists = new InstanceList[]{training, validation, testing};
    String[] listnames = new String[]{"Training", "Validation", "Testing"};
    for (int k = 0; k < lists.length; k++)
      if (lists[k] != null)
        test(crf, lists[k], listnames[k], null);
    return true;
  }


  public void test(Transducer model, InstanceList data, String description,
                   PrintStream viterbiOutputStream)
  {
    Alphabet dict = model.getInputPipe().getTargetAlphabet();
    int numLabels = dict.size();
    int[] numCorrectTokens = new int [numLabels];
    int[] numPredTokens = new int [numLabels];
    int[] numTrueTokens = new int [numLabels];

    logger.info("Per-token results for " + description);
    for (int i = 0; i < data.size(); i++) {
      Instance instance = data.getInstance(i);
      Sequence input = (Sequence) instance.getData();
      Sequence trueOutput = (Sequence) instance.getTarget();
      assert (input.size() == trueOutput.size());
      Sequence predOutput = model.transduce (input);
      assert (predOutput.size() == trueOutput.size());
      for (int j = 0; j < trueOutput.size(); j++) {
        int idx = dict.lookupIndex(trueOutput.get(j));
        numTrueTokens[idx]++;
        numPredTokens[dict.lookupIndex(predOutput.get(j))]++;
        if (trueOutput.get(j).equals(predOutput.get(j)))
          numCorrectTokens[idx]++;
        if (viterbiOutputStream != null) {
          FeatureVector fv = (FeatureVector) input.get(j);
          viterbiOutputStream.println(trueOutput.get(j).toString() + '/' + predOutput.get(j).toString() + "  " +
                                      fv.toString(true));
        }
      }
    }

    DecimalFormat f = new DecimalFormat ("0.####");
    double[] allf = new double [numLabels];
    for (int i = 0; i < numLabels; i++) {
      Object label = dict.lookupObject(i);
      double precision = ((double) numCorrectTokens[i]) / numPredTokens[i];
      double recall = ((double) numCorrectTokens[i]) / numTrueTokens[i];
      double f1 = (2 * precision * recall) / (precision + recall);
      if (!Double.isNaN (f1)) allf [i] = f1;
      logger.info(description +" label " + label + " P " + f.format (precision)
                  + " R " + f.format(recall) + " F1 "+ f.format (f1));
    }

    logger.info ("Macro-average F1 "+f.format (MatrixOps.mean (allf)));

  }

}
