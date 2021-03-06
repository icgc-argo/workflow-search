/*
 * Copyright (c) 2020 The Ontario Institute for Cancer Research. All rights reserved
 *
 * This program and the accompanying materials are made available under the terms of the GNU Affero General Public License v3.0.
 * You should have received a copy of the GNU Affero General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icgc_argo.workflow.search.repository;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.sort.SortOrder.DESC;
import static org.icgc_argo.workflow.search.model.SearchFields.*;
import static org.icgc_argo.workflow.search.util.ElasticsearchQueryUtils.queryFromArgs;
import static org.icgc_argo.workflow.search.util.ElasticsearchQueryUtils.sortsToEsSortBuilders;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.icgc_argo.workflow.search.config.elasticsearch.ElasticsearchProperties;
import org.icgc_argo.workflow.search.model.graphql.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
public class RunRepository {

  private static final List<String> ANALYSIS_SEARCH_FIELDS =
      List.of(
          "parameters.analysis_id",
          "parameters.normal_aln_analysis_id",
          "parameters.tumour_aln_analysis_id");
  private static final Map<String, Function<String, AbstractQueryBuilder<?>>> QUERY_RESOLVER =
      argumentPathMap();

  private static final Map<String, FieldSortBuilder> SORT_BUILDER_RESOLVER = sortPathMap();

  private final ReactiveElasticsearchClient client;
  private final String workflowIndex;

  @Autowired
  public RunRepository(
      @NonNull ReactiveElasticsearchClient client,
      @NonNull ElasticsearchProperties elasticsearchProperties) {
    this.client = client;
    this.workflowIndex = elasticsearchProperties.getWorkflowIndex();
  }

  private static Map<String, Function<String, AbstractQueryBuilder<?>>> argumentPathMap() {
    return ImmutableMap.<String, Function<String, AbstractQueryBuilder<?>>>builder()
        .put(RUN_ID, value -> new TermQueryBuilder("runId", value))
        .put(SESSION_ID, value -> new TermQueryBuilder("sessionId", value))
        .put(STATE, value -> new TermQueryBuilder("state", value))
        .put(
            ANALYSIS_ID,
            value -> {
              val q =
                  new MultiMatchQueryBuilder(value, ANALYSIS_SEARCH_FIELDS.toArray(String[]::new));
              q.minimumShouldMatch("100%");
              return q;
            })
        .put(
            REPOSITORY,
            value -> {
              val q = new MatchQueryBuilder("repository", value);
              q.operator(Operator.AND);
              q.minimumShouldMatch("80%");
              return q;
            })
        .build();
  }

  private static Map<String, FieldSortBuilder> sortPathMap() {
    return ImmutableMap.<String, FieldSortBuilder>builder()
        .put(RUN_ID, SortBuilders.fieldSort("runId"))
        .put(SESSION_ID, SortBuilders.fieldSort("sessionId"))
        .put(STATE, SortBuilders.fieldSort("state"))
        .put(START_TIME, SortBuilders.fieldSort("startTime"))
        .put(COMPLETE_TIME, SortBuilders.fieldSort("completeTime"))
        .put(REPOSITORY, SortBuilders.fieldSort("repository"))
        .build();
  }

  public Mono<SearchResponse> getRuns(Map<String, Object> filter, Map<String, Integer> page) {
    return getRuns(filter, page, List.of());
  }

  public Mono<SearchResponse> getRuns(
      Map<String, Object> filter, Map<String, Integer> page, List<Sort> sorts) {
    final AbstractQueryBuilder<?> query =
        (filter == null || filter.size() == 0)
            ? matchAllQuery()
            : queryFromArgs(QUERY_RESOLVER, filter);

    val searchSourceBuilder = new SearchSourceBuilder();

    if (sorts.isEmpty()) {
      searchSourceBuilder.sort(SORT_BUILDER_RESOLVER.get(START_TIME).order(DESC));
    } else {
      val sortBuilders = sortsToEsSortBuilders(SORT_BUILDER_RESOLVER, sorts);
      sortBuilders.forEach(searchSourceBuilder::sort);
    }

    searchSourceBuilder.query(query);

    if (page != null && page.size() != 0) {
      searchSourceBuilder.size(page.get("size"));
      searchSourceBuilder.from(page.get("from"));
    }

    return execute(searchSourceBuilder);
  }

  /**
   * The system statistics, key is the statistic, value is the count of runs in that state. See the
   * State enum for the possible keys.
   *
   * @return Map of system statistics
   */
  public Mono<Map<String, Long>> getAggregatedRunStateCounts() {
    val searchRequest = new SearchRequest(workflowIndex);
    val matchQueryBuilder = QueryBuilders.matchAllQuery();
    val sourceBuilder = new SearchSourceBuilder();
    val aggregation = AggregationBuilders.terms("states").field(STATE);

    sourceBuilder.aggregation(aggregation);
    sourceBuilder.query(matchQueryBuilder);
    searchRequest.source(sourceBuilder);

    return client
        .searchForResponse(searchRequest)
        .map(
            searchResponse -> {
              Terms states = searchResponse.getAggregations().get("states");
              return states.getBuckets().stream()
                  .collect(
                      (Collectors.toMap(
                          MultiBucketsAggregation.Bucket::getKeyAsString,
                          MultiBucketsAggregation.Bucket::getDocCount)));
            });
  }

  @SneakyThrows
  private Mono<SearchResponse> execute(@NonNull SearchSourceBuilder builder) {
    val searchRequest = new SearchRequest(workflowIndex);
    searchRequest.source(builder);
    return client.searchForResponse(searchRequest);
  }
}
