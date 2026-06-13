package nz.co.ksktech.fraud.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import nz.co.ksktech.fraud.api.FraudDtos.EvaluationResponse;
import nz.co.ksktech.fraud.api.FraudDtos.InitiateRequest;
import nz.co.ksktech.fraud.api.FraudDtos.NotifyRequest;
import nz.co.ksktech.fraud.api.FraudDtos.UpdateRequest;
import nz.co.ksktech.fraud.domain.FraudEvaluation;
import nz.co.ksktech.fraud.service.FraudDetectionService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * BIAN Fraud Detection service-domain resource. Endpoints follow the BIAN verb vocabulary:
 * initiate, evaluate, retrieve, update, notify — each acting on a {@link FraudEvaluation} control
 * record identified by its reference id.
 */
@Path("/fraud-detection")
@Tag(name = "Fraud Detection", description = "BIAN Fraud Detection service domain")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FraudDetectionResource {

  private final FraudDetectionService service;

  /**
   * Constructor.
   *
   * @param service the fraud detection orchestration service
   */
  public FraudDetectionResource(FraudDetectionService service) {
    this.service = service;
  }

  /**
   * BIAN Initiate — creates a new control record.
   *
   * @param request the initiation payload
   * @return 201 with the created control record
   */
  @POST
  @Path("/initiate")
  @Operation(summary = "Initiate a fraud evaluation control record")
  public Response initiate(InitiateRequest request) {
    FraudEvaluation e = service.initiate(request);
    return Response.status(Response.Status.CREATED).entity(EvaluationResponse.from(e)).build();
  }

  /**
   * BIAN Evaluate — runs AI triage and records the verdict.
   *
   * @param ref the control-record reference id
   * @return the evaluated control record
   */
  @POST
  @Path("/{ref}/evaluate")
  @Operation(summary = "Run AI triage and set riskScore, reasoning and decision")
  public EvaluationResponse evaluate(@PathParam("ref") UUID ref) {
    return EvaluationResponse.from(service.evaluate(ref));
  }

  /**
   * BIAN Retrieve — returns a control record.
   *
   * @param ref the control-record reference id
   * @return the control record
   */
  @GET
  @Path("/{ref}/retrieve")
  @Operation(summary = "Retrieve the control record")
  public EvaluationResponse retrieve(@PathParam("ref") UUID ref) {
    return EvaluationResponse.from(service.retrieve(ref));
  }

  /**
   * BIAN Update — applies an analyst disposition override.
   *
   * @param ref the control-record reference id
   * @param request the override payload
   * @return the updated control record
   */
  @PUT
  @Path("/{ref}/update")
  @Operation(summary = "Analyst disposition override")
  public EvaluationResponse update(@PathParam("ref") UUID ref, UpdateRequest request) {
    return EvaluationResponse.from(service.update(ref, request));
  }

  /**
   * BIAN Notify — emits and logs a fraud alert event.
   *
   * @param ref the control-record reference id
   * @param request an optional message override
   * @return the control record the alert was raised for
   */
  @POST
  @Path("/{ref}/notify")
  @Operation(summary = "Emit and log an alert event")
  public EvaluationResponse notify(@PathParam("ref") UUID ref, NotifyRequest request) {
    String message = request == null ? null : request.message();
    return EvaluationResponse.from(service.notify(ref, message));
  }
}
