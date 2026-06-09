To Run:
```
adb shell pm grant com.example.camerapreview android.permission.CAMERA
adb shell am force-stop com.example.camerapreview
adb shell monkey -p com.example.camerapreview 1
```
