# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# https://github.com/Vaccae/AndroidOpenCVTesserartOCR

set(CMAKE_VERBOSE_MAKEFILE on)

include_directories(/home/tianzx/Downloads/opencv-4.6.0-android-sdkx/OpenCV-android-sdk/sdk/native/jni/include)

include_directories(/home/tianzx/app/cv_npu/cv_aix/src/main/cpp/rknn/rga)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
add_library( # Sets the name of the library.
             rknn4j

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             rknn/native-lib-rknn.cc
             rknn/post_process.cc
             rknn/yolo_image.cc
             rknn/rknn_api.h
             rknn/object_tracker/track_link.cc
             rknn/object_tracker/objects_tracker.cc
             rknn/object_tracker/objects_update.cc
             )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.
find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log

              # https://www.thecodingnotebook.com/2020/04/image-processing-with-opencv-in-android.html
              # jnigraphics lib from NDK is used for Bitmap manipulation in native code
              #jnigraphics-lib
              #jnigraphics
              )

set(JNI_LIB "/home/tianzx/app/cv_npu/cv_aix/src/main/jniLibs")

add_library(libopencv_java4 SHARED IMPORTED )
set_target_properties(libopencv_java4 PROPERTIES IMPORTED_LOCATION "${JNI_LIB}/${ANDROID_ABI}/libopencv_java4.so")

add_library(libc++_shared SHARED IMPORTED )
set_target_properties(libc++_shared PROPERTIES IMPORTED_LOCATION "${JNI_LIB}/${ANDROID_ABI}/libc++_shared.so")

add_library(librknnrt SHARED IMPORTED )
set_target_properties(librknnrt PROPERTIES IMPORTED_LOCATION "${JNI_LIB}/${ANDROID_ABI}/librknnrt.so")

add_library(librga SHARED IMPORTED )
set_target_properties(librga PROPERTIES IMPORTED_LOCATION "${JNI_LIB}/${ANDROID_ABI}/librga.so")


# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       rknn4j

                       #${jnigraphics-lib}
                       -ljnigraphics

                       libopencv_java4

                       libc++_shared

                       librknnrt

                       librga

                       # https://www.cnblogs.com/willhua/p/9692529.html invoke android app assets folder
                       # android

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )