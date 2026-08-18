const WORKER_URL = "https://fe53f4d4-yumebox-iconkit.yumeyuka.workers.dev";
const densities = [
  { name: "mdpi", size: 48, foreground: 108 },
  { name: "hdpi", size: 72, foreground: 162 },
  { name: "xhdpi", size: 96, foreground: 216 },
  { name: "xxhdpi", size: 144, foreground: 324 },
  { name: "xxxhdpi", size: 192, foreground: 432 },
];

function clip(context, size, shape) {
  context.beginPath();
  if (shape === "circle")
    context.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
  else if (shape === "rounded")
    context.roundRect(0, 0, size, size, size * 0.22);
  else context.rect(0, 0, size, size);
  context.clip();
}

export const IconKitBuilder = () => {
  const [image, setImage] = useState();
  const [zipReady, setZipReady] = useState(
    typeof window !== "undefined" && Boolean(window.JSZip),
  );
  const [color, setColor] = useState("#ffffff");
  const [shape, setShape] = useState("rounded");
  const [crop, setCrop] = useState(false);
  const [padding, setPadding] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState();
  const [actionsUrl, setActionsUrl] = useState();
  const [error, setError] = useState();
  const canvasRef = useRef(null);
  const drag = useRef();

  useEffect(() => {
    const ready = () => setZipReady(true);
    window.addEventListener("icon-kit-jszip-ready", ready);
    return () => window.removeEventListener("icon-kit-jszip-ready", ready);
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !image) return;
    const context = canvas.getContext("2d");
    if (!context) return;
    const size = 512;
    const available = size * (1 - padding * 2);
    const scale = (crop ? Math.max : Math.min)(
      available / image.naturalWidth,
      available / image.naturalHeight,
    );
    const width = image.naturalWidth * scale * zoom;
    const height = image.naturalHeight * scale * zoom;
    context.clearRect(0, 0, size, size);
    context.save();
    clip(context, size, shape);
    context.fillStyle = color;
    context.fillRect(0, 0, size, size);
    context.drawImage(
      image,
      (size - width) / 2 + offset.x * size,
      (size - height) / 2 + offset.y * size,
      width,
      height,
    );
    context.restore();
  }, [image, color, shape, crop, padding, zoom, offset]);

  function chooseFile(file) {
    if (!file?.type.startsWith("image/"))
      return setError("请选择 PNG、JPG 或 WebP 图片。");
    const url = URL.createObjectURL(file);
    const next = new Image();
    next.onload = () => {
      setImage(next);
      setZoom(1);
      setOffset({ x: 0, y: 0 });
      setError(undefined);
      URL.revokeObjectURL(url);
    };
    next.src = url;
  }

  function draw(source, size, foreground = true, extraPadding = 0) {
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("无法生成 PNG");
    const available = size * (1 - (padding + extraPadding) * 2);
    const scale = (crop ? Math.max : Math.min)(
      available / source.naturalWidth,
      available / source.naturalHeight,
    );
    const width = source.naturalWidth * scale * zoom;
    const height = source.naturalHeight * scale * zoom;
    context.save();
    clip(context, size, shape);
    context.fillStyle = color;
    context.fillRect(0, 0, size, size);
    if (foreground)
      context.drawImage(
        source,
        (size - width) / 2 + offset.x * size,
        (size - height) / 2 + offset.y * size,
        width,
        height,
      );
    context.restore();
    return canvas;
  }

  function blob(canvas) {
    return new Promise((resolve, reject) =>
      canvas.toBlob(
        (value) => (value ? resolve(value) : reject(new Error("无法生成 PNG"))),
        "image/png",
      ),
    );
  }

  async function bundle() {
    if (!image) throw new Error("请先上传一张图标图片。");
    if (!window.JSZip) throw new Error("压缩组件尚未加载，请稍后重试。");
    const zip = new window.JSZip();
    for (const density of densities) {
      zip.file(
        `res/mipmap-${density.name}/ic_launcher.png`,
        await blob(draw(image, density.size)),
      );
      zip.file(
        `res/mipmap-${density.name}/ic_launcher_adaptive_fore.png`,
        await blob(draw(image, density.foreground, true, 0.15)),
      );
      const background = document.createElement("canvas");
      background.width = density.foreground;
      background.height = density.foreground;
      const backgroundContext = background.getContext("2d");
      if (!backgroundContext) throw new Error("无法生成 PNG");
      backgroundContext.fillStyle = color;
      backgroundContext.fillRect(0, 0, background.width, background.height);
      zip.file(
        `res/mipmap-${density.name}/ic_launcher_adaptive_back.png`,
        await blob(background),
      );
    }
    zip.file(
      "res/mipmap-anydpi-v26/ic_launcher.xml",
      '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n  <background android:drawable="@mipmap/ic_launcher_adaptive_back"/>\n  <foreground android:drawable="@mipmap/ic_launcher_adaptive_fore"/>\n</adaptive-icon>',
    );
    zip.file("play_store_512.png", await blob(draw(image, 512)));
    zip.file("1024.png", await blob(draw(image, 1024)));
    return zip.generateAsync({
      type: "blob",
      compression: "DEFLATE",
      compressionOptions: { level: 6 },
    });
  }

  async function download() {
    try {
      const url = URL.createObjectURL(await bundle());
      const link = document.createElement("a");
      link.href = url;
      link.download = "YumeBox-IconKit.zip";
      link.click();
      URL.revokeObjectURL(url);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "生成 ZIP 失败。");
    }
  }

  async function submit() {
    try {
      setBusy(true);
      setError(undefined);
      setActionsUrl(undefined);
      setStatus(undefined);
      const form = new FormData();
      form.append("bundle", await bundle(), "YumeBox-IconKit-icons.zip");
      const response = await fetch(`${WORKER_URL}/v1/jobs`, {
        method: "POST",
        body: form,
      });
      if (!response.ok) throw new Error(await response.text());
      const job = await response.json();
      setStatus("queued");
      for (;;) {
        await new Promise((resolve) => window.setTimeout(resolve, 2000));
        const result = await fetch(`${WORKER_URL}${job.statusUrl}`, {
          cache: "no-store",
        });
        if (!result.ok) continue;
        const next = await result.json();
        setStatus(next.status);
        if (next.actionsUrl) setActionsUrl(next.actionsUrl);
        if (next.status === "succeeded" || next.status === "failed") break;
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "提交失败，请重试。");
    } finally {
      setBusy(false);
    }
  }

  function pointerDown(event) {
    if (!image) return;
    drag.current = {
      x: event.clientX,
      y: event.clientY,
      ox: offset.x,
      oy: offset.y,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  }
  function pointerMove(event) {
    if (!drag.current) return;
    const size = event.currentTarget.getBoundingClientRect().width;
    setOffset({
      x: drag.current.ox + (event.clientX - drag.current.x) / size,
      y: drag.current.oy + (event.clientY - drag.current.y) / size,
    });
  }

  const statusText =
    status === "succeeded"
      ? "构建完成"
      : status === "failed"
        ? "构建失败"
        : status === "running"
          ? "构建中"
          : "已提交";
  return (
    <div className="icon-kit-builder">
      <style>{styles}</style>
      <div className="icon-kit-intro">
        <div>
          <span className="icon-kit-eyebrow">YUMEBOX · ICON KIT</span>
          <h2>制作你的应用图标</h2>
          <p>
            上传一张图片，调整裁剪与形状，生成可直接用于 YumeBox 的 Android
            图标包。
          </p>
        </div>
        <span className="icon-kit-badge">无需登录</span>
      </div>
      <div className="icon-kit-grid">
        <div className="icon-kit-preview">
          <div
            className={`icon-kit-stage ${image ? "has-image" : ""}`}
            onPointerDown={pointerDown}
            onPointerMove={pointerMove}
            onPointerUp={() => {
              drag.current = undefined;
            }}
          >
            {image ? (
              <canvas ref={canvasRef} width={512} height={512} />
            ) : (
              <label className="icon-kit-upload">
                <span className="icon-kit-upload-icon">↑</span>
                <strong>上传图标</strong>
                <small>PNG、JPG 或 WebP</small>
                <input
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  onChange={(event) => chooseFile(event.target.files?.[0])}
                />
              </label>
            )}
          </div>
          <div className="icon-kit-zoom">
            <span>缩放</span>
            <input
              type="range"
              min="0.5"
              max="3"
              step="0.01"
              value={zoom}
              onChange={(event) => setZoom(Number(event.target.value))}
            />
            <output>{Math.round(zoom * 100)}%</output>
          </div>
        </div>
        <div className="icon-kit-controls">
          <div className="icon-kit-control">
            <label>背景色</label>
            <div className="icon-kit-color">
              <input
                type="color"
                value={color}
                onChange={(event) => setColor(event.target.value)}
              />
              <input
                value={color}
                maxLength={7}
                onChange={(event) => setColor(event.target.value)}
              />
            </div>
          </div>
          <div className="icon-kit-control">
            <label>图标形状</label>
            <div className="icon-kit-segment">
              {["square", "rounded", "circle"].map((value) => (
                <button
                  key={value}
                  className={shape === value ? "active" : ""}
                  onClick={() => setShape(value)}
                >
                  {value === "square"
                    ? "方形"
                    : value === "rounded"
                      ? "圆角"
                      : "圆形"}
                </button>
              ))}
            </div>
          </div>
          <div className="icon-kit-control">
            <div className="icon-kit-label-row">
              <label>裁剪方式</label>
              <label className="icon-kit-replace">
                更换图片
                <input
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  onChange={(event) => chooseFile(event.target.files?.[0])}
                />
              </label>
            </div>
            <div className="icon-kit-segment">
              <button
                className={!crop ? "active" : ""}
                onClick={() => setCrop(false)}
              >
                完整显示
              </button>
              <button
                className={crop ? "active" : ""}
                onClick={() => setCrop(true)}
              >
                填充裁剪
              </button>
            </div>
            <div className="icon-kit-range">
              <span>留白</span>
              <output>{Math.round(padding * 100)}%</output>
            </div>
            <input
              type="range"
              min="0"
              max="0.35"
              step="0.01"
              value={padding}
              onChange={(event) => setPadding(Number(event.target.value))}
            />
          </div>
          {error && <div className="icon-kit-error">{error}</div>}
          {actionsUrl && (
            <a
              className="icon-kit-success"
              href={actionsUrl}
              target="_blank"
              rel="noreferrer"
            >
              <span>{statusText}</span>
              <span>查看 Actions ↗</span>
            </a>
          )}
          <div className="icon-kit-actions">
            <button
              className="icon-kit-secondary"
              disabled={!image || busy || !zipReady}
              onClick={download}
            >
              下载 ZIP
            </button>
            <button
              className="icon-kit-primary"
              disabled={!image || busy || !zipReady}
              onClick={submit}
            >
              {busy ? "提交中…" : "构建 APK"}
            </button>
          </div>
          <small className="icon-kit-note">
            生成 Asset Studio 格式图标包 · 保留原签名
          </small>
        </div>
      </div>
    </div>
  );
};

