package dan.bank.bank.kyc.smileid;

import dan.bank.bank.kyc.FaceImages;
import dan.bank.bank.kyc.FaceVerificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import smile.identity.core.enums.ImageType;
import smile.identity.core.enums.JobType;
import smile.identity.core.models.ImageDetail;
import smile.identity.core.models.JobResponse;
import smile.identity.core.models.JobStatusResponse;
import smile.identity.core.models.PartnerParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Face verification through Smile ID SmartSelfie.
 * <ul>
 *   <li>{@link #verify}: SmartSelfie <b>registration</b> (job type 4): enrols the person's face under their user id.</li>
 *   <li>{@link #authenticate}: SmartSelfie <b>authentication</b> (job type 2): checks a new selfie against the enrolled face.</li>
 * </ul>
 * It <b>fails closed</b>: anything unexpected (timeout, incomplete job, unknown result code, missing key) means "not
 * verified", never "approved". Which Smile result codes count as a pass is configuration
 * ({@code smileid.approved-result-codes}); it is empty by default so nothing is approved until you have read the
 * codes from your Smile ID sandbox. Images are sent to Smile ID and never stored or logged here.
 */
@Component
@ConditionalOnProperty(name = "kyc.face.provider", havingValue = "smileid")
public class SmileIdFaceProvider implements FaceVerificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmileIdFaceProvider.class);

    private final SmileJobClient client;
    private final Set<String> approvedCodes;
    private final int minLivenessFrames;

    @Autowired
    public SmileIdFaceProvider(@Value("${smileid.partner-id:}") String partnerId,
                               @Value("${smileid.api-key:}") String apiKey,
                               @Value("${smileid.server:0}") String server,
                               @Value("${smileid.approved-result-codes:}") String approvedCodes,
                               @Value("${smileid.min-liveness-frames:7}") int minLivenessFrames) {
        this(requireConfigured(partnerId, apiKey, server), approvedCodes, minLivenessFrames);
    }

    /** For tests: supply the client directly. */
    SmileIdFaceProvider(SmileJobClient client, String approvedCodes, int minLivenessFrames) {
        this.client = client;
        this.approvedCodes = Arrays.stream(approvedCodes.split(",")).map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.minLivenessFrames = minLivenessFrames;
        if (this.approvedCodes.isEmpty()) {
            log.warn("smileid.approved-result-codes is empty, so NO face check can pass. Run a sandbox check, read the "
                    + "result code in the log, and set it. See the README.");
        }
    }

    private static SmileJobClient requireConfigured(String partnerId, String apiKey, String server) {
        if (partnerId.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("kyc.face.provider=smileid needs SMILE_PARTNER_ID and SMILE_API_KEY to be set");
        }
        if (!server.equals("0") && !server.equals("1")) {
            throw new IllegalStateException("SMILE_SERVER must be 0 (sandbox) or 1 (production)");
        }
        return new SdkSmileJobClient(partnerId, apiKey, server);
    }

    @Override
    public String name() {
        return "smileid";
    }

    @Override
    public Result verify(long userId, FaceImages images) {
        return run(JobType.SMART_SELFIE_REGISTRATION, userId, images);
    }

    @Override
    public Result authenticate(long userId, FaceImages images) {
        return run(JobType.SMART_SELFIE_AUTHENTICATION, userId, images);
    }

    private Result run(JobType type, long userId, FaceImages images) {
        if (images.liveness().size() < minLivenessFrames) {
            // A single photo cannot prove a live person, so refuse before spending a paid call.
            return new Result(false, null, "Please hold still while the camera captures a few frames");
        }
        // Smile ID gets an opaque id, never an email, phone number or name.
        String smileUserId = "danbank-" + userId;
        String jobId = "dbj-" + UUID.randomUUID();
        List<ImageDetail> details = new ArrayList<>();
        details.add(new ImageDetail(ImageType.SELFIE_BASE64, Base64.getEncoder().encodeToString(images.selfie()), ""));
        images.liveness().forEach(frame -> details.add(new ImageDetail(ImageType.LIVENESS_BASE64, Base64.getEncoder().encodeToString(frame), "")));

        JobStatusResponse response;
        try {
            response = client.submit(new PartnerParams(type, smileUserId, jobId, new HashMap<>()), details);
        } catch (Exception e) {
            log.warn("Smile ID job {} could not be completed: {}", jobId, e.getClass().getSimpleName());
            throw new UnavailableException("Smile ID is unavailable", e);
        }
        return interpret(jobId, response);
    }

    Result interpret(String jobId, JobStatusResponse response) {
        if (response == null) throw new UnavailableException("Smile ID returned nothing", null);
        JobResponse job = response.getResult() == null ? null : response.getResult().getJobResponse();
        String code = job == null ? null : job.getResultCode();
        String text = job == null ? null : job.getResultText();
        // The code and text are safe to log: they describe the outcome, never the person or their images.
        log.info("Smile ID job {} finished: complete={}, success={}, resultCode={}, resultText={}",
                jobId, response.isJobComplete(), response.isJobSuccess(), code, text);
        if (!response.isJobComplete()) {
            throw new UnavailableException("Smile ID job did not finish", null);
        }
        boolean approved = response.isJobSuccess() && code != null && approvedCodes.contains(code);
        String reference = job == null ? jobId : (job.getSmileJobId() == null ? jobId : job.getSmileJobId());
        return new Result(approved, reference, approved ? null : "The face check did not pass");
    }
}
