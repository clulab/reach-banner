/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.fst;

import edu.umass.cs.mallet.base.types.InstanceList;
import java.io.*;

public abstract class TransducerEvaluator
{
  protected int numIterationsToWait = 0;
	protected int numIterationsToSkip = 0;
	protected boolean alwaysEvaluateWhenFinished = true;
	protected boolean printModelAtEnd = false;
	protected boolean checkpointTransducer = false;
	protected String checkpointFilePrefix = null;
	protected int checkpointIterationsToSkip = 9;
  protected	boolean viterbiOutput = true;
	protected String viterbiOutputFilePrefix = null;
	protected int viterbiOutputIterationsToWait = 10;
	protected int viterbiOutputIterationsToSkip = 10;
	protected String viterbiOutputEncoding = "UTF-8";


  public int getNumIterationsToWait()
  {
    return numIterationsToWait;
  }


  public void setNumIterationsToWait(int numIterationsToWait)
  {
    this.numIterationsToWait = numIterationsToWait;
  }


  public int getNumIterationsToSkip()
  {
    return numIterationsToSkip;
  }


  public void setNumIterationsToSkip(int numIterationsToSkip)
  {
    this.numIterationsToSkip = numIterationsToSkip;
  }


  public boolean isAlwaysEvaluateWhenFinished()
  {
    return alwaysEvaluateWhenFinished;
  }


  public void setAlwaysEvaluateWhenFinished(boolean alwaysEvaluateWhenFinished)
  {
    this.alwaysEvaluateWhenFinished = alwaysEvaluateWhenFinished;
  }


  public boolean isPrintModelAtEnd()
  {
    return printModelAtEnd;
  }


  public void setPrintModelAtEnd(boolean printModelAtEnd)
  {
    this.printModelAtEnd = printModelAtEnd;
  }


  public void setViterbiOutput(boolean vo)
  {
    viterbiOutput = vo;
  }

  public boolean getViterbiOutput()
  {
    return viterbiOutput;
  }

  public void setViterbiOutputFilePrefix(String p)
  {
    viterbiOutputFilePrefix = p;
  }

  public String getViterbiOutputFilePrefix()
  {
    return viterbiOutputFilePrefix;
  }

  public void setViterbiOutputIterationsToWait(int i)
  {
    viterbiOutputIterationsToWait = i;
  }

  public int getViterbiOutputIterationsToWait()
  {
    return viterbiOutputIterationsToWait;
  }

  public void setViterbiOutputIterationsToSkip(int i)
  {
    viterbiOutputIterationsToSkip = i;
  }

  public int getViterbiOutputIterationsToSkip()
  {
    return viterbiOutputIterationsToSkip;
  }

  public void setViterbiOutputEncoding(String o)
  {
    viterbiOutputEncoding = o;
  }

  public String getViterbiOutputEncoding()
  {
    return viterbiOutputEncoding;
  }
	
  public void setCheckpointTransducer(boolean c)
  {
    checkpointTransducer = c;
  }

  public boolean getCheckpointTransducer()
  {
    return checkpointTransducer;
  }

  public void setCheckpointFilePrefix(String p)
  {
    checkpointFilePrefix = p;
  }

  public String getCheckpointFilePrefix()
  {
    return checkpointFilePrefix;
  }

  public void setCheckpointIterationsToSkip(int i)
  {
    checkpointIterationsToSkip = i;
  }

  public int getCheckpointIterationsToSkip()
  {
    return checkpointIterationsToSkip;
  }

  /**
   * Returns whether an evaluator should run this iteration, based on
   * the current values of numIterationsToWait, alwaysEvaluateWhenFinished,
   * and numIteartionsToSkip
   * @param iteration Training iteration
   * @param finishedTraining Whether training has finished
   * @return boolean Whether to evaluate
   */
  protected boolean shouldDoEvaluate(int iteration, boolean finishedTraining)
  {
    // Don't evaluate if it is too early in training to matter
    if (iteration < numIterationsToWait && !(alwaysEvaluateWhenFinished && finishedTraining))
      return false;
    // Only evaluate every 5th iteration
    if (numIterationsToSkip > 0
         && iteration % numIterationsToSkip != 0
         && !(alwaysEvaluateWhenFinished && finishedTraining))
      return false;
    return true;
  }

  /**
   * Evaluates a Tranducers on a given training, validation, and testing set.               .
   * <P>
   * The default implementation calls the evaluator's <TT>test</TT> on the
   *  training, validation, and testing sets.
   *
   * @param crf The transducer to evaluate.
   * @param finishedTraining Whether training has finished.  Evaluators can be set to always evaluate when training completes.
   * @param iteration The current iteration
   * @param converged Whether training has converged. Most evaluators ignore this.
   * @param cost The current cost during training.  Some evaluators may include this in the output.
   * @param training The training set.  If null, the argument is ignored.
   * @param validation The validation set.  If null, the argument is ignored.
   * @param testing The testing set.  If null, the argument is ignored.
   * @return Whether training should continue.
   */
  public boolean evaluate (Transducer crf, boolean finishedTraining, int iteration, boolean converged,
                           double cost, InstanceList training, InstanceList validation, InstanceList testing)
  {
    if (shouldDoEvaluate(iteration, finishedTraining)) {
      InstanceList[] lists = new InstanceList[] {training, validation, testing};
      String[] listnames = new String[] {"Training", "Validation", "Testing"};
      for (int k = 0; k < lists.length; k++)
        if (lists[k] != null)
          test(crf, lists[k], listnames[k], null);
    }
    return true;
  }

  public abstract void test(Transducer transducer, InstanceList data,
                   String description, PrintStream viterbiOutputStream);
                   
}
