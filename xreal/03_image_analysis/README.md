To Run:
```
adb shell pm grant com.example.imageanalysis android.permission.CAMERA

adb shell am force-stop com.example.imageanalysis

adb shell monkey -p com.example.imageanalysis 1
```
