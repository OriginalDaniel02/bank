import { useEffect, useRef, useState } from "react";

const LIVENESS_FRAMES = 7;      // taken after the selfie; the face check needs several frames to tell a live person from a photo
const FRAME_GAP_MS = 250;

/**
 * Captures a selfie plus a short burst of frames from the live camera. Nothing is uploaded here: the parent decides
 * what to do with the frames. A photo from the gallery is accepted as a limited fallback (a real face-check provider
 * will refuse it, because a single photo cannot prove a live person).
 */
export default function FaceCapture({ onCapture, disabled = false, label = "Take selfie" }) {
  const video = useRef(null);
  const stream = useRef(null);
  const [state, setState] = useState("idle");        // idle | starting | ready | capturing
  const [message, setMessage] = useState("");
  const [progress, setProgress] = useState(0);

  const stop = () => { stream.current?.getTracks().forEach(track => track.stop()); stream.current = null; };
  useEffect(() => stop, []);

  const start = async () => {
    setState("starting"); setMessage("");
    try {
      if (!navigator.mediaDevices?.getUserMedia) throw new Error("no camera");
      stream.current = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user", width: { ideal: 640 }, height: { ideal: 480 } }, audio: false });
      video.current.srcObject = stream.current;
      await video.current.play();
      setState("ready");
    } catch {
      stop(); setState("idle");
      setMessage("We couldn’t open your camera. Allow camera access in your browser settings and try again.");
    }
  };

  const grab = () => new Promise(resolve => {
    const element = video.current;
    const scale = Math.min(1, 640 / (element.videoWidth || 640));
    const canvas = document.createElement("canvas");
    canvas.width = Math.round((element.videoWidth || 640) * scale);
    canvas.height = Math.round((element.videoHeight || 480) * scale);
    canvas.getContext("2d").drawImage(element, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(blob => resolve(blob), "image/jpeg", 0.9);
  });

  const capture = async () => {
    setState("capturing"); setProgress(0);
    const selfie = await grab();
    const liveness = [];
    for (let i = 0; i < LIVENESS_FRAMES; i++) {
      await new Promise(resolve => setTimeout(resolve, FRAME_GAP_MS));
      liveness.push(await grab());
      setProgress(Math.round(((i + 1) / LIVENESS_FRAMES) * 100));
    }
    stop(); setState("idle");
    onCapture({ selfie, liveness });
  };

  const choosePhoto = event => {
    const file = event.target.files[0];
    event.target.value = "";
    if (file) onCapture({ selfie: file, liveness: [] });
  };

  const active = state === "ready" || state === "capturing";
  return <div className="face-capture">
    <div style={{ display: active ? "block" : "none", position: "relative", margin: "12px 0" }}>
      <video ref={video} muted playsInline aria-label="Camera preview" style={{ width: "100%", borderRadius: 14, background: "#0b1f3a", transform: "scaleX(-1)" }} />
      {state === "capturing" && <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 6, background: "rgba(255,255,255,.35)", borderRadius: 3 }}>
        <div style={{ width: `${progress}%`, height: "100%", background: "#14b8a6", borderRadius: 3, transition: "width .2s" }} /></div>}
    </div>
    {state === "ready" && <p className="payment-hint">Look at the camera in good light and hold still, then press the button.</p>}
    {state === "capturing" && <p className="payment-hint">Hold still…</p>}
    {message && <p className="payment-error">{message}</p>}
    {state === "idle" && <button type="button" className="settings-primary" onClick={start} disabled={disabled}>{label}</button>}
    {state === "starting" && <button type="button" className="settings-primary" disabled>Opening camera…</button>}
    {state === "ready" && <button type="button" className="settings-primary" onClick={capture}>Capture now</button>}
    {state === "idle" && <label className="payment-text" style={{ display: "block", textAlign: "center", cursor: disabled ? "default" : "pointer", marginTop: 8 }}>
      or use a photo instead<input type="file" accept="image/jpeg,image/png" onChange={choosePhoto} disabled={disabled} style={{ display: "none" }} /></label>}
  </div>;
}
