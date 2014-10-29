/* 
 Copyright (c) 2007 Arizona State University, Dept. of Computer Science and Dept. of Biomedical Informatics.
 This file is part of the BANNER Named Entity Recognition System, http://banner.sourceforge.net
 This software is provided under the terms of the Common Public License, version 1.0, as published by http://www.opensource.org.  For further information, see the file 'LICENSE.txt' included with this distribution.
 */

package banner.tagging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import dragon.nlp.tool.Lemmatiser;

import edu.umass.cs.mallet.base.fst.CRF4;
import edu.umass.cs.mallet.base.fst.MultiSegmentationEvaluator;
import edu.umass.cs.mallet.base.fst.Transducer.State;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.pipe.SerialPipes;
import edu.umass.cs.mallet.base.pipe.TokenSequence2FeatureVectorSequence;
import edu.umass.cs.mallet.base.pipe.tsf.OffsetConjunctions;
import edu.umass.cs.mallet.base.pipe.tsf.RegexMatches;
import edu.umass.cs.mallet.base.pipe.tsf.TokenTextCharNGrams;
import edu.umass.cs.mallet.base.pipe.tsf.TokenTextCharPrefix;
import edu.umass.cs.mallet.base.pipe.tsf.TokenTextCharSuffix;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.FeatureVectorSequence;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.types.LabelSequence;
import edu.umass.cs.mallet.base.types.Sequence;
import edu.umass.cs.mallet.base.types.SparseVector;
import banner.Sentence;
import banner.BannerProperties.TextDirection;
import banner.tagging.TaggedToken.TagFormat;
import banner.tagging.TaggedToken.TagPosition;

public class CRFTagger implements Tagger
{

	// TODO Add support for TextDirection.Union and TextDirection.Intersection

	private static String GREEK = "(alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega)";
	private CRF4 forwardCRF;
	private CRF4 reverseCRF;
	private String2TokenSequencePipe basePipe;
	private int order;
	private boolean useFeatureInduction;
	private TagFormat format;
	private TextDirection textDirection;

	private CRFTagger(CRF4 forwardCRF, CRF4 reverseCRF, String2TokenSequencePipe basePipe, int order, boolean useFeatureInduction, TagFormat format, TextDirection textDirection)
	{
		// TODO Verify crf==null matches textDirection
		this.forwardCRF = forwardCRF;
		this.reverseCRF = reverseCRF;
		this.basePipe = basePipe;
		this.order = order;
		this.useFeatureInduction = useFeatureInduction;
		this.format = format;
		this.textDirection = textDirection;
	}

