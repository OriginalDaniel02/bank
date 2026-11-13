package dan.bank.bank.kyc;

import java.util.List;

/**
 * What the camera captured: one clear selfie plus a few frames taken a moment apart. Providers use the extra frames
 * to tell a live person from a photo. Never stored and never logged.
 */
public record FaceImages(byte[] selfie, List<byte[]> liveness) {

    public static FaceImages of(org.springframework.web.multipart.MultipartFile selfie, List<org.springframework.web.multipart.MultipartFile> liveness) throws java.io.IOException {
        java.util.List<byte[]> frames = new java.util.ArrayList<>();
        if (liveness != null) for (var frame : liveness) frames.add(frame.getBytes());
        return new FaceImages(selfie.getBytes(), frames);
    }

    public FaceImages {
        liveness = liveness == null ? List.of() : List.copyOf(liveness);
    }
}
