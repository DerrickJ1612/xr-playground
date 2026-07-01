# xr-playground

A collection of Android XR development examples. 

Projects progress from a basic dual-screen hello-world to real-time on-device ML inference via the Qualcomm AI Engine.

---

## Hardware and Prerequisites

- XREAL AI glasses (or a compatible device with SXR2330P / Hexagon HTP v79)
- Android Studio Canary build
- QAIRT SDK — see `docs/` for model compilation instructions; models were produced with v2.46.0.260424
- [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) (ADB, for sideloading and runtime setup)

---

## Projects

| # | Project | Description |
|---|---------|-------------|
| 01 | [Hello XR World](xreal/01_hello_world/) | Dual-activity starter |
| 02 | [Camera Preview](xreal/02_camera_preview/) | Streams the glasses camera to a CameraX `PreviewView` |
| 03 | [Image Analysis](xreal/03_image_analysis/) | Attaches a `CameraX ImageAnalysis` use case; logs frame resolution, format, and rotation |
| 04 | [Object Classification](xreal/04_object_classification/) | Real-time MobileNetV2 classification (QNN HTP, 1000 ImageNet classes) displayed on the glasses |

Each project directory contains a README with ADB launch commands.

---

## Inference Path Status

| Runtime | Status | Notes |
|---------|--------|-------|
| Qualcomm QAIRT / QNN | Validated | MobileNetV2 classification running on HTP v79 (project 04) |
| ExecuTorch (QNN delegate) | In progress | SSD300-VGG16 model exported to `.pte`; Android integration not yet built |
| LiteRT (TFLite) | Not started | — |

---

## Models

Compiled model artifacts are not checked in. Build them from the instructions in
`docs/XR_Aura_Development_Guide.docx` and place them in `xreal/models/`.

| File | Format | Expected path |
|------|--------|---------------|
| `mobilenet_v2.bin` | QNN context binary | `xreal/models/mobilenet_v2.bin` |
| `mobilenet_v2.onnx` | ONNX | `xreal/models/mobilenet_v2.onnx` |
| `ssd300_sxr2330p.pte` | ExecuTorch | `xreal/models/ssd300_sxr2330p.pte` |
| `imagenet_classes.txt` | Text (tracked) | `xreal/models/imagenet_classes.txt` |

See [`models/ssd300_sxr2330p_README.md`](xreal/models/ssd300_sxr2330p_README.md) for the custom ExecuTorch export recipe for SSD300.

---

## Quick Start (Project 04 — Object Classification)

Grant camera permission, stop any running instance, then launch:

```bash
adb shell pm grant com.example.xrobjectclassifier android.permission.CAMERA
adb shell am force-stop com.example.xrobjectclassifier
adb shell monkey -p com.example.xrobjectclassifier 1
```

For full QNN runtime setup (pushing HTP libraries and the model binary), see the [project 04 README](xreal/04_object_classification/README.md).

## Repository Structure

```
xreal/
├── 01_hello_world/           Minimal dual-activity app: phone launcher + glasses Glimmer UI with TTS
├── 02_camera_preview/        Live camera feed on the glasses via CameraX
├── 03_image_analysis/        CameraX ImageAnalysis pipeline with per-frame metadata logging
├── 04_object_classification/ Real-time MobileNetV2 ImageNet classification via QNN HTP
├── models/                   Labels and model docs; compiled binaries are gitignored
└── docs/                     XR Aura Development Guide
```

---
