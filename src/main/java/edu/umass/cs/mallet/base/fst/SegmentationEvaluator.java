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

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.util.MalletLogger;
import java.io.*;
import java.util.logging.*;
import java.util.regex.*;

public class SegmentationEvaluator extends TransducerEvaluator
{
	private static Logger logger = MalletLogger.getLogger(SegmentationEvaluator.class.getName());

	// equals() is called on these objects to determine if this token is the start (end) of a segment
	// "segmentEndTag" should return "true" for the token *after* the end of the segment (i.e. that token
	// is not part of the segment).
	static Pattern startRegex = Pattern.compile ("^B.*");
	//static Pattern endRegex = Pattern.compile ("^O.*");
	Object segmentStartTag = new Object () {
			public boolean equals (Object o) { return startRegex.matcher(o.toString()).matches(); } };
	Object segmentEndTag = new Object () {
			public boolean equals (Object o) { return false; } };

	public SegmentationEvaluator setSegmentStartTag (Object o) { this.segmentStartTag = o; return this; }
	public SegmentationEvaluator setSegmentEndTag (Object o) { this.segmentEndTag = o; return this; }
	
	public boolean evaluate (Transducer model, boolean finishedTraining, int iteration,
													 boolean converged, double cost,
													 InstanceList training, InstanceList validation, InstanceList testing)
	{
		logger.info ("Iteration="+iteration+" Cost="+cost);
		// Don't evaluate if it is too early in training to matter
		if (iteration < numIterationsToWait && !(alwaysEvaluateWhenFinished && finishedTraining))
			return true;
		// Only evaluate every 5th iteration
		if (numIterationsToWait > 0
				&& iteration % numIterationsToSkip != 0
				&& !(alwaysEvaluateWhenFinished && finishedTraining))
			return true;

		// Possibly write model to a checkpoint file
		if (model instanceof Serializable && checkpointTransducer && iteration > 0
				&& iteration % checkpointIterationsToSkip == 0) {
			String checkFilename = checkpointFilePrefix == null ? "" : checkpointFilePrefix + '.';
			checkFilename = checkFilename + "checkpoint"+iteration+".model";
      try {
        ObjectOutputStream oos =
          new ObjectOutputStream(
            new FileOutputStream(new File(checkFilename)));
        oos.writeObject(model);
        oos.close();
        System.err.println("Model written to " + checkFilename);
      }
      catch (IOException e) {
        System.err.println("Exception writing file " + checkFilename + ": " + e);
      }
		}

		InstanceList[] lists = new InstanceList[] {training, validation, testing};
		String[] listnames = new String[] {"Training", "Validation", "Testing"};

		for (int k = 0; k < lists.length; k++)
			if (lists[k] != null)
      {
        PrintStream viterbiOutputStream = null;
        if (iteration >= viterbiOutputIterationsToWait && iteration % viterbiOutputIterationsToSkip == 0) {
          if (viterbiOutputFilePrefix == null) {
            viterbiOutputStream = System.out;
          } else {
            String viterbiFilename = null;
            viterbiFilename = viterbiOutputFilePrefix + ".viterbi";
            try {
              FileOutputStream fos = new FileOutputStream (viterbiFilename);
              if (viterbiOutputEncoding == null)
                viterbiOutputStream = new PrintStream (fos);
              else
                viterbiOutputStream = new PrintStream (fos, true, viterbiOutputEncoding);
            } catch (IOException e) {
              logger.warning ("Couldn't open Viterbi output file '"+viterbiFilename+"'; continuing without Viterbi output trace.");
              viterbiOutputStream = null;
            }
          }
        }
        test(model, lists[k], listnames[k], viterbiOutputStream);
        if (viterbiOutputStream != null && viterbiOutputFilePrefix != null && viterbiOutputStream != System.out)
          viterbiOutputStream.close();
      }
		if (printModelAtEnd && finishedTraining)
      model.toString();
		return true;
	}

