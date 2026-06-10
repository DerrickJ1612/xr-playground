# SSD300-VGG16 — QNN ExecuTorch Export (SXR2330P)

**File:** `ssd300_sxr2330p.pte`  
**Target SoC:** SXR2330P (Qualcomm HTP v79)  
**Precision:** FP16  
**Input:** `(1, 3, 300, 300)` — normalized RGB image  
**Outputs:** `locs (1, 8732, 4)`, `class_scores (1, 8732, 21)` — PASCAL VOC 20 classes + background  
**Size:** 52 MB

---

## Source Model

The model comes from [a-PyTorch-Tutorial-to-Object-Detection](https://github.com/sgrvinod/a-PyTorch-Tutorial-to-Object-Detection) — a clean from-scratch SSD300 implementation trained on PASCAL VOC 2007+2012. Weights are loaded from `checkpoint_ssd300.pth.tar` in that repo.

Architecture: VGG16 backbone → auxiliary convolutions → 6 prediction heads producing 8732 anchor-box predictions.

---

## Why a Custom Export Recipe

No off-the-shelf ExecuTorch example covers this model. The standard QNN export examples target torchvision or HuggingFace models with known entrypoints. Three things made a custom recipe necessary:

**1. Non-standard checkpoint format**  
The checkpoint serializes the full `SSD300` Python object (not just a `state_dict`), so loading requires `weights_only=False` and `sys.path` set to the tutorial repo so PyTorch can unpickle the class. A standard `load_state_dict` call would fail immediately.

**2. Detached prior boxes**  
`SSD300.__init__` computes `self.priors_cxcy` (the 8732 anchor boxes) and stores the result as a plain tensor attribute — not a `nn.Parameter` or registered buffer. `torch.export` captures it as a constant but emits a `UserWarning` about unregistered gradient tensors. The recipe explicitly moves `priors_cxcy` to CPU before export to prevent any device mismatch if the checkpoint was saved from a CUDA run.

**3. Module-level device variable**  
`model.py` sets `device = torch.device("cuda" ...)` at import time. This is consumed by the tutorial's training/eval scripts but not by `forward()`, so it doesn't break export — but it does mean the recipe must control the import environment carefully (CPU-only loading, `map_location="cpu"`).

---

## Export Script

Script lives at `executorch/export_ssd300_qnn.py` in the ExecuTorch repo.

**FP16 (default):**
```bash
source /home/derrjohn/venv/env_executorch/bin/activate
QNN_SDK_ROOT=/home/derrjohn/qnn_sdk/qairt/2.46.0.260424 \
LD_LIBRARY_PATH=build-x86/lib:/home/derrjohn/qnn_sdk/qairt/2.46.0.260424/lib/x86_64-linux-clang \
PYTHONPATH=/home/derrjohn/sandbox \
python export_ssd300_qnn.py
```

**INT8 quantized:**
```bash
python export_ssd300_qnn.py --quantize --output ssd300_sxr2330p_int8.pte
```

---

## Runtime Notes

- The `.pte` fully delegates to QNN HTP — all conv, relu, and pooling ops are supported on v79 with no CPU fallback.
- Post-processing (NMS, decode boxes from `locs` + priors) must run on the host CPU; it is intentionally excluded from the delegate because it involves dynamic shapes and non-differentiable sorting ops that QNN does not support.
- Priors needed for decoding are in `checkpoint_ssd300.pth.tar` via `model.priors_cxcy` (shape `(8732, 4)`, center-size format).
