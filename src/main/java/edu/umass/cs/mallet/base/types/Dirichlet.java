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

import edu.umass.cs.mallet.base.types.Multinomial;
import edu.umass.cs.mallet.base.util.Maths;
import edu.umass.cs.mallet.base.util.Random;
import java.util.ArrayList;

// xxx Make this a subclass of DenseFeatureVector?
// Yes!

public class Dirichlet
{
	double alphas[];
	Alphabet dict;

	public Dirichlet (double[] alphas, Alphabet dict)
	{
		if (dict != null && alphas.length != dict.size())
			throw new IllegalArgumentException ("alphas and dict sizes do not match.");
		this.alphas = alphas;
		this.dict = dict;
		if (dict != null)
			dict.stopGrowth();
	}

	public Dirichlet (double[] alphas)
	{
		this.alphas = alphas;
		this.dict = null;
	}

	public Dirichlet (Alphabet dict)
	{
		this (dict, 1.0);
	}
	
	public Dirichlet (Alphabet dict, double alpha)
	{
		this(dict.size(), alpha);
		this.dict = dict;
		dict.stopGrowth();
	}

	public Dirichlet (int size)
	{
		this (size, 1.0);
	}

	public Dirichlet (int size, double alpha)
	{
		this.alphas = new double[size];
		for (int i = 0; i < size; i++)
			alphas[i] = alpha;
	}

	public Alphabet getAlphabet ()
	{
		return dict;
	}

	public int size ()
	{
		return alphas.length;
	}

	public double alpha (int featureIndex)
	{
		return alphas[featureIndex];
	}

	public void print () {
		if (alphas != null) throw new IllegalStateException ("foo");
		System.out.println ("Dirichlet:");
		for (int j = 0; j < alphas.length; j++)
			System.out.println (dict!= null ? dict.lookupObject(j).toString() : j + "=" + alphas[j]);
	}

	protected double[] randomRawMultinomial (Random r)
	{
		double sum = 0;
		double[] pr = new double[this.alphas.length];
		for (int i = 0; i < this.alphas.length; i++) {
//			if (alphas[i] < 0)
//				for (int j = 0; j < alphas.length; j++)
//					System.out.println (dict.lookupSymbol(j).toString() + "=" + alphas[j]);
			pr[i] = r.nextGamma(alphas[i]);
			sum += pr[i];
		}
		for (int i = 0; i < this.alphas.length; i++)
			pr[i] /= sum;
		return pr;
	}

	public Multinomial randomMultinomial (Random r)
	{
		return new Multinomial (randomRawMultinomial(r), dict, alphas.length, false, false);
	}
	
	public Dirichlet randomDirichlet (Random r, double averageAlpha)
	{
		double[] pr = randomRawMultinomial (r);
		double alphaSum = pr.length*averageAlpha;
		//System.out.println ("randomDirichlet alphaSum = "+alphaSum);
		for (int i = 0; i < pr.length; i++)
			pr[i] *= alphaSum;
		return new Dirichlet (pr, dict);
	}

	public FeatureSequence randomFeatureSequence (Random r, int length)
	{
		Multinomial m = randomMultinomial (r);
		return m.randomFeatureSequence (r, length);
	}

	public FeatureVector randomFeatureVector (Random r, int size)
	{
		return new FeatureVector (this.randomFeatureSequence (r, size));
	}

	public TokenSequence randomTokenSequence (Random r, int length)
	{
		FeatureSequence fs = randomFeatureSequence (r, length);
		TokenSequence ts = new TokenSequence (length);
		for (int i = 0; i < length; i++)
			ts.add (fs.getObjectAtPosition(i));
		return ts;
	}

  public double[] randomVector (Random r)
  {
    return randomRawMultinomial (r);
  }


  public static abstract class Estimator
	{
		ArrayList multinomials;

		public Estimator ()
		{
			this.multinomials = new ArrayList();
		}

		public Estimator (ArrayList multinomials)
		{
			this.multinomials = multinomials;
			for (int i = 1; i < multinomials.size(); i++)
				if (((Multinomial)multinomials.get(i-1)).size()
						!= ((Multinomial)multinomials.get(i)).size()
					|| ((Multinomial)multinomials.get(i-1)).getAlphabet()
						!= ((Multinomial)multinomials.get(i)).getAlphabet())
					throw new IllegalArgumentException
						("All multinomials must have same size and Alphabet.");
		}

		public void addMultinomial (Multinomial m)
		{
			// xxx Assert that it is the right class and size
			multinomials.add (m);
		}

		public abstract Dirichlet estimate ();
		
	}

	public static class MethodOfMomentsEstimator extends Estimator
	{
		public Dirichlet estimate ()
		{
			Dirichlet d = new Dirichlet (((Multinomial)multinomials.get(0)).size());
			for (int i = 1; i < multinomials.size(); i++)
				((Multinomial)multinomials.get(i)).addProbabilitiesTo(d.alphas);
			double alphaSum = 0;
			for (int i = 0; i < d.alphas.length; i++)
				alphaSum += d.alphas[i];
			for (int i = 0; i < d.alphas.length; i++)
				d.alphas[i] /= alphaSum;  // xxx Fix this to set sum by variance matching
			throw new UnsupportedOperationException ("Not yet implemented.");
			//return d;
		}
		
	}


}
