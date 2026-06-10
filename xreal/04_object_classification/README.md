# Single Frame Inference

## Runtime Setup

Create the runtime directory and push the required QNN libraries:

QAIRT SDK: v2.46.0.260424121129

```bash
adb shell mkdir -p /data/local/tmp/qnn

adb push lib/aarch64-android/libQnnHtp.so /data/local/tmp/qnn
adb push lib/aarch64-android/libQnnSystem.so /data/local/tmp/qnn
adb push lib/aarch64-android/libQnnHtpPrepare.so /data/local/tmp/qnn
adb push lib/aarch64-android/libQnnHtpV79Stub.so /data/local/tmp/qnn
adb push lib/aarch64-android/libQnnTFLiteDelegate.so /data/local/tmp/qnn
adb push lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so /data/local/tmp/qnn
adb push bin/aarch64-android/qnn-net-run /data/local/tmp/qnn
```

## QNN Runtime Validation

Push a context binary and verify HTP execution:

```bash
adb push <context_binary> /data/local/tmp/qnn

adb shell
cd /data/local/tmp/qnn

export LD_LIBRARY_PATH=/data/local/tmp/qnn
export ADSP_LIBRARY_PATH=/data/local/tmp/qnn

./qnn-net-run \
    --retrieve_context <context_binary> \
    --backend libQnnHtp.so
```

## Example Inputs

Push the QAIRT example inputs:

```bash
adb shell mkdir -p /data/local/tmp/qnn/examples/images

adb push examples/QAIRT/python/input_list.txt \
    /data/local/tmp/qnn/examples

adb push examples/QAIRT/python/images \
    /data/local/tmp/qnn/examples/images
```

Run inference:

```bash
cd /data/local/tmp/qnn/examples

/data/local/tmp/qnn/qnn-net-run \
    --retrieve_context /data/local/tmp/qnn/mobilenet_v2.bin \
    --backend /data/local/tmp/qnn/libQnnHtp.so \
    --input_list input_list.txt \
    --output_dir output
```

## Application Launch

Grant camera permissions and launch the application:

```bash
adb shell pm grant com.example.singleframeinference android.permission.CAMERA
adb shell am force-stop com.example.singleframeinference
adb shell monkey -p com.example.singleframeinference 1
```
