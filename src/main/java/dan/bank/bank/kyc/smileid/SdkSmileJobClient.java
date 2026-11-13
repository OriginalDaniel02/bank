package dan.bank.bank.kyc.smileid;

import smile.identity.core.WebApi;
import smile.identity.core.models.ImageDetail;
import smile.identity.core.models.JobStatusResponse;
import smile.identity.core.models.Options;
import smile.identity.core.models.PartnerParams;

import java.util.List;

/**
 * Talks to Smile ID through their official Java library. We ask for the result in the same call
 * (returnJobStatus), so no callback URL is needed. The library requires choosing either a callback or a job-status
 * query, never neither.
 */
class SdkSmileJobClient implements SmileJobClient {

    private final WebApi api;

    SdkSmileJobClient(String partnerId, String apiKey, String server) {
        // The second argument is the default callback URL. It is unused because results are returned directly.
        this.api = new WebApi(partnerId, "", apiKey, server);
    }

    @Override
    public JobStatusResponse submit(PartnerParams params, List<ImageDetail> images) throws Exception {
        Options options = new Options(false, false, true, "");   // history, image links, job status, callback
        return api.submitJob(params, images, null, options);
    }
}
