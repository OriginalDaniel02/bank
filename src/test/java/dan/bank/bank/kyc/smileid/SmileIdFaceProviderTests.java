package dan.bank.bank.kyc.smileid;

import dan.bank.bank.kyc.FaceImages;
import dan.bank.bank.kyc.FaceVerificationProvider;
import org.junit.jupiter.api.Test;
import smile.identity.core.enums.ImageType;
import smile.identity.core.enums.JobType;
import smile.identity.core.models.ImageDetail;
import smile.identity.core.models.JobResponse;
import smile.identity.core.models.JobStatusResponse;
import smile.identity.core.models.PartnerParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The decision logic around Smile ID, with the network call replaced. It must never approve by accident. */
class SmileIdFaceProviderTests {

    /** Records what we sent and returns whatever the test says Smile ID answered. */
    static class FakeClient implements SmileJobClient {
        PartnerParams lastParams;
        List<ImageDetail> lastImages;
        int calls = 0;
        JobStatusResponse answer;
        Exception failure;

        public JobStatusResponse submit(PartnerParams params, List<ImageDetail> images) throws Exception {
            calls++;
            lastParams = params;
            lastImages = images;
            if (failure != null) throw failure;
            return answer;
        }
    }

    private static JobStatusResponse response(boolean complete, boolean success, String code) {
        JobResponse job = new JobResponse("2.0", "smile-job-1", null, "Test", "Some text", code, "true", null, null, null, "99", "Smile", null, null);
        return new JobStatusResponse("2302", complete, success, new JobStatusResponse.Result(job), null, null, null, null, null, null);
    }

    private static FaceImages frames(int liveness) {
        List<byte[]> list = new ArrayList<>(Collections.nCopies(liveness, new byte[]{1, 2, 3}));
        return new FaceImages(new byte[]{9, 9, 9}, list);
    }

    @Test
    void approvesOnlyWhenTheJobFinishedSucceededAndTheCodeIsOnTheAllowList() {
        FakeClient client = new FakeClient();
        SmileIdFaceProvider provider = new SmileIdFaceProvider(client, "0810, 1210", 7);

        client.answer = response(true, true, "0810");
        assertTrue(provider.authenticate(42, frames(7)).approved());
        client.answer = response(true, true, "1210");
        assertTrue(provider.authenticate(42, frames(7)).approved());

        client.answer = response(true, true, "0811");                  // some other code: not on the list
        assertFalse(provider.authenticate(42, frames(7)).approved());
        client.answer = response(true, false, "0810");                 // listed code but the job did not succeed
        assertFalse(provider.authenticate(42, frames(7)).approved());
        client.answer = response(true, true, null);                    // no code at all
        assertFalse(provider.authenticate(42, frames(7)).approved());
    }

    @Test
    void withNoAllowListConfiguredNothingIsEverApproved() {
        FakeClient client = new FakeClient();
        client.answer = response(true, true, "0810");
        assertFalse(new SmileIdFaceProvider(client, "", 7).authenticate(1, frames(7)).approved());
        assertFalse(new SmileIdFaceProvider(client, " , ,", 7).verify(1, frames(7)).approved());
    }

    @Test
    void anIncompleteJobAnErrorOrNoAnswerIsUnavailableNeverApproved() {
        FakeClient client = new FakeClient();
        SmileIdFaceProvider provider = new SmileIdFaceProvider(client, "0810", 7);

        client.answer = response(false, false, null);
        assertThrows(FaceVerificationProvider.UnavailableException.class, () -> provider.authenticate(1, frames(7)));
        client.answer = null;
        assertThrows(FaceVerificationProvider.UnavailableException.class, () -> provider.authenticate(1, frames(7)));
        client.failure = new java.io.IOException("timeout");
        assertThrows(FaceVerificationProvider.UnavailableException.class, () -> provider.authenticate(1, frames(7)));
    }

    @Test
    void tooFewLivenessFramesAreRefusedBeforeAnyPaidCall() {
        FakeClient client = new FakeClient();
        client.answer = response(true, true, "0810");
        SmileIdFaceProvider provider = new SmileIdFaceProvider(client, "0810", 7);
        FaceVerificationProvider.Result result = provider.authenticate(1, frames(0));   // a single photo
        assertFalse(result.approved());
        assertTrue(result.reason().contains("hold still"));
        assertFalse(provider.verify(1, frames(6)).approved());
        assertEquals(0, client.calls);
    }

    @Test
    void usesRegistrationToEnrolAndAuthenticationToCheckAndSendsOnlyAnOpaqueId() {
        FakeClient client = new FakeClient();
        client.answer = response(true, true, "0810");
        SmileIdFaceProvider provider = new SmileIdFaceProvider(client, "0810", 7);

        provider.verify(42, frames(7));
        assertEquals(JobType.SMART_SELFIE_REGISTRATION, client.lastParams.getJobType());
        provider.authenticate(42, frames(7));
        assertEquals(JobType.SMART_SELFIE_AUTHENTICATION, client.lastParams.getJobType());

        assertEquals("danbank-42", client.lastParams.getUserId());      // no email, phone or name
        assertTrue(client.lastParams.getJobId().startsWith("dbj-"));
        assertEquals(8, client.lastImages.size());                       // 1 selfie + 7 liveness frames
        assertEquals(1, client.lastImages.stream().filter(i -> i.getImageTypeId() == ImageType.SELFIE_BASE64).count());
        assertEquals(7, client.lastImages.stream().filter(i -> i.getImageTypeId() == ImageType.LIVENESS_BASE64).count());
    }

    @Test
    void refusesToStartWithoutKeys() {
        assertThrows(IllegalStateException.class, () -> new SmileIdFaceProvider("", "key", "0", "0810", 7));
        assertThrows(IllegalStateException.class, () -> new SmileIdFaceProvider("123", "", "0", "0810", 7));
        assertThrows(IllegalStateException.class, () -> new SmileIdFaceProvider("123", "key", "2", "0810", 7));
    }
}
