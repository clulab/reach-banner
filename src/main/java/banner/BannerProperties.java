/**
 * 
 */
package banner;

import java.io.FileInputStream;
import java.io.FileReader;
import java.util.Properties;

import banner.processing.ParenthesisPostProcessor;
import banner.processing.PostProcessor;
import banner.tagging.DictionaryTagger;
import banner.tagging.MentionType;
import banner.tagging.TaggedToken.TagFormat;
import banner.tokenization.SimpleTokenizer;
import banner.tokenization.Tokenizer;
import bc2.GeneDictionaryTagger;
import dragon.nlp.tool.HeppleTagger;
import dragon.nlp.tool.MedPostTagger;
import dragon.nlp.tool.Tagger;
import dragon.nlp.tool.lemmatiser.EngLemmatiser;

public class BannerProperties
{

	public enum TextDirection
	{
		Forward, Reverse, Union, Intersection, Longer, Shorter;

		public boolean doForward()
		{
			return this != Reverse;
		}

		public boolean doReverse()
		{
			return this != Forward;
		}
	}

	private EngLemmatiser lemmatiser;
	private Tagger posTagger;
	private Tokenizer tokenizer;
	private TagFormat tagFormat;
	private PostProcessor postProcessor;
	private boolean useNumericNormalization;
	private int order;
	private boolean useFeatureInduction;
	private TextDirection textDirection;
	private banner.tagging.Tagger preTagger;
	private String regexFilename;

	private BannerProperties()
	{
		// Empty
	}

