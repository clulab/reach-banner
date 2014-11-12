package banner;

import banner.processing.PostProcessor;
import banner.tagging.CRFTagger;
import banner.tagging.Mention;
import banner.tokenization.Tokenizer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A wrapper for the Banner NER, using default options
 * User: mihais
 * Date: 10/29/14
 */
public class BannerWrapper {
  Tokenizer tokenizer;

  CRFTagger tagger;

  PostProcessor postProcessor;

  public BannerWrapper() {
    try {
      Properties props = mkDefaultProps();
      BannerProperties properties = BannerProperties.load(props);
      tokenizer = properties.getTokenizer();
      tagger = CRFTagger.load(new File(props.getProperty("model")), properties.getLemmatiser(), properties.getPosTagger());
      postProcessor = properties.getPostProcessor();
    } catch(Exception e) {
      // e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public List<Mention> tag(String sentenceText) {
    String originalText = new String(sentenceText);
    Sentence sentence = new Sentence(sentenceText);
    tokenizer.tokenize(sentence);

    //System.out.println("original text: " + sentenceText);
    //System.out.println("Sentence text: " + sentence.getText());
    //for(Token t: sentence.getTokens()) {
    //  System.out.println("\t" + t.getText() + " " + t.getStart());
    //}

    tagger.tag(sentence);
    if (postProcessor != null)
      postProcessor.postProcess(sentence);
    // make sure the text of the sentence did not change!
    //   if it did, it is impossible to align the mentions with the original text...
    if(! originalText.equals(sentence.getText())) {
      throw new RuntimeException("ERROR: input sentence [" + originalText + "] is different from sentence output by Banner [" + sentence.getText() + "]!" );
    }
    return sentence.getMentions();
  }

  private static final String BANNER_ENV = "BANNER_DATA";
  private static final String BANNER_MODEL = "banner_model.dat";

  static String findPath() {
    ArrayList<String> paths = new ArrayList<String>();

    String bannerData = System.getenv(BANNER_ENV);
    if(bannerData != null) {
      System.err.println("BannerWrapper: $" + BANNER_ENV + " is set to " + bannerData + ". Will attempt to use this.");
      paths.add(bannerData);
    }

    String homeDir = System.getProperty("user.home");
    if(homeDir != null) {
      paths.add(homeDir + "/github/banner/banner_data");
      paths.add(homeDir + "/banner/banner_data");
      paths.add("/usr/local/banner/banner_data");
      paths.add("/opt/banner/banner_data");
    }

    for(String path: paths) {
      if(new File(path + "/" + BANNER_MODEL).exists()) {
        System.err.println("BannerWrapper: " + path + " seems like a valid data path. I will use it.");
        return path;
      }
    }

    throw new RuntimeException("BannerWrapper ERROR: could not find a valid data directory! Set $" + BANNER_ENV + " to a valid location or use one of the defaults, e.g., ~/github/banner/banner_data/");
  }

  static Properties mkDefaultProps() {
    Properties props = new Properties();

    String bannerData = findPath();
    props.setProperty("lemmatiserDataDirectory", bannerData + "/nlpdata/lemmatiser");
    props.setProperty("posTaggerDataDirectory", bannerData + "/nlpdata/tagger");
    props.setProperty("posTagger", "dragon.nlp.tool.HeppleTagger");
    props.setProperty("tokenizer", "banner.tokenization.SimpleTokenizer");
    props.setProperty("tagFormat", "IOB");
    props.setProperty("useNumericNormalization", "true");
    props.setProperty("useParenthesisPostProcessing", "true");
    props.setProperty("order", "2");
    props.setProperty("dictionary", bannerData + "/dict/single.txt");
    props.setProperty("regexFilename", bannerData + "/regex.txt");
    props.setProperty("useFeatureInduction", "false");
    props.setProperty("textDirection", "Forward");

    props.setProperty("model", bannerData + "/" + BANNER_MODEL);

    return props;
  }
}
