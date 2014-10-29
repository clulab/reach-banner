/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.types;
import edu.umass.cs.mallet.base.fst.Segment;

public class SequencePair 
{
	protected Sequence input;
	protected Sequence output;

	//N-best implementation added by Fuchun Peng 
	protected Sequence[] outputNBest;//store the N-best list
	protected double[] costNBest;//store the costs of the N-best results
	protected double[] confidenceNBest;//store the confidence of the N-best results (normalized cost)

	public SequencePair (Sequence input, Sequence output)
	{
		this.input = input;
		this.output = output;
	}

	protected SequencePair ()
	{
	}

	public Sequence input() { return input; }
	public Sequence output() { return output; }
	
	public Sequence[] outputNBest() {return outputNBest;}
	public double[] costNBest(){return costNBest;}
	public double[] confidenceNBest(){return confidenceNBest;}
}