	/**
	 * Loads the properties file from the specified filename, and instantiates
	 * any objects to be used, such as the lemmatiser and part-of-speech (pos)
	 * tagger
	 * 
	 * @param filename
	 * @return An instance of {@link BannerProperties} which can be queried for
	 *         configuration parameters
	 */
	public static BannerProperties load(String filename)
	{

		Properties properties = new Properties();
		BannerProperties bannerProperties = new BannerProperties();
		try
		{
			FileInputStream propertiesInputStream = new FileInputStream(filename);
			properties.load(propertiesInputStream);
			propertiesInputStream.close();
			String lemmatiserDataDirectory = properties.getProperty("lemmatiserDataDirectory");
			if (lemmatiserDataDirectory != null)
				bannerProperties.lemmatiser = new EngLemmatiser(lemmatiserDataDirectory, false, true);
			String posTaggerDataDirectory = properties.getProperty("posTaggerDataDirectory");
			if (posTaggerDataDirectory != null)
			{
				String posTagger = properties.getProperty("posTagger", HeppleTagger.class.getName());
				if (posTagger.equals(HeppleTagger.class.getName()))
					bannerProperties.posTagger = new HeppleTagger(posTaggerDataDirectory);
				else if (posTagger.equals(MedPostTagger.class.getName()))
					bannerProperties.posTagger = new MedPostTagger(posTaggerDataDirectory);
				else
					throw new IllegalArgumentException("Unknown POS tagger type: " + posTagger);
			}
			String tokenizer = properties.getProperty("tokenizer", SimpleTokenizer.class.getName());
			bannerProperties.tokenizer = (Tokenizer) Class.forName(tokenizer).newInstance();
			// Note assumption that the tokenizer constructor takes no
			// parameters
			bannerProperties.tagFormat = TagFormat.valueOf(properties.getProperty("tagFormat", "IOB"));
			if (Boolean.parseBoolean(properties.getProperty("useParenthesisPostProcessing", "true")))
				bannerProperties.postProcessor = new ParenthesisPostProcessor();
			bannerProperties.useNumericNormalization = Boolean.parseBoolean(properties.getProperty("useNumericNormalization", "true"));
			bannerProperties.order = Integer.parseInt(properties.getProperty("order", "2"));
			bannerProperties.useFeatureInduction = Boolean.parseBoolean(properties.getProperty("useFeatureInduction", "false"));
			bannerProperties.textDirection = TextDirection.valueOf(properties.getProperty("textDirection", "Forward"));
			String dictionaryFileName = properties.getProperty("dictionary");
			if (dictionaryFileName != null)
			{
				// FIXME This is a temporary hack
				DictionaryTagger dictTagger = new GeneDictionaryTagger(bannerProperties.tokenizer, true);
				FileReader reader = new FileReader(dictionaryFileName);
				dictTagger.add(reader, MentionType.getType("GENE"));
				reader.close();
				System.out.println("Dict size - " + dictTagger.size());
				bannerProperties.preTagger = dictTagger;
			}
			bannerProperties.regexFilename = properties.getProperty("regexFilename");
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		return bannerProperties;
	}

	/**
	 * @return The {@link TextDirection} to use for training and tagging,
	 *         default is Forward (only)
	 */
	public TextDirection getTextDirection()
	{
		return textDirection;
	}

	/**
	 * @return The lemmatiser ({@link EngLemmatiser}) to use for training and
	 *         tagging
	 */
	public EngLemmatiser getLemmatiser()
	{
		return lemmatiser;
	}

	/**
	 * @return The CRF order to use for training and tagging. Valid values are 1
	 *         or 2, default is 2
	 */
	public int getOrder()
	{
		return order;
	}

	/**
	 * @return The part-of-speech {@link Tagger} to use for training and
	 *         tagging.
	 */
	public Tagger getPosTagger()
	{
		return posTagger;
	}

	/**
	 * @return The instance of {@link ParenthesisPostProcessor} to use for
	 *         training and tagging, or <code>null</code> if it should not be
	 *         used
	 */
	public PostProcessor getPostProcessor()
	{
		return postProcessor;
	}

	/**
	 * @return The {@link TagFormat} (IO/IOB/IOBEW) which should be used for
	 *         training and tagging. Default is IOB
	 */
	public TagFormat getTagFormat()
	{
		return tagFormat;
	}

	/**
	 * @return The tokenizer to use for training and tagging. Default is
	 *         {@link SimpleTokenizer}
	 */
	public Tokenizer getTokenizer()
	{
		return tokenizer;
	}

	/**
	 * @return Whether or not to use feature induction
	 */
	public boolean isUseFeatureInduction()
	{
		return useFeatureInduction;
	}

	/**
	 * @return Whether or not to include numeric normalization features
	 */
	public boolean isUseNumericNormalization()
	{
		return useNumericNormalization;
	}

	/**
	 * @return The dictionary tagger whose output will be used as features for
	 *         the CRF model
	 */
	public banner.tagging.Tagger getPreTagger()
	{
		return preTagger;
	}

	/**
	 * @return A String containing the filename of the regular expression
	 *         (regex) pattern file
	 */
	public String getRegexFilename()
	{
		return regexFilename;
	}

	/**
	 * Outputs the settings for this configuration to the console, very useful
	 * for ensuring the configuration is set as desired prior to a training run
	 */
	public void log()
	{
		System.out.println("Lemmatiser: " + (lemmatiser == null ? null : lemmatiser.getClass().getName()));
		System.out.println("POSTagger: " + (posTagger == null ? null : posTagger.getClass().getName()));
		System.out.println("Tokenizer: " + tokenizer.getClass().getName());
		System.out.println("Tag format: " + tagFormat.name());
		System.out.println("PostProcessor: " + (postProcessor == null ? null : postProcessor.getClass().getName()));
		System.out.println("Using numeric normalization: " + useNumericNormalization);
		System.out.println("CRF order is " + order);
		System.out.println("Using feature induction: " + useFeatureInduction);
		System.out.println("Text textDirection: " + textDirection);
		System.out.println("Regex filename: " + regexFilename);
		System.out.println("Dictionary: " + (preTagger == null ? null : preTagger.getClass().getName()));
	}

}