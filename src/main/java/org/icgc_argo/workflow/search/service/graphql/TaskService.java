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

package org.icgc_argo.workflow.search.service.graphql;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.icgc_argo.workflow.search.model.EsDefaults.ES_PAGE_DEFAULT_FROM;
import static org.icgc_argo.workflow.search.model.EsDefaults.ES_PAGE_DEFAULT_SIZE;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.val;
import org.elasticsearch.search.SearchHit;
import org.icgc_argo.workflow.search.model.graphql.*;
import org.icgc_argo.workflow.search.repository.TaskRepository;
import org.icgc_argo.workflow.search.service.annotations.HasQueryAccess;
import org.springframework.stereotype.Service;

@Service
@HasQueryAccess
public class TaskService {

  private final TaskRepository taskRepository;

  public TaskService(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  public SearchResult<Task> searchRuns(
          Map<String, Object> filter, Map<String, Integer> page, List<Sort> sorts) {
    val response = taskRepository.getTasks(filter, page, sorts);
    val responseSearchHits = response.getHits();

    val totalHits = responseSearchHits.getTotalHits().value;
    val from = page.getOrDefault("from", ES_PAGE_DEFAULT_FROM);
    val size = page.getOrDefault("size", ES_PAGE_DEFAULT_SIZE);

    val analyses =
            Arrays.stream(responseSearchHits.getHits())
                    .map(TaskService::hitToTask)
                    .collect(toUnmodifiableList());
    val nextFrom = (totalHits - from) / size > 0;
    return new SearchResult<>(analyses, nextFrom, totalHits);
  }

  public AggregationResult aggregateTasks(Map<String, Object> filter) {
    val response = taskRepository.getTasks(filter, Map.of(), List.of());
    val responseSearchHits = response.getHits();
    val totalHits = responseSearchHits.getTotalHits().value;
    return new AggregationResult(totalHits);
  }

  public List<Task> getTasks(String runId, Map<String, Object> filter, Map<String, Integer> page) {
    val mergedBuilder = ImmutableMap.<String, Object>builder();
    if (runId != null) {
      mergedBuilder.put("runId", runId);
    }
    if (filter != null && filter.size() > 0) {
      mergedBuilder.putAll(filter);
    }
    val merged = mergedBuilder.build();

    val response = taskRepository.getTasks(merged, page);
    val hitStream = Arrays.stream(response.getHits().getHits());
    return hitStream.map(TaskService::hitToTask).collect(toUnmodifiableList());
  }

  private static Task hitToTask(SearchHit hit) {
    val sourceMap = hit.getSourceAsMap();
    return Task.parse(sourceMap);
  }
}