const styles = `.icon-kit-builder{--ink:#242322;--muted:#756f68;--line:#e6e0d8;--soft:#faf8f5;margin:28px 0 44px;color:var(--ink);font-family:inherit}.icon-kit-intro{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;padding:24px 26px;border:1px solid var(--line);border-radius:18px;background:#faf8f5}.icon-kit-eyebrow{font-size:10px;font-weight:800;letter-spacing:.14em;color:#a07852}.icon-kit-intro h2{margin:7px 0 6px;font-size:25px;letter-spacing:-.02em}.icon-kit-intro p{margin:0;max-width:480px;color:var(--muted);font-size:13px;line-height:1.6}.icon-kit-badge{white-space:nowrap;border:1px solid #ddd2c4;border-radius:999px;padding:6px 10px;color:#856e58;font-size:11px}.icon-kit-grid{display:grid;grid-template-columns:minmax(220px,.86fr) minmax(280px,1fr);gap:28px;margin-top:18px;padding:20px;border:1px solid var(--line);border-radius:18px;background:#fff}.icon-kit-stage{aspect-ratio:1;display:grid;place-items:center;overflow:hidden;border:1px dashed #b9afa4;border-radius:16px;background:#faf8f5;touch-action:none}.icon-kit-stage.has-image{cursor:grab}.icon-kit-stage canvas{display:block;width:100%;height:100%;filter:drop-shadow(0 12px 14px #30251d1a)}.icon-kit-upload{width:100%;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;color:var(--muted);cursor:pointer}.icon-kit-upload input,.icon-kit-replace input{display:none}.icon-kit-upload-icon{display:grid;place-items:center;width:38px;height:38px;margin-bottom:4px;border:1px solid #d7cec4;border-radius:12px;font-size:26px;line-height:1;color:#9c8064}.icon-kit-upload strong{font-size:13px}.icon-kit-upload small{font-size:11px;color:#aaa198}.icon-kit-zoom{display:grid;grid-template-columns:34px 1fr 38px;gap:8px;align-items:center;margin-top:13px;color:var(--muted);font-size:11px}.icon-kit-zoom input,.icon-kit-control>input{width:100%;accent-color:#896c51}.icon-kit-zoom output,.icon-kit-range output{font-variant-numeric:tabular-nums;text-align:right;color:#6d6258}.icon-kit-controls{display:grid;align-content:start;gap:16px}.icon-kit-control{display:grid;gap:7px}.icon-kit-control>label,.icon-kit-label-row>label:first-child{font-size:12px;font-weight:750}.icon-kit-color{display:grid;grid-template-columns:42px 1fr;gap:8px}.icon-kit-color input[type=color]{width:42px;height:38px;padding:3px;border:1px solid var(--line);border-radius:9px;background:#fff}.icon-kit-color input:not([type=color]){min-width:0;border:1px solid var(--line);border-radius:9px;padding:0 10px;color:var(--ink);outline:0}.icon-kit-segment{display:grid;grid-template-columns:repeat(3,1fr);gap:6px}.icon-kit-segment button{min-height:35px;border:1px solid var(--line);border-radius:8px;color:#6e655d;background:#fff;font-size:11px;cursor:pointer}.icon-kit-segment button.active{border-color:var(--ink);color:#fff;background:var(--ink)}.icon-kit-label-row{display:flex;align-items:center;justify-content:space-between;gap:10px}.icon-kit-replace{color:#9a8068;font-size:11px;cursor:pointer}.icon-kit-segment:has(button:nth-child(2):last-child){grid-template-columns:1fr 1fr}.icon-kit-range{display:flex;justify-content:space-between;margin-top:4px;color:var(--muted);font-size:11px}.icon-kit-error{padding:9px 10px;border-radius:8px;color:#a23b33;background:#fff1ef;font-size:11px}.icon-kit-success{display:flex;justify-content:space-between;gap:10px;padding:10px 11px;border:1px solid #c9dfcb;border-radius:9px;color:#407548;background:#f2faf1;font-size:11px;font-weight:700;text-decoration:none}.icon-kit-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}.icon-kit-actions button{min-height:40px;border-radius:9px;font-size:12px;font-weight:750;cursor:pointer}.icon-kit-primary{border:1px solid var(--ink);color:#fff;background:var(--ink)}.icon-kit-secondary{border:1px solid var(--line);color:#5f554c;background:#fff}.icon-kit-actions button:disabled{opacity:.45;cursor:not-allowed}.icon-kit-note{display:block;text-align:center;color:#aaa198;font-size:10px}@media(max-width:700px){.icon-kit-intro{padding:20px}.icon-kit-badge{display:none}.icon-kit-grid{grid-template-columns:1fr;padding:14px;gap:20px}.icon-kit-stage{max-width:330px;margin:auto}.icon-kit-actions{grid-template-columns:1fr 1fr}}`;
