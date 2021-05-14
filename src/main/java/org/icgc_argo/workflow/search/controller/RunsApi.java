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

package org.icgc_argo.workflow.search.controller;

import io.swagger.annotations.*;
import javax.validation.Valid;
import org.icgc_argo.workflow.search.model.common.RunId;
import org.icgc_argo.workflow.search.model.common.RunRequest;
import org.icgc_argo.workflow.search.model.wes.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Api(
    value = "WorkflowExecutionService",
    description = "the runs API",
    tags = "WorkflowExecutionService")
public interface RunsApi {

  @ApiOperation(
      value = "Get detailed info about a workflow run.",
      nickname = "getRunLog",
      notes =
          "This endpoint provides detailed information about a given workflow run. The returned result has information about the outputs produced by this workflow (if available), a log object which allows the stderr and stdout to be retrieved, a log array so stderr/stdout for individual tasks can be retrieved, and the overall state of the workflow run (e.g. RUNNING, see the State section).",
      response = RunResponse.class,
      tags = {
        "WorkflowExecutionService",
      })
  @ApiResponses(
      value = {
        @ApiResponse(
            code = 200,
            message = "Get detailed info about a workflow run",
            response = RunResponse.class),
        @ApiResponse(
            code = 401,
            message = "The request is unauthorized.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 403,
            message = "The requester is not authorized to perform this action.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 404,
            message = "The requested workflow run not found.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 500,
            message = "An unexpected error occurred.",
            response = ErrorResponse.class)
      })
  @RequestMapping(
      value = "/runs/{run_id}",
      produces = MediaType.APPLICATION_JSON_VALUE,
      method = RequestMethod.GET)
  Mono<RunResponse> getRunLog(
      @ApiParam(value = "", required = true) @PathVariable("run_id") String runId);

  @ApiOperation(
      value = "Get quick status info about a workflow run.",
      nickname = "getRunStatus",
      notes =
          "This provides an abbreviated (and likely fast depending on implementation) status of the running workflow, returning a simple result with the  overall state of the workflow run (e.g. RUNNING, see the State section).",
      response = RunStatus.class,
      tags = {
        "WorkflowExecutionService",
      })
  @ApiResponses(
      value = {
        @ApiResponse(
            code = 200,
            message = "Get quick status info about a workflow run",
            response = RunStatus.class),
        @ApiResponse(
            code = 401,
            message = "The request is unauthorized.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 403,
            message = "The requester is not authorized to perform this action.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 404,
            message = "The requested workflow run wasn't found.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 500,
            message = "An unexpected error occurred.",
            response = ErrorResponse.class)
      })
  @RequestMapping(
      value = "/runs/{run_id}/status",
      produces = MediaType.APPLICATION_JSON_VALUE,
      method = RequestMethod.GET)
  Mono<RunStatus> getRunStatus(
      @ApiParam(value = "", required = true) @PathVariable("run_id") String runId);

  @ApiOperation(
      value = "List the workflow runs.",
      nickname = "listRuns",
      notes =
          "This list should be provided in a stable ordering. (The actual ordering is implementation dependent.) When paging through the list, the client should not make assumptions about live updates, but should assume the contents of the list reflect the workflow list at the moment that the first page is requested.  To monitor a specific workflow run, use GetRunStatus or GetRunLog.",
      response = RunListResponse.class,
      tags = {
        "WorkflowExecutionService",
      })
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "List Run Results", response = RunListResponse.class),
        @ApiResponse(
            code = 400,
            message = "The request is malformed.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 401,
            message = "The request is unauthorized.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 403,
            message = "The requester is not authorized to perform this action.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 500,
            message = "An unexpected error occurred.",
            response = ErrorResponse.class)
      })
  @RequestMapping(
      value = "/runs",
      produces = MediaType.APPLICATION_JSON_VALUE,
      method = RequestMethod.GET)
  Mono<RunListResponse> listRuns(
      @ApiParam(
              example = "10",
              value =
                  "OPTIONAL The preferred number of workflow runs to return in a page. If not provided, the implementation should use a default page size. The implementation must not return more items than `page_size`, but it may return fewer.  Clients should not assume that if fewer than `page_size` items are returned that all items have been returned.  The availability of additional pages is indicated by the value of `next_page_token` in the response.")
          @Valid
          @RequestParam(value = "page_size", defaultValue = "10", required = false)
          Integer pageSize,
      @ApiParam(
              example = "0",
              value =
                  "OPTIONAL Token to use to indicate where to start getting results. If unspecified, return the first page of results.")
          @Valid
          @RequestParam(value = "page_token", defaultValue = "0", required = false)
          Integer pageToken);

  @ApiOperation(
      value = "Get information about workflow execution service.",
      nickname = "service info",
      response = ServiceInfo.class,
      tags = {
        "WorkflowExecutionService",
      })
  @RequestMapping(
      value = "/service-info",
      produces = MediaType.APPLICATION_JSON_VALUE,
      method = RequestMethod.GET)
  Mono<ServiceInfo> getServiceInfo();

  @ApiOperation(
      value = "Run a workflow",
      nickname = "runs",
      notes =
          "This endpoint creates a new workflow run and returns a runId to monitor its progress.\n\n"
              + "The workflow_attachment is part of the GA4GH WES API Standard however we currently not supporting it as of this release.\n\n"
              + "The workflow_url is the workflow GitHub repository URL (ex. icgc-argo/nextflow-dna-seq-alignment) that is accessible by the WES endpoint.\n\n"
              + "The workflow_params JSON object specifies the input parameters for a workflow. The exact format of the JSON object depends on the conventions of the workflow.\n\n"
              + "The workflow_engine_parameters JSON object specifies additional run-time arguments to the workflow engine (ie. specific workflow version, resuming a workflow, etc.)"
              + "The workflow_type is the type of workflow language, currently this WES API supports \"nextflow\" only.\n\n"
              + "The workflow_type_version is the version of the workflow language to run the workflow against and must be one supported by this WES instance.\n",
      response = RunId.class,
      tags = {
        "WorkflowExecutionService",
      })
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "", response = RunId.class),
        @ApiResponse(
            code = 401,
            message = "The request is unauthorized.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 403,
            message = "The requester is not authorized to perform this action.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 404,
            message = "The requested workflow run not found.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 500,
            message = "An unexpected error occurred.",
            response = ErrorResponse.class)
      })
  @RequestMapping(
          value = "/runs",
          produces = MediaType.APPLICATION_JSON_VALUE,
          consumes = MediaType.APPLICATION_JSON_VALUE,
          method = RequestMethod.POST)
  Mono<RunId> postRun(@Valid @RequestBody RunRequest runRequest);

  @ApiOperation(
      value = "Cancel a running workflow",
      nickname = "cancel run",
      notes = " ",
      response = RunId.class,
      tags = {
        "WorkflowExecutionService",
      })
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "", response = RunId.class),
        @ApiResponse(
            code = 401,
            message = "The request is unauthorized.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 403,
            message = "The requester is not authorized to perform this action.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 404,
            message = "The requested workflow run not found.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 500,
            message = "An unexpected error occurred.",
            response = ErrorResponse.class)
      })
  @RequestMapping(
          value = "/runs/{run_id}/cancel",
          produces = MediaType.APPLICATION_JSON_VALUE,
          method = RequestMethod.POST)
  Mono<RunId> cancelRun(@Valid @PathVariable("run_id") String runId);
}
