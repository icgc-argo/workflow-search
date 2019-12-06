package org.icgc_argo.workflow.search.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.*;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class RunResponse {
  @NonNull private String runId;

  private RunRequest request;

  private State state;

  private RunLog runLog;

  private List<TaskLog> taskLogs;

  private Object outputs;
}
