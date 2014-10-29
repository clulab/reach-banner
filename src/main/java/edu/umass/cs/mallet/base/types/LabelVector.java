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

import edu.umass.cs.mallet.base.types.Label;
import edu.umass.cs.mallet.base.types.RankedFeatureVector;

public class LabelVector extends RankedFeatureVector implements Labeling
{
	public LabelVector (LabelAlphabet dict,
											int[] features,
											double[] values)
	{
		super (dict, features, values);
	}

	private static int[] indicesForLabels (Label[] labels)
	{
		int[] indices = new int[labels.length];
		for (int i = 0; i < labels.length; i++)
			indices[i] = labels[i].getIndex();
		return indices;
	}

	public LabelVector (Label[] labels,
											double[] values)
	{
		super (labels[0].dictionary, indicesForLabels(labels), values);
	}

	public LabelVector (LabelAlphabet dict, double[] values)
	{
		super (dict, values);
	}

	public final Label labelAtLocation (int loc)
	{
		return ((LabelAlphabet)dictionary).lookupLabel(indexAtLocation (loc));
	}

	public LabelAlphabet getLabelAlphabet ()
	{
		return (LabelAlphabet) dictionary;
	}


	// Labeling interface

	// xxx Change these names to better match RankedFeatureVector?

	public int getBestIndex ()
	{
		if (rankOrder == null)
			setRankOrder ();
		return rankOrder[0];
	}

	public Label getBestLabel ()
	{
		return ((LabelAlphabet)dictionary).lookupLabel (getBestIndex());
	}

	public double getBestValue ()
	{
		if (rankOrder == null)
			setRankOrder ();
		return values[rankOrder[0]];
	}

	public double value (Label label)
	{
		assert (label.dictionary  == this.dictionary);
		return values[this.location (label.toString ())];
	}

	public int getRank (Label label)
	{
		throw new UnsupportedOperationException ();
	}

	public int getRank (int labelIndex)
	{
		throw new UnsupportedOperationException ();
	}

	public Label getLabelAtRank (int rank)
	{
		if (rankOrder == null)
			setRankOrder ();
		return ((LabelAlphabet)dictionary).lookupLabel (rankOrder[rank]);
	}

	public double getValueAtRank (int rank)
	{
		if (rankOrder == null)
			setRankOrder ();
		return values[rankOrder[rank]];
	}

	public LabelVector toLabelVector ()
	{
		return this;
	}


	// Inherited from FeatureVector or SparseVector
	// public void addTo (double[] values)
	// public void addTo (double[] values, double scale)
	// public int numLocations ();
	// public double valueAtLocation (int loc)

	
}