	/**
	 * Loads a {@link CRFTagger} from the specified file. As the lemmatiser and
	 * part-of-speech tagger both require data, these cannot be written to disk
	 * and must be passed in new.
	 * 
	 * @param f
	 *            The file to load the CRFTagger from, as written by the {@link}
	 *            write() method.
	 * @param lemmatiser
	 *            The {@link Lemmatiser} to use
	 * @param posTagger
	 *            The part-of-speech {@link dragon.nlp.tool.Tagger} to use
	 * @param preTagger
	 *            The pre-tagger (such as a dictionary) to use
	 * @throws IOException
	 * @return A new instance of the CRFTagger contained in the specified file
	 */
	public static CRFTagger load(File f, Lemmatiser lemmatiser, dragon.nlp.tool.Tagger posTagger, Tagger preTagger) throws IOException
	{
		try
		{
			ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(f)));
			// ObjectInputStream ois = new ObjectInputStream((new
			// FileInputStream(f)));
			TextDirection textDirection = (TextDirection) ois.readObject();
			CRF4 forwardCRF = null;
			if (textDirection.doForward())
				forwardCRF = (CRF4) ois.readObject();
			CRF4 reverseCRF = null;
			if (textDirection.doReverse())
				reverseCRF = (CRF4) ois.readObject();
			String2TokenSequencePipe basePipe = (String2TokenSequencePipe) ois.readObject();
			basePipe.setLemmatiser(lemmatiser);
			basePipe.setPosTagger(posTagger);
			basePipe.setPreTagger(preTagger);
			int order = ois.readInt();
			boolean useFeatureInduction = ois.readBoolean();
			TagFormat format = (TagFormat) ois.readObject();
			ois.close();
			return new CRFTagger(forwardCRF, reverseCRF, basePipe, order, useFeatureInduction, format, textDirection);
		} catch (ClassNotFoundException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Loads a {@link CRFTagger} from the specified file. As the lemmatiser and
	 * part-of-speech tagger both require data, these cannot be written to disk
	 * and must be passed in new. This version of the method assumes no
	 * preTagger; it is primarily intended for backwards compatibility.
	 * 
	 * @param f
	 *            The file to load the CRFTagger from, as written by the {@link}
	 *            write() method.
	 * @param lemmatiser
	 *            The {@link Lemmatiser} to use
	 * @param posTagger
	 *            The part-of-speech {@link dragon.nlp.tool.Tagger} to use
	 * @throws IOException
	 * @return A new instance of the CRFTagger contained in the specified file
	 */
	public static CRFTagger load(File f, Lemmatiser lemmatiser, dragon.nlp.tool.Tagger posTagger) throws IOException
	{
		return load(f, lemmatiser, posTagger, null);
	}

	private static void setupPipes(ArrayList<Pipe> pipes, String regexFilename)
	{
		try
		{
			if (regexFilename != null)
				pipes.add(new ConfigurableRegexMatches(regexFilename));
		} catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		pipes.add(new TokenTextCharPrefix("2PREFIX=", 2));
		pipes.add(new TokenTextCharPrefix("3PREFIX=", 3));
		pipes.add(new TokenTextCharPrefix("4PREFIX=", 4));
		pipes.add(new TokenTextCharSuffix("2SUFFIX=", 2));
		pipes.add(new TokenTextCharSuffix("3SUFFIX=", 3));
		pipes.add(new TokenTextCharSuffix("4SUFFIX=", 4));
		pipes.add(new TokenTextCharNGrams("CHARNGRAM=", new int[] { 2, 3 }, true));
		// pipes.add(new LexiconMembership()); // Use this for determining
		// whether word in a lexicon
		pipes.add(new RegexMatches("ROMAN", Pattern.compile("[IVXDLCM]+", Pattern.CASE_INSENSITIVE)));
		pipes.add(new RegexMatches("GREEK", Pattern.compile(GREEK, Pattern.CASE_INSENSITIVE)));
		pipes.add(new RegexMatches("ISPUNCT", Pattern.compile("[`~!@#$%^&*()-=_+\\[\\]\\\\{}|;\':\\\",./<>?]+")));
		pipes.add(new OffsetConjunctions(new int[][] { { -2 }, { -1 }, { 1 }, { 2 } }));
		pipes.add(new TokenSequence2FeatureVectorSequence(true, true));
	}

	/**
	 * Trains and returns a {@link CRFTagger} on the specified {@link Sentence}
	 * s. This method may take hours or even days to complete. When training,
	 * you will likely need to increase the amount of memory used by the Java
	 * virtual machine (try adding "-Xms1024m" to the command line).
	 * 
	 * @param sentences
	 *            The {@link Sentence}s to train the tagger on
	 * @param order
	 *            The CRF order to use
	 * @param useFeatureInduction
	 *            Whether or not to use feature induction
	 * @param format
	 *            The {@link TagFormat} to use
	 * @param textDirection
	 *            The {@link TextDirection} to use
	 * @param lemmatiser
	 *            The {@link Lemmatiser} to use
	 * @param posTagger
	 *            The part-of-speech {@link dragon.nlp.tool.Tagger} to use
	 * @param useNumericalNormalization
	 *            Whether to use numeric normalization
	 * @return A trained CRFTagger; ready to tag unseen sentences or be output
	 *         to disk
	 */
	public static CRFTagger train(List<Sentence> sentences, int order, boolean useFeatureInduction, TagFormat format, TextDirection textDirection, Lemmatiser lemmatiser,
			dragon.nlp.tool.Tagger posTagger, boolean useNumericalNormalization, Tagger preTagger, String regexFilename)
	{
		if (sentences.size() == 0)
			throw new RuntimeException("Number of sentences must be greater than zero");
		String2TokenSequencePipe localBasePipe = new String2TokenSequencePipe(lemmatiser, posTagger, useNumericalNormalization, preTagger);
		ArrayList<Pipe> pipes = new ArrayList<Pipe>();
		pipes.add(localBasePipe);
		setupPipes(pipes, regexFilename);
		Pipe pipe = new SerialPipes(pipes);
		CRF4 forwardCRF = null;
		if (textDirection == TextDirection.Intersection)
			throw new UnsupportedOperationException("TextDirection.Intersection not yet supported");
		if (textDirection.doForward())
			forwardCRF = train(sentences, order, useFeatureInduction, format, pipe, false);
		CRF4 reverseCRF = null;
		if (textDirection.doReverse())
			reverseCRF = train(sentences, order, useFeatureInduction, format, pipe, true);
		return new CRFTagger(forwardCRF, reverseCRF, localBasePipe, order, useFeatureInduction, format, textDirection);
	}

	private static CRF4 train(List<Sentence> sentences, int order, boolean useFeatureInduction, TagFormat format, Pipe pipe, boolean reverse)
	{
		InstanceList instances = new InstanceList(pipe);
		for (Sentence sentence : sentences)
		{
			Instance instance = new Instance(sentence.getTokenText(), sentence.getTokenLabels(format), sentence.getTag(), null, pipe);
			instances.add(instance);
			if (!(instance.getData() instanceof FeatureVectorSequence))
			{
				String err = "Instance data should be of type FeatureVectorSequence";
				err += ", type is " + instance.getData().getClass().getCanonicalName();
				err += ", content is " + instance.getData();
				err += ", original sentence=" + sentence.getText();
				err += ", labels=" + sentence.getTokenLabels(format);
				throw new IllegalStateException(err);
			}
			if (!(instance.getTarget() instanceof LabelSequence))
			{
				String err = "Instance target should be of type LabelsSequence";
				err += ", type is " + instance.getTarget().getClass().getCanonicalName();
				err += ", content is " + instance.getTarget();
				err += ", original sentence=" + sentence.getText();
				err += ", labels=" + sentence.getTokenLabels(format);
				throw new IllegalStateException(err);
			}
		}
		CRF4 crf = new CRF4(pipe, null);
		if (order == 1)
			crf.addStatesForLabelsConnectedAsIn(instances);
		else if (order == 2)
			crf.addStatesForBiLabelsConnectedAsIn(instances);
		else
			throw new IllegalArgumentException("Order must be equal to 1 or 2");
		if (useFeatureInduction)
			crf.trainWithFeatureInduction(instances, null, null, null, 99999, 100, 10, 1000, 0.5, false, new double[] { .2, .5, .8 });
		else
			crf.train(instances, null, null, (MultiSegmentationEvaluator) null, 99999, 10, new double[] { .2, .5, .8 });
		return crf;
	}

	/**
	 * Serializes and writes this CRFTagger to the specified file
	 * 
	 * @param f
	 *            The file to write this CRFTagger to
	 */
	public void write(File f)
	{
		try
		{
			ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(f)));
			oos.writeObject(textDirection);
			if (textDirection.doForward())
				oos.writeObject(forwardCRF);
			if (textDirection.doReverse())
				oos.writeObject(reverseCRF);
			oos.writeObject(basePipe);
			oos.writeInt(order);
			oos.writeBoolean(useFeatureInduction);
			oos.writeObject(format);
			oos.close();
		} catch (IOException e)
		{
			System.err.println("Exception writing file " + f + ": " + e);
		}
	}

	public void describe(String fileName) throws IOException
	{
		System.out.println("Number of default weights = " + forwardCRF.getDefaultWeights().length);
		System.out.println("Number of states = " + forwardCRF.numStates());
		for (int i = 0; i < forwardCRF.numStates(); i++)
		{
			State state = forwardCRF.getState(i);
			System.out.println("State " + i + " is " + state.getName());
		}
		SparseVector[] weights = forwardCRF.getWeights();
		System.out.println("Size of weights vector = " + weights.length);
		for (int i = 0; i < weights.length; i++)
			System.out.println("Number of non-zero values for weight vector " + i + " is " + weights[i].numLocations());
		int size = forwardCRF.getInputAlphabet().size();
		System.out.println("Size of input alphabet: " + size);
		PrintWriter output = new PrintWriter(fileName);
		for (int i = 0; i < size; i++)
		{
			String featureName = forwardCRF.getInputAlphabet().lookupObject(i).toString();
			// System.out.println("featureName=" + featureName);
			int equalsIndex = featureName.indexOf("=");
			// System.out.println("equalsIndex=" + equalsIndex);
			int atIndex = featureName.indexOf("@");
			// System.out.println("atIndex=" + atIndex);
			int featureTypeEnd = featureName.length();
			if (equalsIndex != -1 && equalsIndex < featureTypeEnd)
				featureTypeEnd = equalsIndex;
			if (atIndex != -1 && atIndex < featureTypeEnd)
				featureTypeEnd = atIndex;
			String featureType = featureName.substring(0, featureTypeEnd);
			// System.out.println("featureType=" + featureType);
			String featureOffset = "0";
			int featureDataEnd = featureName.length();
			if (atIndex != -1)
			{
				featureDataEnd = atIndex;
				featureOffset = featureName.substring(atIndex + 1, featureName.length());
			}
			// System.out.println("featureOffset=" + featureOffset);
			String featureData = "";
			if (featureDataEnd > featureTypeEnd)
				featureData = featureName.substring(featureTypeEnd + 1, featureDataEnd);
			// System.out.println("featureData=" + featureData);
			double max = Double.MIN_VALUE;
			double min = Double.MAX_VALUE;
			for (int j = 0; j < weights.length; j++)
			{
				double weight = weights[j].value(i);
				if (max < weight)
					max = weight;
				if (min > weight)
					min = weight;
			}
			output.println(i + "\t" + featureName + "\t" + featureType + "\t" + featureOffset + "\t" + featureData + "\t" + max + "\t" + min);
		}
		output.close();
	}

	public Map<String, Double> getMaxWeights()
	{
		Map<String, Double> weightMap = new HashMap<String, Double>();
		SparseVector[] weights = forwardCRF.getWeights();
		Alphabet inputAlphabet = forwardCRF.getInputAlphabet();
		int size = inputAlphabet.size();
		for (int i = 0; i < size; i++)
		{
			double max = Double.MIN_VALUE;
			for (int j = 0; j < weights.length; j++)
			{
				double weight = weights[j].value(i);
				if (max < weight)
					max = weight;
			}
			String featureName = inputAlphabet.lookupObject(i).toString();
			weightMap.put(featureName, max);
		}
		return weightMap;
	}

	public Map<String, Double> getMinWeights()
	{
		Map<String, Double> weightMap = new HashMap<String, Double>();
		SparseVector[] weights = forwardCRF.getWeights();
		Alphabet inputAlphabet = forwardCRF.getInputAlphabet();
		int size = inputAlphabet.size();
		for (int i = 0; i < size; i++)
		{
			double min = Double.MAX_VALUE;
			for (int j = 0; j < weights.length; j++)
			{
				double weight = weights[j].value(i);
				if (min > weight)
					min = weight;
			}
			String featureName = inputAlphabet.lookupObject(i).toString();
			weightMap.put(featureName, min);
		}
		return weightMap;
	}

	public void tag(Sentence sentence)
	{
		int size = sentence.getTokens().size();
		TagPosition[] forwardPositions = new TagPosition[size];
		MentionType[] forwardTypes = new MentionType[size];
		if (textDirection.doForward())
			getPositionsAndTypes(sentence, forwardPositions, forwardTypes, false);
		TagPosition[] reversePositions = new TagPosition[size];
		MentionType[] reverseTypes = new MentionType[size];
		if (textDirection.doReverse())
			getPositionsAndTypes(sentence, reversePositions, reverseTypes, true);

		if (textDirection == TextDirection.Forward)
		{
			sentence.addMentions(forwardPositions, forwardTypes);
		} else if (textDirection == TextDirection.Reverse)
		{
			sentence.addMentions(reversePositions, reverseTypes);
		} else if (textDirection == TextDirection.Longer)
		{
			// System.out.println();
			// System.out.println(sentence.getText());
			// System.out.println("Original mentions: " +
			// sentence.getMentions());
			// System.out.println(Arrays.toString(forwardPositions));
			// System.out.println(Arrays.toString(forwardTypes));
			// System.out.println(Arrays.toString(reversePositions));
			// System.out.println(Arrays.toString(reverseTypes));
			Set<Mention> mentionSet = new HashSet<Mention>();
			mentionSet.addAll(getMentions(sentence, forwardPositions, forwardTypes));
			mentionSet.addAll(getMentions(sentence, reversePositions, reverseTypes));
			// System.out.println("Mentions to resolve: " + mentionSet);
			while (mentionSet.size() > 0)
			{
				Mention current = mentionSet.iterator().next();
				mentionSet.remove(current);
				// System.out.println("Current mention is \"" +
				// current.getText() + "\" " + current.getStart() + "-" +
				// current.getEnd());
				int start = current.getStart();
				int end = current.getEnd();
				Mention longest = current;
				boolean changed = true;
				while (changed)
				{
					changed = false;
					Iterator<Mention> mentionIterator = mentionSet.iterator();
					while (mentionIterator.hasNext())
					{
						Mention next = mentionIterator.next();
						// System.out.println("Checking against \"" +
						// next.getText() + "\" " + next.getStart() + "-" +
						// next.getEnd());
						if (end > next.getStart() && start < next.getEnd())
						{
							// System.out.println("Overlaps with \"" +
							// next.getText() + "\" " + next.getStart() + "-" +
							// next.getEnd());
							mentionIterator.remove();
							// System.out.println("longest: " +
							// longest.length());
							// System.out.println("next: " + next.length());
							if (next.length() > longest.length())
							{
								start = Math.min(start, next.getStart());
								end = Math.max(end, next.getEnd());
								longest = next;
								changed = true;
							}
						}
					}
				}
				// System.out.println("Longest mention is \"" +
				// current.getText() + "\" " + current.getStart() + "-" +
				// current.getEnd());
				sentence.addMention(longest);
			}
			sentence.getTaggedTokens();
		} else if (textDirection == TextDirection.Union)
		{
			// System.out.println();
			// System.out.println(sentence.getText());
			// System.out.println("Original mentions: " +
			// sentence.getMentions());
			// System.out.println(Arrays.toString(forwardPositions));
			// System.out.println(Arrays.toString(forwardTypes));
			// System.out.println(Arrays.toString(reversePositions));
			// System.out.println(Arrays.toString(reverseTypes));
			TagPosition[] unionPositions = new TagPosition[size];
			MentionType[] unionTypes = new MentionType[size];
			for (int i = 0; i < size; i++)
			{
				if (forwardPositions[i] == TagPosition.O && reversePositions[i] == TagPosition.O)
					unionPositions[i] = TagPosition.O;
				else if (forwardPositions[i] == TagPosition.I || reversePositions[i] == TagPosition.I)
					unionPositions[i] = TagPosition.I;
				else
					unionPositions[i] = TagPosition.B;
				if (unionPositions[i] == TagPosition.O)
					unionTypes[i] = null;
				else if (forwardTypes[i] != null)
					unionTypes[i] = forwardTypes[i];
				else
					unionTypes[i] = reverseTypes[i];
			}
			// System.out.println(Arrays.toString(unionPositions));
			// System.out.println(Arrays.toString(unionTypes));
			sentence.addMentions(unionPositions, unionTypes);
			sentence.getTaggedTokens();
		}
	}

	public List<Mention> getMentions(Sentence sentence, TagPosition[] positions, MentionType[] types)
	{
		List<Mention> mentions = new ArrayList<Mention>();
		int startIndex = -1;
		for (int i = 0; i < positions.length; i++)
		{
			if (positions[i] == TagPosition.O)
			{
				if (startIndex != -1)
					mentions.add(new Mention(sentence, types[i - 1], startIndex, i));
				startIndex = -1;
			} else if (positions[i] == TagPosition.B)
			{
				if (startIndex != -1)
					mentions.add(new Mention(sentence, types[i - 1], startIndex, i));
				startIndex = i;
			} else if (positions[i] == TagPosition.W)
			{
				if (startIndex != -1)
					mentions.add(new Mention(sentence, types[i - 1], startIndex, i));
				startIndex = i;
			} else
			{
				if (startIndex == -1)
					startIndex = i;
			}
		}
		return mentions;
	}

	private static void reverse(Object[] array)
	{
		Object[] copy = new Object[array.length];
		System.arraycopy(array, 0, copy, 0, array.length);
		int offset = array.length - 1;
		for (int i = 0; i < array.length; i++)
			array[i] = copy[offset - i];
	}

	private void getPositionsAndTypes(Sentence sentence, TagPosition[] positions, MentionType[] types, boolean reverse)
	{
		Instance instance = new Instance(sentence.getTokenText(), sentence.getTokenLabels(format), sentence.getTag(), null, forwardCRF.getInputPipe());
		Sequence tags;
		if (reverse)
		{
			tags = reverseCRF.viterbiPath((Sequence) instance.getData()).output();
		} else
		{
			tags = forwardCRF.viterbiPath((Sequence) instance.getData()).output();
		}
		if (positions.length != tags.size())
			throw new IllegalArgumentException();
		if (types.length != tags.size())
			throw new IllegalArgumentException();
		for (int i = 0; i < tags.size(); i++)
		{
			// The tag string is e.g. "O" or "B-GENE"
			String[] split = tags.get(i).toString().split("-");
			positions[i] = TagPosition.valueOf(split[0]);
			// TODO Verify that the type stays the same
			if (split.length == 2)
				types[i] = MentionType.getType(split[1]);
		}
		if (reverse)
		{
			reverse(positions);
			// Modify the tag positions to make sense if read from forward
			// System.out.println("Before: " + Arrays.toString(positions));
			TagPosition[] positionsCopy = new TagPosition[positions.length];
			System.arraycopy(positions, 0, positionsCopy, 0, positions.length);
			for (int i = 0; i < positions.length; i++)
			{
				if (positionsCopy[i] == TagPosition.O)
					positions[i] = TagPosition.O;
				else if (i == 0 || positionsCopy[i - 1] == TagPosition.O || positionsCopy[i - 1] == TagPosition.B)
					positions[i] = TagPosition.B;
				else
					positions[i] = TagPosition.I;
			}
			// System.out.println(" After: " + Arrays.toString(positions));
			reverse(types);
		}
	}

	/**
	 * @return The {@link TagFormat} used by this tagger
	 */
	public TagFormat getFormat()
	{
		return format;
	}

	/**
	 * @return The CRF order used by this tagger. Order 1 means that the last
	 *         state is used and order 2 means that the last 2 states are used.
	 */
	public int getOrder()
	{
		return order;
	}

	/**
	 * @return Whether this {@link CRFTagger} was trained with feature induction
	 */
	public boolean isUseFeatureInduction()
	{
		return useFeatureInduction;
	}

	/**
	 * @return The {@link TextDirection} used by this {@link CRFTagger}
	 */
	public TextDirection getTextDirection()
	{
		return textDirection;
	}

	public void setTextDirection(TextDirection textDirection)
	{
		if (textDirection.doForward() && forwardCRF == null)
			throw new IllegalArgumentException("Forward CRF was not trained");
		if (textDirection.doReverse() && reverseCRF == null)
			throw new IllegalArgumentException("Reverse CRF was not trained");
		this.textDirection = textDirection;
	}

}
