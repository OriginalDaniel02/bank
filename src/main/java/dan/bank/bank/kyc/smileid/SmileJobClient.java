package dan.bank.bank.kyc.smileid;

import smile.identity.core.models.ImageDetail;
import smile.identity.core.models.JobStatusResponse;
import smile.identity.core.models.PartnerParams;

import java.util.List;

/** The one call we make to Smile ID. An interface so the decision logic can be tested without the network. */
public interface SmileJobClient {
    JobStatusResponse submit(PartnerParams params, List<ImageDetail> images) throws Exception;
}
