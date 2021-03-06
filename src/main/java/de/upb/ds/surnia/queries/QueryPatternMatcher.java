package de.upb.ds.surnia.queries;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.upb.ds.surnia.preprocessing.model.Token;
import de.upb.ds.surnia.util.SurniaUtil;
import org.apache.jena.query.ParameterizedSparqlString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class QueryPatternMatcher {

  public static final float QUERY_RANKING_THRESHOlD = 0.5f;
  static final Logger logger = LoggerFactory.getLogger(QueryPatternMatcher.class);
  private List<QueryTemplate> queryTemplates;

  /**
   * Parses all queryTemplates from a given file.
   *
   * @param queryTemplatesFileName Name of the query file.
   */
  public QueryPatternMatcher(String queryTemplatesFileName) {
    queryTemplates = new LinkedList<>();
    try {
      // Read all prepared queryTemplates from the JSON file.
      String queryTemplatesFile = getClass().getClassLoader().getResource(queryTemplatesFileName)
        .getFile();
      BufferedReader queryTemplateFileReader = new BufferedReader(
        new FileReader(queryTemplatesFile));
      String line;
      StringBuilder jsonStringBuilder = new StringBuilder();
      while ((line = queryTemplateFileReader.readLine()) != null) {
        jsonStringBuilder.append(line);
      }
      if (jsonStringBuilder.length() > 0) {
        ObjectMapper mapper = new ObjectMapper();
        queryTemplates = mapper
          .readValue(jsonStringBuilder.toString(), new TypeReference<ArrayList<QueryTemplate>>() {
          });
      }
      queryTemplateFileReader.close();
    } catch (Exception e) {
      logger.error("{}", e.getLocalizedMessage());
    }
  }

  /**
   * Find all queryTemplates that were rated above the threshold for the given question.
   *
   * @param questionTokens Tokens of the question with the analysis of the pre-processing pipeline.
   * @return A list with all parameterized SPARQL queryTemplates with a good rating.
   */
  public Map<Float, List<ParameterizedSparqlString>> findMatchingQueries(List<Token> questionTokens) {
    float bufferRanking = 0.000f; // This is used to break tie between queries; Breaking tie is necessary to process all queries
    QuestionProperties questionProperties = new QuestionProperties(questionTokens);
    logger.info("{}", questionProperties);
    Map<Float, List<ParameterizedSparqlString>> possibleQueries = new TreeMap<>(Collections.reverseOrder());
    for (QueryTemplate queryTemplate : queryTemplates) {
      bufferRanking = bufferRanking + 0.001f;
      String bestQuestionTempalateRawString = rateQuery(questionProperties, queryTemplate);
      if (null != bestQuestionTempalateRawString) {
        String[] bestQuestionTemplateTokenArray = bestQuestionTempalateRawString.split("-");
        String bestQuestionTemplate = bestQuestionTemplateTokenArray[0];
        QueryParameterReplacer queryParameterReplacer = new QueryParameterReplacer(questionTokens,
          bestQuestionTemplate,
          queryTemplate);
        List<ParameterizedSparqlString> queriesWithReplacedParameters = queryParameterReplacer.getQueriesWithReplacedParameters();
        String queryRanking = bestQuestionTemplateTokenArray[1];
        if (null != queriesWithReplacedParameters && null != queryRanking) {
          possibleQueries.put(Float.parseFloat(queryRanking) + bufferRanking, queriesWithReplacedParameters);
        }
      }
    }
    logger.debug("QueryTemplate amount: {}", possibleQueries.size());
    return possibleQueries;
  }

  /**
   * Rate a query according to the properties of the given question.
   *
   * @param questionProperties Analyzed properties of the input question.
   * @param queryTemplate      A query template from the prepared query set.
   * @return A ranking for the query regarding the question between 0 and 1.
   */
  private String rateQuery(QuestionProperties questionProperties, QueryTemplate queryTemplate) {
    String questionStartWord = questionProperties.getQuestionStart();
    if (!Arrays.asList(queryTemplate.getQuestionStartWord()).contains(questionStartWord)) {
      logger.debug("Wrong question word");
      return null;
    }
    if (queryTemplate.containsSuperlative() && !questionProperties.containsSuperlative()) {
      logger.debug("Inconsistent superlative");
      return null;
    }
    // Commented out for now, as these conditions will never be met with the new implementation of tasks.
//    if (query.resourceAmount > questionProperties.resourceAmount) {
//      logger.info("Not enough properties");
//      return null;
//    }
//    if (query.ontologyAmount > questionProperties.ontologyAmount) {
//      logger.info("Not enough ontologies");
//      return null;
//    }

    double max = 0.0f;
    String bestFitQuestion = "";
    for (String questionTemplate : queryTemplate.getExampleQuestions()) {
      double similarity = SurniaUtil.stringSimilarity(questionTemplate,
        questionProperties.getRepresentationForm());
      if (similarity > max) {
        max = similarity;
        bestFitQuestion = questionTemplate;
      }
    }
    if (max >= QUERY_RANKING_THRESHOlD) {
      logger.info("{} - {}: {}", bestFitQuestion, questionProperties.getRepresentationForm(), max);
      return String.format("%s - %f", bestFitQuestion, max);
    } else {
      logger.warn("Similarity too low. Maximum is only {}", max);
      return null;
    }
  }
}