  public void test(Transducer model, InstanceList data, String description,
                   PrintStream viterbiOutputStream)
  {
		int numCorrectTokens, totalTokens;
		int numTrueSegments, numPredictedSegments, numCorrectSegments;
		int numCorrectSegmentsInAlphabet, numCorrectSegmentsOOV;
		int numIncorrectSegmentsInAlphabet, numIncorrectSegmentsOOV;
		TokenSequence sourceTokenSequence = null;

    totalTokens = numCorrectTokens = 0;
    numTrueSegments = numPredictedSegments = numCorrectSegments = 0;
    numCorrectSegmentsInAlphabet = numCorrectSegmentsOOV = 0;
    numIncorrectSegmentsInAlphabet = numIncorrectSegmentsOOV = 0;
    for (int i = 0; i < data.size(); i++) {
      if (viterbiOutputStream != null)
        viterbiOutputStream.println ("Viterbi path for "+description+" instance #"+i);
      Instance instance = data.getInstance(i);
      Sequence input = (Sequence) instance.getData();
      //String tokens = null;
      //if (instance.getSource() != null)
      //tokens = (String) instance.getSource().toString();
      Sequence trueOutput = (Sequence) instance.getTarget();
      assert (input.size() == trueOutput.size());
      Sequence predOutput = model.transduce (input);
      assert (predOutput.size() == trueOutput.size());
      boolean trueStart, predStart;
      for (int j = 0; j < trueOutput.size(); j++) {
        totalTokens++;
        trueStart = predStart = false;
        if (segmentStartTag.equals(trueOutput.get(j))) {
          numTrueSegments++;
          trueStart = true;
        }
        if (segmentStartTag.equals(predOutput.get(j))) {
          predStart = true;
          numPredictedSegments++;
        }
        if (trueStart && predStart) {
          int m;
          //StringBuffer sb = new StringBuffer();
          //sb.append (tokens.charAt(j));
          for (m = j+1; m < trueOutput.size(); m++) {
            trueStart = predStart = false; // Here, these actually mean "end", not "start"
            if (segmentEndTag.equals(trueOutput.get(m)))
              trueStart = true;
            if (segmentEndTag.equals(predOutput.get(m)))
              predStart = true;
            if (trueStart || predStart) {
              if (trueStart && predStart) {
                // It is a correct segment
                numCorrectSegments++;
                //if (HashFile.allLexicons.contains(sb.toString()))
                //numCorrectSegmentsInAlphabet++;
                //else
                //numCorrectSegmentsOOV++;
              } else {
                // It is an incorrect segment; let's find out if it was in the lexicon
                //for (int mm = m; mm < trueOutput.size(); mm++) {
                //if (segmentEndTag.equals(predOutput.get(mm)))
                //break;
                //sb.append (tokens.charAt(mm));
                //}
                //if (HashFile.allLexicons.contains(sb.toString()))
                //numIncorrectSegmentsInAlphabet++;
                //else
                //numIncorrectSegmentsOOV++;
              }
              break;
            }
            //sb.append (tokens.charAt(m));
          }
          // for the case of the end of the sequence
          if(m==trueOutput.size()) {
            if (trueStart==predStart) {
              numCorrectSegments++;
              //if (HashFile.allLexicons.contains(sb.toString()))
              //numCorrectSegmentsInAlphabet++;
              //else
              //numCorrectSegmentsOOV++;
            } else {
              //if (HashFile.allLexicons.contains(sb.toString()))
              //numIncorrectSegmentsInAlphabet++;
              //else
              //numIncorrectSegmentsOOV++;
            }
          }
        } else if (predStart) {
          // Here is an incorrect predicted start, find out if the word is in the lexicon
          //StringBuffer sb = new StringBuffer();
          //sb.append (tokens.charAt(j));
          //for (int mm = j+1; mm < trueOutput.size(); mm++) {
          //if (segmentEndTag.equals(predOutput.get(mm)))
          //break;
          //sb.append (tokens.charAt(mm));
          //}
          //if (HashFile.allLexicons.contains(sb.toString()))
          //numIncorrectSegmentsInAlphabet++;
          //else
          //numIncorrectSegmentsOOV++;
        }
        if (trueOutput.get(j).equals(predOutput.get(j)))
          numCorrectTokens++;
        if (viterbiOutputStream != null) {
          FeatureVector fv = (FeatureVector) input.get(j);
          //viterbiOutputStream.println (tokens.charAt(j)+" "+trueOutput.get(j).toString()+
          //'/'+predOutput.get(j).toString()+"  "+ fv.toString(true));
          if (sourceTokenSequence != null)
            viterbiOutputStream.print (sourceTokenSequence.getToken(j).getText()+": ");
          viterbiOutputStream.println (trueOutput.get(j).toString()+
                                       '/'+predOutput.get(j).toString()+"  "+ fv.toString(true));

        }
      }
    }
    logger.info (description +" accuracy="+((double)numCorrectTokens)/totalTokens);
    double precision = numPredictedSegments == 0 ? 1 : ((double)numCorrectSegments) / numPredictedSegments;
    double recall = numTrueSegments == 0 ? 1 : ((double)numCorrectSegments) / numTrueSegments;
    double f1 = recall+precision == 0.0 ? 0.0 : (2.0 * recall * precision) / (recall + precision);
    logger.info (" precision="+precision+" recall="+recall+" f1="+f1);
    logger.info ("segments true="+numTrueSegments+" pred="+numPredictedSegments+" correct="+numCorrectSegments+" misses="+(numTrueSegments-numCorrectSegments)+" alarms="+(numPredictedSegments-numCorrectSegments));
    //System.out.println ("correct segments OOV="+numCorrectSegmentsOOV+" IV="+numCorrectSegmentsInAlphabet);
    //System.out.println ("incorrect segments OOV="+numIncorrectSegmentsOOV+" IV="+numIncorrectSegmentsInAlphabet);
  }
}
